package org.lbee.models;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.lbee.instrumentation.TLASerializer;

public class Entry implements TLASerializer {

    private final long term;
    private final String content;

    public Entry(long term, String content) {
        this.term = term;
        this.content = content;
    }

    public Entry(JsonObject jsonObject) {
        this.term = jsonObject.get("term").getAsInt();
        this.content = jsonObject.get("value").getAsString();
    }

    public long getTerm() {
        return term;
    }

    public String getContent() {
        return content;
    }

    public JsonElement toJson() {
        return tlaSerialize();
    }

    @Override
    public JsonElement tlaSerialize() {
        final JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("term", term);
        jsonObject.addProperty("value", content);
        return jsonObject;
    }

    @Override
    public String toString() {
        return tlaSerialize().toString();
    }
}
