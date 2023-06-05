package org.lbee.models.messages;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.lbee.models.Entry;

import java.util.ArrayList;
import java.util.List;

public class AppendEntriesRequest extends RequestMessage {

    private final List<Entry> entries;
    private final int commitIndex;

    public List<Entry> getEntries() {
        return entries;
    }
    public int getCommitIndex() {
        return commitIndex;
    }

    public AppendEntriesRequest(String from, String to, long term, int prevLogIndex, int prevLogTerm, List<Entry> entries, int commitIndex, long senderClock) {
        super(from, to, MessageType.AppendEntriesRequest, term, prevLogIndex, prevLogTerm, senderClock);
        this.entries = entries;
        this.commitIndex = commitIndex;
    }

    public AppendEntriesRequest(JsonObject jsonObject) {
        super(
                jsonObject.get("msource").getAsString(),
                jsonObject.get("mdest").getAsString(),
                MessageType.AppendEntriesRequest,
                jsonObject.get("mterm").getAsLong(),
                jsonObject.get("mprevLogIndex").getAsLong(),
                jsonObject.get("mprevLogTerm").getAsLong(),
                jsonObject.get("senderClock").getAsLong()
        );
        this.commitIndex = jsonObject.get("mcommitIndex").getAsInt();

        this.entries = new ArrayList<>();
        JsonArray jsonEntries = jsonObject.get("mentries").getAsJsonArray();
        for (JsonElement jsonEntry : jsonEntries) {
            this.entries.add(new Entry(jsonEntry.getAsJsonObject()));
        }
    }

    @Override
    public String toString() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("msource", from);
        jsonObject.addProperty("mdest", to);
        jsonObject.addProperty("mtype", type.toString());
        jsonObject.addProperty("mterm", term);
        jsonObject.addProperty("mprevLogIndex", lastLogIndex);
        jsonObject.addProperty("mprevLogTerm", lastLogTerm);
        jsonObject.addProperty("mcommitIndex", commitIndex);

        final JsonArray jsonEntries = new JsonArray();
        for (Entry entry : entries) {
            jsonEntries.add(entry.toJson());
        }

        jsonObject.add("mentries", jsonEntries);
        jsonObject.addProperty("senderClock", senderClock);
        return jsonObject.toString();
    }
}
