package io.github.wizard_arena.net;

import io.github.wizard_arena.net.Messages.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ListenServer {
    private static final int MAX_CLIENTS = 4;
    private final int port;
    private final String hostIP;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private Thread acceptThread; // Thread for accepting new client connections
    private Thread tickThread;
    private final Map<Integer, ClientHandler> clients = new ConcurrentHashMap<>();
    private final long serverStartTime = System.nanoTime();
    private final Map<Integer, String> playerNames = new ConcurrentHashMap<>();

    private final Map<Integer, PlayerState> players = new ConcurrentHashMap<>(); // Map of clientId to PlayerState
    private final List<ProjectileState> projectiles = Collections.synchronizedList(new ArrayList<>()); // List of active projectiles

    private static class ClientHandler {
        final int clientId;
        final Socket socket;
        final ObjectOutputStream out;
        final ObjectInputStream in;
        final BlockingQueue<Messages.Msg> messageQueue = new LinkedBlockingQueue<>();
        final Thread readThread;
        ClientHandler(int clientId, Socket socket, ObjectOutputStream out, ObjectInputStream in, Thread readThread) {
            this.clientId = clientId;
            this.socket = socket;
            this.out = out;
            this.in = in;
            this.readThread = readThread;
        }
    }

    public ListenServer(int port) {
        this.port = port;
        this.hostIP = detectHostIP();
    }

    // helper method to cheese a LAN IP address
    private String detectHostIP() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()){
                        // example: 192.168.x.x or 10.x.x.x
                        return addr.getHostAddress(); // return the first found LAN IPv4 address
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return "127.0.0.1"; // fallback to localhost
    }

    public String getHostIP() {
        return hostIP;
    }

    public void start() throws IOException {
        if (running) return; // already running
        running = true;
        serverSocket = new ServerSocket(port);
        serverSocket.setReuseAddress(true); // quick restart

        acceptThread = new Thread(this::acceptLoop, "Server-Accept-Thread");
        acceptThread.setDaemon(true);
        acceptThread.start();

        tickThread = new Thread(this::tickLoop, "Server-Tick-Thread");
        tickThread.setDaemon(true);
        tickThread.start();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept(); // blocking
                clientSocket.setTcpNoDelay(true);
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                out.flush(); // flush header
                ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(clientSocket.getInputStream()));

                // first message should be JoinRequest
                Object firstMsg = in.readObject();
                if (!(firstMsg instanceof JoinRequest)) {
                    clientSocket.close();
                    continue;
                }

                JoinRequest joinRequest = (JoinRequest) firstMsg;

                // check lobby size before assigning ID
                if (clients.size() >= MAX_CLIENTS) {
                    // send rejection
                    out.writeObject(new JoinResponse(false, "Server full", -1, serverStartTime));
                    out.flush();
                    clientSocket.close();
                    continue;
                }

                // assign client ID
                int clientId = assignClientId();
                if (clientId == -1) {
                    // additional safety check just in case
                    out.writeObject(new JoinResponse(false, "Server full", -1, serverStartTime));
                    out.flush();
                    clientSocket.close();
                    continue;
                }

                String playerName = (joinRequest.playerName != null && !joinRequest.playerName.isEmpty())
                        ? joinRequest.playerName : "Player" + clientId;

                playerNames.put(clientId, playerName);
                players.put(clientId, new PlayerState(clientId, 100 + clientId * 20, 100, 100f)); // spawn player at position

                // send JoinResponse
                out.writeObject(new JoinResponse(true, null, clientId, serverStartTime));
                out.flush();

                Thread rx = new Thread(() -> {
                    ClientHandler c = clients.get(clientId);
                    try {
                        while (running) {
                            Object obj = c.in.readObject();
                            if (obj instanceof InputUpdate) {
                                InputUpdate iu = (InputUpdate) obj;
                                PlayerState p = players.get(clientId);
                                if (p != null) {
                                    // TEMP: simple movement update
                                    float speed = 200f / NetConfig.TICK_RATE;
                                    p.x += iu.moveX * speed;
                                    p.y += iu.moveY * speed;
                                }
                            } else if (obj instanceof Ping) {
                                Ping ping = (Ping) obj;
                                sendTo(c, new Pong(ping.clientTime, System.nanoTime()));
                            }
                        }
                    } catch (IOException | ClassNotFoundException e) {
                        // client disconnected or error
                        System.out.println("Client " + clientId + " disconnected: " + e.getMessage());
                    } finally {
                        // cleanup on disconnect
                        players.remove(clientId);
                        closeClient(clients.get(clientId));
                        clients.remove(clientId);
                        playerNames.remove(clientId);
                        broadcastLobbyUpdate(); // notify all clients of updated lobby state
                    }
                }, "Client-RX-" + clientId);

                ClientHandler clientHandler = new ClientHandler(clientId, clientSocket, out, in, rx);
                clients.put(clientId, clientHandler);
                rx.setDaemon(true);
                rx.start();
                System.out.println("Client " + clientId + " connected.");
                broadcastLobbyUpdate(); // notify all clients of new lobby state

                // logging
                System.out.println("[Server] Client " + clientId + " connection from " + clientSocket.getRemoteSocketAddress());

            } catch (EOFException | SocketException e) {
                // socket closed while accepting
                if(running) System.out.println("Socket closed during accept: " + e.getMessage());
            } catch (Exception e) { // general error
                if (running) e.printStackTrace(); // log error
            }
        }
    }

    private void tickLoop() {
        final long stepNanos = 1_000_000_000L / NetConfig.TICK_RATE; // tick duration
        long prevTime = System.nanoTime(); // initial time
        int tick = 0;
        double snapshotAccumulator = 0.0;
        double snapshotInterval = (double)NetConfig.TICK_RATE / NetConfig.SNAPSHOT_RATE; // ticks per snapshot

        while (running) {
            long now = System.nanoTime(); // current time
            if (now - prevTime < stepNanos) {
                try {
                    Thread.sleep(0, 500_000); // sleep 0.5ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                continue;
            }
            prevTime = now;
            tick++;

            // TODO: update game state (projectiles, collisions, etc.)

            snapshotAccumulator += 1.0;
            if (snapshotAccumulator >= snapshotInterval) {
                snapshotAccumulator -= snapshotInterval;
                sendSnapshots(tick);
            }
        }
    }

    private void sendSnapshots(int tick) {
        List<PlayerState> playerStates = new ArrayList<>(players.values()); // copy player states
        List<ProjectileState> projectileStates; // copy projectile states
        synchronized (projectiles) {
            projectileStates = new ArrayList<>(projectiles); // copy projectiles
        }

        Snapshot snapshot = new Snapshot();
        snapshot.tick = tick;
        snapshot.serverTime = System.nanoTime();
        snapshot.players = playerStates;
        snapshot.projectiles = projectileStates;

        for (ClientHandler client : clients.values()) {
            sendTo(client, snapshot);
        }
    }

    private void sendTo(ClientHandler client, Messages.Msg msg) {
        try {
            client.out.writeObject(msg);
            client.out.flush();
        } catch (IOException e) {
            System.out.println("Error sending to client " + client.clientId + ": " + e.getMessage());
            closeClient(client);
            clients.remove(client.clientId);
        }
    }

    private void closeClient(ClientHandler client) {
        if (client == null) return;
        try {
            client.socket.close();
        } catch (IOException ignored) {}
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        for (ClientHandler client : clients.values()) {
            closeClient(client);
        }
        clients.clear();
    }

    private void broadcastLobbyUpdate() {
        List<LobbyPlayerInfo> info = new ArrayList<>();
        for (ClientHandler client : clients.values()) {
            String name = playerNames.getOrDefault(client.clientId, "Player" + client.clientId);
            boolean isHost = (client.clientId == 1);
            info.add(new LobbyPlayerInfo(client.clientId, name, isHost));
        }

        LobbyUpdate lobbyUpdate = new LobbyUpdate(info, getHostIP());
        for (ClientHandler client : clients.values()) {
            sendTo(client, lobbyUpdate);
        }
    }

    public void sendStartGame() {
        StartGame startGame = new StartGame();
        for (ClientHandler client : clients.values()) {
            sendTo(client, startGame);
        }
    }

    private int assignClientId() {
        for (int i = 1; i <= MAX_CLIENTS; i++) {
            if (!clients.containsKey(i)) {
                return i;
            }
        }
        return -1; // full
    }
}
