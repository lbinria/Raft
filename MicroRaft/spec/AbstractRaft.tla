------------------------------- MODULE AbstractRaft ---------------------------
EXTENDS Naturals, Sequences
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

None == CHOOSE s : s \notin Server

ASSUME QuorumAssumption ==
    \* Quorums are sets of servers and any two quorums intersect.
    /\ Quorum \in SUBSET (SUBSET Server)
    /\ \A Q1, Q2 \in Quorum : Q1 \cap Q2 # {}

VARIABLES
    entries,   \* sequence of entries per server
    commitIdx, \* index up to which entries have been committed
    role,      \* leader, follower, or candidate
    term,      \* current term, increased at election of new leader
    votedFor   \* who did the server vote for in the current term (if any)?

vars == <<entries, commitIdx, role, term, votedFor>>

Entry == [val: Value, term: Nat]

TypeOK ==
    /\ entries \in [Server -> Seq(Entry)]
    /\ commitIdx \in [Server -> Nat]
    /\ \A s \in Server : commitIdx[s] <= Len(entries[s])
    /\ role \in [Server -> {"leader", "follower", "candidate"}]
    /\ term \in [Server -> Nat]
    /\ votedFor \in [Server -> Server \cup {None}]

Min(m,n) == IF m <= n THEN m ELSE n

Init ==
    /\ entries = [s \in Server |-> << >>]
    /\ commitIdx = [s \in Server |-> 0]
    /\ role = [s \in Server |-> "follower"]
    /\ term = [s \in Server |-> 0]
    /\ votedFor = [s \in Server |-> None]

(*****************************************************************************)
(* A follower suspects the current leader to have crashed, becomes candidate *)
(* for the subsequent term and votes for itself.                             *)
(*****************************************************************************)
Timeout(s) ==
    /\ role[s] \in {"follower", "candidate"}
    /\ role' = [role EXCEPT ![s] = "candidate"]
    /\ term' = [term EXCEPT ![s] = @+1]
    /\ votedFor' = [votedFor EXCEPT ![s] = s]
    /\ UNCHANGED <<entries, commitIdx>>

(*****************************************************************************)
(* A server may vote for a candidate if the candidate's term is not behind   *)
(* and if the server has not yet cast a vote for that term. The server that  *)
(* casts the vote becomes a follower.                                        *)
(*****************************************************************************)
Vote(s) == \E cdt \in Server :
    /\ role[cdt] = "candidate"
    /\ \/ term[s] < term[cdt]
       \/ term[s] = term[cdt] /\ votedFor[s] = None
    /\ role' = [role EXCEPT ![s] = "follower"]
    /\ term' = [term EXCEPT ![s] = term[cdt]]
    /\ votedFor' = [votedFor EXCEPT ![s] = cdt]
    /\ UNCHANGED <<entries, commitIdx>>

(*****************************************************************************)
(* A new leader gets elected when a quorum of servers voted for it in the    *)
(* current round.                                                            *)
(*****************************************************************************)
ElectLeader(s) ==
    /\ role[s] = "candidate"
    /\ {srv \in Server : term[srv] = term[s] /\ votedFor[srv] = s} \in Quorum
    /\ role' = [role EXCEPT ![s] = "leader"]
    /\ UNCHANGED <<entries, commitIdx, term, votedFor>>

(*****************************************************************************)
(* A server becomes a follower. This would happen in practice when the       *)
(* server notices that it is behind the term of the current leader.          *)
(*****************************************************************************)
ResignLeader(s) ==
    /\ role' = [role EXCEPT ![s] = "follower"]
    /\ UNCHANGED <<entries, commitIdx, term, votedFor>>

(*****************************************************************************)
(* A leader appends a new value to its log.                                  *)
(*****************************************************************************)
AppendEntry(s) ==
    /\ role[s] = "leader"
    /\ \E v \in Value : entries' = [entries EXCEPT ![s] =
                                   Append(@, [val |-> v, term |-> term[s]])]
    /\ UNCHANGED <<commitIdx, role, term, votedFor>>

(*****************************************************************************)
(* A follower copies an entry from another server. In practice, that server  *)
(* would be a leader but we require just that it is at least as up to date   *)
(* as the server that copies the entry.                                      *)
(*****************************************************************************)
LearnEntry(s) ==
    /\ role[s] = "follower"
    /\ \E ldr \in Server :
          /\ term[ldr] >= term[s]
          /\ \E i \in 1 .. Min(Len(entries[s])+1, Len(entries[ldr])) :
                \* s will copy entry i from ldr if the preceding entry has the same term
                /\ \/ i = 1
                   \/ i > 1 /\ entries[s][i-1].term = entries[ldr][i-1].term
                /\ entries' = [entries EXCEPT ![s] =
                      IF i <= Len(entries[s])
                      THEN [@ EXCEPT ![i] = entries[ldr][i]]
                      ELSE Append(@, entries[ldr][i])]
          /\ term' = [term EXCEPT ![s] = term[ldr]]
          /\ IF term'[s] # term[s]
             THEN votedFor' = [votedFor EXCEPT ![s] = None]
             ELSE votedFor' = votedFor
    /\ UNCHANGED <<commitIdx, role>>

(*****************************************************************************)
(* Increment the commit index when a quorum of servers agrees on the         *)
(* subsequent entry. In the real protocol, the leader learns when this is    *)
(* the case based on the replies to append entry messages, and followers     *)
(* learn the commit index subsequently from later append entry messages      *)
(* because the leader includes its commit index.                             *)
(*****************************************************************************)
Commit(s) == \E Q \in Quorum :
    /\ Len(entries[s]) > commitIdx[s]
    /\ \A srv \in Q :
          /\ Len(entries[srv]) > commitIdx[s]
          /\ entries[srv][commitIdx[s]+1] = entries[s][commitIdx[s]+1]
    /\ commitIdx' = [commitIdx EXCEPT ![s] = @+1]
    /\ UNCHANGED <<entries, role, term, votedFor>>

Next == \E s \in Server :
    \/ Timeout(s)
    \/ Vote(s)
    \/ ElectLeader(s)
    \/ ResignLeader(s)
    \/ AppendEntry(s)
    \/ LearnEntry(s)
    \/ Commit(s)

Spec == Init /\ [][Next]_vars

-------------------------------------------------------------------------------
(*****************************************************************************)
(* Correctness properties                                                    *)
(*****************************************************************************)
AtMostOneLeaderPerTerm == \A s1, s2 \in Server :
    role[s1] = "leader" /\ role[s2] = "leader" /\ term[s1] = term[s2] => s1 = s2

SameTermSameValue ==
    \A s1, s2 \in Server : \A i \in 1 .. Min(Len(entries[s1]), Len(entries[s2])) :
       entries[s1][i].term = entries[s2][i].term =>
         entries[s1][i].val = entries[s2][i].val

CommitsAreStable ==
    \* TLC's warning about the risk of checking liveness properties in the
    \* presence of state constraints can be ignored because this is a safety property
    \A s \in Server : \A v \in Value :
       [](\A i \in 1 .. commitIdx[s] : entries[s][i] = v => [](entries[s][i] = v))


-------------------------------------------------------------------------------
(*****************************************************************************)
(* State constraint for model checking using TLC.                            *)
(*****************************************************************************)
StateConstraint == \A s \in Server :
    /\ term[s] <= MaxTerm
    /\ Len(entries[s]) <= MaxEntries

===============================================================================
