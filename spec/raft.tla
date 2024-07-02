------------------------------- MODULE raft ---------------------------
EXTENDS Naturals, Sequences, TLC
(*****************************************************************************)
(* A high-level specification of the fundamental mechanisms of the Raft      *)
(* consensus protocol. This specification abstracts away from message        *)
(* passing but represents the algorithmic essence of Raft.                   *)
(*****************************************************************************)

CONSTANTS 
    Server,    \* set of servers
    Value,     \* values written to the log
    Quorum,    \* set of quorums
    MaxTerm,   \* bound on number of terms for model checking
    MaxEntries \* bound on the length of the entries for model checking

NatSubset == 0..10

Term == NatSubset    \* override for model checking
Index == NatSubset   \* override for model checking

ASSUME QuorumAssumption ==
    \* Quorums are sets of servers and any two quorums intersect.
    /\ Quorum \in SUBSET (SUBSET Server)
    /\ \A Q1, Q2 \in Quorum : Q1 \cap Q2 # {}

VARIABLES 
    entries,   \* sequence of entries per server
    commitIdx, \* index up to which entries have been committed
    role,      \* leader, follower, or candidate
    term,      \* current term, increased at election of new leader
    ballots,   \* record votes cast by each server
    \* history variable used only for proof, not for the algorithm
    ghostEntries \* previous entries on the log, including the actual entry

vars == <<entries, commitIdx, role, term, ballots, ghostEntries>>

Entry == [val: Value, term: Term]
Ballot == Server \X Term

TypeOK ==
    /\ entries \in [Server -> Seq(Entry)]
    /\ commitIdx \in [Server -> Nat]
    /\ \A s \in Server : commitIdx[s] <= Len(entries[s])
    /\ role \in [Server -> {"leader", "follower", "candidate"}]
    /\ term \in [Server -> Term]
    /\ ballots \in [Server -> SUBSET Ballot]
       \* We let ghostEntries be a stream, rather than a sequence,
       \* of sets of entries so that we don't have to worry about its length.
    /\ ghostEntries \in [Server -> [Index -> SUBSET Entry]]

Min(m,n) == IF m <= n THEN m ELSE n
\* term of the last entry of server s, 0 if there is no entry
lastEntryTerm(s) ==
    IF entries[s] = << >> THEN 0
    ELSE entries[s][Len(entries[s])].term

(***************************************************************************)
(* A server s received a quorum of votes for term t. This is a necessary   *)
(* condition for s to become a leader for term t.                          *)
(***************************************************************************)
BackedByQuorum(s,t) ==
    \E Q \in Quorum : \A srv \in Q : <<s,t>> \in ballots[srv]

Init ==
    /\ entries = [s \in Server |-> << >>]
    /\ commitIdx = [s \in Server |-> 0]
    /\ role = [s \in Server |-> "follower"]
    /\ term = [s \in Server |-> 0]
    /\ ballots = [s \in Server |-> {}]
    /\ ghostEntries = [s \in Server |-> [n \in NatSubset |-> {}]]

(*****************************************************************************)
(* A follower suspects the current leader to have crashed, becomes candidate *)
(* for the subsequent term and votes for itself.                             *)
(*****************************************************************************)
Timeout(s) ==
    /\ role[s] \in {"follower", "candidate"}
    /\ role' = [role EXCEPT ![s] = "candidate"]
    /\ \E t \in Term : 
          /\ t > term[s] /\ term' = [term EXCEPT ![s] = t]
          /\ ballots' = [ballots EXCEPT ![s] = @ \union {<<s,t>>}]
    /\ UNCHANGED <<entries, commitIdx, ghostEntries>>

