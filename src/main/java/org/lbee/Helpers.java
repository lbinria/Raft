package org.lbee;

import java.util.Random;

public class Helpers {

    // Random generator
    private final static Random random = new Random();

    /**
     * Pick up a random val from vals found in config
     * @param configuration Configuration from which pick up
     * @return A value
     */
    public static String pickRandomVal(Configuration configuration) {
        return configuration.getVals().get(random.nextInt(configuration.getVals().size()));
    }

}
