package org.lbee;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.lbee.models.ClusterInfo;
import org.lbee.models.NodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Configuration {

    private final ClusterInfo clusterInfo;
    private static final String LOCALHOST = "localhost";
    public final List<String> vals;

    public Configuration(JsonObject jsonConfig) {

        this.vals = new ArrayList<>();
        for (JsonElement e : jsonConfig.getAsJsonArray("Value")) {
            vals.add(e.getAsString());
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

    public List<String> getVals() { return vals; }

    @Override
    public String toString() {
        return "Configuration{" +
                "clusterInfo=" + clusterInfo +
                ", vals=" + vals +
                '}';
    }
}
