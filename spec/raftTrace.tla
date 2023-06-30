---- MODULE raftTrace ----

EXTENDS TLC, Sequences, SequencesExt, Naturals, FiniteSets, Bags, Json, IOUtils, raft

ASSUME TLCGet("config").mode = "bfs"

VARIABLES l
(* Read trace *)
JsonTrace ==
    IF "TRACE_PATH" \in DOMAIN IOEnv THEN
        ndJsonDeserialize(IOEnv.TRACE_PATH)
    ELSE
        Print(<<"Failed to validate the trace. TRACE_PATH environnement variable was expected.">>, "")

\*JsonTrace ==
\*        ndJsonDeserialize("/home/me/Projects/Raft/trace-tla.ndjson")

(* Replace Nil by string *)
TraceNil == "null"

(* Replace Server constant *)
TraceServer ==
    ToSet(JsonTrace[1].Server)

(* Replace Value constant *)
TraceValue ==
    ToSet(JsonTrace[1].Value)

(* Get trace skipping config line *)
Trace ==
    SubSeq(JsonTrace, 2, Len(JsonTrace))

(* Generic operators *)
Replace(cur, val) == val
AddElement(cur, val) == cur \cup {val}
AddElements(cur, vals) == cur \cup ToSet(vals)
RemoveElement(cur, val) == cur \ {val}
Clear(cur, val) == {}
AppendElement(cur, val) == Append(cur, val)
RemoveKey(cur, val) == [k \in DOMAIN cur |-> IF k = val THEN Nil ELSE cur[k]]
UpdateRec(cur, val) == [k \in DOMAIN cur |-> IF k \in DOMAIN val THEN val[k] ELSE cur[k]]
AddToBag(cur, val) ==
    IF val \in DOMAIN cur THEN
        [cur EXCEPT ![val] = cur[val] + 1]
    ELSE
        cur @@ (val :> 1)

RemoveFromBag(cur, val) ==
    IF val \in DOMAIN cur THEN
        [cur EXCEPT ![val] = cur[val] - 1]
    ELSE
        cur

Add(cur, val) == cur + val

(* Can be extracted from init *)
Default(varName) ==
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

Apply(var, default, op, args) ==
    CASE op = "Replace" -> Replace(var, args[1])
    []   op = "AddElement" -> AddElement(var, args[1])
    []   op = "AddElements" -> AddElements(var, args[1])
    []   op = "RemoveElement" -> RemoveElement(var, args[1])
    []   op = "AddToBag" -> AddToBag(var, args[1])
    []   op = "RemoveFromBag" -> RemoveFromBag(var, args[1])
    []   op = "Add" -> Add(var, args[1])
    []   op = "Clear" -> Clear(var, <<>>)
    []   op = "AppendElement" -> AppendElement(var, args[1])
    []   op = "RemoveKey" -> RemoveKey(var, args[1])
    []   op = "UpdateRec" -> UpdateRec(var, args[1])
    []   op = "Init" -> Replace(var, default)
    []   op = "InitWithValue" -> UpdateRec(default, args[1])

RECURSIVE ExceptAtPath(_,_,_,_,_)
LOCAL ExceptAtPath(var, default, path, op, args) ==
    LET h == Head(path) IN
    IF Len(path) > 1 THEN
        [var EXCEPT ![h] = ExceptAtPath(var[h], default[h], Tail(path), op, args)]
    ELSE
        [var EXCEPT ![h] = Apply(@, default[h], op, args)]

RECURSIVE ApplyUpdates(_,_,_)
LOCAL ApplyUpdates(var, varName, updates) ==
    LET update == Head(updates) IN

    LET applied ==
        IF Len(update.path) > 0 THEN
            ExceptAtPath(var, Default(varName), update.path, update.op, update.args)
        ELSE
            Apply(var, Default(varName), update.op, update.args)
    IN
    IF Len(updates) > 1 THEN
        ApplyUpdates(applied, varName, Tail(updates))
    ELSE
        applied

TraceInit ==
    /\ l = 1
    /\ Init

logline ==
    Trace[l]

MapVariables(t) ==
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

IsEvent(e) ==
    \* Equals FALSE if we get past the end of the log, causing model checking to stop.
    /\ l \in 1..Len(Trace)
    /\ IF "desc" \in DOMAIN logline THEN logline.desc = e ELSE TRUE
    /\ l' = l + 1
    /\ MapVariables(logline)
\*    /\ Next
    /\ allLogs' = allLogs \cup {log[i] : i \in Server}

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

TraceNext ==
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

ComposedNext == TRUE

TraceSpec ==
    \* Because of  [A]_v <=> A \/ v=v'  , the following formula is logically
     \* equivalent to the (canonical) Spec formual  Init /\ [][Next]_vars  .
     \* However, TLC's breadth-first algorithm does not explore successor
     \* states of a *seen* state.  Since one or more states may appear one or
     \* more times in the the trace, the  UNCHANGED vars  combined with the
     \*  TraceView  that includes  TLCGet("level")  is our workaround.
    TraceInit /\ [][TraceNext]_<<l, vars>>

TraceAccepted ==
    LET d == TLCGet("stats").diameter IN
    IF d - 1 = Len(Trace) THEN TRUE
    ELSE Print(<<"Failed matching the trace to (a prefix of) a behavior:", Trace[d],
                    "TLA+ debugger breakpoint hit count " \o ToString(d+1)>>, FALSE)

TraceView ==
    \* A high-level state  s  can appear multiple times in a system trace.  Including the
     \* current level in TLC's view ensures that TLC will not stop model checking when  s
     \* appears the second time in the trace.  Put differently,  TraceView  causes TLC to
     \* consider  s_i  and s_j  , where  i  and  j  are the positions of  s  in the trace,
     \* to be different states.
    <<vars, l>>

TraceAlias ==
    [
        len |-> Len(Trace),
        log     |-> <<TLCGet("level"), Trace[TLCGet("level")]>>,
        enabled |-> [
            Timeout |-> ENABLED \E i \in Server : Timeout(i),
            RequestVote |-> ENABLED \E i, j \in Server : RequestVote(i, j),
            HandleRequestVoteRequest |-> ENABLED \E m \in DOMAIN messages : m.mtype = "RequestVoteRequest" /\ HandleRequestVoteRequest(m.mdest, m.msource, m),
            HandleRequestVoteResponse |-> ENABLED \E m \in DOMAIN messages : m.mtype = "RequestVoteResponse" /\ HandleRequestVoteResponse(m.mdest, m.msource, m),
            BecomeLeader |-> ENABLED \E i \in Server : BecomeLeader(i),
            AdvanceCommitIndex |-> ENABLED \E i \in Server : AdvanceCommitIndex(i),
            Map |-> ENABLED MapVariables(Trace[TLCGet("level")])
        ]
    ]


BASE == INSTANCE raft
BaseSpec == BASE!Init /\ [][BASE!Next \/ ComposedNext]_BASE!vars
-----------------------------------------------------------------------------
=============================================================================