(*****************************************************************************)
(* A server s may vote for a candidate cdt if:                               *)
(* (1)   the term of cdt is not behind the term of s,                        *)
(* (2)   s has not yet cast a vote for that term, and                        *)
(* (3)   cdt contains all committed entries of the server.                   *)
(* In fact, Raft implements condition (3) by checking the condition below:   *)
(* (3')  the term of the last log entry of cdt is higher than that of s      *)
(*       or the terms are equal and the length of cdt's log is at least as   *)
(*       long as the log of s.                                               *)
(* The server s adopts cdt's term and becomes a follower.                    *)
(*****************************************************************************)
Vote(s) == \E cdt \in Server \ {s} :
    /\ term[s] <= term[cdt]
    /\ \A b \in ballots[s] : b[2] # term[cdt]
    /\ LET cidx == commitIdx[s]
       IN  cidx > 0 => 
             /\ Len(entries[cdt]) >= cidx
             /\ entries[cdt][cidx].term = entries[s][cidx].term
    /\ role' = [role EXCEPT ![s] = "follower"]
    /\ term' = [term EXCEPT ![s] = term[cdt]]
    /\ ballots' = [ballots EXCEPT ![s] = @ \union {<<cdt, term[cdt]>>}]
    /\ UNCHANGED <<entries, commitIdx, ghostEntries>>

(*****************************************************************************)
(* A new leader gets elected when a quorum of servers voted for it in the    *)
(* current term.                                                             *)
(*****************************************************************************)
ElectLeader(s) ==
    /\ role[s] = "candidate"
    /\ BackedByQuorum(s, term[s])
    /\ role' = [role EXCEPT ![s] = "leader"]
    /\ UNCHANGED <<entries, commitIdx, term, ballots, ghostEntries>>

(*****************************************************************************)
(* A server becomes a follower when it learns of a higher term value.        *)
(*****************************************************************************)
UpdateTerm(s) == \E srv \in Server :
\*    /\ role[s] = "leader"  
\* allow anybody to update their term when they learn of a more recent one
    /\ term[srv] > term[s]
    /\ role' = [role EXCEPT ![s] = "follower"]
    /\ term' = [term EXCEPT ![s] = term[srv]]
    /\ UNCHANGED <<ballots, entries, commitIdx, ghostEntries>>

(*****************************************************************************)
(* A leader appends a new value to its log.                                  *)
(*****************************************************************************)
AppendEntry(s) == 
    /\ role[s] = "leader"
    /\ \E v \in Value : 
          LET entry == [val |-> v, term |-> term[s]]
          IN  /\ entries' = [entries EXCEPT ![s] = Append(@, entry)]
              /\ ghostEntries' = [ghostEntries EXCEPT ![s] = 
                                    [@ EXCEPT ![Len(entries[s])+1] = 
                                       ghostEntries[s][Len(entries[s])+1] \union {entry}]]
    /\ UNCHANGED <<commitIdx, role, term, ballots>>

(*****************************************************************************)
(* A follower copies the first diverging entry from a leader and clears any  *)
(* subsequent entries.                                                       *)
(*****************************************************************************)
LearnEntry(s) ==
    /\ role[s] = "follower"
    /\ \E ldr \in Server :
          /\ term[ldr] >= term[s]
          /\ role[ldr] = "leader"
          /\ \E n \in 1 .. Min(Len(entries[s])+1, Len(entries[ldr])) :
                /\ n \in 1 .. Len(entries[s]) => 
                       entries[s][n].term # entries[ldr][n].term
                /\ n-1 \in 1 .. Len(entries[s]) => 
                       entries[s][n-1].term = entries[ldr][n-1].term
                /\ entries' = [entries EXCEPT ![s] = 
                      Append(SubSeq(entries[s], 1, n-1), entries[ldr][n])]
                /\ ghostEntries' = [ghostEntries EXCEPT ![s] =
                      [@ EXCEPT ![n] = ghostEntries[s][n] \union {entries[ldr][n]}]]
                   \* make sure the commit index stays in range (should really never update)
                /\ commitIdx' = [commitIdx EXCEPT ![s] =
                      IF n < @ THEN n ELSE @]
          /\ term' = [term EXCEPT ![s] = term[ldr]]
    /\ UNCHANGED <<ballots, role>>

