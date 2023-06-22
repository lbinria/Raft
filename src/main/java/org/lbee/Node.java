package org.lbee;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.lbee.instrumentation.TraceInstrumentation;
import org.lbee.instrumentation.VirtualField;
import org.lbee.instrumentation.clock.SharedClock;
import org.lbee.models.CandidateState;
import org.lbee.models.ClusterInfo;
import org.lbee.models.Entry;
import org.lbee.models.LeaderState;
import org.lbee.models.NodeInfo;
import org.lbee.models.NodeState;
import org.lbee.models.messages.AppendEntriesRequest;
import org.lbee.models.messages.AppendEntriesResponse;
import org.lbee.models.messages.Message;
import org.lbee.models.messages.RequestVoteRequest;
import org.lbee.models.messages.RequestVoteResponse;

public class Node {
    private long term;
    private int commitIndex;
    private long matchIndex;

    private long lastHeartbeat;
    private NodeState state;
    private final ArrayList<Entry> logs;
    private String votedFor = "";
    private CandidateState candidateState;
    private LeaderState leaderState;

    // Information about nodes cluster
    private final NodeInfo nodeInfo;
    private final ClusterInfo clusterInfo;

    private Configuration configuration;

    private final Random randTimeout;
    private final Random randEvent;

    private final Network network;
    private final Server server;

    private boolean shutdown;

    private long electionTimeout;

    // commitIndex
    public int getLastLogIndex() {
        return logs.size();
    }

    public long getLastLogTerm() {
        return logs.size() == 0 ? 0 : logs.get(logs.size() - 1).getTerm();
    }


    private TraceInstrumentation spec;
    private final VirtualField specState;
    private final VirtualField specVotedFor;
    private final VirtualField specVotesResponded;
    private final VirtualField specVotesGranted;
    private final VirtualField specMatchIndex;
    private final VirtualField specNextIndex;
    private final VirtualField specCommitIndex;
    private final VirtualField specCurrentTerm;
    private final VirtualField specMessages;
    private final VirtualField specLog;

    private boolean reduceSSflag;

    public Node(String nodeName, Configuration configuration) throws IOException {
        // Utils
        this.configuration = configuration;
        this.clusterInfo = configuration.getClusterInfo();
        this.nodeInfo = clusterInfo.getNode(nodeName);

        this.term = 1;
        this.state = NodeState.Follower;
        this.logs = new ArrayList<>();
        this.randTimeout = new Random(nodeInfo.seed());
        this.randEvent = new Random(6);
        this.network = new Network();

        // Listen for connections
        this.server = new Server(nodeInfo.port());
        // this.server.start();
        // System.out.printf("Node %s is listening on port %s. Seed: %s.\n", nodeInfo.name(), nodeInfo.port(), nodeInfo.seed());

        this.lastHeartbeat = System.currentTimeMillis();

        this.shutdown = false;

        electionTimeout = 1000 + randTimeout.nextInt(0, 5000);
        System.out.printf("election timeout %s.\n", electionTimeout);

        final SharedClock clock = SharedClock.get("raft.clock");
        clock.reset();
        this.spec = new TraceInstrumentation(nodeInfo.name() + ".ndjson", clock);

        this.spec = new TraceInstrumentation(nodeInfo.name() + ".ndjson", SharedClock.get("raft.clock"));
        // can we have getVariable().getField().getField()....
        this.specState = spec.getVariable("state").getField(nodeInfo.name());
        this.specVotedFor = spec.getVariable("votedFor").getField(nodeInfo.name());
        this.specVotesResponded = spec.getVariable("votesResponded").getField(nodeInfo.name());
        this.specVotesGranted = spec.getVariable("votesGranted").getField(nodeInfo.name());
        this.specNextIndex = spec.getVariable("nextIndex").getField(nodeInfo.name());
        this.specMatchIndex = spec.getVariable( "matchIndex").getField(nodeInfo.name());
        this.specCommitIndex = spec.getVariable("commitIndex").getField(nodeInfo.name());
        this.specCurrentTerm = spec.getVariable("currentTerm").getField(nodeInfo.name());
        this.specMessages = spec.getVariable("messages");
        this.specLog = spec.getVariable("log").getField(nodeInfo.name());
        // Feature flags
        this.reduceSSflag = true;
    }

