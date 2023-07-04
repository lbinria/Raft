--------------------------- MODULE raftTrace2 ---------------------------
(***************************************************************************)
(* Simplified specification of 2PC *)
(***************************************************************************)

EXTENDS TLC, Sequences, SequencesExt, Naturals, FiniteSets, Bags, Json, IOUtils, raft, TVOperators, TraceSpec

(* Override CONSTANTS *)

(* Replace Nil constant *)
TraceNil == "null"

(* Replace Server constant *)
TraceServer ==
    ToSet(JsonTrace[1].Server)

(* Replace Value constant *)
TraceValue ==
    ToSet(JsonTrace[1].Value)

(* Can be extracted from init *)
RADefault(varName) ==
    CASE varName = "currentTerm" -> [i \in Server |-> 1]
    []  varName = "state" -> [i \in Server |-> Follower]
    []  varName = "votedFor" -> [i \in Server |-> Nil]
    []  varName = "votesResponded" -> [i \in Server |-> {}]
    []  varName = "votesGranted" -> [i \in Server |-> {}]
    []  varName = "nextIndex" -> [i \in Server |-> [j \in Server |-> 1]]
    []  varName = "matchIndex" -> [i \in Server |-> [j \in Server |-> 0]]
    []  varName = "messages" -> [m \in {} |-> 0]
    []  varName = "log" -> [i \in Server |-> << >>]
    []  varName = "commitIndex" -> [i \in Server |-> 0]

RAMapVariables(t) ==
    /\
        IF "currentTerm" \in DOMAIN t
        THEN currentTerm' = ApplyUpdates(currentTerm, "currentTerm", t.currentTerm)
        ELSE TRUE
    /\
        IF "state" \in DOMAIN t
        THEN state' = ApplyUpdates(state, "state", t.state)
        ELSE TRUE
    /\
        IF "votedFor" \in DOMAIN t
        THEN votedFor' = ApplyUpdates(votedFor, "votedFor", t.votedFor)
        ELSE TRUE
    /\
        IF "votesResponded" \in DOMAIN t
        THEN votesResponded' = ApplyUpdates(votesResponded, "votesResponded", t.votesResponded)
        ELSE TRUE
    /\
        IF "votesGranted" \in DOMAIN t
        THEN votesGranted' = ApplyUpdates(votesGranted, "votesGranted", t.votesGranted)
        ELSE TRUE
    /\
        IF "nextIndex" \in DOMAIN t
        THEN nextIndex' = ApplyUpdates(nextIndex, "nextIndex", t.nextIndex)
        ELSE TRUE
    /\
        IF "matchIndex" \in DOMAIN t
        THEN matchIndex' = ApplyUpdates(matchIndex, "matchIndex", t.matchIndex)
        ELSE TRUE
    /\
        IF "messages" \in DOMAIN t
        THEN messages' = ApplyUpdates(messages, "messages", t.messages)
        ELSE TRUE
    /\
        IF "log" \in DOMAIN t
        THEN log' = ApplyUpdates(log, "log", t.log)
        ELSE TRUE
    /\
        IF "commitIndex" \in DOMAIN t
        THEN commitIndex' = ApplyUpdates(commitIndex, "commitIndex", t.commitIndex)
        ELSE TRUE



IsRestart ==
    /\ IsEvent("Restart")
    /\
        \/
            /\ "node" \in DOMAIN logline
            /\ Restart(logline.node)
        \/
            \E i \in Server : Restart(i)

IsTimeout ==
    /\ IsEvent("Timeout")
    /\
        \/
            /\ "node" \in DOMAIN logline
            /\ Timeout(logline.node)
        \/
            /\ \E i \in Server : Timeout(i)

IsRequestVote ==
    /\ IsEvent("RequestVoteRequest")
    /\
        \/
            /\ "src" \in DOMAIN logline
            /\ "dest" \in DOMAIN logline
            /\ RequestVote(logline.src, logline.dest)
        \/
            /\ \E i,j \in Server : RequestVote(i, j)

IsBecomeLeader ==
    /\ IsEvent("BecomeLeader")
    /\
        \/
            /\ "node" \in DOMAIN logline
            /\ BecomeLeader(logline.node)
        \/
            /\ \E i \in Server : BecomeLeader(i)

IsHandleRequestVoteRequest ==
    /\ IsEvent("HandleRequestVoteRequest")
    /\ \E m \in DOMAIN messages :
        LET i == m.mdest
        j == m.msource IN
        /\ m.mtype = RequestVoteRequest
        /\ HandleRequestVoteRequest(i, j, m)

IsHandleRequestVoteResponse ==
    /\ IsEvent("HandleRequestVoteResponse")
    /\ \E m \in DOMAIN messages :
        LET i == m.mdest
        j == m.msource IN
        /\ m.mtype = RequestVoteResponse
        /\ HandleRequestVoteResponse(i, j, m)

IsUpdateTerm ==
    /\ IsEvent("UpdateTerm")
    /\ \E m \in DOMAIN messages :
        LET i == m.mdest
        j == m.msource IN
        UpdateTerm(i, j, m)

IsClientRequest ==
    /\ IsEvent("ClientRequest")
    /\
        \/
            /\ "node" \in DOMAIN logline
            /\ "val" \in DOMAIN logline
            /\ ClientRequest(logline.node, logline.val)
        \/
            /\ \E i \in Server, v \in Value : ClientRequest(i, v)

IsAppendEntries ==
    /\ IsEvent("AppendEntries")
    /\ \E m \in DOMAIN messages :
        LET i == m.mdest
        j == m.msource IN
        AppendEntries(i, j)

IsAdvanceCommitIndex ==
    /\ IsEvent("AdvanceCommitIndex")
    /\
        \/
            /\ "node" \in DOMAIN logline
            /\ AdvanceCommitIndex(logline.node)
        \/
            /\ \E i \in Server : AdvanceCommitIndex(i)

IsHandleAppendEntriesRequest ==
    /\ IsEvent("HandleAppendEntriesRequest")
    /\ \E m \in DOMAIN messages :
        LET i == m.mdest
        j == m.msource IN
        /\ m.mtype = AppendEntriesRequest
        /\ HandleAppendEntriesRequest(i, j, m)

IsHandleAppendEntriesResponse ==
    /\ IsEvent("HandleAppendEntriesResponse")
    /\ \E m \in DOMAIN messages :
        LET i == m.mdest
        j == m.msource IN
        /\ m.mtype = AppendEntriesResponse
        /\ HandleAppendEntriesResponse(i, j, m)

RATraceNext ==
    /\
        \/ IsRestart
        \/ IsTimeout
        \/ IsRequestVote
        \/ IsBecomeLeader
        \/ IsHandleRequestVoteRequest
        \/ IsHandleRequestVoteResponse
        \/ IsUpdateTerm
        \/ IsClientRequest
        \/ IsAppendEntries
        \/ IsAdvanceCommitIndex
        \/ IsHandleAppendEntriesRequest
        \/ IsHandleAppendEntriesResponse
    /\ allLogs' = allLogs \cup {log[i] : i \in Server}



ComposedNext == TRUE

BASE == INSTANCE raft
BaseSpec == BASE!Init /\ [][BASE!Next \/ ComposedNext]_vars
-----------------------------------------------------------------------------
=============================================================================