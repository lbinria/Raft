--------------------------- MODULE raftTrace ---------------------------
(***************************************************************************)
(* Simplified specification of 2PC *)
(***************************************************************************)

EXTENDS TLC, Sequences, SequencesExt, Naturals, FiniteSets, Bags, Json, IOUtils, raft, TVOperators, TraceSpec

(* Override CONSTANTS *)

(* Replace Nil constant *)
TraceNil == "null"

(* Replace Server constant *)
TraceServer ==
    ToSet(Trace[1].Server)

(* Replace Value constant *)
TraceValue ==
    ToSet(Trace[1].Value)

(* Replace Quorum constant *)
TraceQuorum ==
    ToSet(Trace[1].Quorum)

(* Replace MaxTerm constant *)
TraceMaxTerm ==
    Trace[1].MaxTerm

(* Replace None constant *)
TraceNone == "null"

(* Replace Term constant *)
TraceTerm ==
    Trace[1].Term

(* Replace Entries constant *)

(* Can be extracted from init *)
RADefault(varName) ==
    (* /\ entries \in [Server -> Seq(Entry)] *)
    CASE varName = "entries" -> [i \in Server |-> << >>]
    []  varName = "commitIdx" -> [i \in Server |-> 0]
    []  varName = "role" -> [i \in Server |-> Follower]
    []  varName = "term" -> [i \in Server |-> 1]
    []  varName = "ballots" -> [i \in Server |-> Nil]
    []  varName = "ghostEntries" -> [i \in Server |-> {}]

RAMapVariables(t) ==
    /\
        IF "entries" \in DOMAIN t
        THEN currentTerm' = UpdateVariable(entries, "entries", t)
        ELSE TRUE
    /\
        IF "commitIdx" \in DOMAIN t
        THEN commitIdx' = UpdateVariable(commitIdx, "commitIdx", t)
        ELSE TRUE
    /\
        IF "role" \in DOMAIN t
        THEN role' = UpdateVariable(role, "role", t)
        ELSE TRUE
    /\
        IF "term" \in DOMAIN t
        THEN term' = UpdateVariable(term, "term", t)
        ELSE TRUE
    /\
        IF "ballots" \in DOMAIN t
        THEN ballots' = UpdateVariable(ballots, "ballots", t)
        ELSE TRUE
    /\
        IF "ghostEntries" \in DOMAIN t
        THEN ghostEntries' = UpdateVariable(ghostEntries, "ghostEntries", t)
        ELSE TRUE

IsTimeout ==
    /\ IsEvent("Timeout")
    /\
        \/
            /\ "node" \in DOMAIN logline
            /\ Timeout(logline.node)
        \/
            /\ \E i \in Server : Timeout(i)

IsVote ==
    /\ IsEvent("Vote")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) >= 1 THEN
            Vote(logline.event_args[1])
        ELSE
            \E s \in DOMAIN Server : Vote(s)

IsElectLeader ==
    /\ IsEvent("ElectLeader")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) >= 1 THEN
            ElectLeader(logline.event_args[1])
        ELSE
            \E s \in DOMAIN Server : ElectLeader(s)

IsUpdateTerm ==
    /\ IsEvent("UpdateTerm")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) >= 1 THEN
            UpdateTerm(logline.event_args[1])
        ELSE
            \E s \in DOMAIN Server : UpdateTerm(s)

IsAppendEntry ==
    /\ IsEvent("AppendEntry")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) >= 1 THEN
            AppendEntry(logline.event_args[1])
        ELSE
            \E s \in DOMAIN Server : AppendEntry(s)

IsLearnEntry ==
    /\ IsEvent("LearnEntry")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) >= 1 THEN
            LearnEntry(logline.event_args[1])
        ELSE
            \E s \in DOMAIN Server : LearnEntry(s)

IsLeaderCommit ==
    /\ IsEvent("LeaderCommit")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) >= 1 THEN
            LeaderCommit(logline.event_args[1])
        ELSE
            \E s \in DOMAIN Server : LeaderCommit(s)

IsNonLeaderCommit ==
    /\ IsEvent("NonLeaderCommit")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) >= 1 THEN
            NonLeaderCommit(logline.event_args[1])
        ELSE
            \E s \in DOMAIN Server : NonLeaderCommit(s)

RATraceNext ==
    \/ IsTimeout
    \/ IsVote
    \/ IsElectLeader
    \/ IsUpdateTerm
    \/ IsAppendEntry
    \/ IsLearnEntry
    \/ IsLeaderCommit
    \/ IsNonLeaderCommit

ComposedNext == FALSE

BASE == INSTANCE raft
BaseSpec == BASE!Init /\ [][BASE!Next \/ ComposedNext]_vars
-----------------------------------------------------------------------------
=============================================================================