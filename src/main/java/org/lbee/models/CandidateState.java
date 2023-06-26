package org.lbee.models;

import java.util.ArrayList;
import java.util.HashMap;

public class CandidateState {

    private final ArrayList<String> responded;
    private final ArrayList<String> granted;

    public CandidateState() {
        this.responded = new ArrayList<>();
        this.granted = new ArrayList<>();
    }

    public void clear() {
        this.responded.clear();
        this.granted.clear();
    }

    public ArrayList<String> getResponded() { return responded; }
    public ArrayList<String> getGranted() { return granted; }
}
