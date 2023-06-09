package org.lbee;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server extends Thread {

    private final int port;
    private final MessageBox messageBox;

    public Server(int port) {
        this.port = port;
        this.messageBox = new MessageBox();
    }

    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                System.out.printf("Waiting for node connection on port %s\n", port);
                // Accept connection from another node
                final Socket socket = serverSocket.accept();
                System.out.printf("Accept node connection request on port %s\n", port);
                new ServerThread(socket, messageBox).start();
            } 
        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public MessageBox getMessageBox() { return messageBox; }

}