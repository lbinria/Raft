package org.lbee.protocol;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.lbee.instrumentation.trace.TLATracer;
import org.lbee.instrumentation.trace.VirtualField;
import org.lbee.config.Configuration;
import org.lbee.protocol.state.CandidateState;
import org.lbee.models.ClusterInfo;
import org.lbee.models.Entry;
import org.lbee.protocol.state.LeaderState;
import org.lbee.models.NodeInfo;
import org.lbee.protocol.state.NodeState;
import org.lbee.models.messages.AppendEntriesRequest;
import org.lbee.models.messages.AppendEntriesResponse;
import org.lbee.models.messages.Message;
import org.lbee.models.messages.RequestVoteRequest;
import org.lbee.models.messages.RequestVoteResponse;
import org.lbee.network.Network;
import org.lbee.network.Server;
import org.lbee.helpers.Helpers;

public class Node {
    public final TLATracer tracer;

    // term number (initialized to 1 then incremented at each election)
    private long term;

    // index of the log entry that is safely replicated on a majority of nodes (called quorum)
    private int commitIndex;

    // For each server, index of the next log entry to send to that server (used by leader to replicate log entries)
    private long matchIndex;

    private long lastHeartbeat;

    // State of the node (Follower, Candidate, Leader)
    private NodeState state;

    // All logs entries
    private final ArrayList<Entry> logs;

    private String votedFor = "";
    private CandidateState candidateState;
    private LeaderState leaderState;

    // Information about nodes cluster
    private final NodeInfo nodeInfo;
    private final ClusterInfo clusterInfo;

    private Configuration configuration;

    // Random number generator
    private final Random randTimeout;
    private final Random randEvent;

    // Network
    private final Network network;
    private final Server server;

    // Shutdown flag
    private boolean shutdown;

    // Election timeout
    private long electionTimeout;

    // CommitIndex
    public int getLastLogIndex() {
        return logs.size();
    }

    public long getLastLogTerm() {
        return logs.isEmpty() ? 0 : logs.get(logs.size() - 1).getTerm();
    }

    // Trace variables
    private final VirtualField traceState;
    private final VirtualField traceVotedFor;
    private final VirtualField traceVotesResponded;
    private final VirtualField traceVotesGranted;
    private final VirtualField traceMatchIndex;
    private final VirtualField traceNextIndex;
    private final VirtualField traceCommitIndex;
    private final VirtualField traceCurrentTerm;
    private final VirtualField traceLog;

    private boolean reduceSSflag;

    public Node(String nodeName, Configuration configuration, TLATracer tracer) {
        // Utils
        this.configuration = configuration;
        this.clusterInfo = configuration.getClusterInfo();
        this.nodeInfo = clusterInfo.getNode(nodeName);

        this.term= 1;
        this.state = NodeState.Follower;
        this.logs = new ArrayList<>();
        this.randTimeout = new Random(nodeInfo.seed());
        this.randEvent = new Random(nodeInfo.seed() + 1423);
        this.network = new Network();

        // Listen for connections
        this.server = new Server(nodeInfo.port());
        // this.server.start();
        // System.out.printf("Node %s is listening on port %s. Seed: %s.\n", nodeInfo.name(), nodeInfo.port(), nodeInfo.seed());

        this.lastHeartbeat = System.currentTimeMillis();

        this.shutdown = false;

        //leaderState = new LeaderState(/*quorum*/);

        electionTimeout = 1000 + randTimeout.nextInt(0, 5000);
        System.out.printf("election timeout %s.\n", electionTimeout);

        // Tracer initialization
        this.tracer = tracer;

        // Initialize trace variables
        this.traceState = tracer.getVariableTracer("state");
        this.traceVotedFor = tracer.getVariableTracer("votedFor");
        this.traceVotesResponded = tracer.getVariableTracer("votesResponded");
        this.traceVotesGranted = tracer.getVariableTracer("votesGranted");
        this.traceNextIndex = tracer.getVariableTracer("nextIndex");
        this.traceMatchIndex = tracer.getVariableTracer("matchIndex");
        this.traceCommitIndex = tracer.getVariableTracer("commitIndex");
        this.traceCurrentTerm = tracer.getVariableTracer("currentTerm");
        this.traceLog = tracer.getVariableTracer("log");

        // Feature flags
        this.reduceSSflag = true;
    }