    private void setState(NodeState state) {
        this.state = state;
        // this.spec.notify(specState, SET, state.toString());
        this.specState.set(state.toString());
    }

    private void toCandidate() {
        setState(NodeState.Candidate);
        candidateState = new CandidateState();
    }

    private void toLeader() {
        setState(NodeState.Leader);
        leaderState = new LeaderState(candidateState.getGranted());
        // deja fait dans setState(state) ?
        // pourquoi toCandidate() fait pas ca?
        specState.set(state.toString());
    }

    private void toFollower() {
        setState(NodeState.Follower);
        specState.set(state.toString());
    }

    public void start() {
        // accept connections
        this.server.start();
        System.out.printf("Node %s is listening on port %s. Seed: %s.\n", nodeInfo.name(), nodeInfo.port(), nodeInfo.seed());
        // connect to all other nodes
        clusterInfo.getNodes().stream()
            .filter(n -> !n.name().equals(nodeInfo.name()))
            .forEach(n -> network.addConnection(n.name(), n.hostname(), n.port()));
    }

    private void restart() {
        System.out.printf("Node %s restarted.\n", nodeInfo.name());
//    /\ state'          = [state EXCEPT ![i] = Follower]
//                /\ votesResponded' = [votesResponded EXCEPT ![i] = {}]
//                /\ votesGranted'   = [votesGranted EXCEPT ![i] = {}]
//\*    /\ voterLog'       = [voterLog EXCEPT ![i] = [j \in {} |-> <<>>]]
//                /\ nextIndex'      = [nextIndex EXCEPT ![i] = [j \in Server |-> 1]]
//                /\ matchIndex'     = [matchIndex EXCEPT ![i] = [j \in Server |-> 0]]
//                /\ commitIndex'    = [commitIndex EXCEPT ![i] = 0]

        toFollower();
        if (candidateState != null) {
            candidateState.clear();
            // Notify spec
            // Comment or uncomment line below doesn't change the size of state space
            // specVotesResponded.clear();
            // specVotesGranted.clear();
        }
        else if (leaderState != null) {
            leaderState.clear();
            // Comment or uncomment line below doesn't change the size of state space
            // specNextIndex.init();
            // specMatchIndex.clear();
        }

        commitIndex = 0;
        // Comment or uncomment line below doesn't change the size of state space
        //specCommitIndex.set(0);

        spec.commitChanges("Restart");
    }


    public void run() throws IOException {
        long start = System.currentTimeMillis();
        // Prepare shutdown trigger
        final IntervalTrigger shutdownTrigger = new IntervalTrigger(() -> {
            try {
                shutdown();
            } catch (IOException e) {
                // throw new RuntimeException(e);
                System.out.printf("Node %s couldn't shutdown.\n", nodeInfo.name());
            }
        }, 60000);

        final IntervalTrigger sendHeartbeatTrigger =  new IntervalTrigger(() -> {
            try {
                if (state == NodeState.Leader)
                    sendHeartbeat();
            } catch (IOException e) {
                // throw new RuntimeException(e);
                System.out.printf("Node %s couldn't heartbeat.\n", nodeInfo.name());
            }
        }, 500);

        final IntervalTrigger restartTrigger = new IntervalTrigger(() -> {
            if (randEvent.nextInt(0, 10) == 0)
                restart();
        }, 1000);

        final IntervalTrigger clientRequestTrigger = new IntervalTrigger(() -> {
            if (randEvent.nextInt(0, 2) == 0)
                clientRequest();
        }, 1000);

        final IntervalTrigger appendEntriesTrigger = new IntervalTrigger(() -> {
            if (randEvent.nextInt(0, 5) == 0) {
                try {
                    if (state == NodeState.Leader)
                        appendEntries();
                } catch (IOException e) {
                    // throw new RuntimeException(e);
                   System.out.printf("Node %s couldn't append entries.\n", nodeInfo.name());
                }
            }
        }, 1000);

        while (!shutdown) {
            // Leader sends heartbeat every 500ms
            sendHeartbeatTrigger.run();
            // Start new election if it hasn't received heartbeat for some time
            if (System.currentTimeMillis() >= lastHeartbeat + electionTimeout 
                    && (state == NodeState.Follower || state == NodeState.Candidate)){
                timeout();
            }

            takeMessage();

            // Simulate a client request to that node
            clientRequestTrigger.run();
            // Append entries from time to time
            appendEntriesTrigger.run();
            // Restart node randomly
            restartTrigger.run();
            // Shutdown at some point
            shutdownTrigger.run();
        }

    }


