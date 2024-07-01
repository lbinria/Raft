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
import org.lbee.helpers.ValuesGenerator;

public class Node {
    public final TLATracer tracer;

    // Term number (initialized to 1 then incremented at each election)
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
    private final List<String> values;

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
    private final VirtualField traceMessages;
    private final VirtualField traceElections;

    private final boolean classic_raft = false;
    private final boolean abstract_raft = false;

    public Node(String nodeName, ClusterInfo clusterInfo, List<String> values, TLATracer tracer) {
        this.clusterInfo = clusterInfo;
        this.values = values;
        this.nodeInfo = clusterInfo.getNode(nodeName);

        this.term = 1;
        this.state = NodeState.Follower;
        this.logs = new ArrayList<>();
        this.randTimeout = new Random(nodeInfo.seed());
        this.randEvent = new Random(nodeInfo.seed() + 1423);
        this.network = new Network();

        // Listen for connections
        this.server = new Server(nodeInfo.port());

        this.lastHeartbeat = System.currentTimeMillis();

        this.shutdown = false;

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
        this.traceMessages = tracer.getVariableTracer("messages");
        this.traceElections = tracer.getVariableTracer("elections");
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

    /**
     * Restarts the node.
     * This method transitions the node to the follower state, clears various parameters and updates the trace fields.
     * 
     * @throws InterruptedException if the thread is interrupted while waiting
     * @throws IOException if an I/O error occurs
     */
    private void restart() throws InterruptedException, IOException {
        System.out.printf("Node %s restarted.\n", nodeInfo.name());

        toFollower();

        if(classic_raft){
            String stateString = this.state.toString().substring(0, 1).toUpperCase(Locale.ROOT) + this.state.toString().substring(1).toLowerCase(Locale.ROOT);
            this.traceState.getField(this.nodeInfo.name()).update(stateString);        
            this.traceVotesResponded.getField(this.nodeInfo.name()).clear();
            this.traceVotesGranted.getField(this.nodeInfo.name()).clear();
        }

        if (candidateState != null) {
            candidateState.clear();
        }

        else if (leaderState != null) {
            leaderState.clear();
            for (NodeInfo ni : clusterInfo.getNodes()) {
                leaderState.getNextIndexes().put(ni.name(), 1);
                leaderState.getMatchIndexes().put(ni.name(), 0);

                if(classic_raft){
                    this.traceNextIndex.getField(ni.name()).update(1);
                    this.traceMatchIndex.getField(ni.name()).update(0);
                }
            }
        }

        commitIndex = 0;

        if(classic_raft){
            this.traceCommitIndex.getField(this.nodeInfo.name()).update(0);
            tracer.log("Restart", new Object[] { nodeInfo.name() });
        }
    }

    /**
     * Runs the node and performs various actions periodically, such as sending heartbeats,
     * handling client requests, appending entries, and restarting the node randomly.
     * The method also checks for shutdown triggers and handles timeouts.
     *
     * @throws IOException if an I/O error occurs during the execution of the method.
     */
    public void run() throws IOException {

        // Prepare shutdown trigger
        final IntervalTrigger shutdownTrigger = new IntervalTrigger(() -> {
            try {
                shutdown();
            } catch (IOException e) {
                // throw new RuntimeException(e);
                System.out.printf("Node %s couldn't shutdown.\n", nodeInfo.name());
            }
        }, 60000);

        // Prepare heartbeat trigger
        final IntervalTrigger sendHeartbeatTrigger =  new IntervalTrigger(() -> {
            try {
                if (state == NodeState.Leader)
                    sendHeartbeat();
            } catch (IOException e) {
                // throw new RuntimeException(e);
                System.out.printf("Node %s couldn't heartbeat.\n", nodeInfo.name());
            }
        }, 500);

        // Restart node randomly
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

        // Append entries from time to time
        final IntervalTrigger appendEntriesTrigger = new IntervalTrigger(() -> {
            try {
                if (state == NodeState.Leader)
                    appendEntries();
            } catch (IOException e) {
                System.out.printf("Node %s couldn't append entries.\n", nodeInfo.name());
            }
        }, 1000);

        // Display logs
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
            shutdownTrigger.run();
        }
    }

