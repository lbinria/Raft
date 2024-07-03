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

(* Replace MaxEntries constant *)
TraceMaxEntries ==
    Trace[1].MaxEntries

(* Can be extracted from init *)
RADefault(varName) ==
    CASE varName = "entries" -> [i \in Server |-> << >>]
    []  varName = "commitIdx" -> [i \in Server |-> 0]
    []  varName = "role" -> [i \in Server |-> "follower"]
    []  varName = "term" -> [i \in Server |-> 0]
    []  varName = "ballots" -> [i \in Server |-> {}]
    []  varName = "ghostEntries" -> [i \in Server |-> [n \in NatSubset |-> {}]]

RAMapVariables(t) ==
    /\
        IF "entries" \in DOMAIN t
        THEN entries' = UpdateVariable(entries, "entries", t)
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
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) >= 1 THEN
            Timeout(logline.event_args[1])
        ELSE
            \E i \in Server : Timeout(i)

IsVote ==
    /\ IsEvent("Vote")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) >= 1 THEN
            Vote(logline.event_args[1])
        ELSE
            \E s \in Server : Vote(s)

IsElectLeader ==
    /\ IsEvent("ElectLeader")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) >= 1 THEN
            ElectLeader(logline.event_args[1])
        ELSE
            \E s \in Server : ElectLeader(s)

IsUpdateTerm ==
    /\ IsEvent("UpdateTerm")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) >= 1 THEN
            UpdateTerm(logline.event_args[1])
        ELSE
            \E s \in Server : UpdateTerm(s)

IsAppendEntry ==
    /\ IsEvent("AppendEntry")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) >= 1 THEN
            AppendEntry(logline.event_args[1])
        ELSE
            \E s \in Server : AppendEntry(s)

IsLearnEntry ==
    /\ IsEvent("LearnEntry")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) >= 1 THEN
            LearnEntry(logline.event_args[1])
        ELSE
            \E s \in Server : LearnEntry(s)

IsLeaderCommit ==
    /\ IsEvent("LeaderCommit")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) >= 1 THEN
            LeaderCommit(logline.event_args[1])
        ELSE
            \E s \in Server : LeaderCommit(s)

IsNonLeaderCommit ==
    /\ IsEvent("NonLeaderCommit")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) >= 1 THEN
            NonLeaderCommit(logline.event_args[1])
        ELSE
            \E s \in Server : NonLeaderCommit(s)

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