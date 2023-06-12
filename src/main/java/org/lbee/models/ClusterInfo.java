package org.lbee.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClusterInfo {

    private final HashMap<String, NodeInfo> nodes;

    public ClusterInfo(List<NodeInfo> nodes) {
        this.nodes = new HashMap<>();

        for (NodeInfo nodeInfo : nodes) {
            this.nodes.put(nodeInfo.name(), nodeInfo);
        }
    }

    public HashMap<String, NodeInfo> getNodes() {
        return nodes;
    }

    public List<NodeInfo> getNodeList() {
        return nodes.values().stream().toList();
    }

    public List<String> getNodeNames() {
        return getNodeList().stream().map(NodeInfo::name).toList();
    }

    public long getQuorum() {
        return (long)Math.ceil(nodes.size() / 2d);
    }
}
