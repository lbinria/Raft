package org.lbee.models;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClusterInfo {

    private final Map<String, NodeInfo> nodes;

    public ClusterInfo(List<NodeInfo> nodes) {
        this.nodes = new HashMap<>();
        for (NodeInfo nodeInfo : nodes) {
            this.nodes.put(nodeInfo.name(), nodeInfo);
        }
    }

    public NodeInfo getNode(String nodeName) {
        return nodes.get(nodeName);
    }

    public boolean hasNode(String nodeName) { return nodes.containsKey(nodeName); }

    public List<NodeInfo> getNodes() {
        return nodes.values().stream().toList();
    }

    public long getQuorum() {
        return nodes.size() / 2;
    }

    @Override
    public String toString() {
        return "ClusterInfo{" +
                "nodes=" + nodes +
                '}';
    }
}
