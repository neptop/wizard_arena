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
        if (running) throw new IOException("Client is already connected");

        socket = new Socket();
        socket.setTcpNoDelay(true);

        try {
            // connect TCP
            socket.connect(new InetSocketAddress(host, port), NetConfig.TIMEOUT_MS);

            // setup streams
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));

            // send join request
            out.writeObject(new JoinRequest(name));
            out.flush();

            // wait for join response
            Object first = in.readObject();
            if (first instanceof JoinResponse) {
                if (!((JoinResponse) first).accepted) {
                    throw new IOException("Join rejected by server: " + ((JoinResponse) first).reason);
                }
                clientId = ((JoinResponse) first).clientId;
            } else {
                throw new IOException("Invalid join response from server");
            }

            // connected
            running = true;
            System.out.println("Connected to server as client " + clientId);

            // start read thread
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
                } finally {
                    running = false;
                }
            }, "Client-Read-Thread");
            readThread.setDaemon(true);
            readThread.start();

        } catch (IOException | ClassNotFoundException e) {
            // cleanup on failure
            running = false;
            clientId = -1;
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {}
            socket = null;
            out = null;
            in = null;
        }
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

    public boolean isConnected() {
        return !running || socket == null || socket.isClosed();
    }
}
