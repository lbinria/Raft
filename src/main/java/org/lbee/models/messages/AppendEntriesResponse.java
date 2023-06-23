package org.lbee.models.messages;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.lbee.instrumentation.TLASerializer;
import org.lbee.models.Entry;

public class AppendEntriesResponse extends Message implements TLASerializer {

    private final boolean success;
    private final long matchIndex;

    public AppendEntriesResponse(String from, String to, long term, boolean success, long matchIndex, long senderClock) {
        super(from, to, MessageType.AppendEntriesResponse, term, senderClock);
        this.success = success;
        this.matchIndex = matchIndex;
    }

    public AppendEntriesResponse(JsonObject jsonObject) {
        super(
                jsonObject.get("msource").getAsString(),
                jsonObject.get("mdest").getAsString(),
                MessageType.AppendEntriesResponse,
                jsonObject.get("mterm").getAsLong(),
                jsonObject.get("senderClock").getAsLong()
        );

        this.success = jsonObject.get("msuccess").getAsBoolean();
        this.matchIndex = jsonObject.get("mmatchIndex").getAsLong();
    }

    @Override
    public String toString() {
        final JsonObject jsonObject = tlaSerialize().getAsJsonObject();
        jsonObject.addProperty("senderClock", senderClock);
        return jsonObject.toString();
    }

    @Override
    public JsonElement tlaSerialize() {
        final JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("msource", from);
        jsonObject.addProperty("mdest", to);
        jsonObject.addProperty("mtype", type.toString());
        jsonObject.addProperty("mterm", term);
        jsonObject.addProperty("msuccess", success);
        jsonObject.addProperty("mmatchIndex", matchIndex);
        return jsonObject;
    }

    public boolean isSuccess() {
        return success;
    }

    public long getMatchIndex() {
        return matchIndex;
    }
}
