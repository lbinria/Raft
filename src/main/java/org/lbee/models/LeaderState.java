package org.lbee.models;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LeaderState {

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
