package org.lbee.models.messages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.lbee.instrumentation.TLASerializer;

public class RequestVoteRequest extends RequestMessage implements TLASerializer {

    public RequestVoteRequest(String from, String to, long term, long lastLogTerm, long lastLogIndex, long senderClock) {
        super(from, to, MessageType.RequestVoteRequest, term, lastLogTerm, lastLogIndex, senderClock);
    }

    public RequestVoteRequest(JsonObject jsonObject) {
        super(
                jsonObject.get("msource").getAsString(),
                jsonObject.get("mdest").getAsString(),
                MessageType.RequestVoteRequest,
                jsonObject.get("mterm").getAsLong(),
                jsonObject.get("mlastLogTerm").getAsLong(),
                jsonObject.get("mlastLogIndex").getAsLong(),
                jsonObject.get("senderClock").getAsLong()
        );
    }

    @Override
    public JsonElement tlaSerialize() {
        final JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("msource", from);
        jsonObject.addProperty("mdest", to);
        jsonObject.addProperty("mtype", type.toString());
        jsonObject.addProperty("mterm", term);
        jsonObject.addProperty("mlastLogTerm", lastLogTerm);
        jsonObject.addProperty("mlastLogIndex", lastLogIndex);
        return jsonObject;
    }

    @Override
    public String toString() {
        final JsonObject jsonObject = tlaSerialize().getAsJsonObject();
        jsonObject.addProperty("senderClock", senderClock);
        return jsonObject.toString();
    }
}