    // TLA Timeout
    public void timeout() throws IOException {
        assert state == NodeState.Follower || state == NodeState.Candidate : "Only follower or candidate can start an election";

        // Next election timeout will be between 5-10 s.
        lastHeartbeat = System.currentTimeMillis();
        electionTimeout = 5000 + randTimeout.nextInt(0, 5000);


        // Change state to candidate
        toCandidate();
        // Vote for himself
        candidateState.getResponded().add(nodeInfo.name());
        candidateState.getGranted().add(nodeInfo.name());
        votedFor = nodeInfo.name();
        // Add term
        term += 1;
        // Comment or uncomment line below doesn't change the size of state space
//        specCurrentTerm.apply("Add", 1);

        System.out.printf("Node %s is %s.\n", nodeInfo.name(), state);
        spec.commitChanges("Timeout");

        // Simulate message exchange between this node and himself (see in raft spec, localhost exchange messages with itself)

        // Necessary log if we want obtains Quorum, because trace spec can check holes
        // in variable, but not hole in event
        // Reproduce bug by commenting this bloc, show with tla+ debug how to find what's wrong ! by using hit count and ENABLED
        if (reduceSSflag) {
            final Message fakeMessage = new RequestVoteRequest(nodeInfo.name(), nodeInfo.name(), term, getLastLogTerm(), getLastLogIndex(),0);
            specMessages.apply("AddToBag", fakeMessage);
        }
        spec.commitChanges("RequestVoteRequest");
        specVotedFor.set(nodeInfo.name());
        spec.commitChanges("HandleRequestVoteRequest");
        specVotesGranted.add(nodeInfo.name());
        spec.commitChanges("HandleRequestVoteResponse");


        sendVoteRequest();
    }

    public void takeMessage() throws IOException {
        // Check box
        final Message message = server.getMessageBox().take(nodeInfo.name());
        // No message
        if (message == null)
            return;


        // Update term first
        if (message.getTerm() > term)
            updateTerm(message.getTerm());

        //specMessages.apply("RemoveFromBag", message);

        // Redirect according to message type
        if (message instanceof final RequestVoteRequest requestVoteRequest)
            handleVoteRequest(requestVoteRequest);
        else if (message instanceof final RequestVoteResponse requestVoteResponse)
            handleVoteReply(requestVoteResponse);
        else if (message instanceof final AppendEntriesRequest appendEntriesRequest)
        {
            if (appendEntriesRequest.getEntries().isEmpty())
                handleHeartbeat();
            else
                handleAppendEntriesRequest(appendEntriesRequest);
        }
        else if (message instanceof final AppendEntriesResponse appendEntriesResponse) {
            handleAppendEntriesResponse(appendEntriesResponse);
        }


    }


    // TLA UpdateTerm
    private void updateTerm(long newTerm) {
        term = newTerm;
        toFollower();
        votedFor = "";
        spec.commitChanges("UpdateTerm");
    }

