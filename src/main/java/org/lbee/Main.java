package org.lbee;

import org.lbee.instrumentation.ConfigurationWriter;
import org.lbee.models.ClusterInfo;
import org.lbee.models.NodeInfo;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        // Check args
        assert args.length >= 1 : "Missing arguments. node name expected.";

        // Get node name to initialize
        final String nodeName = args[0];

        // Write config
        final Configuration configuration = new Configuration(ConfigurationWriter.read("raft.ndjson.conf"));
        System.out.println("Config: " + configuration);

        // Some checks
        if (!configuration.getClusterInfo().getNodes().containsKey(nodeName)) {
            System.out.printf("Node name '%s' given as program parameter doesn't exist in configuration.\n", nodeName);
            return;
        }

        // Init node
        final Node node = new Node(nodeName, configuration);

        // Wait a bit for other nodes setup
        TimeUnit.SECONDS.sleep(2);
        // Connect to another nodes of cluster
        node.start();
        // Run node
        node.run();
    }
}
