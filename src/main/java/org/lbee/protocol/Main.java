package org.lbee.protocol;

import org.lbee.config.Configuration;
import org.lbee.instrumentation.clock.ClockException;
import org.lbee.instrumentation.clock.ClockFactory;

import org.lbee.instrumentation.helper.ConfigurationManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.lbee.instrumentation.trace.TLATracer;

public class Main {

    public static void main(String[] args) throws IOException, InterruptedException, ClockException {
        // Check args
        assert args.length >= 1 : "Missing arguments. node name expected.";

        // Get node name to initialize
        final String nodeName = args[0];

        // Write configuration
        final Configuration configuration = new Configuration(ConfigurationManager.read("raft.ndjson.conf"));
        //System.out.println("Config: " + configuration);

        // Some checks
        if (!configuration.getClusterInfo().hasNode(nodeName)) {
            System.out.printf("Node name '%s' given as program parameter doesn't exist in configuration.\n", nodeName);
            return;
        }

        // Init tracer
        TLATracer spec = TLATracer.getTracer(nodeName + ".ndjson",
                ClockFactory.getClock(ClockFactory.FILE,"raft.clock"));

        // Init node
        final Node node = new Node(nodeName, configuration, spec);

        // Initialize node and start node server
        node.start();

        // Wait a bit for other nodes setup
        TimeUnit.SECONDS.sleep(1);

        // Connect to another nodes of cluster
        node.connect();

        // Run node
        node.run();
    }
}
