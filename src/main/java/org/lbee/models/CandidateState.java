package org.lbee.models;

import java.util.ArrayList;
import java.util.HashMap;

public class CandidateState {

    private final ArrayList<String> responded;
    private final ArrayList<String> granted;
    private final HashMap<String, ArrayList<Entry>> logs;

    public CandidateState() {
        this.responded = new ArrayList<>();
        this.granted = new ArrayList<>();
        this.logs = new HashMap<>();
    }

    public void clear() {
        this.responded.clear();
        this.granted.clear();
        this.logs.clear();
    }

    public ArrayList<String> getResponded() { return responded; }
    public ArrayList<String> getGranted() { return granted; }

    public HashMap<String, ArrayList<Entry>> getLogs() {
        return logs;
    }
}
