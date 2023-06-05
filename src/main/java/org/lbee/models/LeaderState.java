package org.lbee.models;

import java.util.HashMap;

public class LeaderState {

    private final HashMap<String, Integer> nextIndexes;
    private final HashMap<String, Integer> matchIndexes;

    public LeaderState() {
        this.nextIndexes = new HashMap<>();
        this.matchIndexes = new HashMap<>();
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
}
