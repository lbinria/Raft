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

    // numéro de mandat (initialisé à 1 puis incrémenté à chaque élection)
    private long term;

    // index of the log entry that is safely replicated on a majority of nodes (called quorum)
    private int commitIndex;

    // Indique l'index de la dernière entrée de journal réussie pour chaque nœud suiveur.
    // Utilisé par le leader pour déterminer quel entrée de journal peut être considérée comme engagée.
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

    // commitIndex
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
    private final VirtualField traceMessages;
    private final VirtualField traceLog;

    private boolean reduceSSflag;

    public Node(String nodeName, Configuration configuration, TLATracer tracer) {
        // Utils
        this.configuration = configuration;
        this.clusterInfo = configuration.getClusterInfo();
        this.nodeInfo = clusterInfo.getNode(nodeName);

        this.term = 1;
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
        //this.traceState = tracer.getVariableTracer("state").getField(this.nodeInfo.name());

        this.traceVotedFor = tracer.getVariableTracer("votedFor");
        this.traceVotesResponded = tracer.getVariableTracer("votesResponded");
        this.traceVotesGranted = tracer.getVariableTracer("votesGranted");
        this.traceNextIndex = tracer.getVariableTracer("nextIndex");
        this.traceMatchIndex = tracer.getVariableTracer("matchIndex");
        this.traceCommitIndex = tracer.getVariableTracer("commitIndex");
        this.traceCurrentTerm = tracer.getVariableTracer("currentTerm");
        this.traceMessages = tracer.getVariableTracer("messages");
        this.traceLog = tracer.getVariableTracer("log");

        // Feature flags
        this.reduceSSflag = true;
    }

    private void setState(NodeState state) {
        this.state = state;
        // this.spec.notify(specState, SET, state.toString());
//        this.specState.set(state.toString());
    }

    private void toCandidate() {
        setState(NodeState.Candidate);
        candidateState = new CandidateState();
    }

    private void toLeader() throws IOException {
        setState(NodeState.Leader);

//        BecomeLeader(i) ==
        //    /\ state[i] = Candidate
        //                /\ votesGranted[i] \in Quorum
        //    /\ state'      = [state EXCEPT ![i] = Leader]
        //                /\ nextIndex'  = [nextIndex EXCEPT ![i] =
        //                [j \in Server |-> Len(log[i]) + 1]]
        //    /\ matchIndex' = [matchIndex EXCEPT ![i] =
        //                [j \in Server |-> 0]]
        //    /\ elections'  = elections \cup
        //        {[eterm     |-> currentTerm[i],
        //                eleader   |-> i,
        //                elog      |-> log[i],
        //                evotes    |-> votesGranted[i](*,
        //                evoterLog |-> voterLog[i] *)]}
        //    /\ UNCHANGED <<messages, currentTerm, votedFor, candidateVars, logVars>>

//        this.traceState.update(state.toString());

//        tracer.log("BecomeLeader", new Object[] { this.nodeInfo.name() });


//        final Set<String> quorum = new HashSet<>(candidateState.getGranted());
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

//    /\ state'          = [state EXCEPT ![i] = Follower]                   (0)
//    /\ votesResponded' = [votesResponded EXCEPT ![i] = {}]                (1)
//    /\ votesGranted'   = [votesGranted EXCEPT ![i] = {}]                  (2)
//    /\ nextIndex'      = [nextIndex EXCEPT ![i] = [j \in Server |-> 1]]   (3)
//    /\ matchIndex'     = [matchIndex EXCEPT ![i] = [j \in Server |-> 0]]  (4)
//    /\ commitIndex'    = [commitIndex EXCEPT ![i] = 0]                    (5)

        toFollower(); // (0)
//        traceState.update(state.toString()); // (0)

        if (candidateState != null) {
            candidateState.clear(); // (1) (2)
//            traceVotesResponded.update(new ArrayList<>(candidateState.getResponded())); // (1)
//            traceVotesGranted.update(new ArrayList<>(candidateState.getGranted())); // (2)
        }
        else if (leaderState != null) {
            leaderState.clear();
            for (NodeInfo ni : clusterInfo.getNodes()) {
                leaderState.getNextIndexes().put(ni.name(), 1); // (3)
                leaderState.getMatchIndexes().put(ni.name(), 0); // (4)
//                traceNextIndex.update(ni.name() + " -> 1"); // (3)
//                traceMatchIndex.update(ni.name() + " -> 0"); // (4)
            }
        }

        commitIndex = 0; // (5)
//        traceCommitIndex.update(0); // (5)


        // BUG : here
        //this.traceState.update(this.state.toString().toLowerCase(Locale.ROOT));

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
            /**
             * 1 - si je suis leader, j'envoie un heartbeat toutes les 500ms
             * 2 - si je suis follower ou candidat et que je n'ai pas reçu de heartbeat depuis un certain temps, je déclenche une nouvelle élection
             * 3 - je prends les messages (peu importe si je suis leader, follower ou candidat)
             * 4 - j'affiche le log de temps en temps
             * 5 - je simule une requête client à ce noeud (si je suis leader)
             */

            // Leader sends heartbeat every 500ms
            sendHeartbeatTrigger.run();
            // Start new election if it hasn+'t received heartbeat for some time
            if (System.currentTimeMillis() >= lastHeartbeat + electionTimeout
                    && (state == NodeState.Follower || state == NodeState.Candidate)){
                timeout();
            }

            // TODO : BEGIN
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

        // NOTE : trace Timeout
        tracer.log("Timeout", new Object[] { nodeInfo.name() });

        // Comment or uncomment line below doesn't change the size of state space
//        specCurrentTerm.apply("Add", 1);

        System.out.printf("Node %s is %s.\n", nodeInfo.name(), state);

        // Simulate message exchange between this node and himself (see in raft spec, localhost exchange messages with itself)

        // Necessary log if we want obtains Quorum, because trace spec can check holes
        // in variable, but not hole in event
        // Reproduce bug by commenting this bloc, show with tla+ debug how to find what's wrong ! by using hit count and ENABLED
//        if (reduceSSflag) {
//            final Message fakeMessage = new RequestVoteRequest(nodeInfo.name(), nodeInfo.name(), term, getLastLogTerm(), getLastLogIndex(),0);
////            specMessages.apply("AddToBag", fakeMessage);
//        }

        // NOTE : trace RequestVoteResponse -> ne devrait pas exister
//        tracer.log("RequestVoteResponse");
//        specVotesGranted.add(nodeInfo.name());
//        spec.commitChanges("HandleRequestVoteResponse");


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
    private void updateTerm(long newTerm) throws IOException {
        term = newTerm;
        toFollower();
        votedFor = "";

        // NOTE : trace UpdateTerm PROBLEM
//        tracer.log("UpdateTerm");
//        commitChanges("UpdateTerm");
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

        // NOTE : trace RequestVote
        tracer.log("RequestVoteRequest");
//        spec.commitChanges("RequestVoteRequest");
//        specVotedFor.set(nodeInfo.name());

        // NOTE : trace HandleRequestVoteRequest
        tracer.log("HandleRequestVoteRequest");
//        spec.commitChanges("HandleRequestVoteRequest");

        System.out.println("Start sending vote requests.");

        for (NodeInfo ni : clusterInfo.getNodes()) {

            // Skip vote request for node that responded
            if (ni.name().equals(nodeInfo.name()) || candidateState.getResponded().contains(ni.name()))
                continue;

            final Message message = new RequestVoteRequest(nodeInfo.name(), ni.name(), term, getLastLogTerm(), getLastLogIndex(),0);

            if (reduceSSflag)
                // specMessages.apply("AddToBag", message);

            // NOTE : trace RequestVote PROBLEM
             tracer.log("RequestVoteRequest");
            // spec.commitChanges("RequestVoteRequest");
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
            // specVotedFor.set(votedFor);
        }

        // Reply to vote request
        final Message response = new RequestVoteResponse(nodeInfo.name(), m.getFrom(), term, grant, 0);

        // NOTE : trace HandleRequestVoteRequest
        tracer.log("HandleRequestVoteRequest");
        // spec.commitChanges("HandleRequestVoteRequest");
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
            // specVotesGranted.add(m.getFrom());
        }

        // NOTE : trace HandleRequestVoteResponse PROBLEM
        tracer.log("HandleRequestVoteResponse");
        // spec.commitChanges("HandleRequestVoteResponse");

        // Note: BUG
        if (state == NodeState.Candidate && candidateState.getGranted().size() > clusterInfo.getQuorum()) {
            becomeLeader();
        }
    }

    // TLA:BecomeLeader
    public void becomeLeader() throws IOException {
        // Note: weird ! assertion doesn't trigger when node is leader, it seems like it doesn't check == Candidate
        assert state == NodeState.Candidate : "Only a candidate can become a leader.";
        assert candidateState.getGranted().size() > clusterInfo.getQuorum() : "A candidate should have a minimum of vote to become a leader.";
        // Note: bug found with trace validation at 57th depth
//        assert candidateState.getGranted().size() > clusterInfo.getQuorum() : "A candidate should have a minimum of vote to become a leader.";

        toLeader();

        // NOTE : trace BecomeLeader PROBLEM
//        tracer.log("BecomeLeader");

        sendHeartbeat();
        System.out.printf("Node %s is Leader.\n", nodeInfo.name());

        for (NodeInfo ni : clusterInfo.getNodes()) {
            leaderState.getNextIndexes().put(ni.name(), logs.size() + 1);
            leaderState.getMatchIndexes().put(ni.name(), 0);
            // specNextIndex.getField(ni.name()).set(logs.size() + 1);
            // specMatchIndex.getField(ni.name()).set(0);
        }
    }

    private void clientRequest() throws IOException {
        if (state != NodeState.Leader)
            return;
//    /\ LET entry == [term  |-> currentTerm[i],
//                value |-> v]
//        newLog == Append(log[i], entry)
//        IN  log' = [log EXCEPT ![i] = newLog]

        final Entry entry = new Entry(term, Helpers.pickRandomVal(configuration));
        logs.add(entry);

        System.out.printf("Node %s receive a client request and add entry %s.\n", nodeInfo.name(), entry);
        // specLog.apply("AppendElement", entry);

        // NOTE : trace ClientRequest PROBLEM
//        tracer.log("ClientRequest");
//        commitChanges("ClientRequest");
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
        long previousLogTerm = previousIndex > 0 ? logs.get(previousIndex - 1).getTerm() : 0;

        final int lastEntryIndex = Math.min(logs.size(), nextIndex);

        final List<Entry> entries = logs.subList(nextIndex - 1, lastEntryIndex);
        System.out.printf("Take entries [%s, %s]\n", nextIndex - 1, lastEntryIndex);

        int msgCommitIndex = Math.min(commitIndex, lastEntryIndex);
        final Message appendEntriesRequest = new AppendEntriesRequest(nodeInfo.name(), nodeName, term, previousIndex, previousLogTerm, entries, msgCommitIndex, 0);
        // specMessages.apply("AddToBag", appendEntriesRequest);
        System.out.println(appendEntriesRequest);

        // NOTE : trace AppendEntries PROBLEM
//        tracer.log("AppendEntries");
        // spec.commitChanges("AppendEntries");

        // networkManagers.get(nodeName).send(appendEntriesRequest);
        network.send(nodeName,appendEntriesRequest);
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

        // specCommitIndex.set(commitIndex);
        // NOTE : trace AdvanceCommitIndex PROBLEM
//        tracer.log("AdvanceCommitIndex");
//        commitChanges("AdvanceCommitIndex");
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
            if (appendEntriesRequest.getTerm() == term)
                toFollower();
            // NOTE : trace HandleAppendEntriesRequest PROBLEM
//            tracer.log("HandleAppendEntriesRequest");
            // spec.commitChanges("HandleAppendEntriesRequest");
        }
        else if (state == NodeState.Follower) {
            if (appendEntriesRequest.getTerm() == term && logOk)
                acceptAppendEntries(appendEntriesRequest);
            else
                rejectAppendEntries(appendEntriesRequest);
        }

        System.out.printf("--- NODE %s ENTRIES %s.\n", nodeInfo.name(), logs);

        // NOTE : trace HandleAppendEntriesRequest PROBLEM