    /**
     * Handles the timeout event for the node. This method is called when the node's election timeout expires.
     * Only a follower or candidate can start an election.
     *
     * @throws IOException if an I/O error occurs.
     */
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

        if(classic_raft){
            String stateString = this.state.toString().substring(0, 1).toUpperCase(Locale.ROOT) + this.state.toString().substring(1).toLowerCase(Locale.ROOT);
            this.traceState.getField(this.nodeInfo.name()).update(stateString);
            this.traceCurrentTerm.getField(this.nodeInfo.name()).update(term);
            this.traceVotedFor.getField(this.nodeInfo.name()).update("null");
            this.traceVotesResponded.getField(this.nodeInfo.name()).clear();
            this.traceVotesGranted.getField(this.nodeInfo.name()).clear();

            tracer.log("Timeout", new Object[] { nodeInfo.name() });
        }

        System.out.printf("Node %s is %s.\n", nodeInfo.name(), state);

        if(classic_raft){
            // BUG : simulate message exchange in the bag
            // Send vote request himself (simulate message exchange)
            RequestVoteRequest requestVoteRequest = new RequestVoteRequest(nodeInfo.name(), nodeInfo.name(), term, getLastLogTerm(), getLastLogIndex(),0);
            this.traceMessages.addToBag(requestVoteRequest);
            tracer.log("RequestVoteRequest", new Object[] {nodeInfo.name(),nodeInfo.name()});
            
            Message response = new RequestVoteResponse(nodeInfo.name(), nodeInfo.name(), term, false, 0);

            //this.traceMessages.addToBag(response);
            //this.traceMessages.removeFromBag(requestVoteRequest);

            this.traceVotedFor.getField(this.nodeInfo.name()).update(nodeInfo.name());
            tracer.log("HandleRequestVoteRequest", new Object[] {nodeInfo.name(),nodeInfo.name(), requestVoteRequest});
            
            //this.traceMessages.removeFromBag(response);
            this.traceVotesGranted.getField(this.nodeInfo.name()).add(nodeInfo.name());
            this.traceVotesResponded.getField(this.nodeInfo.name()).add(nodeInfo.name());    
            tracer.log("HandleRequestVoteResponse", new Object[] {nodeInfo.name(),nodeInfo.name()});
        }

