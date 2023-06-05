package org.lbee;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.lbee.models.messages.Message;

import java.io.*;
import java.net.Socket;

public class ServerThread extends Thread {

    private final Socket socket;
    private final MessageBox messageBox;

    public ServerThread(Socket socket, MessageBox messageBox) {
        this.socket = socket;
        this.messageBox = messageBox;
    }

    public void run() {
        try {

            final InputStream input = socket.getInputStream();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(input));

            final OutputStream output = socket.getOutputStream();
            final PrintWriter writer = new PrintWriter(output, true);

            while (true) {

                final String messageData = reader.readLine();

                if (messageData == null) {

                    continue;
                }



                // Quit loop and kill thread
                if (messageData.equals("bye")) {
                    System.out.println("Server receive bye.");
                    writer.println("bye");
                    break;
                }

                // Create message object from data
                JsonObject jsonObject = new Gson().fromJson(messageData, JsonObject.class);
                Message message = Message.createMessage(jsonObject);

                // Put message on queue
                messageBox.put(message);
                writer.println("ack");
            }

            System.out.println("A client quit.");
            socket.close();

        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}