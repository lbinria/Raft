package org.lbee;

import org.lbee.models.messages.Message;

import java.io.*;
import java.net.Socket;

public class NetworkManager {

    private final Socket socket;

    public NetworkManager(Socket socket) throws IOException {
        this.socket = socket;

    }

    protected boolean send(Message message) throws IOException {
        InputStream inputStream = socket.getInputStream();
        OutputStream outputStream = socket.getOutputStream();
        PrintWriter writer = new PrintWriter(outputStream, true);
        // Send message to server
        writer.println(message.toString());
        // Read response
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String data = reader.readLine();
        return data.equals("ack");
    }
    /*
    protected Message receive(String processName) throws IOException {
        // Request for message destined to me
        writer.println(processName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(this.inputStream));
        String data = reader.readLine();
        if (data.equals("null"))
            return null;
        else {
            String[] components = data.split(";");
            return new Message(components);
        }
    }
    */

    protected String sendRaw(String s) throws IOException {
        InputStream inputStream = socket.getInputStream();
        OutputStream outputStream = socket.getOutputStream();
        PrintWriter writer = new PrintWriter(outputStream, true);
        writer.println(s);
        // Read response
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        return reader.readLine();
    }

}