    public void sendHeartbeat() throws IOException {
        assert state == NodeState.Leader : "Only leader can send heartbeat";

        for (NodeInfo ni : clusterInfo.getNodes()) {
            // Skip this
            if (nodeInfo.name().equals(ni.name()))
                continue;

            final Message heartbeatMessage = new AppendEntriesRequest(nodeInfo.name(), ni.name(), term, 0, 0, new ArrayList<>(), commitIndex, 0);
            // networkManagers.get(ni.name()).send(heartbeatMessage);
            network.send(ni.name(),heartbeatMessage);
        }
    }



    public void handleHeartbeat() {
        System.out.printf("Node %s handle heartbeat.\n", nodeInfo.name());
        lastHeartbeat = System.currentTimeMillis();
    }

    public void sendVoteRequest() throws IOException {
        assert state == NodeState.Candidate : "Node should be candidate in order to request a vote.";

        System.out.println("Start sending vote requests.");

        for (NodeInfo ni : clusterInfo.getNodes()) {

            // Skip vote request for node that responded
            if (ni.name().equals(nodeInfo.name()) || candidateState.getResponded().contains(ni.name()))
                continue;

            final Message message = new RequestVoteRequest(nodeInfo.name(), ni.name(), term, getLastLogTerm(), getLastLogIndex(),0);

            if (reduceSSflag)
                specMessages.apply("AddToBag", message);

            spec.commitChanges("RequestVoteRequest");
            // networkManagers.get(ni.name()).send(message);
            network.send(ni.name(),message);
        }
    }

    public void handleVoteRequest(RequestVoteRequest m) throws IOException {
        System.out.printf("handleVoteRequest %s.\n", m.toString());

        boolean logOk = m.getLastLogTerm() > getLastLogTerm() || m.getLastLogTerm() == getLastLogTerm() && m.getLastLogIndex() >= getLastLogIndex();
        boolean grant = m.getTerm() == term && logOk && (votedFor.equals(m.getFrom()) || votedFor.equals(""));

        if (m.getTerm() <= term && grant) {
            votedFor = m.getFrom();
            specVotedFor.set(votedFor);
        }

        // Reply to vote request
        final Message response = new RequestVoteResponse(nodeInfo.name(), m.getFrom(), term, grant, 0);
        spec.commitChanges("HandleRequestVoteRequest");
        // networkManagers.get(m.getFrom()).send(response);
        network.send(m.getFrom(),response);
    }

    public void handleVoteReply(RequestVoteResponse m) throws IOException {
        assert state == NodeState.Candidate : "Only candidate can handle vote reply.";
        assert m.getTerm() == term;

        System.out.printf("handleVoteReply %s.\n", m);

        // Add node that responded to my vote request
        candidateState.getResponded().add(m.getFrom());

        if (m.isGranted()) {
            // Add node that granted a vote to me
            candidateState.getGranted().add(m.getFrom());
            specVotesGranted.add(m.getFrom());
        }

        spec.commitChanges("HandleRequestVoteResponse");

        if (state == NodeState.Candidate && candidateState.getGranted().size() >= clusterInfo.getQuorum())
            becomeLeader();
    }

    // TLA:BecomeLeader
    public void becomeLeader() throws IOException {
        // Note: weird ! assertion doesn't trigger when node is leader, it seems like it doesn't check == Candidate
        assert state == NodeState.Candidate : "Only a candidate can become a leader.";
        assert candidateState.getGranted().size() >= clusterInfo.getQuorum() : "A candidate should have a minimum of vote to become a leader.";
        // Note: bug found with trace validation at 57th depth
//        assert candidateState.getGranted().size() > clusterInfo.getQuorum() : "A candidate should have a minimum of vote to become a leader.";

        toLeader();
        sendHeartbeat();
        System.out.printf("Node %s is Leader.\n", nodeInfo.name());

        for (NodeInfo ni : clusterInfo.getNodes()) {
            leaderState.getNextIndexes().put(ni.name(), logs.size());
            leaderState.getMatchIndexes().put(ni.name(), 0);
        }

        spec.commitChanges("BecomeLeader");
    }

