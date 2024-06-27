package org.lbee.helpers;

import org.lbee.config.Configuration;

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
        return configuration.getValues().get(random.nextInt(configuration.getValues().size()));
    }

}
