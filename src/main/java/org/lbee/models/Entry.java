package org.lbee.models;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class Entry {

    private final int term;
    private final String content;

    public Entry(int term, String content) {
        this.term = term;
        this.content = content;
    }

    public Entry(JsonObject jsonObject) {
        this.term = jsonObject.get("term").getAsInt();
        this.content = jsonObject.get("content").getAsString();
    }

    public int getTerm() {
        return term;
    }

    public String getContent() {
        return content;
    }

    public JsonElement toJson() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("term", term);
        jsonObject.addProperty("content", content);
        return jsonObject;
    }
}