    private void clientRequest() {
        if (state != NodeState.Leader)
            return;
//    /\ LET entry == [term  |-> currentTerm[i],
//                value |-> v]
//        newLog == Append(log[i], entry)
//        IN  log' = [log EXCEPT ![i] = newLog]

        final Entry entry = new Entry(term, Helpers.pickRandomVal(configuration));
        logs.add(entry);

        System.out.printf("Node %s receive a client request and add entry %s.\n", nodeInfo.name(), entry);
        specLog.apply("AppendElement", entry);
        spec.commitChanges("ClientRequest");
    }

    private void appendEntries() throws IOException {
        assert state == NodeState.Leader : "Only leader can send append entries requests.";

        for (NodeInfo ni : clusterInfo.getNodes()) {
            if (!ni.name().equals(nodeInfo.name()))
                appendEntries(ni.name());
        }
    }

    private void appendEntries(String nodeName) throws IOException {
        // TODO optimization: when entries empty, quit

        int nextIndex = leaderState.getNextIndexes().get(nodeName);
        int previousIndex = nextIndex - 1;
        // Note >= instead of > because of discrepancy between TLA base index = 1 and java => 0
//        prevLogTerm == IF prevLogIndex > 0 THEN
//        log[i][prevLogIndex].term
//        ELSE
//        0
        long previousLogTerm = previousIndex >= 0 ? logs.get(previousIndex).getTerm() : 0;

        // Note: -1
        final int lastEntryIndex = Math.min(logs.size() - 1, nextIndex);

        // Note: +1 exclusive
        final List<Entry> entries = logs.subList(nextIndex, lastEntryIndex + 1);
        System.out.printf("Take entries [%s, %s]\n", nextIndex, lastEntryIndex);

        int msgCommitIndex = Math.min(commitIndex, lastEntryIndex);
        final Message appendEntriesRequest = new AppendEntriesRequest(nodeInfo.name(), nodeName, term, previousIndex, previousLogTerm, entries, msgCommitIndex, 0);

        // Note: Recompute some variable to log (because of index based is one in TLA+)
        int msgCommitIndexLog = Math.min(commitIndex, lastEntryIndex + 1);
        int previousIndexLog = previousIndex + 1;
        final Message appendEntriesRequestLog = new AppendEntriesRequest(nodeInfo.name(), nodeName, term, previousIndexLog, previousLogTerm, entries, msgCommitIndexLog, 0);
        specMessages.apply("AddToBag", appendEntriesRequestLog);
        System.out.println(appendEntriesRequest);
        spec.commitChanges("AppendEntries");
        // networkManagers.get(nodeName).send(appendEntriesRequest);
        network.send(nodeName,appendEntriesRequest);
    }

    private void advanceCommitIndex() {

        int maxAgreeIndex = -1;
        for (int i = logs.size(); i > 0; i--) {
            int finalI = i;
            boolean allAgree = leaderState.getQuorum().stream().allMatch(nodeName -> leaderState.getMatchIndexes().get(nodeName) >= finalI);

            if (allAgree)
            {
                maxAgreeIndex = i;
                break;
            }
        }

        if (maxAgreeIndex != -1 && logs.get(maxAgreeIndex).getTerm() == term)
            commitIndex = maxAgreeIndex;

        // +1 for base-1 indexing in spec
        specCommitIndex.set(commitIndex + 1);
        spec.commitChanges("AdvanceCommitIndex");
    }

