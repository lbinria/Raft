package org.lbee.protocol.state;

import java.util.ArrayList;

public class CandidateState {

    /**
     * Difference between the two states:
     * - Responded: The list of candidates that have responded to the request.
     * - Granted: The list of candidates that have been granted access to the resource.
     */

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