    private void setState(NodeState state) {
        this.state = state;
    }

    private void toCandidate() {
        setState(NodeState.Candidate);
        candidateState = new CandidateState();
    }

    private void toLeader() throws IOException {
        setState(NodeState.Leader);

        if (leaderState == null)
            leaderState = new LeaderState();
    }

    private void toFollower() {
        setState(NodeState.Follower);
    }

    public void start() {
        // accept connections
        this.server.start();
        System.out.printf("Node %s is listening on port %s. Seed: %s.\n", nodeInfo.name(), nodeInfo.port(), nodeInfo.seed());
    }

    public void connect() {
        // connect to all other nodes
        clusterInfo.getNodes().stream()
                .filter(n -> !n.name().equals(nodeInfo.name()))
                .forEach(n -> network.addConnection(n.name(), n.hostname(), n.port()));
    }

    private void restart() throws InterruptedException, IOException {
        System.out.printf("Node %s restarted.\n", nodeInfo.name());

        toFollower();

        // PARAM : state'
        String stateString = this.state.toString().substring(0, 1).toUpperCase(Locale.ROOT) + this.state.toString().substring(1).toLowerCase(Locale.ROOT);
        this.traceState.getField(this.nodeInfo.name()).update(stateString);
        
        // PARAM : votesResponded'
        this.traceVotesResponded.getField(this.nodeInfo.name()).clear();

        // PARAM : votesGranted'
        this.traceVotesGranted.getField(this.nodeInfo.name()).clear();


        if (candidateState != null) {
            candidateState.clear();
        }
        else if (leaderState != null) {
            leaderState.clear();
            for (NodeInfo ni : clusterInfo.getNodes()) {
                leaderState.getNextIndexes().put(ni.name(), 1);
                leaderState.getMatchIndexes().put(ni.name(), 0);

                // PARAM : parameter nextIndex'
                this.traceNextIndex.getField(ni.name()).update(1);

                // PARAM : parameter matchIndex'
                this.traceMatchIndex.getField(ni.name()).update(0);
            
            }
        }

        commitIndex = 0;

        // PARAM : parameter commitIndex'
        this.traceCommitIndex.getField(this.nodeInfo.name()).update(0);

        // OK : trace Restart
        tracer.log("Restart", new Object[] { nodeInfo.name() });

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
            if (randEvent.nextInt(0, 8) == 0) {
                try {
                    restart();
                } catch (InterruptedException | IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }, 1000);

        // Simulate client request (only leader can handle client request)
        final IntervalTrigger clientRequestTrigger = new IntervalTrigger(() -> {
            if (randEvent.nextInt(0, 2) == 0) {
                try {
                    clientRequest();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }, 1000);

        final IntervalTrigger appendEntriesTrigger = new IntervalTrigger(() -> {
            //if (randEvent.nextInt(0, 5) == 0) {
                try {
                    if (state == NodeState.Leader)
                        appendEntries();
                } catch (IOException e) {
                    // throw new RuntimeException(e);
                   System.out.printf("Node %s couldn't append entries.\n", nodeInfo.name());
                }
            //}
        }, 1000);

        final IntervalTrigger displayLogTrigger = new IntervalTrigger(() -> {
            System.out.printf("LOG: %s.\n", logs.stream().map(Entry::getContent).collect(Collectors.toList()));
        }, 3000);

        while (!shutdown) {
            // Leader sends heartbeat every 500ms
            sendHeartbeatTrigger.run();
            // Start new election if it hasn+'t received heartbeat for some time
            if (System.currentTimeMillis() >= lastHeartbeat + electionTimeout
                    && (state == NodeState.Follower || state == NodeState.Candidate)){
                timeout();
            }

            takeMessage();

            displayLogTrigger.run();
            // Simulate a client request to that node
            clientRequestTrigger.run();
            // Append entries from time to time
            appendEntriesTrigger.run();
            // Restart node randomly
            restartTrigger.run();
            // Shutdown at some point
            //shutdownTrigger.run();
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
        term += 1; // because of new election

        // PARAM : state'
        String stateString = this.state.toString().substring(0, 1).toUpperCase(Locale.ROOT) + this.state.toString().substring(1).toLowerCase(Locale.ROOT);
        this.traceState.getField(this.nodeInfo.name()).update(stateString);

        // PARAM : currentTerm'
        this.traceCurrentTerm.getField(this.nodeInfo.name()).update(term);

        // PARAM : votedFor'
        this.traceVotedFor.getField(this.nodeInfo.name()).update("null");

        // PARAM : votesResponded'
        this.traceVotesResponded.getField(this.nodeInfo.name()).clear();

        // PARAM : votesGranted'
        this.traceVotesGranted.getField(this.nodeInfo.name()).clear();

        // OK : trace Timeout
        tracer.log("Timeout", new Object[] { nodeInfo.name() });

        System.out.printf("Node %s is %s.\n", nodeInfo.name(), state);

        tracer.log("RequestVoteRequest", new Object[] {nodeInfo.name(),nodeInfo.name()});
        tracer.log("HandleRequestVoteRequest", new Object[] {nodeInfo.name(),nodeInfo.name()});
        tracer.log("HandleRequestVoteResponse", new Object[] {nodeInfo.name(),nodeInfo.name()});

        // Simulate message exchange between this node and himself (see in raft spec, localhost exchange messages with itself)

        // Necessary log if we want obtains Quorum, because trace spec can check holes
        // in variable, but not hole in event
        // Reproduce bug by commenting this bloc, show with tla+ debug how to find what's wrong ! by using hit count and ENABLED
//        if (reduceSSflag) {
//            final Message fakeMessage = new RequestVoteRequest(nodeInfo.name(), nodeInfo.name(), term, getLastLogTerm(), getLastLogIndex(),0);
////            specMessages.apply("AddToBag", fakeMessage);
//        }

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
    private void updateTerm(long newTerm) throws IOException {
        term = newTerm;
        toFollower();
        votedFor = "";

        // OK : trace UpdateTerm
        tracer.log("UpdateTerm");
    }

    public void sendHeartbeat() throws IOException {
        assert state == NodeState.Leader : "Only leader can send heartbeat";

        for (NodeInfo ni : clusterInfo.getNodes()) {
            // Skip this
            if (nodeInfo.name().equals(ni.name()))
                continue;

            final Message heartbeatMessage = new AppendEntriesRequest(nodeInfo.name(), ni.name(), term, 0, 0, new ArrayList<>(), commitIndex, 0);
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
            if (ni.name().equals(nodeInfo.name()) || candidateState.getResponded().contains(ni.name())){
                continue;
            }

            final Message message = new RequestVoteRequest(nodeInfo.name(), ni.name(), term, getLastLogTerm(), getLastLogIndex(),0);

            if (reduceSSflag)
                // specMessages.apply("AddToBag", message);

            // OK : trace RequestVote
            tracer.log("RequestVoteRequest", new Object[] {nodeInfo.name(),ni.name()});

            network.send(ni.name(),message);
        }
    }

    public void handleVoteRequest(RequestVoteRequest m) throws IOException {
        System.out.printf("handleVoteRequest %s.\n", m.toString());

        boolean logOk = m.getLastLogTerm() > getLastLogTerm() || m.getLastLogTerm() == getLastLogTerm() && m.getLastLogIndex() >= getLastLogIndex();
        boolean grant = m.getTerm() == term && logOk && (votedFor.equals(m.getFrom()) || votedFor.equals(""));
        
        if (m.getTerm() <= term && grant) {
            // PARAM : votedFor'
            this.traceVotedFor.getField(this.nodeInfo.name()).update(m.getFrom());
        }
        
        // OK : trace HandleRequestVoteRequest
        tracer.log("HandleRequestVoteRequest", new Object[] {nodeInfo.name(),m.getFrom()});
        
        // Reply to vote request
        final Message response = new RequestVoteResponse(nodeInfo.name(), m.getFrom(), term, grant, 0);

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

            // PARAM : votesGranted'
            this.traceVotesGranted.getField(this.nodeInfo.name()).add(m.getFrom());
        }

        // m.mterm = currentTerm[i]
        assert m.getTerm() == term : "Term should be the same.";

        // PARAM : votesResponded'
        this.traceVotesResponded.getField(this.nodeInfo.name()).add(m.getFrom());

        // OK : trace HandleRequestVoteResponse
        tracer.log("HandleRequestVoteResponse", new Object[] {nodeInfo.name(),m.getFrom()});

        // Note: BUG -> Quorum == {i \in SUBSET(Server) : Cardinality(i) * 2 > Cardinality(Server)}
        if (state == NodeState.Candidate && candidateState.getGranted().size() > clusterInfo.getQuorum()) {
            becomeLeader();
        }
    }

    public void becomeLeader() throws IOException {

        assert state == NodeState.Candidate : "Only a candidate can become a leader.";
        assert candidateState.getGranted().size() > clusterInfo.getQuorum() : "A candidate should have a minimum of vote to become a leader.";

        toLeader();

        sendHeartbeat();
        System.out.printf("Node %s is Leader.\n", nodeInfo.name());

        // PARAM : state'
        traceState.getField(nodeInfo.name()).update("Leader");

        for (NodeInfo ni : clusterInfo.getNodes()) {
            leaderState.getNextIndexes().put(ni.name(), logs.size() + 1);
            leaderState.getMatchIndexes().put(ni.name(), 0);

             // PARAM : nextIndex'
             //this.traceNextIndex.getField(ni.name()).update(logs.size() + 1);

             // PARAM : matchIndex'
             //this.traceMatchIndex.getField(ni.name()).update(0);

            // specNextIndex.getField(ni.name()).set(logs.size() + 1);
            // specMatchIndex.getField(ni.name()).set(0);
        }

        // OK : trace BecomeLeader
        tracer.log("BecomeLeader", new Object[] { nodeInfo.name() });
    }

    private void clientRequest() throws IOException {
        if (state != NodeState.Leader)
            return;

        String entry_value = Helpers.pickRandomVal(configuration);

        final Entry entry = new Entry(term, entry_value);
        logs.add(entry);

        System.out.printf("Node %s receive a client request and add entry %s.\n", nodeInfo.name(), entry);

        // PARAM : log'
        this.traceLog.getField(nodeInfo.name()).append(entry);

        // OK : trace ClientRequest
        tracer.log("ClientRequest", new Object[] { nodeInfo.name(), entry_value });
    }

    private void appendEntries() throws IOException {
        assert state == NodeState.Leader : "Only leader can send append entries requests.";

        for (NodeInfo ni : clusterInfo.getNodes()) {
            if (!ni.name().equals(nodeInfo.name())){
                appendEntries(ni.name());

                // OK : trace AppendEntries
                tracer.log("AppendEntries", new Object[] { nodeInfo.name(), ni.name() });
            }
        }
    }

    private void appendEntries(String nodeName) throws IOException {
        // Optimization: Return immediately if there are no entries to append
        int nextIndex = leaderState.getNextIndexes().get(nodeName);
        if (nextIndex > logs.size()) {
            System.out.println("No new entries to append for node: " + nodeName);
            return;
        }
    
        int previousIndex = nextIndex - 1;
        long previousLogTerm = previousIndex > 0 ? logs.get(previousIndex - 1).getTerm() : 0;
    
        final int lastEntryIndex = Math.min(logs.size(), nextIndex);
        final List<Entry> entries = logs.subList(nextIndex - 1, lastEntryIndex);
        System.out.printf("Take entries [%d, %d] for node %s\n", nextIndex - 1, lastEntryIndex, nodeName);
    
        int msgCommitIndex = Math.min(commitIndex, lastEntryIndex);
    
        final Message appendEntriesRequest = new AppendEntriesRequest(
            nodeInfo.name(), 
            nodeName, 
            term, 
            previousIndex, 
            previousLogTerm, 
            entries, 
            msgCommitIndex, 
            0
        );
    
        System.out.println("Sending AppendEntriesRequest to node: " + nodeName);
        tracer.log("AppendEntries", new Object[] { nodeInfo.name(), nodeName });
    
        network.send(nodeName, appendEntriesRequest);
    }    

    private void advanceCommitIndex() throws IOException {

        if (state != NodeState.Leader)
            return;

        int maxAgreeIndex = -1;
        for (int i = logs.size(); i > 0; i--) {
            int finalI = i;

            long nbAgree = clusterInfo.getNodes().stream().filter(nodeInfo -> !nodeInfo.name().equals(this.nodeInfo.name()) && leaderState.getMatchIndexes().get(nodeInfo.name()) >= finalI).count() + 1;

            // TEST : BUG
            if (nbAgree > clusterInfo.getQuorum())
            {
                maxAgreeIndex = i;
                break;
            }
        }

        if (maxAgreeIndex != -1 && logs.get(maxAgreeIndex - 1).getTerm() == term) {
            System.out.printf("SET NEW COMMIT INDEX %s.\n", maxAgreeIndex);
            commitIndex = maxAgreeIndex;
        }

        // PARAM : commitIndex'
        this.traceCommitIndex.getField(this.nodeInfo.name()).update(commitIndex);

        // OK : trace AdvanceCommitIndex
        tracer.log("AdvanceCommitIndex", new Object[] { nodeInfo.name() });
    }

    private void handleAppendEntriesRequest(AppendEntriesRequest appendEntriesRequest) throws IOException {

        System.out.printf("handleAppendEntriesRequest %s.\n", appendEntriesRequest);

        long previousLogIndex = appendEntriesRequest.getLastLogIndex();
//        LET logOk == \/ m.mprevLogIndex = 0
//                 \/ /\ m.mprevLogIndex > 0
//                /\ m.mprevLogIndex <= Len(log[i])
//                /\ m.mprevLogTerm = log[i][m.mprevLogIndex].term
        boolean logOk = previousLogIndex == 0 ||
                (previousLogIndex > 0
                        &&  previousLogIndex <= logs.size()
                        && appendEntriesRequest.getLastLogTerm() == logs.get((int)previousLogIndex - 1).getTerm());

        // Return to follower state
        if (state == NodeState.Candidate) {
            if (appendEntriesRequest.getTerm() == term){
                toFollower();
                // OK : trace HandleAppendEntriesRequest
                tracer.log("HandleAppendEntriesRequest", new Object[] { nodeInfo.name(), appendEntriesRequest.getFrom() });
            }
        }
        else if (state == NodeState.Follower) {
            if (appendEntriesRequest.getTerm() == term && logOk)
                acceptAppendEntries(appendEntriesRequest);
            else
                rejectAppendEntries(appendEntriesRequest);
        }

        System.out.printf("--- NODE %s ENTRIES %s.\n", nodeInfo.name(), logs);

        // OK : trace HandleAppendEntriesRequest
        tracer.log("HandleAppendEntriesRequest", new Object[] { nodeInfo.name(), appendEntriesRequest.getFrom() });
    }

    private void acceptAppendEntries(AppendEntriesRequest appendEntriesRequest) throws IOException {
        System.out.print("Accept append entries.\n");
        int index = (int)appendEntriesRequest.getLastLogIndex() + 1;

        if (appendEntriesRequest.getEntries().isEmpty() || (logs.size() >= index && logs.get(index - 1).getTerm() == appendEntriesRequest.getEntries().get(0).getTerm())) {
            System.out.print("Already done.\n");

            commitIndex = appendEntriesRequest.getCommitIndex();
            
            int matchIndex = (int)appendEntriesRequest.getLastLogIndex() + appendEntriesRequest.getEntries().size();

            Message appendEntriesResponse = new AppendEntriesResponse(nodeInfo.name(), appendEntriesRequest.getFrom(), term, true, matchIndex, 0);

            // OK : trace HandleAppendEntriesRequest
            tracer.log("HandleAppendEntriesRequest", new Object[] { nodeInfo.name(), appendEntriesRequest.getFrom() });

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
        if (!appendEntriesRequest.getEntries().isEmpty() && logs.size() >= index && logs.get(index - 1).getTerm() != appendEntriesRequest.getEntries().get(0).getTerm()) {
            System.out.print("Conflict.\n");
            logs.remove(logs.size() - 1);

            // PARAM : commitIndex'
            this.traceCommitIndex.getField(nodeInfo.name()).update(appendEntriesRequest.getCommitIndex());

            // OK : trace HandleAppendEntriesRequest
            tracer.log("HandleAppendEntriesRequest", new Object[] { nodeInfo.name(), appendEntriesRequest.getFrom() });
        }

        // No conflict append entries
        if (!appendEntriesRequest.getEntries().isEmpty() && logs.size() == appendEntriesRequest.getLastLogIndex()) {
            System.out.print("No conflict.\n");
            logs.addAll(appendEntriesRequest.getEntries());
            
            /* log' = [log EXCEPT ![i] =
                                      Append(log[i], m.mentries[1])] */   
                                      
            // PARAM : log'
            //this.traceLog.getField(nodeInfo.name()).append(appendEntriesRequest.getEntries().get(0));

            // OK : trace HandleAppendEntriesRequest
            tracer.log("HandleAppendEntriesRequest", new Object[] { nodeInfo.name(), appendEntriesRequest.getFrom() });
        }
    }

    private void handleAppendEntriesResponse(AppendEntriesResponse appendEntriesResponse) throws IOException {
        System.out.printf("handleAppendEntriesResponse %s.\n", appendEntriesResponse);

// TODO : parameters
/*
        /\ m.mterm = currentTerm[i]
        /\ \/ /\ m.msuccess \* successful
              /\ nextIndex'  = [nextIndex  EXCEPT ![i][j] = m.mmatchIndex + 1]
              /\ matchIndex' = [matchIndex EXCEPT ![i][j] = m.mmatchIndex]
           \/ /\ \lnot m.msuccess \* not successful
              /\ nextIndex' = [nextIndex EXCEPT ![i][j] =
                                   Max({nextIndex[i][j] - 1, 1})]
              /\ UNCHANGED <<matchIndex>>
        /\ Discard(m)
        /\ UNCHANGED <<serverVars, candidateVars, logVars, elections>>
*/

        if (appendEntriesResponse.getTerm() != term)
            return;

        String fromNodeName = appendEntriesResponse.getFrom();
        if (appendEntriesResponse.isSuccess()) {
            int matchIndex = (int)appendEntriesResponse.getMatchIndex();
            int nextIndex = matchIndex + 1;
            leaderState.getNextIndexes().put(fromNodeName, nextIndex);
            
            // use this : Map.of("type", TwoPhaseMessage.Prepared.toString(), "rm", this.name)
            //this.traceNextIndex.getField(this.nodeInfo.name()).update(Map.of(fromNodeName, nextIndex));
            this.traceNextIndex.getField(this.nodeInfo.name()).setKey(fromNodeName, nextIndex);

            leaderState.getMatchIndexes().put(fromNodeName, matchIndex);


            // PARAM : matchIndex'
            this.traceMatchIndex.getField(this.nodeInfo.name()).setKey(fromNodeName, matchIndex);
            
        } else {
            int nextIndex = leaderState.getNextIndexes().get(fromNodeName);
            leaderState.getNextIndexes().put(fromNodeName, Math.max(nextIndex - 1, 1));

            // PARAM : nextIndex'
            //this.traceNextIndex.getField(this.nodeInfo.name()).update(Map.of(fromNodeName, Math.max(nextIndex - 1, 1)));
            this.traceNextIndex.getField(this.nodeInfo.name()).setKey(fromNodeName, Math.max(nextIndex - 1, 1));
        }

        // OK : trace HandleAppendEntriesResponse
        tracer.log("HandleAppendEntriesResponse", new Object[] { nodeInfo.name(), fromNodeName });

        // Advance index
        advanceCommitIndex();
    }

    private void rejectAppendEntries(AppendEntriesRequest appendEntriesRequest) throws IOException {
        System.out.print("Reject append entries.\n");
        String to = appendEntriesRequest.getFrom();
        Message appendEntriesResponse = new AppendEntriesResponse(nodeInfo.name(), to, term, false, 0, 0);
        // specMessages.apply("AddToBag", appendEntriesResponse);
        // specMessages.apply("RemoveFromBag", appendEntriesRequest);

        // OK : trace HandleAppendEntriesRequest
        tracer.log("HandleAppendEntriesRequest", new Object[] { nodeInfo.name(), to });

        network.send(to, appendEntriesResponse);
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