    private void handleAppendEntriesRequest(AppendEntriesRequest appendEntriesRequest) throws IOException {
//        LET logOk == \/ m.mprevLogIndex = 0
//                 \/ /\ m.mprevLogIndex > 0
//                /\ m.mprevLogIndex <= Len(log[i])
//                /\ m.mprevLogTerm = log[i][m.mprevLogIndex].term

        System.out.printf("handleAppendEntriesRequest %s.\n", appendEntriesRequest);

        long previousLogIndex = appendEntriesRequest.getLastLogIndex();

        boolean logOk = previousLogIndex == -1 ||
                (previousLogIndex >= 0
                        &&  previousLogIndex < logs.size()
                        && appendEntriesRequest.getLastLogTerm() == logs.get((int)previousLogIndex).getTerm());

        // Return to follower state
        if (state == NodeState.Candidate) {
            if (appendEntriesRequest.getTerm() == term)
                toFollower();
        }
        else if (state == NodeState.Follower) {
            if (appendEntriesRequest.getTerm() == term && logOk)
                acceptAppendEntries(appendEntriesRequest);
            else
                rejectAppendEntries(appendEntriesRequest.getFrom());
        }
        spec.commitChanges("HandleAppendEntriesRequest");
    }

    private void acceptAppendEntries(AppendEntriesRequest appendEntriesRequest) throws IOException {
        System.out.print("Accept append entries.\n");
        int index = (int)appendEntriesRequest.getLastLogIndex() + 1;

        // already done with request
        //\/ m.mentries = << >>
        //\/ /\ m.mentries /= << >>
        ///\ Len(log[i]) >= index
        ///\ log[i][index].term = m.mentries[1].term
        if (appendEntriesRequest.getEntries().isEmpty() || (logs.size() > index && logs.get(index).getTerm() == appendEntriesRequest.getEntries().get(0).getTerm())) {
//          /\ commitIndex' = [commitIndex EXCEPT ![i] = m.mcommitIndex]
            commitIndex = appendEntriesRequest.getCommitIndex();
            specCommitIndex.set(commitIndex);
//            /\ Reply([mtype           |-> AppendEntriesResponse,
//            mterm           |-> currentTerm[i],
//            msuccess        |-> TRUE,
//            mmatchIndex     |-> m.mprevLogIndex +
//            Len(m.mentries),
//            msource         |-> i,
//            mdest           |-> j],
//            m)
            int matchIndex = (int)appendEntriesRequest.getLastLogIndex() + appendEntriesRequest.getEntries().size();
            Message appendEntriesResponse = new AppendEntriesResponse(nodeInfo.name(), appendEntriesRequest.getFrom(), term, true, matchIndex, 0);
            network.send(appendEntriesRequest.getFrom(), appendEntriesResponse);
        }

        // TODO implement Conflict
//        \/ \* conflict: remove 1 entry
//        /\ m.mentries /= << >>
//        /\ Len(log[i]) >= index
//        /\ log[i][index].term /= m.mentries[1].term
//        /\ LET new == [index2 \in 1..(Len(log[i]) - 1) |->
//        log[i][index2]]
//        IN log' = [log EXCEPT ![i] = new]
//        /\ UNCHANGED <<serverVars, commitIndex, messages>>
//        \/ \* no conflict: append entry

        // No conflict append entries
        if (!appendEntriesRequest.getEntries().isEmpty() && logs.size() == appendEntriesRequest.getLastLogIndex() + 1) {
            System.out.printf("YOPI.\n");
            logs.addAll(appendEntriesRequest.getEntries());
            specLog.apply("AppendElement", appendEntriesRequest.getEntries().get(0));
        }
    }

    private void handleAppendEntriesResponse(AppendEntriesResponse appendEntriesResponse) throws IOException {
        System.out.printf("handleAppendEntriesResponse %s.\n", appendEntriesResponse);
        // TODO implement
        spec.commitChanges("HandleAppendEntriesResponse");
        // Advance index
        advanceCommitIndex();
    }

    private void rejectAppendEntries(String to) throws IOException {
        System.out.print("Reject append entries.\n");
        Message appendEntriesResponse = new AppendEntriesResponse(nodeInfo.name(), to, term, false, 0, 0);
        // networkManagers.get(to).send(appendEntriesResponse);
        network.send(to,appendEntriesResponse);
    }



    public void shutdown() throws IOException {
        network.shutdown();
        shutdown = true;
    }

    /**
     * Is the manager has been shutdown
     * @return True if manager has been shutdown
     */
    public boolean isShutdown() { return shutdown; }


}