(*****************************************************************************)
(* The two following actions describe how entries are committed.             *)
(* A leader commits an entry of its current term (and implicitly all         *)
(* preceding entries) when it finds a quorum of servers whose logs contain   *)
(* that entry.                                                               *)
(* Followers commit an entry when the leader of their current term committed *)
(* the same entry.                                                           *)
(*****************************************************************************)
LeaderCommit(ldr) ==
    /\ role[ldr] = "leader"
    /\ \E i \in commitIdx[ldr]+1 .. Len(entries[ldr]) : 
          /\ entries[ldr][i].term = term[ldr]
          /\ \E Q \in Quorum : \A srv \in Q :
                /\ i <= Len(entries[srv])
                /\ entries[srv][i].term = entries[ldr][i].term
          /\ commitIdx' = [commitIdx EXCEPT ![ldr] = i]
    /\ UNCHANGED <<entries, role, term, ballots, ghostEntries>>

NonLeaderCommit(s) ==
    /\ role[s] = "follower"
    /\ \E ldr \in Server :
          /\ role[ldr] = "leader"
          /\ term[ldr] = term[s]
          /\ LET i == commitIdx[ldr]
             IN  /\ i \in 1 .. Len(entries[s])
                 /\ entries[s][i].term = entries[ldr][i].term
                 /\ commitIdx' = [commitIdx EXCEPT ![s] = i]
    /\ UNCHANGED <<entries, role, term, ballots, ghostEntries>>

Next == \E s \in Server :
    \/ Timeout(s)
    \/ Vote(s)
    \/ ElectLeader(s)
    \/ UpdateTerm(s)
    \/ AppendEntry(s)
    \/ LearnEntry(s)
    \/ LeaderCommit(s)
    \/ NonLeaderCommit(s)

Spec == Init /\ [][Next]_vars

-------------------------------------------------------------------------------
(*****************************************************************************)
(* Correctness properties                                                    *)
(*****************************************************************************)
AtMostOneLeaderPerTerm == \A s1, s2 \in Server : 
    role[s1] = "leader" /\ role[s2] = "leader" /\ term[s1] = term[s2] => s1 = s2

TermsMonotonic ==
    \A srv \in Server : \A i,j \in 1 .. Len(entries[srv]) :
       i <= j => entries[srv][i].term <= entries[srv][j].term

SameTermSameEntry == 
    \A s1, s2 \in Server : \A i \in 1 .. Min(Len(entries[s1]), Len(entries[s2])) :
       entries[s1][i].term = entries[s2][i].term =>
         entries[s1][i] = entries[s2][i]

\* In fact we have the stronger property that for any two servers and index
\* into their log, if the terms at that index agree, then so does the entire
\* log up to that index.
SameTermSameLog ==
    \A s1, s2 \in Server : \A i \in 1 .. Min(Len(entries[s1]), Len(entries[s2])) :
       entries[s1][i].term = entries[s2][i].term =>
       \A j \in 1 .. i : entries[s1][j] = entries[s2][j]

\* A (potential) leader for a term that is at least as high as the term of 
\* some committed entry must contain that entry.
LeaderComplete ==
    \A s, cdt \in Server : \A i \in 1 .. commitIdx[s] : \A t \in Term :
       t >= entries[s][i].term /\ BackedByQuorum(cdt, t) =>
          /\ i <= Len(entries[cdt])
          /\ entries[cdt][i] = entries[s][i]

AgreeOnCommittedEntries ==
    \A s1, s2 \in Server : \A i \in 1 .. Min(commitIdx[s1], commitIdx[s2]) :
       entries[s1][i] = entries[s2][i]

CommitsAreStable ==
    \* TLC's warning about the risk of checking liveness properties in the
    \* presence of state constraints can be ignored because this is a safety property
    \A s \in Server : \A v \in Value :
       [](\A i \in 1 .. commitIdx[s] : entries[s][i] = v => [](entries[s][i] = v))

(***************************************************************************)
(* Inductive invariants.                                                   *)
(***************************************************************************)

