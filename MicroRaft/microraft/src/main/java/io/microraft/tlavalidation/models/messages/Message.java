package io.microraft.tlavalidation.models.messages;

import com.google.gson.JsonObject;

public abstract class Message {

    protected final String from;
    protected final String to;
    protected final MessageType type;
    protected final long term;
    protected final long senderClock;

    public Message(String from, String to, MessageType type, long term, long senderClock) {
        this.from = from;
        this.to = to;
        this.type = type;
        this.term = term;
        this.senderClock = senderClock;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }
    public long getSenderClock() {
        return senderClock;
    }
    public MessageType getType() {
        return type;
    }

    public long getTerm() {
        return term;
    }

    public static Message createMessage(JsonObject jsonObject) {
        final MessageType messageType = MessageType.valueOf(jsonObject.get("mtype").getAsString());
        return switch (messageType) {
            case RequestVoteRequest -> new RequestVoteRequest(jsonObject);
            case RequestVoteResponse -> new RequestVoteResponse(jsonObject);
            case AppendEntriesRequest -> new AppendEntriesRequest(jsonObject);
            case AppendEntriesResponse -> new AppendEntriesResponse(jsonObject);
        };
    }

    @Override
    public String toString() {
        return String.join(";",
                new String[]{from, to, type.toString(), Long.toString(term), Long.toString(senderClock)});
    }
}