        sendVoteRequest();
    }

    /**
     * Takes a message from the server's message box and processes it accordingly.
     * If the message has a higher term than the current term, the term is updated.
     *
     * @throws IOException if an I/O error occurs while taking the message
     */
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
        else if (message instanceof final RequestVoteResponse requestVoteResponse){
            
            if(requestVoteResponse.getTerm() < term){
                if(classic_raft) this.traceMessages.removeFromBag(message);
            } else if(message.getTerm() == term){
                handleVoteReply(requestVoteResponse);
            }
        }
        else if (message instanceof final AppendEntriesRequest appendEntriesRequest)
        {
            if (appendEntriesRequest.getEntries().isEmpty())
                handleHeartbeat();
            else
                handleAppendEntriesRequest(appendEntriesRequest);
        }
        else if (message instanceof final AppendEntriesResponse appendEntriesResponse) {
            if(appendEntriesResponse.getTerm() < term){
                if(classic_raft) this.traceMessages.removeFromBag(message);
            } else if(message.getTerm() == term){
                handleAppendEntriesResponse(appendEntriesResponse);
            }
        }
    }

    /**
     * Updates the term of the node.
     * 
     * @param newTerm the new term to update the node with
     * @throws IOException if an I/O error occurs
     */
    private void updateTerm(long newTerm) throws IOException {
        term = newTerm;
        toFollower();
        this.votedFor = "";

        if(classic_raft){
            this.traceCurrentTerm.getField(this.nodeInfo.name()).update(newTerm);
            String stateString = this.state.toString().substring(0, 1).toUpperCase(Locale.ROOT) + this.state.toString().substring(1).toLowerCase(Locale.ROOT);
            this.traceState.getField(this.nodeInfo.name()).update(stateString);
            this.traceVotedFor.getField(this.nodeInfo.name()).update("null");

            tracer.log("UpdateTerm", new Object[] { nodeInfo.name() });
        }
    }

    /**
     * Sends a heartbeat to all nodes in the cluster.
     * Only the leader node can send a heartbeat.
     *
     * @throws IOException if an I/O error occurs while sending the heartbeat.
     */
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

    /**
     * Handles a heartbeat message from the leader.
     * This method updates the last heartbeat timestamp for the node.
     */
    public void handleHeartbeat() {
        System.out.printf("Node %s handle heartbeat.\n", nodeInfo.name());
        lastHeartbeat = System.currentTimeMillis();
    }

    /**
     * Sends a vote request to all nodes in the cluster.
     * A vote request is sent by a candidate node to all other nodes in the cluster to request their vote.
     *
     * @throws IOException if an I/O error occurs while sending the vote request.
     * @throws IllegalStateException if the node is not in the candidate state.
     */
    public void sendVoteRequest() throws IOException {
        assert state == NodeState.Candidate : "Node should be candidate in order to request a vote.";

        System.out.println("Start sending vote requests.");

        for (NodeInfo ni : clusterInfo.getNodes()) {

            // Skip vote request for node that responded
            if (ni.name().equals(nodeInfo.name()) || candidateState.getResponded().contains(ni.name())){
                continue;
            }

            final Message message = new RequestVoteRequest(nodeInfo.name(), ni.name(), term, getLastLogTerm(), getLastLogIndex(),0);

            if(classic_raft){
                this.traceMessages.addToBag(message);
                tracer.log("RequestVoteRequest", new Object[] {nodeInfo.name(),ni.name()});
            }

            network.send(ni.name(),message);
        }
    }


    /**
     * Handles a vote request from a candidate node.
     * This method checks if the candidate's log is up-to-date and grants the vote if the candidate's log is at least as up-to-date as the receiver's log.
     *
     * @param m The request vote request message.
     * @throws IOException If an I/O error occurs.
     */
    public void handleVoteRequest(RequestVoteRequest m) throws IOException {
        System.out.printf("handleVoteRequest %s.\n", m.toString());

        boolean logOk = m.getLastLogTerm() > getLastLogTerm() || m.getLastLogTerm() == getLastLogTerm() && m.getLastLogIndex() >= getLastLogIndex();
        boolean grant = m.getTerm() == term && logOk && (votedFor.equals(m.getFrom()) || votedFor.equals(""));
        
        if (m.getTerm() <= term) {
            if(grant){
                votedFor = m.getFrom();
                
                if(classic_raft){
                    this.traceVotedFor.getField(this.nodeInfo.name()).update(m.getFrom());
                }
            }
            // Reply to vote request
            final Message response = new RequestVoteResponse(nodeInfo.name(), m.getFrom(), term, grant, 0);

            if(classic_raft){
                // BUG : reply
                // this.traceMessages.addToBag(response);
                // this.traceMessages.removeFromBag(m);
            }

            network.send(m.getFrom(),response);
        }

        if(classic_raft){
            tracer.log("HandleRequestVoteRequest", new Object[] {nodeInfo.name(),m.getFrom(),m});
        }    
    }

    /**
     * Used for tracing purposes.
     * Replies to a vote request.
     * 
     * @param m The vote request message
     * @param response The vote response message
     * @throws IOException if an I/O error occurs
     */
    private void reply(Message response, Message request) throws IOException {
        this.traceMessages.addToBag(response);
        this.traceMessages.removeFromBag(request);
    }

    /**
     * Handles the response to a vote request from other nodes.
     * Only a candidate node can handle the vote reply.
     *
     * @param m The response to the vote request.
     * @throws IOException If an I/O error occurs.
     */
    public void handleVoteReply(RequestVoteResponse m) throws IOException {
        assert state == NodeState.Candidate : "Only candidate can handle vote reply.";
        assert m.getTerm() == term;

        System.out.printf("handleVoteReply %s.\n", m);

        // Add node that responded to my vote request
        candidateState.getResponded().add(m.getFrom());

        if (m.isGranted()) {
            // Add node that granted a vote to me
            candidateState.getGranted().add(m.getFrom());

            if(classic_raft){
                this.traceVotesGranted.getField(this.nodeInfo.name()).add(m.getFrom());
            }
        }

        assert m.getTerm() == term : "Term should be the same.";

        if(classic_raft){
            // BUG : remove message from bag
            //this.traceMessages.removeFromBag(m);

            this.traceVotesResponded.getField(this.nodeInfo.name()).add(m.getFrom());

            tracer.log("HandleRequestVoteResponse", new Object[] {nodeInfo.name(),m.getFrom()});
        }

        if (state == NodeState.Candidate && candidateState.getGranted().size() > clusterInfo.getQuorum()) {
            becomeLeader();
        }
    }

    /**
     * Transitions the node to the leader state.
     * 
     * @throws IOException if an I/O error occurs during the transition.
     */
    public void becomeLeader() throws IOException {

        assert state == NodeState.Candidate : "Only a candidate can become a leader.";
        assert candidateState.getGranted().size() > clusterInfo.getQuorum() : "A candidate should have a minimum of vote to become a leader.";

        toLeader();

        sendHeartbeat();
        System.out.printf("Node %s is Leader.\n", nodeInfo.name());

        if(classic_raft){
            traceState.getField(nodeInfo.name()).update("Leader");
        }

        for (NodeInfo ni : clusterInfo.getNodes()) {
            leaderState.getNextIndexes().put(ni.name(), logs.size() + 1);
            leaderState.getMatchIndexes().put(ni.name(), 0);
            
            if(classic_raft){
                this.traceNextIndex.getField(this.nodeInfo.name()).setKey(ni.name(), logs.size() + 1);
                this.traceMatchIndex.getField(this.nodeInfo.name()).setKey(ni.name(), 0);
            }
        }

        if(classic_raft){            
            // TODO : election' ?
            /* /\ elections'  = elections \cup
            {[eterm     |-> currentTerm[i],
            eleader   |-> i,
            elog      |-> log[i],
            evotes    |-> votesGranted[i] (this is the set of servers from which the candidate has received a vote in its currentTerm)*/
            
            //this.traceElections.add(Map.of("eterm", term, "eleader", nodeInfo.name()));

            tracer.log("BecomeLeader", new Object[] { nodeInfo.name() });
        }
    }

    /**
     * Processes a client request and adds an entry to the logs if the node is in the Leader state.
     * 
     * @throws IOException if an I/O error occurs
     */
    private void clientRequest() throws IOException {
        if (state != NodeState.Leader)
            return;

        // String entry_value = Helpers.pickRandomVal(configuration);
        String entry_value = ValuesGenerator.pickRandomVal(values);

        final Entry entry = new Entry(term, entry_value);
        logs.add(entry);

        System.out.printf("Node %s receive a client request and add entry %s.\n", nodeInfo.name(), entry);

        if(classic_raft){
            this.traceLog.getField(nodeInfo.name()).append(entry);
            tracer.log("ClientRequest", new Object[] { nodeInfo.name(), entry_value });
        }
    }


    /**
     * Sends append entries requests to all nodes in the cluster except for the current node.
     * This method can only be called by the leader node.
     *
     * @throws IOException if an I/O error occurs while sending the append entries requests.
     */
    private void appendEntries() throws IOException {
        assert state == NodeState.Leader : "Only leader can send append entries requests.";

        for (NodeInfo ni : clusterInfo.getNodes()) {
            if (!ni.name().equals(nodeInfo.name())){
                appendEntries(ni.name());
            }
        }
    }

    /**
     * Appends entries to the specified node.
     *
     * @param nodeName the name of the node to append entries to
     * @throws IOException if an I/O error occurs
     */
    private void appendEntries(String nodeName) throws IOException {

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
        
        if(classic_raft){
            this.traceMessages.addToBag(appendEntriesRequest);
            tracer.log("AppendEntries", new Object[] { nodeInfo.name(), nodeName });    
        }

        network.send(nodeName, appendEntriesRequest);
    }    

    /**
     * Advances the commit index of the Raft node.
     * This method is called by the leader node to determine the new commit index based on the agreement of the cluster nodes.
     * The commit index is updated if a majority of nodes in the cluster have agreed on a log entry.
     *
     * @throws IOException if an I/O error occurs while updating the commit index.
     */
    private void advanceCommitIndex() throws IOException {

        if (state != NodeState.Leader)
            return;

        int maxAgreeIndex = -1;
        for (int i = logs.size(); i > 0; i--) {
            int finalI = i;

            long nbAgree = clusterInfo.getNodes().stream().filter(nodeInfo -> !nodeInfo.name().equals(this.nodeInfo.name()) && leaderState.getMatchIndexes().get(nodeInfo.name()) >= finalI).count() + 1;

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

        if(classic_raft){
            this.traceCommitIndex.getField(this.nodeInfo.name()).update(commitIndex);
            tracer.log("AdvanceCommitIndex", new Object[] { nodeInfo.name() });
        }
    }

    /**
     * Handles an AppendEntriesRequest received from the leader.
     *
     * @param appendEntriesRequest The AppendEntriesRequest to handle.
     * @throws IOException If an I/O error occurs.
     */
    private void handleAppendEntriesRequest(AppendEntriesRequest appendEntriesRequest) throws IOException {

        System.out.printf("handleAppendEntriesRequest %s.\n", appendEntriesRequest);

        long previousLogIndex = appendEntriesRequest.getLastLogIndex();
        boolean logOk = previousLogIndex == 0 ||
                (previousLogIndex > 0
                        &&  previousLogIndex <= logs.size()
                        && appendEntriesRequest.getLastLogTerm() == logs.get((int)previousLogIndex - 1).getTerm());

        // Return to follower state
        if (state == NodeState.Candidate) {
            if (appendEntriesRequest.getTerm() == term){
                toFollower();

                if(classic_raft){
                    String stateString = this.state.toString().substring(0, 1).toUpperCase(Locale.ROOT) + this.state.toString().substring(1).toLowerCase(Locale.ROOT);
                    this.traceState.getField(this.nodeInfo.name()).update(stateString);

                    tracer.log("HandleAppendEntriesRequest", new Object[] { nodeInfo.name(), appendEntriesRequest.getFrom() });
                }
            }
        }
        else if (state == NodeState.Follower) {
            if (appendEntriesRequest.getTerm() == term && logOk)
                acceptAppendEntries(appendEntriesRequest);
            else
                rejectAppendEntries(appendEntriesRequest);
        }

        System.out.printf("--- NODE %s ENTRIES %s.\n", nodeInfo.name(), logs);

        if(classic_raft){
            tracer.log("HandleAppendEntriesRequest", new Object[] { nodeInfo.name(), appendEntriesRequest.getFrom() });
        }
    }

    /**
     * Accepts an AppendEntriesRequest and appends the entries to the logs.
     *
     * @param appendEntriesRequest The AppendEntriesRequest to be accepted.
     * @throws IOException If an I/O error occurs.
     */
    private void acceptAppendEntries(AppendEntriesRequest appendEntriesRequest) throws IOException {
        System.out.print("Accept append entries.\n");
        int index = (int)appendEntriesRequest.getLastLogIndex() + 1;

        if (appendEntriesRequest.getEntries().isEmpty() || (logs.size() >= index && logs.get(index - 1).getTerm() == appendEntriesRequest.getEntries().get(0).getTerm())) {
            System.out.print("Already done.\n");

            commitIndex = appendEntriesRequest.getCommitIndex();
            
            int matchIndex = (int)appendEntriesRequest.getLastLogIndex() + appendEntriesRequest.getEntries().size();

            Message appendEntriesResponse = new AppendEntriesResponse(nodeInfo.name(), appendEntriesRequest.getFrom(), term, true, matchIndex, 0);

            if(classic_raft){
                reply(appendEntriesResponse, appendEntriesRequest);
                this.traceCommitIndex.getField(nodeInfo.name()).update(appendEntriesRequest.getCommitIndex());
                tracer.log("HandleAppendEntriesRequest", new Object[] { nodeInfo.name(), appendEntriesRequest.getFrom() });
            }

            network.send(appendEntriesRequest.getFrom(), appendEntriesResponse);
        }

        // Conflict : remove 1 entry
        if (!appendEntriesRequest.getEntries().isEmpty() && logs.size() >= index && logs.get(index - 1).getTerm() != appendEntriesRequest.getEntries().get(0).getTerm()) {
            System.out.print("Conflict.\n");
            logs.remove(logs.size() - 1);

            if(classic_raft){
                tracer.log("HandleAppendEntriesRequest", new Object[] { nodeInfo.name(), appendEntriesRequest.getFrom() });
            }
        }

        // No conflict append entries
        if (!appendEntriesRequest.getEntries().isEmpty() && logs.size() == appendEntriesRequest.getLastLogIndex()) {
            System.out.print("No conflict.\n");
            logs.addAll(appendEntriesRequest.getEntries());
                        
            if(classic_raft){
                this.traceLog.getField(nodeInfo.name()).append(appendEntriesRequest.getEntries().get(0));
                tracer.log("HandleAppendEntriesRequest", new Object[] { nodeInfo.name(), appendEntriesRequest.getFrom() });
            }
        }
    }

    /**
     * Handles the response to an append entries request.
     * This method updates the next index and match index of the leader node.
     *
     * @param appendEntriesResponse The response to the append entries request.
     * @throws IOException If an I/O error occurs.
     */
    private void handleAppendEntriesResponse(AppendEntriesResponse appendEntriesResponse) throws IOException {
        System.out.printf("handleAppendEntriesResponse %s.\n", appendEntriesResponse);

        if (appendEntriesResponse.getTerm() != term)
            return;

        String fromNodeName = appendEntriesResponse.getFrom();
        if (appendEntriesResponse.isSuccess()) {
            int matchIndex = (int)appendEntriesResponse.getMatchIndex();
            int nextIndex = matchIndex + 1;
            leaderState.getNextIndexes().put(fromNodeName, nextIndex);
            leaderState.getMatchIndexes().put(fromNodeName, matchIndex);
            
            if(classic_raft){
                this.traceNextIndex.getField(this.nodeInfo.name()).setKey(fromNodeName, nextIndex);
                this.traceMatchIndex.getField(this.nodeInfo.name()).setKey(fromNodeName, matchIndex);
            }

        } else {
            int nextIndex = leaderState.getNextIndexes().get(fromNodeName);
            leaderState.getNextIndexes().put(fromNodeName, Math.max(nextIndex - 1, 1));

            if(classic_raft){
                this.traceNextIndex.getField(this.nodeInfo.name()).setKey(fromNodeName, Math.max(nextIndex - 1, 1));
            }
        }

        if(classic_raft){
            this.traceMessages.removeFromBag(appendEntriesResponse);
            tracer.log("HandleAppendEntriesResponse", new Object[] { nodeInfo.name(), fromNodeName });
        }
        
        advanceCommitIndex();
    }

    /**
     * Rejects the append entries request by sending an append entries response with success set to false.
     * 
     * @param appendEntriesRequest The append entries request to reject.
     * @throws IOException If an I/O error occurs while sending the append entries response.
     */
    private void rejectAppendEntries(AppendEntriesRequest appendEntriesRequest) throws IOException {
        System.out.print("Reject append entries.\n");
        String to = appendEntriesRequest.getFrom();
        Message appendEntriesResponse = new AppendEntriesResponse(nodeInfo.name(), to, term, false, 0, 0);

        if(classic_raft){
            reply(appendEntriesResponse, appendEntriesRequest);
            tracer.log("HandleAppendEntriesRequest", new Object[] { nodeInfo.name(), to });
        }

        network.send(to, appendEntriesResponse);
    }


    /**
     * Shuts down the node by stopping the network.
     *
     * @throws IOException if an I/O error occurs while shutting down the network.
     */
    public void shutdown() throws IOException {
        network.shutdown();
        shutdown = true;
    }

    /**
     * Is the manager has been shutdown
     * @return True if manager has been shutdown
     */
    public boolean isShutdown() {
        return shutdown;
    }
}
