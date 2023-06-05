//import org.lbee.*;
//import org.lbee.instrumentation.TraceInstrumentation;
//import org.lbee.instrumentation.VirtualField;
//import org.lbee.instrumentation.clock.SharedClock;
//import org.lbee.models.*;
//import org.lbee.models.messages.*;
//
//import java.io.IOException;
//import java.net.Socket;
//import java.net.UnknownHostException;
//import java.util.*;
//
//public class Node_old {
//
//    private TraceInstrumentation spec;
//    private int term;
//    private int commitIndex;
//    private long lastHeartbeat;
//    private String state;
//    private final ArrayList<Log> logs;
//    private String votedFor = "";
//    private CandidateInfo candidateInfo;
//    private LeaderInfo leaderInfo;
//
//    private long matchIndex;
//
//    // Information about nodes cluster
//    private final NodeInfo nodeInfo;
//    private final ClusterInfo clusterInfo;
//
//    private Configuration configuration;
//
//
//    private final Random rand;
//
//    private final HashMap<String, NetworkManager> networkManagers;
//    private final Server server;
//
//    private boolean shutdown;
//
//    private final long electionTimeout;
//
//    private final VirtualField specState;
//    private final VirtualField specVotedFor;
//    private final VirtualField specVotesGranted;
//    private final VirtualField specMessages;
//
//    public void setState(String state) {
//        this.state = state;
//        specState.set(state);
//    }
//
//    public Node_old(NodeInfo nodeInfo, ClusterInfo clusterInfo) throws IOException {
//
//        this.term = 1;
//        this.state = "Follower";
//        this.logs = new ArrayList<>();
//        this.rand = new Random(nodeInfo.seed());
//        this.nodeInfo = nodeInfo;
//        this.clusterInfo = clusterInfo;
//        this.networkManagers = new HashMap<>();
//
//        // Listen for connections
//        this.server = new Server(nodeInfo.port());
//        this.server.start();
//        System.out.printf("Node %s is listening on port %s. Seed: %s.\n", nodeInfo.name(), nodeInfo.port(), nodeInfo.seed());
//
//        this.lastHeartbeat = System.currentTimeMillis();
//
//        this.shutdown = false;
//
//        electionTimeout = 500 + rand.nextInt(1000, 3000);
//        System.out.printf("election timeout %s.\n", electionTimeout);
//
//        configuration = new Configuration(10, false);
//
//        this.spec = new TraceInstrumentation(nodeInfo.name() + ".ndjson", SharedClock.get("raft.clock"));
//        this.specState = spec.getVariable("state").getField(nodeInfo.name());
//        this.specVotedFor = spec.getVariable("votedFor").getField(nodeInfo.name());
//        this.specVotesGranted = spec.getVariable("votesGranted").getField(nodeInfo.name());
//        this.specMessages = spec.getVariable("messages");
//    }
//
//    public void start() {
//        for (final NodeInfo n : clusterInfo.getNodeList()) {
//            // Skip this
//            if (n.name().equals(nodeInfo.name()))
//                continue;
//
//            // Try to connect to other nodes
//            try {
//                Socket socket = new Socket(n.hostname(), n.port());
//                System.out.printf("Node %s try connect to %s node at %s:%s.\n", nodeInfo.name(), n.name(), n.hostname(), n.port());
//                NetworkManager nm = new NetworkManager(socket);
//                networkManagers.put(n.name(), nm);
//
//            } catch (UnknownHostException ex) {
//                System.out.println("Server not found: " + ex.getMessage());
//            } catch (IOException ex) {
//                System.out.println("I/O error: " + ex.getMessage());
//            }
//        }
//    }
//
//    public int getLastLogTerm() {
//        return logs.size() == 0 ? 0 : logs.get(logs.size() - 1).getTerm();
//    }
//
//
//    public void run() throws IOException {
//
//        long start = System.currentTimeMillis();
//
//        final IntervalTrigger appendEntriesTrigger = new IntervalTrigger(() -> {
//            try {
//                sendAppendEntries();
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        }, 3000);
//
//        while (!shutdown) {
//
//            if (state.equals("Leader")) {
//                // Send heartbeat
//                sendHeartbeat();
//                // Sometimes append entries
//                appendEntriesTrigger.run();
//            }
//
//            checkNewElection();
//            checkMessage();
//            simulateClientRequest();
//
//
//
//
//
//            // Timeout
//            if (System.currentTimeMillis() - start >= 10 * 1000)
//                shutdown();
//        }
//
//    }
//
//    public void checkNewElection() throws IOException {
//
//        if (System.currentTimeMillis() >= lastHeartbeat + electionTimeout && !state.equals("Leader") && !state.equals("Candidate"))
//            startElection();
//    }
//
//    // TLA Timeout
//    public void startElection() throws IOException {
//        assert state.equals("Follower") : "Only follower can start an election";
//
//        setState("Candidate");
//        // Vote for himself
//        candidateInfo = new CandidateInfo();
//        candidateInfo.getResponded().add(nodeInfo.name());
//        candidateInfo.getGranted().add(nodeInfo.name());
//        votedFor = nodeInfo.name();
//        // Add term
//        term += 1;
//        System.out.printf("Node %s is %s.\n", nodeInfo.name(), state);
//        spec.commitChanges("Timeout");
//        sendVoteRequest();
//    }
//
//    public void checkMessage() throws IOException {
//        // Check box
//        final Message message = server.getMessageBox().take(nodeInfo.name());
//        // No message
//        if (message == null)
//            return;
//
//        // Redirect according to message type
//        if (message instanceof VoteMessage)
//            handleVoteRequest((VoteMessage)message);
//        else if (message instanceof VoteReplyMessage)
//            handleVoteReply((VoteReplyMessage) message);
//        else if (message instanceof final AppendEntryMessage appendEntryMessage)
//        {
//            if (appendEntryMessage.getEntries().isEmpty())
//                handleHeartbeat();
//
//            handleAppendEntries(appendEntryMessage);
//        }
//
//        //
//        if (message.getTerm() > term)
//            updateTerm(message.getTerm());
//    }
//
//
//    // TLA UpdateTerm
//    private void updateTerm(long newTerm) {
//        // TODO remove ugly cast
//        term = (int)newTerm;
//        setState("Follower");
//        votedFor = "";
//    }
//
//    private long nextClientRequest = System.currentTimeMillis() + 5000;
//    // TLA ClientRequest
//    // Simulate receive of client request
//    private void simulateClientRequest() {
//        if (System.currentTimeMillis() >= nextClientRequest && state.equals("Leader")) {
//            String v = Helpers.pickRandomVal(configuration);
//            logs.add(new Log(term, v));
//
//            System.out.printf("Receive client request, add %s to log.\n", v);
//            // Between 2 - 10 s
//            nextClientRequest = System.currentTimeMillis() + rand.nextInt(2000, 10000);
//        }
//    }
//
//    public void sendHeartbeat() throws IOException {
//        assert state.equals("Leader") : "Only leader can send heartbeat";
//
//        // Send heartbeat every 500ms
//        if (System.currentTimeMillis() < lastHeartbeat + 250)
//            return;
//
//        //System.out.printf("%s send heartbeat.\n", nodeInfo.name());
//        for (NodeInfo ni : clusterInfo.getNodeList()) {
//            // Skip this
//            if (nodeInfo.name().equals(ni.name()))
//                continue;
//
//            final Message heartbeatMessage = new AppendEntryMessage(nodeInfo.name(), ni.name(), term, 0, 0, new ArrayList<>(), commitIndex, 0);
//            networkManagers.get(ni.name()).send(heartbeatMessage);
//        }
//
//        // Reset last heartbeat
//        lastHeartbeat = System.currentTimeMillis();
//    }
//
//    public void handleHeartbeat() {
//        //System.out.printf("Node %s handle heartbeat.\n", nodeInfo.name());
//        lastHeartbeat = System.currentTimeMillis();
//    }
//
//    public void sendVoteRequest() throws IOException {
//        assert state.equals("Candidate") : "Node should be candidate in order to request a vote.";
//
//        System.out.println("Start sending vote requests.");
//        for (NodeInfo ni : clusterInfo.getNodeList()) {
//
//            // Skip vote request for node that responded
//            if (ni.name().equals(nodeInfo.name()) || candidateInfo.getResponded().contains(ni.name()))
//                continue;
//
//            final Message message = new VoteMessage(nodeInfo.name(), ni.name(), term, getLastLogTerm(), getLastLogIndex(),0);
//            specMessages.apply("AddToBag", message);
//            spec.commitChanges("RequestVote");
//            boolean success = networkManagers.get(ni.name()).send(message);
//        }
//        System.out.println("Vote requests sent.");
//    }
//
//    public void handleVoteRequest(VoteMessage message) throws IOException {
//        System.out.printf("Handle vote request %s.\n", message.toString());
//
//        boolean logOk = message.getLastLogTerm() > getLastLogTerm() || message.getLastLogTerm() == getLastLogTerm() && message.getLastLogIndex() >= getLastLogIndex();
//        boolean grant = message.getTerm() == term && logOk && votedFor.equals(message.getFrom()) || votedFor.equals("");
//
//
//        if (message.getTerm() <= term && grant) {
//                votedFor = message.getFrom();
//                specVotedFor.set(votedFor);
//        }
//
//        final Message response = new VoteReplyMessage(nodeInfo.name(), message.getFrom(), term, grant, logs, 0);
//        spec.commitChanges("HandleRequestVoteRequest");
//        boolean success = networkManagers.get(message.getFrom()).send(response);
//    }
//
//    public void handleVoteReply(VoteReplyMessage message) {
//        System.out.printf("handleVoteReply %s.\n", message);
//        assert state.equals("Candidate") : "Node should be candidate in order to request a vote.";
//        assert message.getTerm() == term;
//
//        // Add node that responded to my vote request
//        candidateInfo.getResponded().add(message.getFrom());
//
//
//        if (message.isGranted()) {
//            // Add node that granted a vote to me
//            candidateInfo.getGranted().add(message.getFrom());
//            specVotesGranted.add(message.getFrom());
//            spec.commitChanges("HandleRequestVoteResponse");
//
////            if (!candidateInfo.getLogs().containsKey(message.getFrom()))
////                candidateInfo.getLogs().put(message.getFrom(), new ArrayList<>());
//
////            // Put log from sender
////            final ArrayList<Log> logs = candidateInfo.getLogs().get(message.getFrom());
////            logs.addAll(message.getLogs());
//
//            if (state.equals("Candidate") && candidateInfo.getGranted().size() >= clusterInfo.getQuorum()) {
//                becomeLeader();
//            }
//        }
//
//        // All responded but not a leader now ?
//        else if (state.equals("Candidate") && candidateInfo.getResponded().containsAll(clusterInfo.getNodeNames()))
//        {
//            // Return to follower state
//            setState("Follower");
//            handleHeartbeat();
//            spec.commitChanges("HandleRequestVoteResponse");
//
//        }
//
//    }
//
//    // TLA : AppendEntries
//    public void sendAppendEntries() throws IOException {
//        assert state.equals("Leader") : "Only leader can send append entries request";
//
//        System.out.printf("Node %s append entries.\n", nodeInfo.name());
//
//        for (NodeInfo ni : clusterInfo.getNodeList()) {
//            // Skip this
//            if (ni.name().equals(nodeInfo.name()))
//                continue;
//
//            sendAppendEntriesTo(ni.name());
//        }
//    }
//
//    private void sendAppendEntriesTo(String nodeName) throws IOException {
//        int nextIndex = leaderInfo.getNextIndexes().get(nodeName);
//        int prevLogIndex = leaderInfo.getNextIndexes().get(nodeName) - 1;
//        int prevLogTerm = prevLogIndex > 0 ? logs.get(prevLogIndex).getTerm() : 0;
//        int lastEntryIndex = Math.min(logs.size(), nextIndex);
//        final List<Log> entries = logs.subList(nextIndex, lastEntryIndex);
//
//        // Send message
//        Message m = new AppendEntryMessage(nodeInfo.name(), nodeName, term, prevLogIndex, prevLogTerm, entries, commitIndex, 0);
//        System.out.printf("send %s.\n", m);
//        networkManagers.get(nodeName).send(m);
//    }
//
//    private void handleAppendEntries(AppendEntryMessage m) throws IOException {
////        LET logOk == \/ m.mprevLogIndex = 0
////                 \/ /\ m.mprevLogIndex > 0
////                /\ m.mprevLogIndex <= Len(log[i])
////                /\ m.mprevLogTerm = log[i][m.mprevLogIndex].term
//
//
//
//        if (m.getTerm() <= term)
//            return;
//
//        System.out.printf("Handle append entries.\n");
//
//        long prevLogIndex = m.getLastLogIndex();
//        long prevLogTerm = m.getLastLogIndex();
//        boolean logOk = prevLogIndex == 0 || (prevLogIndex > 0 && prevLogIndex <= logs.size() && prevLogTerm == logs.get((int)prevLogIndex).getTerm());
//
////                  \/ \* return to follower state
////             /\ m.mterm = currentTerm[i]
////                /\ state[i] = Candidate
////                /\ state' = [state EXCEPT ![i] = Follower]
////                /\ UNCHANGED <<currentTerm, votedFor, logVars, messages>>
//        // return to follower state (receive heartbeat)
//        if (state.equals("Candidate") && m.getTerm() == term) {
//            state = "Follower";
//        }
//
////\/ /\ \* reject request
////                \/ m.mterm < currentTerm[i]
////                \/ /\ m.mterm = currentTerm[i]
////                /\ state[i] = Follower
////                /\ \lnot logOk
////             /\ Reply([mtype           |-> AppendEntriesResponse,
////                mterm           |-> currentTerm[i],
////                msuccess        |-> FALSE,
////                mmatchIndex     |-> 0,
////                msource         |-> i,
////                mdest           |-> j],
////        m)
////             /\ UNCHANGED <<serverVars, logVars>>
//
//        // Reject request
//        if (m.getTerm() < term || (m.getTerm()==term && state.equals("Follower") && !logOk)) {
//            // Reply
//            Message appendEntryReplyMessage = new AppendEntryReplyMessage(nodeInfo.name(), m.getFrom(), term, false, 0, 0);
//            networkManagers.get(m.getFrom()).send(appendEntryReplyMessage);
//        }
//        // Accept request
//        else if (m.getTerm() == term && state.equals("Follower") && logOk) {
//            //
//            long index = prevLogIndex + 1;
////\/ \* already done with request
////                       /\ \/ m.mentries = << >>
////                          \/ /\ m.mentries /= << >>
////                             /\ Len(log[i]) >= index
////                    /\ log[i][index].term = m.mentries[1].term
//            // already done with request
//            if (m.getEntries().isEmpty() || !m.getEntries().isEmpty() && logs.size() >= index && logs.get((int)index).getTerm() == m.getEntries().get(0).getTerm()) {
//                commitIndex = m.getCommitIndex();
//                // Reply a truc
//                Message appendEntryReplyMessage = new AppendEntryReplyMessage(nodeInfo.name(), m.getFrom(), term, true, prevLogIndex + m.getEntries().size(), 0);
//                networkManagers.get(m.getFrom()).send(appendEntryReplyMessage);
//            }
////                   \/ \* conflict: remove 1 entry
////                    /\ m.mentries /= << >>
////                       /\ Len(log[i]) >= index
////                    /\ log[i][index].term /= m.mentries[1].term
////                    /\ LET new == [index2 \in 1..(Len(log[i]) - 1) |->
////            log[i][index2]]
////            IN log' = [log EXCEPT ![i] = new]
////                    /\ UNCHANGED <<serverVars, commitIndex, messages>>
//
//            // conflict: remove 1 entry
//            if (!m.getEntries().isEmpty() && logs.size() >= index && logs.get((int)index).getTerm() != m.getEntries().get(0).getTerm()) {
//                //
//                logs.remove(logs.size() - 1);
//            }
//
////            \* no conflict: append entry
////                       /\ m.mentries /= << >>
////                       /\ Len(log[i]) = m.mprevLogIndex
/////\ log' = [log EXCEPT ![i] =
////            Append(log[i], m.mentries[1])]
//            if (!m.getEntries().isEmpty() && logs.size() == prevLogIndex) {
//                logs.addAll(m.getEntries());
//            }
//
//
//
//        }
//
//
//
//
//    }
//
//    // TLA:AppendEntries
//    public void commitEntries() {
//        assert state.equals("Leader") : "Only leader can append entries.";
//
//        for (NodeInfo ni : clusterInfo.getNodeList()) {
//            if (nodeInfo.name().equals(ni.name()))
//                continue;
//
//
//        }
//    }
//
//    // TLA:BecomeLeader
//    public void becomeLeader() {
//        assert state.equals("Candidate") : "Only a candidate can become a leader.";
//        assert candidateInfo.getGranted().size() >= clusterInfo.getQuorum() : "A candidate should have a minimum of vote to become a leader.";
//
//        setState("Leader");
//        leaderInfo = new LeaderInfo();
//
//        for (NodeInfo ni : clusterInfo.getNodeList()) {
//            leaderInfo.getNextIndexes().put(ni.name(), logs.size() + 1);
//            leaderInfo.getMatchIndexes().put(ni.name(), 0);
//        }
//
//        spec.commitChanges("BecomeLeader");
//    }
//
//    public void handleClientRequest(String value) {
//        // Only leader can handle client request
//        if (!state.equals("Leader"))
//            return;
//
//        // Add entry to logs
//        final Log entry = new Log(term, value);
//        logs.add(entry);
//    }
//
//    private Set<String> agreeIndex(long index) {
//
//        final HashSet<String> agreeServers = new HashSet<>();
//        agreeServers.add(nodeInfo.name());
//
//        for (Map.Entry<String, Integer> matchIndex : leaderInfo.getMatchIndexes().entrySet()) {
//            if (matchIndex.getValue() >= index)
//                agreeServers.add(matchIndex.getKey());
//        }
//
//        return agreeServers;
//    }
//
//    private long getNbAgree(long index) {
//        return agreeIndex(index).size();
//    }
//
//    public void advanceCommitIndex(long index) {
//        assert state.equals("Leader") : "Only a leader can advance commit index.";
//
//        int agreeIndexes = -1;
//        for (int i = 1; i < logs.size(); i++) {
//            if (getNbAgree(i) >= clusterInfo.getQuorum() && i > agreeIndexes)
//                agreeIndexes = i;
//        }
//
//        if (agreeIndexes != -1 && logs.get(agreeIndexes).getTerm() == term) {
//            commitIndex = agreeIndexes;
//        }
//    }
//
//    public void clear() {
//        setState("Follower");
//    }
//
//    // commitIndex
//    public int getLastLogIndex() {
//        return logs.size();
//    }
//
//    public void shutdown() throws IOException {
//        // Request shutdown
//        for (NetworkManager nm : networkManagers.values()) {
//            nm.sendRaw("bye");
//        }
//
//        shutdown = true;
//        server.interrupt();
//    }
//
//    /**
//     * Is the manager has been shutdown
//     * @return True if manager has been shutdown
//     */
//    public boolean isShutdown() { return shutdown; }
//
//
//}
