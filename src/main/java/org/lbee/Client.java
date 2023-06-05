package org.lbee;

import org.lbee.models.ClusterInfo;
import org.lbee.models.NodeInfo;

import java.io.IOException;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class Client {
    public static void main(String[] args) throws IOException, InterruptedException {

        final ClusterInfo clusterInfo = Configuration.getClusterInfo();

        final NodeInfo nodeInfo = clusterInfo.getNodeList().get(0);

        Socket socket = new Socket(nodeInfo.hostname(), nodeInfo.port());
        System.out.printf("Client try connect to %s node at %s:%s.\n", nodeInfo.name(), nodeInfo.hostname(), nodeInfo.port());
        NetworkManager nm = new NetworkManager(socket);

        final Random rand = new Random();


        while (true) {
            // Sleep
            TimeUnit.SECONDS.sleep(1000 + rand.nextInt(3000));

            // Send append entry request
            //nm.send(new AppendEntryMessage())
        }

    }
}
