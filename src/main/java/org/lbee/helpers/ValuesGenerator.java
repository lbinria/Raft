package org.lbee.helpers;

import java.util.List;
import java.util.Random;

public class ValuesGenerator {
    private final static Random random = new Random();

    public static String pickRandomVal(List<String> values) {
        return values.get(random.nextInt(values.size()));
    }
}
