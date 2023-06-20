package org.lbee.models;

import java.util.HashMap;
import java.util.List;

public class LeaderState {

    private final HashMap<String, Integer> nextIndexes;
    private final HashMap<String, Integer> matchIndexes;
    private final List<String> quorum;

    public LeaderState(List<String> quorum) {
        this.nextIndexes = new HashMap<>();
        this.matchIndexes = new HashMap<>();
        this.quorum = quorum;
    }

    public void clear() {
        nextIndexes.clear();
        matchIndexes.clear();
    }

    public HashMap<String, Integer> getNextIndexes() {
        return nextIndexes;
    }

    public HashMap<String, Integer> getMatchIndexes() {
        return matchIndexes;
    }

    public List<String> getQuorum() {
        return quorum;
    }
}
