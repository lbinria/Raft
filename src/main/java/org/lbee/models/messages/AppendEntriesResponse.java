package org.lbee.models.messages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.lbee.instrumentation.TLASerializer;

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
                MessageType.RequestVoteRequest,
                jsonObject.get("mterm").getAsLong(),
                jsonObject.get("senderClock").getAsLong()
        );

        this.success = jsonObject.get("msuccess").getAsBoolean();
        this.matchIndex = jsonObject.get("mmatchIndex").getAsLong();
    }


    @Override
    public JsonElement tlaSerialize() {
        return null;
    }
}
