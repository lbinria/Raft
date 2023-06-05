package org.lbee.models.messages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.lbee.instrumentation.TLASerializer;

public class RequestVoteResponse extends Message implements TLASerializer {

    private final boolean granted;
    public boolean isGranted() {
        return granted;
    }

    public RequestVoteResponse(String from, String to, long term, boolean granted, long senderClock) {
        super(from, to, MessageType.RequestVoteResponse, term, senderClock);
        this.granted = granted;
    }

    public RequestVoteResponse(JsonObject jsonObject) {
        super(
                jsonObject.get("msource").getAsString(),
                jsonObject.get("mdest").getAsString(),
                MessageType.RequestVoteResponse,
                jsonObject.get("mterm").getAsLong(),
                jsonObject.get("senderClock").getAsLong()
        );
        this.granted = jsonObject.get("mvoteGranted").getAsBoolean();
    }

    @Override
    public JsonElement tlaSerialize() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("msource", from);
        jsonObject.addProperty("mdest", to);
        jsonObject.addProperty("mtype", type.toString());
        jsonObject.addProperty("mterm", term);
        jsonObject.addProperty("mvoteGranted", granted);
        jsonObject.addProperty("senderClock", senderClock);
        return jsonObject;
    }

    @Override
    public String toString() {
        return tlaSerialize().toString();
    }
}
