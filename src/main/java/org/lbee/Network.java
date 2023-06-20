package org.lbee;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.lbee.models.messages.Message;

public class Network {
    private final Map<String, Socket> connections;

    public Network() {
        this.connections = new HashMap<String, Socket>();
    }

    public String addConnection(String name, String host, int port){
        String added = "NONE";
        try {
            System.out.printf("Try connect to %s node at %s:%s.\n", name, host, port);
            Socket socket = new Socket(host, port);
            connections.put(name, socket);
            added = name;
        } catch (UnknownHostException ex) {
            System.out.println("Server not found: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("Error to " +host+":"+port);
            System.out.println("Spec: " + ex);
        }
        return added;
    }

    private boolean sendTextMessage(String to, String message) throws IOException{
        Socket socket = this.connections.get(to);
        InputStream inputStream = socket.getInputStream();
        OutputStream outputStream = socket.getOutputStream();
        PrintWriter writer = new PrintWriter(outputStream, true);
        // Send message to server
        writer.println(message);
        // Read response
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String data = reader.readLine();
        return data.equals("ack");
    }

    public boolean send(String to, Message message) throws IOException{
        return this.sendTextMessage(to, message.toString());
    }

    public void shutdown() throws IOException {
        for (String to : connections.keySet()) {
            this.sendTextMessage(to,"bye");
        }
    }

}
