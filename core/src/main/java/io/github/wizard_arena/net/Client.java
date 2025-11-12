package io.github.wizard_arena.net;

import io.github.wizard_arena.net.Messages.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Client {
    private final ConcurrentLinkedQueue<Msg> inbox = new ConcurrentLinkedQueue<>();
    private volatile boolean running = false;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Thread readThread;

    public int clientId = -1;

    public void connect(String host, int port, String name) throws IOException {
        if (running) return; // already connected
        running = true;

        socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(host, port), NetConfig.TIMEOUT_MS);

        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));

        // Send join request
        send(new JoinRequest(name));

        // wait for join response
        try {
            Object first = in.readObject();
            if (first instanceof JoinResponse) {
                if (!((JoinResponse) first).accepted) {
                    throw new IOException("Join rejected by server: " + ((JoinResponse) first).reason);
                }
                clientId = ((JoinResponse) first).clientId;
            } else {
                throw new IOException("Invalid join response from server");
            }
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to read join response", e);
        }

        // Start read thread
        readThread = new Thread(() -> {
            try {
                while (running) {
                    Object obj = in.readObject();
                    if (obj instanceof Msg) {
                        inbox.add((Msg) obj);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                // Connection lost or an error
            }
            finally {
                running = false;
            }
        } , "Client-Read-Thread");
        readThread.setDaemon(true);
        readThread.start();
    }

    public void send(Msg msg) throws IOException {
        if (!running) return;
        out.writeObject(msg);
        out.flush();
    }

    public void disconnect(){
        running = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }

    public Msg pollMessage() {
        return inbox.poll();
    }
}
