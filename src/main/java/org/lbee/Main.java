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

        // Get info of requested node
        final NodeInfo nodeInfo = Configuration.getClusterInfo().getNodes().get(nodeName);
        // Init node
        final Node node = new Node(nodeInfo, Configuration.getClusterInfo());

        // Write config
        Configuration configuration = new Configuration(10, false);
        ConfigurationWriter.write("raft.ndjson.conf", Map.of("Server", Configuration.getClusterInfo().getNodeNames(), "Value", configuration.getVals()));

        // Wait a bit for other nodes setup
        TimeUnit.SECONDS.sleep(2);
        // Connect to another nodes of cluster
        node.start();
        // Run node
        node.run();
    }
}
