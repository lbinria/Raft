package org.lbee.protocol.state;

import java.util.HashMap;

public class LeaderState {

    /**
     * Difference between nextIndex and matchIndex is that nextIndex is the index of the next log to send
     * while matchIndex is the index of the last log that has been confirmed by the follower
     * nextIndex is a hashmap that contains for each follower the index of the next log to send
     * matchIndex is a hashmap that contains for each follower the index of the last confirmed log
     */

    private final HashMap<String, Integer> nextIndexes;
    private final HashMap<String, Integer> matchIndexes;
    //private final Set<String> quorum;

    public LeaderState(/*Set<String> quorum*/) {
        this.nextIndexes = new HashMap<>();
        this.matchIndexes = new HashMap<>();
        //this.quorum = quorum;
    }

    public void clear() {
        nextIndexes.clear();
        matchIndexes.clear();
        //quorum.clear();
    }

    public HashMap<String, Integer> getNextIndexes() {
        return nextIndexes;
    }

    public HashMap<String, Integer> getMatchIndexes() {
        return matchIndexes;
    }

//    public Set<String> getQuorum() {
//        return quorum;
//    }
}
