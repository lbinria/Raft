---- MODULE AbstractRaftTrace ----

EXTENDS TLC, Sequences, SequencesExt, Naturals, FiniteSets, Bags, Json, IOUtils, AbstractRaft, TVOperators, TraceSpec

(* Override CONSTANTS *)

(* Replace Nil constant *)
TraceNil == "null"

(* Replace Server constant *)
TraceServer ==
    ToSet(JsonTrace[1].Server)

(* Replace Value constant *)
TraceValue ==
    ToSet(JsonTrace[1].Value)

(* Replace MaxTerm constant *)
TraceMaxTerm ==
    JsonTrace[1].MaxTerm

(* Replace MaxEntries constant *)
TraceMaxEntries ==
    JsonTrace[1].MaxEntries

(* Replace Quorum constant *)
TraceQuorum == { Q \in SUBSET Server : 2 * Cardinality(Q) > Cardinality(Server) }

(* Can be extracted from init *)
RADefault(varName) ==
    CASE varName = "entries" -> [s \in Server |-> << >>]
    [] varName = "commitIdx" -> [s \in Server |-> 0]
    [] varName = "role" -> [s \in Server |-> "follower"]
    [] varName = "term" -> [s \in Server |-> 0]
    [] varName = "votedFor" -> [s \in Server |-> None]

RAMapVariables(t) ==
    /\
        IF "entries" \in DOMAIN t
        THEN entries' = MapVariable(entries, "entries", t)
        ELSE TRUE
    /\
        IF "commitIdx" \in DOMAIN t
        THEN commitIdx' = MapVariable(commitIdx, "commitIdx", t)
        ELSE TRUE
    /\
        IF "role" \in DOMAIN t
        THEN role' = MapVariable(role, "role", t)
        ELSE TRUE
    /\
        IF "term" \in DOMAIN t
        THEN term' = MapVariable(term, "term", t)
        ELSE TRUE
    /\
        IF "votedFor" \in DOMAIN t
        THEN votedFor' = MapVariable(votedFor, "votedFor", t)
        ELSE TRUE

IsTimeout ==
    /\ IsEvent("Timeout")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) = 1 THEN
            Timeout(logline.event_args[1])
        ELSE
            \E s \in Server : Timeout(s)

IsVote ==
    /\ IsEvent("Vote")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) = 1 THEN
            Vote(logline.event_args[1])
        ELSE
            \E s \in Server : Vote(s)

IsElectLeader ==
    /\ IsEvent("ElectLeader")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) = 1 THEN
            ElectLeader(logline.event_args[1])
        ELSE
            \E s \in Server : ElectLeader(s)

IsResignLeader ==
    /\ IsEvent("ResignLeader")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) = 1 THEN
            ResignLeader(logline.event_args[1])
        ELSE
            \E s \in Server : ResignLeader(s)

IsAppendEntry ==
    /\ IsEvent("AppendEntry")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) = 1 THEN
            AppendEntry(logline.event_args[1])
        ELSE
            \E s \in Server : AppendEntry(s)

IsLearnEntry ==
    /\ IsEvent("LearnEntry")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) = 1 THEN
            LearnEntry(logline.event_args[1])
        ELSE
            \E s \in Server : LearnEntry(s)

IsCommit ==
    /\ IsEvent("Commit")
    /\
        IF "event_args" \in DOMAIN logline /\ Len(logline.event_args) = 1 THEN
            Commit(logline.event_args[1])
        ELSE
            \E s \in Server : Commit(s)

RATraceNext ==
    \/ IsTimeout
    \/ IsVote
    \/ IsElectLeader
    \/ IsResignLeader
    \/ IsAppendEntry
    \/ IsLearnEntry
    \/ IsCommit

ComposedNext == FALSE

BASE == INSTANCE AbstractRaft
BaseSpec == BASE!Init /\ [][BASE!Next \/ ComposedNext]_vars
====