//        tracer.log("HandleAppendEntriesRequest");
        //commitChanges("HandleAppendEntriesRequest");
    }

    private void acceptAppendEntries(AppendEntriesRequest appendEntriesRequest) throws IOException {
        System.out.print("Accept append entries.\n");
        int index = (int)appendEntriesRequest.getLastLogIndex() + 1;

        // already done with request
        //\/ m.mentries = << >>
        //\/ /\ m.mentries /= << >>
        ///\ Len(log[i]) >= index
        ///\ log[i][index].term = m.mentries[1].term
        if (appendEntriesRequest.getEntries().isEmpty() || (logs.size() >= index && logs.get(index - 1).getTerm() == appendEntriesRequest.getEntries().get(0).getTerm())) {
            System.out.print("Already done.\n");

//          /\ commitIndex' = [commitIndex EXCEPT ![i] = m.mcommitIndex]
            commitIndex = appendEntriesRequest.getCommitIndex();
            // specCommitIndex.set(commitIndex);
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
            // specMessages.apply("AddToBag", appendEntriesResponse);
            // specMessages.apply("RemoveFromBag", appendEntriesRequest);

            // NOTE : trace HandleAppendEntriesRequest PROBLEM
//            tracer.log("HandleAppendEntriesRequest");
            // spec.commitChanges("HandleAppendEntriesRequest");
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
            //specLog.apply("RemoveElementAt", logs.size() - 1);

            // NOTE : trace HandleAppendEntriesRequest PROBLEM
//            tracer.log("HandleAppendEntriesRequest");
            // spec.commitChanges("HandleAppendEntriesRequest");
        }

        // No conflict append entries
        if (!appendEntriesRequest.getEntries().isEmpty() && logs.size() == appendEntriesRequest.getLastLogIndex()) {
            System.out.print("No conflict.\n");
            logs.addAll(appendEntriesRequest.getEntries());
            // specLog.apply("AppendElement", appendEntriesRequest.getEntries().get(0));

            // NOTE : trace HandleAppendEntriesRequest PROBLEM
//            tracer.log("HandleAppendEntriesRequest");
            // spec.commitChanges("HandleAppendEntriesRequest");
        }
    }

    private void handleAppendEntriesResponse(AppendEntriesResponse appendEntriesResponse) throws IOException {
        System.out.printf("handleAppendEntriesResponse %s.\n", appendEntriesResponse);

//        /\ m.mterm = currentTerm[i]
//        /\ \/ /\ m.msuccess \* successful
//        /\ nextIndex'  = [nextIndex  EXCEPT ![i][j] = m.mmatchIndex + 1]
//        /\ matchIndex' = [matchIndex EXCEPT ![i][j] = m.mmatchIndex]
//        \/ /\ \lnot m.msuccess \* not successful
//        /\ nextIndex' = [nextIndex EXCEPT ![i][j] =
//        Max({nextIndex[i][j] - 1, 1})]
//        /\ UNCHANGED <<matchIndex>>
//        /\ Discard(m)


        if (appendEntriesResponse.getTerm() != term)
            return;

        String fromNodeName = appendEntriesResponse.getFrom();
        if (appendEntriesResponse.isSuccess()) {
            int matchIndex = (int)appendEntriesResponse.getMatchIndex();
            int nextIndex = matchIndex + 1;
            leaderState.getNextIndexes().put(fromNodeName, nextIndex);
            leaderState.getMatchIndexes().put(fromNodeName, matchIndex);
            // specNextIndex.getField(fromNodeName).set(nextIndex);
            // specMatchIndex.getField(fromNodeName).set(matchIndex);
        } else {
            int nextIndex = leaderState.getNextIndexes().get(fromNodeName);
            leaderState.getNextIndexes().put(fromNodeName, Math.max(nextIndex - 1, 1));
        }


        // NOTE : trace HandleAppendEntriesResponse PROBLEM
//        tracer.log("HandleAppendEntriesResponse");
        // spec.commitChanges("HandleAppendEntriesResponse");

        // Advance index
        advanceCommitIndex();

    }

    private void rejectAppendEntries(AppendEntriesRequest appendEntriesRequest) throws IOException {
        System.out.print("Reject append entries.\n");
        String to = appendEntriesRequest.getFrom();
        Message appendEntriesResponse = new AppendEntriesResponse(nodeInfo.name(), to, term, false, 0, 0);
        // specMessages.apply("AddToBag", appendEntriesResponse);
        // specMessages.apply("RemoveFromBag", appendEntriesRequest);

        // NOTE : trace HandleAppendEntriesRequest PROBLEM
//        tracer.log("HandleAppendEntriesRequest");
        // spec.commitChanges("HandleAppendEntriesRequest");
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

    private void commitChanges(String description) {
//        try {
//            // spec.commitChanges(description);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
    }

}