(***************************************************************************)
(* The first inductive invariant implies `AtMostOneLeaderPerTerm'.         *)
(***************************************************************************)
ElectionInv ==
  /\ \* terms in ballots are bounded by the term of the server that cast them,
     \* and there is at most one ballot per term
     \A srv \in Server : \A b \in ballots[srv] : 
        /\ b[2] <= term[srv]
        /\ \A bb \in ballots[srv] : bb[2] = b[2] => bb = b
  /\ \* candidates and leaders have non-zero-terms
     \A srv \in Server : role[srv] \in {"candidate", "leader"} => term[srv] > 0
  /\ \* leaders are supported by a quorum of ballots
     \A ldr \in Server : role[ldr] = "leader" => BackedByQuorum(ldr, term[ldr])

(***************************************************************************)
(* The second inductive invariant implies `SameTermSameLog'. Some of the   *)
(* predicates are written in terms of `ghostEntries' but also apply to the *)
(* current entry since it is included in `ghostEntries'.                   *)
(***************************************************************************)
EntriesTermInv ==
  /\ TermsMonotonic
  /\ \* The current entry at any position is among the ghosts.
     \A srv \in Server : \A i \in 1 .. Len(entries[srv]) :
        entries[srv][i] \in ghostEntries[srv][i]
  /\ \* Terms of (ghost) entries are non-zero and bounded by the term of the server.
     \* Since entries are introduced by leaders, there must be a server who
     \* was backed by a quorum for the entry of the term and for which that
     \* entry is (at least) among the ghosts.
     \A srv \in Server : \A i \in Index : \A e \in ghostEntries[srv][i] :
        /\ e.term \in 1 .. term[srv]
        /\ role[srv] = "candidate" => e.term < term[srv]
        /\ \E ldr \in Server : 
              /\ BackedByQuorum(ldr, e.term)
              /\ e \in ghostEntries[ldr][i]
  /\ \* Entries are copied from leaders, thus if the term of a (ghost) entry
     \* agrees with the term of a leader, the entry must be the same
     \A srv \in Server : \A i \in Index : \A e \in ghostEntries[srv][i]:
     \A ldr \in Server :
        role[ldr] = "leader" /\ e.term = term[ldr] =>
             /\ i \in 1 .. Len(entries[ldr])
             /\ entries[ldr][i] = e
  /\ SameTermSameLog

(***************************************************************************)
(* The third inductive invariant implies `AgreeOnCommittedEntries'.        *)
(***************************************************************************)
CommitInv ==
    \* All committed entries of a leader for its term are backed by a quorum
    /\ \A ldr \in Server : \A i \in 1 .. commitIdx[ldr] :
          BackedByQuorum(ldr, entries[ldr][i].term) =>
          \E Q \in Quorum : \A s \in Q :
             /\ i \in 1 .. Len(entries[s])
             /\ entries[s][i] = entries[ldr][i]
(*
    /\ \A ldr \in Server : \A i \in 1 .. commitIdx[ldr] : 
          role[ldr] = "leader" /\ entries[ldr][i].term = term[ldr] => 
          \E Q \in Quorum : \A s \in Q :
             /\ i \in 1 .. Len(entries[s])
             /\ entries[s][i] = entries[ldr][i]
*)
    \* For every committed entry, there is a committed entry further out
    \* that is backed up by a server who had a majority for the term of
    \* that entry (and could therefore commit it).
    /\ \A srv \in Server : \A i \in 1 .. commitIdx[srv] :
          \E j \in i .. commitIdx[srv] : \E ldr \in Server :
             /\ BackedByQuorum(ldr, entries[srv][j].term)
             /\ j \in 1 .. commitIdx[ldr]
             /\ entries[ldr][j] = entries[srv][j]
    \* A server only votes for a candidate if it contains all committed entries
    /\ \A srv, cdt \in Server : \A i \in 1 .. commitIdx[srv] : \A t \in Term :
          t >= entries[srv][i].term /\ <<cdt, t>> \in ballots[srv] =>
          /\ i <= Len(entries[cdt])
          /\ entries[cdt][i] = entries[srv][i]

-------------------------------------------------------------------------------
(*****************************************************************************)
(* State constraint and symmetry reduction for model checking using TLC.     *)
(*****************************************************************************)
MCTerm == 0 .. MaxTerm
MCIndex == 1 .. MaxEntries
StateConstraint == \A s \in Server :
    /\ term[s] \in MCTerm
    /\ Len(entries[s]) <= MaxEntries

ServerValuePerms == Permutations(Server) \union Permutations(Value)
===============================================================================