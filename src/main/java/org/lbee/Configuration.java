package org.lbee;

import org.lbee.models.ClusterInfo;
import org.lbee.models.NodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Configuration {

    private static final String LOCALHOST = "localhost";
    public final List<String> vals;

    public Configuration(int nVals, boolean randomGenerated) {
        this.vals = new ArrayList<>();
        for (int i = 0; i < nVals; i++) {
            this.vals.add(getLabel(randomGenerated, "v_", i + 1));
        }
    }

    private String getLabel(boolean randomGenerated, String prefix, int i) {
        return randomGenerated ? prefix + UUID.randomUUID() : prefix + i;
    }

    public static ClusterInfo getClusterInfo() {

        final NodeInfo nodeInfo1 = new NodeInfo("node1", 4, LOCALHOST, 1200);
        final NodeInfo nodeInfo2 = new NodeInfo("node2", 4200,LOCALHOST, 1201);
        final NodeInfo nodeInfo3 = new NodeInfo("node3", 6800, LOCALHOST, 1202);

        return new ClusterInfo(List.of(nodeInfo1, nodeInfo2, nodeInfo3));
    }

    public List<String> getVals() { return vals; }

}
