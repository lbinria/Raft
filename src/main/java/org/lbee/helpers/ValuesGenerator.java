package org.lbee.helpers;

import org.lbee.instrumentation.helper.ConfigurationManager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.MalformedJsonException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ValuesGenerator {
    private final static Random random = new Random();
    // defaultValues should be initialized before values
    private static final List<String> defaultValues = List.of("v_1","v_2","v_3","v_4","v_5","v_6","v_7","v_8","v_9","v_10");
    private static final List<String> values = readConfiguration();

    private static List<String> readConfiguration() {
        JsonObject jsonValues;
        List<String> values = new ArrayList<>();
        try {
            jsonValues = ConfigurationManager.read("conf.ndjson");
            for (JsonElement e : jsonValues.getAsJsonArray("Value")) {
                values.add(e.getAsString());
            }
        } catch (IOException e) {
        // } catch (IOException|JsonSyntaxException e) {
            System.out.println("Error reading values.ndjson. Using default values.");
            values.addAll(defaultValues);
        }
        return values;
    }

    public static String pickRandomVal() {
        return values.get(random.nextInt(values.size()));
    }

}
