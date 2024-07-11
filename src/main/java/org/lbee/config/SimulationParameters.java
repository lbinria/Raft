package org.lbee.config;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SimulationParameters {
    @JsonProperty("MIN_ELECTION_TIMEOUT")
    private int minElectionTimeout;

    @JsonProperty("MAX_ELECTION_TIMEOUT")
    private int maxElectionTimeout;

    @JsonProperty("SEND_HEARTBEAT_INTERVAL")
    private int sendHeartbeatInterval; 

    @JsonProperty("RESTART_PROBABILITY")
    private int restartProbability;

    @JsonProperty("RESTART_INTERVAL")
    private int restartInterval;

    @JsonProperty("SHUTDOWN_INTERVAL")
    private int shutdownInterval;

    @JsonProperty("CLIENT_REQUEST_PROBABILITY")
    private int clientRequestProbability;

    @JsonProperty("CLIENT_REQUEST_INTERVAL")
    private int clientRequestInterval;

    @JsonProperty("APPEND_ENTRIES_INTERVAL")
    private int appendEntriesInterval;

    @JsonProperty("DISPLAY_LOG_INTERVAL")
    private int displayLogInterval;

    public int getMinElectionTimeout() {
        return minElectionTimeout;
    }

    public void setMinElectionTimeout(int minElectionTimeout) {
        this.minElectionTimeout = minElectionTimeout;
    }

    public int getMaxElectionTimeout() {
        return maxElectionTimeout;
    }

    public void setMaxElectionTimeout(int maxElectionTimeout) {
        this.maxElectionTimeout = maxElectionTimeout;
    }

    public int getSendHeartbeatInterval() {
        return sendHeartbeatInterval;
    }

    public void setSendHeartbeatInterval(int sendHeartbeatInterval) {
        this.sendHeartbeatInterval = sendHeartbeatInterval;
    }

    public int getRestartProbability() {
        return restartProbability;
    }

    public void setRestartProbability(int restartProbability) {
        this.restartProbability = restartProbability;
    }

    public int getRestartInterval() {
        return restartInterval;
    }

    public void setRestartInterval(int restartInterval) {
        this.restartInterval = restartInterval;
    }

    public int getShutdownInterval() {
        return shutdownInterval;
    }

    public void setShutdownInterval(int shutdownInterval) {
        this.shutdownInterval = shutdownInterval;
    }

    public int getClientRequestProbability() {
        return clientRequestProbability;
    }

    public void setClientRequestProbability(int clientRequestProbability) {
        this.clientRequestProbability = clientRequestProbability;
    }

    public int getClientRequestInterval() {
        return clientRequestInterval;
    }

    public void setClientRequestInterval(int clientRequestInterval) {
        this.clientRequestInterval = clientRequestInterval;
    }

    public int getAppendEntriesInterval() {
        return appendEntriesInterval;
    }

    public void setAppendEntriesInterval(int appendEntriesInterval) {
        this.appendEntriesInterval = appendEntriesInterval;
    }

    public int getDisplayLogInterval() {
        return displayLogInterval;
    }

    public void setDisplayLogInterval(int displayLogInterval) {
        this.displayLogInterval = displayLogInterval;
    }
}
