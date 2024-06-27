package org.lbee.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.lbee.instrumentation.helper.ConfigurationManager;
import org.lbee.models.ClusterInfo;
import org.lbee.models.NodeInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Configuration {

    private final ClusterInfo clusterInfo;
    private static final String LOCALHOST = "localhost";
    public final List<String> values;

    public Configuration(String confFile) throws IOException {
        this.values = new ArrayList<>();
        JsonObject jsonConfig;
        jsonConfig = ConfigurationManager.read(confFile);

        for (JsonElement e : jsonConfig.getAsJsonArray("Value")) {
            values.add(e.getAsString());
        }

        final ArrayList<NodeInfo> nodesInfo = new ArrayList<>();
        for (JsonElement e : jsonConfig.getAsJsonArray("ClusterInfo")) {
            final JsonObject jsonNodeInfo = e.getAsJsonObject();
            // Extract node info
            String nodeName = jsonNodeInfo.get("name").getAsString();
            int seed = jsonNodeInfo.get("seed").getAsInt();
            String host = jsonNodeInfo.get("host").getAsString();
            int port = jsonNodeInfo.get("port").getAsInt();

            nodesInfo.add(new NodeInfo(nodeName, seed, host, port));
        }

        this.clusterInfo = new ClusterInfo(nodesInfo);
    }

    public ClusterInfo getClusterInfo() {
        return this.clusterInfo;
    }

    public List<String> getValues() {
        return values;
    }

    @Override
    public String toString() {
        return "Configuration{" +
                "clusterInfo=" + clusterInfo +
                ", vals=" + values +
                '}';
    }
}
