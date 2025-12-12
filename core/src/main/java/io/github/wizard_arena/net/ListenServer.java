package io.github.wizard_arena.net;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Rectangle;
import io.github.wizard_arena.MapLoader;
import io.github.wizard_arena.SpawnPoint;
import io.github.wizard_arena.Tile;
import io.github.wizard_arena.net.Messages.*;
import io.github.wizard_arena.spells.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ListenServer {

    private float tileSize = 64;

    private String currentMapFile = "map1.txt";

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

    private static final float BLINK_COOLDOWN = 2.0f; // seconds

    private static final float MAX_HP = 100f;
    private final Set<Integer> godModePlayers = ConcurrentHashMap.newKeySet();

    private final Map<Integer, PlayerState> players = new ConcurrentHashMap<>(); // Map of clientId to PlayerState
    private final List<ProjectileState> projectiles = Collections.synchronizedList(new ArrayList<>()); // List of active projectiles

    private final List<SpawnPoint> spawnPoints = new ArrayList<>();
    private final List<SpawnPoint> spellSpawnPoints = new ArrayList<>();
    private final List<SpawnPoint> powerupSpawnPoints = new ArrayList<>();
    private final Random random = new Random();

    private static final int MAX_SPELL_SLOTS = 4;
    private final Map<Integer, BaseSpell[]> playerSpellInventories = new ConcurrentHashMap<>();
    private final AtomicInteger nextProjectileId = new AtomicInteger(1);

    private static final String[] MAP_SPELL_TYPES = {
            "starter",
            "poison",
            "fire",
            "lightning"
    };

    private static final String[] MAP_POWERUP_TYPES = {
            "speed",
            "overdrive",
            "health"
    };

    private final List<SpellPickupState> spellPickups = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger nextPickupId = new AtomicInteger(1);
    private static final float SPELL_PICKUP_RADIUS = 80f;

    private final List<PowerupState> powerups = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger nextPowerupId = new AtomicInteger(1);
    private static final float POWERUP_PICKUP_RADIUS = 64f;

    private boolean[][] wallGrid;
    private Tile[][] tileMap;
    private int mapWidth;
    private int mapHeight;
    private static final float TILE_SIZE = 64f;
    private static final float PLAYER_SIZE = 48f;
    private static final float PROJECTILE_SIZE = 32f;

    private volatile boolean debugMode = false;

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    private enum RoundState {
        WAITING,
        IN_PROGRESS,
        POST_ROUND,
        MATCH_OVER
    }
    private RoundState currentRoundState = RoundState.WAITING;

    private static final float ROUND_DURATION = 120f;
    private static final float POST_ROUND_DELAY = 10f; // time between rounds
    private static final int ROUNDS_TO_WIN = 5;

    private float roundTimer = 0f;
    private float postRoundTimer = 0f;
    private int currentRound = 0;
    private int lastRoundWinnerId = -1;
    private int matchWinnerId = -1;

    // track wins per player
    private final Map<Integer, Integer> winCount = new ConcurrentHashMap<>();

    // lava barrier parameters
    private static final float LAVA_START_TIME = 60f; // time before lava starts rising
    private static final float LAVA_INITIAL_RADIUS = 1600f; // tune to fit map size
    private static final float LAVA_MIN_RADIUS = 150;
    private static final float LAVA_SHRINK_RATE = 15f;
    private static final float LAVA_DAMAGE_PER_TICK = 20f;
    private boolean lavaActive = false;
    private boolean lavaJustStarted = false;
    private float lavaCenterX;
    private float lavaCenterY;
    private float lavaRadius;
    private float lavaInitialRadius = LAVA_INITIAL_RADIUS;

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

    public ListenServer() {
        this.port = findOpenPort(NetConfig.MIN_TCP_PORT, NetConfig.MAX_TCP_PORT);
        this.hostIP = detectHostIP();
        setMapFile(currentMapFile);
    }

    // helper method to find an open port in the given range
    private int findOpenPort(int min, int max) {
        Random r = new Random();
        while (true) {
            int port = r.nextInt(max - min) + min;
            try {
                ServerSocket ss = new ServerSocket(port);
                ss.close();
                return port;
            } catch (IOException e) {
                // port is in use, try another
            }
        }
    }

    // helper method to choose a LAN IP address
    private String detectHostIP() {
        try {
            List <InetAddress> candidates = new ArrayList<>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;

                String name = iface.getDisplayName().toLowerCase();
                String sysName = iface.getName().toLowerCase();

                if (name.contains("virtual") || name.contains("vmware") || sysName.contains("vbox") ||
                    sysName.contains("docker") || sysName.contains("tunnel") || sysName.contains("loopback")
                    || sysName.contains("vmnet") || sysName.contains("veth") || sysName.contains("br-")
                    || sysName.contains("hyper-v")) {
                    continue; // skip virtual interfaces / tunnels / loopbacks / docker bridges / wsl-related interfaces
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()){
                        candidates.add(addr);
                    }
                }
            }
            // prefer 192.168.x.x and 10.x.x.x ranges
            for (InetAddress addr : candidates) {
                String ip = addr.getHostAddress();
                if (ip.startsWith("192.168.") || ip.startsWith("10.")) {
                    return ip; // return the first found LAN IPv4 address
                }
            }
            if (!candidates.isEmpty()) {
                return candidates.get(0).getHostAddress(); // return first candidate if no preferred range found
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return "127.0.0.1"; // fallback to localhost
    }

    public String getHostIP() {
        return hostIP;
    }
    public int getPort() {
        return port;
    }

    public String getMapFile() {
        return currentMapFile;
    }

    public synchronized void setMapFile(String mapFilePath) {
        this.currentMapFile = mapFilePath;
        MapLoader.MapSpawns spawns = MapLoader.loadSpawns(mapFilePath);

        spawnPoints.clear();
        spawnPoints.addAll(spawns.playerSpawns);
        spellSpawnPoints.clear();
        spellSpawnPoints.addAll(spawns.spellSpawns);
        powerupSpawnPoints.clear();
        powerupSpawnPoints.addAll(spawns.powerupSpawns);

        loadMapCollision(mapFilePath);
        System.out.println("Server map set to " + mapFilePath);
    }

    private void loadMapCollision(String mapFilePath) {
        FileHandle file = Gdx.files.internal(mapFilePath);
        String text = file.readString();
        String[] lines = text.split("\\r?\\n");

        List<String> list = Arrays.asList(lines);
        Collections.reverse(list);
        mapHeight = lines.length;
        mapWidth = lines[0].length();
        wallGrid = new boolean[mapHeight][mapWidth];

        for (int col = 0; col < mapHeight; col++) {
            String line = lines[col];
            System.out.println(line);
            for (int row = 0; row < mapWidth; row++) {


                char tileChar = line.charAt(row);

                if (tileChar == 'X') {
                    wallGrid[col][row] = true; // X denotes a wall
                }

            }
        }
        System.out.println("Loaded map collision from " + mapFilePath);
        System.out.println("Map size: " + mapWidth + "x" + mapHeight);
    }

    private String getRandomMapSpellType() {
        if (MAP_SPELL_TYPES.length == 0) return "starter";
        return MAP_SPELL_TYPES[random.nextInt(MAP_SPELL_TYPES.length)];
    }

    private String getRandomMapPowerupType() {
        if (MAP_POWERUP_TYPES.length == 0) return "speed";
        return MAP_POWERUP_TYPES[random.nextInt(MAP_POWERUP_TYPES.length)];
    }

    private boolean projectileHitsWall(float x, float y, float size) {
        if (wallGrid == null) return false;
        float centerX = x + size / 2f;
        float centerY = y + size / 2f;
        Rectangle ballRect = new Rectangle(x, y, size, size);
        int tileX = (int)(centerX / TILE_SIZE);
        int tileY = (int)(centerY / TILE_SIZE);

        for (int ty = tileY - 1; ty <= tileY + 1; ty++) {
            for (int tx = tileX - 1; tx <= tileX + 1; tx++) {
                Rectangle adjTile = new Rectangle(tx * TILE_SIZE, ty * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                if (tx >= 0 && tx < mapWidth && ty >= 0 && ty < mapHeight) {
                    if (wallGrid[ty][tx] && ballRect.overlaps(adjTile)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // check if two rectangles overlap
    private boolean rectsOverlap(float x1, float y1, float w1, float h1,
                                    float x2, float y2, float w2, float h2) {
        return (x1 < x2 + w2) && (x1 + w1 > x2) &&
               (y1 < y2 + h2) && (y1 + h1 > y2);
    }

    public void start() throws IOException {
        if (running) return; // already running
        running = true;
        serverSocket = new ServerSocket(port);
        serverSocket.setReuseAddress(true); // quick restart
        System.out.println("Server listening on " + hostIP + ":" + port);

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
                PlayerState ps = new PlayerState(clientId, 0f, 0f, MAX_HP); // spawn player at position
                ps.lastProcessedInput = 0;
                ps.blinkCooldown = 0f;
                ps.alive = true;
                ps.roundsWon = winCount.getOrDefault(clientId, 0); // retrieve previous wins
                ps.speedMultiplier = 1.0f;
                ps.speedBoostDuration = 0f;
                ps.overdriveDuration = 0f;
                players.put(clientId, ps); // add to player states

                // give player the starter spell
                BaseSpell[] inv = new BaseSpell[MAX_SPELL_SLOTS];
                inv[0] = new StarterBallSpell(); // first slot
                playerSpellInventories.put(clientId, inv);
                ps.spellSlots[0] = "starter";

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

                                // debug logging
                                //System.out.println("client input " + clientId + " move=(" + iu.moveX + "," + iu.moveY + ") dt=" + iu.dt);

                                PlayerState p = players.get(clientId);
                                if (p != null) {
                                    float baseSpeed = 300f;
                                    float speed = baseSpeed * p.speedMultiplier;
                                    float dt = iu.dt;

                                    // movement
                                    // blink
                                    if(iu.blink && p.blinkCooldown <= 0f) {
                                        p.x += iu.moveX * tileSize;
                                        p.y += iu.moveY * tileSize;
                                        p.blinkCooldown = BLINK_COOLDOWN;
                                    }
                                    else {
                                        p.x += iu.moveX * speed * dt;
                                        p.y += iu.moveY * speed * dt;
                                    }
                                    p.blinkCooldown = Math.max(0f, p.blinkCooldown - dt); // reduce blink cooldown
                                    p.currentAnimationID = iu.currentAnimationID;
                                    // process rotation of players
                                    p.lookAngle = iu.lookAngle;

                                    if (iu.selectedSpellSlot < 0) iu.selectedSpellSlot = 0; // clamp spell slot
                                    if (iu.selectedSpellSlot >= MAX_SPELL_SLOTS) iu.selectedSpellSlot = MAX_SPELL_SLOTS - 1;
                                    p.selectedSpellSlot = iu.selectedSpellSlot; // update selected spell slot

                                    // spell pickup
                                    if(iu.pickupSpell){
                                        SpellPickupState picked = null;
                                        float bestDistSq = SPELL_PICKUP_RADIUS * SPELL_PICKUP_RADIUS;
                                        synchronized (spellPickups){
                                            for (SpellPickupState sp : spellPickups){
                                                float dx = sp.x - p.x;
                                                float dy = sp.y - p.y;
                                                float distSq = dx * dx + dy * dy;
                                                if (distSq < bestDistSq){
                                                    bestDistSq = distSq;
                                                    picked = sp;
                                                }
                                            }

                                            if (picked != null) {
                                                // give spell to player
                                                BaseSpell newSpell = createSpellFromType(picked.spellType);
                                                if (newSpell != null) {
                                                    // add spell to player's inventory and pass position for dropping old spell if needed
                                                    giveSpellToPlayer(clientId, newSpell, picked.spellType, p.x, p.y);
                                                    spellPickups.remove(picked);
                                                    System.out.println("Player " + clientId + " picked up spell " + picked.spellType + " from pickup " + picked.id);
                                                }
                                            }
                                        }
                                    }

                                    // spell casting
                                    if(iu.castSpell){
                                        BaseSpell[] inventory = playerSpellInventories.get(clientId);
                                        if (inventory != null) {
                                            BaseSpell spell = inventory[p.selectedSpellSlot];
                                            if (spell != null) {
                                                boolean overdrive = (p.overdriveDuration > 0f);
                                                if (overdrive || spell.canCast()) {
                                                    int projectileId = nextProjectileId.getAndIncrement();
                                                    if (!overdrive) {
                                                        spell.onCast(); // start cooldown if overdrive powerup not active
                                                    }
                                                    spell.spawnProjectile(projectiles, p, wallGrid, iu.aimX, iu.aimY, projectileId);

                                                    System.out.println("player " + clientId + " spawned projectile " + projectileId + " from spell slot " + p.selectedSpellSlot);
                                                }
                                            }
                                        }
                                    }

                                    // powerup pickup
                                    PowerupState pickedPowerup = null;
                                    float bestPowerupDistSq = POWERUP_PICKUP_RADIUS * POWERUP_PICKUP_RADIUS;
                                    synchronized (powerups) {
                                        for (PowerupState pu : powerups) {
                                            float dx = pu.x - p.x;
                                            float dy = pu.y - p.y;
                                            float distSq = dx * dx + dy * dy;
                                            if (distSq < bestPowerupDistSq) {
                                                bestPowerupDistSq = distSq;
                                                pickedPowerup = pu;
                                            }
                                        }
                                        if (pickedPowerup != null) {
                                            applyPowerup(p, pickedPowerup.powerupType);
                                            powerups.remove(pickedPowerup);
                                            System.out.println("Player " + clientId + " picked up powerup " + pickedPowerup.powerupType + " from pickup " + pickedPowerup.id);
                                        }
                                    }

                                    p.lastProcessedInput = iu.seq; // update last processed input seq
                                }
                            } else if (obj instanceof Ping) {
                                Ping ping = (Ping) obj;
                                sendTo(c, new Pong(ping.clientTime, System.nanoTime()));

                            } else if (obj instanceof DamageRequest) {
                                DamageRequest dr = (DamageRequest) obj;
                                PlayerState p = players.get(dr.playerId);
                                if (p == null) continue;
                                if (dr. amount > 0 && godModePlayers.contains(dr.playerId)) {
                                    // ignore damage in god mode
                                    continue;
                                }

                                float prevHp = p.hp;
                                p.hp -= dr.amount;
                                if (p.hp < 0f) p.hp = 0f;
                                if (p.hp > MAX_HP) p.hp = MAX_HP;

                                // death handling
                                if (prevHp > 0f && p.hp <= 0f) {
                                    p.alive = false;
                                    System.out.println("Player " + dr.playerId + " is dead");
                                    if (currentRoundState == RoundState.IN_PROGRESS) {
                                        checkPlayersAlive();
                                    }
                                }
                                System.out.println("Player " + dr.playerId + " took " + dr.amount + " damage, HP = " + p.hp);

                            } else if (obj instanceof HealRequest) {
                                HealRequest hr = (HealRequest) obj;
                                PlayerState p = players.get(hr.playerId);
                                if ( p == null) continue;
                                p.hp += hr.amount;
                                if (p.hp > MAX_HP) p.hp = MAX_HP;
                                System.out.println("Player " + hr.playerId + " healed " + hr.amount + ", HP = " + p.hp);

                            } else if (obj instanceof GodmodeRequest) {
                                GodmodeRequest gr = (GodmodeRequest) obj;
                                if (gr.enabled) {
                                    godModePlayers.add(gr.playerId);
                                    System.out.println("Player " + gr.playerId + " enabled god mode.");
                                } else {
                                    godModePlayers.remove(gr.playerId);
                                    System.out.println("Player " + gr.playerId + " disabled god mode.");
                                }

                            } else if (obj instanceof SpawnSpellPickupRequest) {
                                SpawnSpellPickupRequest spq = (SpawnSpellPickupRequest) obj;
                                PlayerState p = players.get(spq.playerId);
                                if (p != null) {
                                    synchronized (spellPickups){
                                        int pickupId = nextPickupId.getAndIncrement();
                                        SpellPickupState sps = new SpellPickupState(
                                                pickupId,
                                                p.x,
                                                p.y,
                                                spq.spellType
                                        );
                                        spellPickups.add(sps);
                                        System.out.println("Spawned spell pickup " + spq.spellType + " with ID " + pickupId);
                                    }
                                }
                            } else if (obj instanceof SpawnPowerupRequest) {
                                SpawnPowerupRequest spuq = (SpawnPowerupRequest) obj;
                                PlayerState p = players.get(spuq.playerId);
                                if(p != null) {
                                    float spawnOffset = 64f;
                                    float spawnX = p.x + spawnOffset;
                                    float spawnY = p.y;
                                    synchronized (powerups){
                                        int pickupId = nextPowerupId.getAndIncrement();
                                        PowerupState pus = new PowerupState(
                                                pickupId,
                                                spawnX,
                                                spawnY,
                                                spuq.powerupType
                                        );
                                        powerups.add(pus);
                                        System.out.println("Spawned powerup pickup " + spuq.powerupType + " with ID " + pickupId + " at (" + spawnX + "," + spawnY + ") for player " + spuq.playerId);
                                    }
                                }
                            } else if (obj instanceof LavaStartRequest) {
                                LavaStartRequest lsr = (LavaStartRequest) obj;
                                if (currentRoundState == RoundState.IN_PROGRESS && !lavaActive) {
                                    lavaActive = true;
                                    lavaJustStarted = true;
                                    lavaRadius = lavaInitialRadius;
                                    System.out.println("Lava has been started early by player " + lsr.playerId);
                                }
                            }
                        }
                    } catch (IOException | ClassNotFoundException e) {
                        // client disconnected or error
                        System.out.println("Client " + clientId + " disconnected: " + e.getMessage());
                    } finally {
                        // cleanup on disconnect
                        players.remove(clientId);
                        playerSpellInventories.remove(clientId);
                        closeClient(clients.get(clientId));
                        clients.remove(clientId);
                        playerNames.remove(clientId);
                        broadcastLobbyUpdate(); // notify all clients of updated lobby state
                    }
                }, "Client-RX-" + clientId); // receive thread

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
            float dt = (now - prevTime) / 1_000_000_000f; // delta time in seconds
            prevTime = now;
            tick++;

            updateGame(dt);
            updatePowerupTimers(dt);
            updateSpells(dt);
            updateStatusEffects(dt);
            updateProjectiles(dt);

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

        List<SpellPickupState> pickupStates;
        synchronized (spellPickups){
            pickupStates = new ArrayList<>(spellPickups); // copy spell pickups
        }

        List<PowerupState> powerupStates;
        synchronized (powerups){
            powerupStates = new ArrayList<>(powerups); // copy powerups
        }

        Snapshot snapshot = new Snapshot();
        snapshot.tick = tick;
        snapshot.serverTime = System.nanoTime();
        snapshot.players = playerStates;
        snapshot.projectiles = projectileStates;
        snapshot.spellPickups = pickupStates;
        snapshot.powerups = powerupStates;

        snapshot.roundNumber = currentRound;
        snapshot.roundTimeRemaining = roundTimer;
        snapshot.roundState = currentRoundState.ordinal(); // 0=waiting,1=in progress,2=post round,3=match over
        snapshot.roundWinnerId = lastRoundWinnerId;
        snapshot.matchWinnerId = matchWinnerId;
        snapshot.postRoundTimeRemaining = postRoundTimer;

        snapshot.lavaActive = lavaActive;
        snapshot.lavaJustStarted = lavaJustStarted;
        snapshot.lavaCenterX = lavaCenterX;
        snapshot.lavaCenterY = lavaCenterY;
        snapshot.lavaRadius = lavaRadius;
        lavaJustStarted = false; // reset after sending *should only be true for one snapshot*

        snapshot.playerNames = new HashMap<>(playerNames);

        if (lastRoundWinnerId != -1) {
            snapshot.roundWinnerName = playerNames.get(lastRoundWinnerId);
        }

        if (matchWinnerId != -1) {
            snapshot.matchWinnerName = playerNames.get(matchWinnerId);
        }

        for (ClientHandler client : clients.values()) {
            sendTo(client, snapshot);
        }

        // debug logging
        /*for (PlayerState ps : playerStates){
            System.out.println("snapshot player " + ps.id + " pos=(" + ps.x + "," + ps.y + ")");
        } */
    }

    private void sendTo(ClientHandler client, Messages.Msg msg) {
        try {
            client.out.reset();
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

        LobbyUpdate lobbyUpdate = new LobbyUpdate(info, getHostIP(), port);
        for (ClientHandler client : clients.values()) {
            sendTo(client, lobbyUpdate);
        }
    }

    public void sendStartGame() {
        if (currentRoundState == RoundState.WAITING){
            int numPlayers = players.size();

            if (!debugMode && numPlayers < 2) {
                System.out.println("Not enough players to start the game.");
                return; // not enough players to start
            }

            startNewRound();
        }

        StartGame startGame = new StartGame(currentMapFile);
        for (ClientHandler client : clients.values()) {
            sendTo(client, startGame);
        }
    }

    private int assignClientId() {
        for (int i = 1; i <= MAX_CLIENTS; i++) {
            if (!clients.containsKey(i)) {
                System.out.println(i);
                return i;
            }
        }
        return -1; // full
    }

    // assign spawn points to players at the start of each round
    private void assignSpawns(){
        if (spawnPoints == null || spawnPoints.isEmpty()) {
            System.out.println("no spawn points available");
            return;
        }

        List<PlayerState> playerStates = new ArrayList<>(players.values());
        if (playerStates.isEmpty()) {
            System.out.println("no players to assign spawns to");
            return;
        }

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < spawnPoints.size(); i++) {
            indices.add(i);
        }

        Collections.shuffle(indices, random);
        int spawnIndex = 0;
        for (PlayerState p : playerStates) {
            if(indices.isEmpty()){
                System.out.println("not enough spawn points for all players");
                break;
            }

            if (spawnIndex >= indices.size()) {
                spawnIndex = 0; // wrap around if more players than spawn points *shouldn't happen*
            }

            int chosenIndex = indices.get(spawnIndex++);
            SpawnPoint sp = spawnPoints.get(chosenIndex);
            p.x = sp.x;
            p.y = sp.y;
            p.hp = MAX_HP; // reset health on spawn
            p.alive = true;
            p.blinkCooldown = 0f; // reset blink cooldown
            System.out.println("Assigned spawn to player " + p.id + " at (" + p.x + ", " + p.y + ")");
        }
    }

    // update game logic per tick
    private void updateGame(float dt) {
        if (debugMode) {
            if (currentRoundState == RoundState.WAITING) {
                startNewRound();
            }
            return; // skip round logic in debug mode
        }

        switch(currentRoundState){
            case WAITING:
                break;
            case IN_PROGRESS:
                roundTimer -= dt;
                if (roundTimer < 0f) roundTimer = 0f; // clamp to zero so it doesn't go negative

                if(!lavaActive && (ROUND_DURATION - roundTimer) >= LAVA_START_TIME){
                    // start lava
                    lavaActive = true;
                    lavaJustStarted = true;
                    lavaRadius = lavaInitialRadius;
                    System.out.println("Lava has started rising!");
                }

                if (lavaActive) {
                    updateLava(dt);
                }

                checkPlayersAlive();
                break;
            case POST_ROUND:
                postRoundTimer -= dt;
                if (postRoundTimer <= 0f) {
                    startNewRound();
                }
                break;
            case MATCH_OVER:
                // return to the lobby after delay
                postRoundTimer -= dt;
                if (postRoundTimer <= 0f) {
                    System.out.println("match over, returning to lobby");
                    // this should close all sockets and set running to false
                    // host and clients should return to the main menu
                    stop();
                }
                break;
        }
    }

    // update projectiles per tick
    private void updateProjectiles(float dt) {
        float worldWidth = mapWidth * TILE_SIZE;
        float worldHeight = mapHeight * TILE_SIZE;

        synchronized (projectiles) {
            Iterator<ProjectileState> it = projectiles.iterator();
            while (it.hasNext()) {
                ProjectileState proj = it.next();
                float nextX = proj.x + proj.vx * dt;
                float nextY = proj.y + proj.vy * dt;
                boolean remove = false;
                float size = PROJECTILE_SIZE;

                if (!proj.piercing) {
                    // check wall collisions
                    // starter/breakout ball bounce
                    if ("starter".equals(proj.spellType)) {
                        float testX = nextX;
                        if (projectileHitsWall(testX, proj.y, size)) {
                            proj.vx = -proj.vx; // reverse X velocity
                        } else { // no collision, apply new X
                            proj.x = testX;
                        }
                        float testY = nextY;
                        if (projectileHitsWall(proj.x, testY, size)) {
                            proj.vy = -proj.vy; // reverse Y velocity
                        } else { // no collision, apply new Y
                            proj.y = testY;
                        }
                    } else {
                        // other spell types just get removed on wall hit
                        if (projectileHitsWall(nextX, nextY, size)) {
                            remove = true;
                        } else {
                            proj.x = nextX;
                            proj.y = nextY;
                        }
                    }
                } else {
                    // piercing projectiles just move
                    proj.x = nextX;
                    proj.y = nextY;
                }
                // check out of bounds
                if (!remove) {
                    if (proj.x < 0f || proj.x > worldWidth || proj.y < 0f || proj.y > worldHeight) {
                        remove = true;
                    }
                }
                // projectile vs player collisions
                if (!remove) {
                    for (PlayerState p : players.values()) {
                        if (!p.alive) continue;
                        if (rectsOverlap(proj.x, proj.y, size, size, p.x, p.y, PLAYER_SIZE, PLAYER_SIZE)) {
                            // hit detected
                            damagePlayer(p.id, proj.damage);
                            // apply debuffs based on spell type
                            if ("poison".equals(proj.spellType)) {
                                applyPoisonDebuff(p);
                            } else if ("fire".equals(proj.spellType)) {
                                applyBurnDebuff(p);
                            }

                            // add more spell types here...
                            remove = true;
                            break;
                        }
                    }
                }
                if (remove) {
                    it.remove();
                }
            }
            // handle special cases here
            // e.g. fire and ice interactions, etc.
            handleProjectileCollisions();
        }
    }

    private void handleProjectileCollisions() {
        if (projectiles.size() < 2) return; // need at least 2 projectiles to collide
        float size = PROJECTILE_SIZE;
        Set<Integer> toRemove = new HashSet<>();

        int n = projectiles.size();
        for (int i = 0; i < n; i++) {
            ProjectileState p1 = projectiles.get(i);
            if (p1 == null) continue;
            for (int j = i + 1; j < n; j++) {
                ProjectileState p2 = projectiles.get(j);
                if (p2 == null) continue;

                boolean fireIceCollision =
                    ("fire".equals(p1.spellType) && "ice".equals(p2.spellType)) ||
                        ("ice".equals(p1.spellType) && "fire".equals(p2.spellType));

                if (fireIceCollision) continue;

                if (rectsOverlap(p1.x, p1.y, size, size, p2.x, p2.y, size, size)) {
                    // collision detected, mark both for removal
                    toRemove.add(p1.id);
                    toRemove.add(p2.id);
                }

                // TODO: add effects for specific spell collisions here
                //System.out.println("Projectile " + p1.id + " (" + p1.spellType + ") collided with Projectile " + p2.id + " (" + p2.spellType + ")");
            }
        }
    }

    private void updateSpells(float dt){
        for (Map.Entry<Integer, BaseSpell[]> entry : playerSpellInventories.entrySet()) {
            int playerId = entry.getKey();
            PlayerState ps = players.get(playerId);
            if (ps == null) continue;

            BaseSpell [] inv = entry.getValue();
            for (int i = 0; i < MAX_SPELL_SLOTS; i++) {
                BaseSpell spell = inv[i];
                if (spell != null) {
                    spell.update(dt);

                    // sync spell types and cooldowns to player state
                    ps.spellSlots[i] = spell.getTypeId();
                    ps.spellCooldownRemaining[i] = spell.getCooldownTimer();
                    ps.spellCooldownMax[i] = spell.getCooldownTime();
                } else {
                    ps.spellSlots[i] = null;
                    ps.spellCooldownRemaining[i] = 0f;
                    ps.spellCooldownMax[i] = 0f;
                }
            }
        }
    }

    private void giveSpellToPlayer(int playerId, BaseSpell newSpell, String spellType, float dropX, float dropY){
        BaseSpell[] inv = playerSpellInventories.get(playerId);
        if (inv == null ) return;
        PlayerState ps = players.get(playerId);

        // if empty slot, add spell there
        for(int i = 0; i < MAX_SPELL_SLOTS; i++){
            if (inv[i] == null) {
                inv[i] = newSpell;
                System.out.println("Gave spell to player " + playerId + " in slot " + i);

                // put spell on cooldown
                if (newSpell != null) {
                    newSpell.putOnCooldown();
                }

                if (ps != null && ps.spellSlots != null && i < ps.spellSlots.length) {
                    ps.spellSlots[i] = spellType; // update spell type for client display
                }
                return;
            }
        }

        // inventory full, replace currently selected slot and drop old spell
        int selected = (ps != null) ? ps.selectedSpellSlot : 0;
        if (selected < 0 || selected >= MAX_SPELL_SLOTS) {
            selected = 0; // clamp
        }

        BaseSpell oldSpell = inv[selected];
        if (oldSpell != null) {
            String oldSpellType = oldSpell.getTypeId();
            synchronized (spellPickups) {
                int pickupId = nextPickupId.getAndIncrement();
                SpellPickupState sps = new SpellPickupState(
                        pickupId,
                        ps.x,
                        ps.y,
                        oldSpellType
                );
                spellPickups.add(sps);
                System.out.println("dropped old spell pickup " + oldSpellType + " with ID " + pickupId + " at (" + ps.x + "," + ps.y + ") for player " + playerId);
            }
        }
        inv[selected] = newSpell;
        System.out.println("Replaced spell in slot " + selected + " for player " + playerId + " with " + newSpell);

        // put replaced spell on cooldown
        if (newSpell != null) {
            newSpell.putOnCooldown();
        }

        // sync spell type to player state spell slots
        if (ps != null && ps.spellSlots != null && selected < ps.spellSlots.length) {
            ps.spellSlots[selected] = spellType; // update spell type for client display
        }
    }

    private BaseSpell createSpellFromType(String spellType){
        switch (spellType) {
            case "starter":
                return new StarterBallSpell();
            case "poison":
                return new PoisonBallSpell();
            case "fire":
                return new FireBallSpell();
            case "lightning":
                return new LightningBallSpell();
            default:
                System.out.println("Unknown spell type: " + spellType);
                return new StarterBallSpell(); // default spell if unknown
        }
    }

    private void applyPoisonDebuff(PlayerState p) {
        if (p.burnDuration > 0f || p.combinedDotDuration > 0f) {
            p.poisonDuration = 0f; // cancel poison if burn or combined DOT active
            p.burnDuration = 0f; // cancel burn
            p.combinedDotDuration = 5.0f; // apply combined DOT for 5 secs
            System.out.println("player " + p.id + " combined DOT applied");
        } else {
            p.poisonDuration = 5.0f; // apply poison for 5 secs
            System.out.println("player " + p.id + "is poisoned");
        }
    }

    private void applyBurnDebuff(PlayerState p) {
        if (p.poisonDuration > 0f || p.combinedDotDuration > 0f) {
            p.burnDuration = 0f; // cancel burn if poison or combined DOT active
            p.poisonDuration = 0f; // cancel poison
            p.combinedDotDuration = 5.0f; // apply combined DOT for 5 secs
            System.out.println("player " + p.id + " combined DOT applied");
        } else {
            p.burnDuration = 5.0f; // apply burn for 5 secs
            System.out.println("player " + p.id + "is burned");
        }
    }

    private void updateStatusEffects(float dt) {
        for (PlayerState p : players.values()) {
            if (!p.alive) continue;

            // decrease durations
            if (p.poisonDuration > 0f) {
                p.poisonDuration -= dt;
                if (p.poisonDuration <= 0f) {
                    p.poisonDuration = 0f;
                }
            }

            if (p.burnDuration > 0f) {
                p.burnDuration -= dt;
                if (p.burnDuration <= 0f) {
                    p.burnDuration = 0f;
                }
            }

            if (p.combinedDotDuration > 0f) {
                p.combinedDotDuration -= dt;
                if (p.combinedDotDuration <= 0f) {
                    p.combinedDotDuration = 0f;
                }
            }

            boolean hasDot =
                (p.poisonDuration > 0f) ||
                (p.burnDuration > 0f) ||
                (p.combinedDotDuration > 0f);

            if (hasDot) {
                // accumulate tick timer until 1 second
                p.dotTickTimer += dt;
                if (p.dotTickTimer >= 1.0f) {
                    p.dotTickTimer -= 1.0f;
                    float dmgThisTick = 0; // damage per second

                    if (p.combinedDotDuration > 0f) {
                        dmgThisTick = 12f;
                    } else {
                        if (p.poisonDuration > 0f) {
                            dmgThisTick += 5f;
                        }
                        if (p.burnDuration > 0f) {
                            dmgThisTick += 4f;
                        }
                    }
                    if (dmgThisTick > 0f) {
                        damagePlayer(p.id, dmgThisTick);
                        System.out.println("player " + p.id + " took " + dmgThisTick + " DOT damage");
                    }
                }
            } else {
                p.dotTickTimer = 0f; // reset if no DOT active
            }
        }
    }

    // server side damage application
    private void damagePlayer(int playerId, float amount) {
        PlayerState p = players.get(playerId);
        if (p == null) return;

        if (amount > 0 && godModePlayers.contains(playerId)) {
            // ignore damage in god mode
            return;
        }

        float prevHp = p.hp;
        p.hp -= amount;
        if (p.hp < 0f) p.hp = 0f;
        if (p.hp > MAX_HP) p.hp = MAX_HP;

        // death handling
        if (prevHp > 0f && p.hp <= 0f) {
            p.alive = false;
            System.out.println("Player " + playerId + " is dead");
            if (currentRoundState == RoundState.IN_PROGRESS) {
                checkPlayersAlive();
            }
        }
        System.out.println("Player " + playerId + " took " + amount + " damage, HP = " + p.hp);
    }

    private void updatePowerupTimers(float dt) {
        for (PlayerState p : players.values()){

            // speed boost timer
            if (p.speedBoostDuration > 0f) {
                p.speedBoostDuration -= dt;
                if (p.speedBoostDuration <= 0f) {
                    p.speedMultiplier = 1.0f; // reset speed
                    p.speedBoostDuration = 0f;
                    System.out.println("Player " + p.id + " speed boost ended.");
                }
            }

            if (p.overdriveDuration > 0f) {
                p.overdriveDuration -= dt;
                if (p.overdriveDuration <= 0f) {
                    p.overdriveDuration = 0f;
                    System.out.println("Player " + p.id + " overdrive ended.");
                }
            }
        }
    }

    private void applyPowerup(PlayerState p, String powerupType) {
        switch (powerupType) {
            case "speed":
                p.speedMultiplier = 1.5f; // 50% speed boost
                p.speedBoostDuration = 5.0f; // 5 secs
                System.out.println("Player " + p.id + " speed boost applied");
                break;

            case "overdrive":
                p.overdriveDuration = 5.0f; // 5 secs
                System.out.println("Player " + p.id + " overdrive applied");
                break;

            case "health":
                float prevHp = p.hp;
                p.hp = MAX_HP;
                System.out.println("Player " + p.id + " health restored from " + prevHp + " to " + p.hp);
                break;

            default:
                System.out.println("Unknown powerup type: " + powerupType);
                break;
        }
    }

    private void checkPlayersAlive(){
        if (currentRoundState != RoundState.IN_PROGRESS) return;
        int aliveCount = 0;
        int lastAliveId = -1;

        for (PlayerState p : players.values()) {
            if (p.alive) {
                aliveCount++;
                lastAliveId = p.id;
            }
        }

        // if 0 alive draw, if 1 alive that player wins
        if (aliveCount <= 1) {
            endRound(lastAliveId);
        }
    }

    private void updateLava(float dt) {
        // shrink lava radius over time
        if (lavaRadius > LAVA_MIN_RADIUS) {
            lavaRadius -= LAVA_SHRINK_RATE * dt;
            if (lavaRadius < LAVA_MIN_RADIUS) {
                lavaRadius = LAVA_MIN_RADIUS;
            }
        }

        for (PlayerState p : players.values()) {
            if (!p.alive) continue; // skip dead players

            float dx = p.x - lavaCenterX;
            float dy = p.y - lavaCenterY;
            float dist = (float)Math.sqrt(dx * dx + dy * dy);
            boolean nowInLava = (dist > lavaRadius);
            if (nowInLava) {
                // take 20 dmg in the first tick
                if (!p.inLava) {
                    damagePlayer(p.id, LAVA_DAMAGE_PER_TICK);
                    p.lavaTickTimer = 0f;
                } else {
                    // accumulate lava tick timer, 1 tick every second
                    p.lavaTickTimer += dt;
                    if (p.lavaTickTimer >= 1.0f) {
                        p.lavaTickTimer -= 1.0f;
                        damagePlayer(p.id, LAVA_DAMAGE_PER_TICK);
                    }
                }
            } else {
                // player is out of lava, reset timer
                p.lavaTickTimer = 0f;
            }
            p.inLava = nowInLava; // update lava status
        }
    }

    private void startNewRound() {
        currentRound++;
        roundTimer = ROUND_DURATION;
        postRoundTimer = 0f;
        lastRoundWinnerId = -1;

        if (matchWinnerId != -1) {
            currentRoundState = RoundState.MATCH_OVER;
            System.out.println("match over, player " + matchWinnerId + " won the match");
            return;
        }

        assignSpawns();

        // calculate lava center
        if (spawnPoints != null && !spawnPoints.isEmpty()) {
            float sumX = 0f;
            float sumY = 0f;
            float maxDistSq = 0f;
            for (SpawnPoint sp : spawnPoints) {
                sumX += sp.x;
                sumY += sp.y;
            }
            lavaCenterX = sumX / spawnPoints.size();
            lavaCenterY = sumY / spawnPoints.size();

            // compute radius to cover all spawn points
            for (SpawnPoint sp : spawnPoints) {
                float dx = sp.x - lavaCenterX;
                float dy = sp.y - lavaCenterY;
                float distSq = dx * dx + dy * dy;
                if (distSq > maxDistSq) {
                    maxDistSq = distSq;
                }
            }

            float margin = 400f;
            lavaInitialRadius = (float) Math.sqrt(maxDistSq) + margin;

            if (lavaInitialRadius < LAVA_MIN_RADIUS) {
                lavaInitialRadius = LAVA_MIN_RADIUS;
            }
        }

        // reset lava
        lavaActive = false;
        lavaJustStarted = false;
        lavaRadius = 0f;
        for (PlayerState p : players.values()) {
            p.inLava = false;
            p.lavaTickTimer = 0f;
        }

        // reset players
        for (PlayerState p : players.values()) {
            p.hp = MAX_HP;
            p.alive = true;
            p.blinkCooldown = 0f;
            p.roundsWon = winCount.getOrDefault(p.id, 0);
        }

        // reset projectiles
        synchronized (projectiles) {
            projectiles.clear();
        }

        // reset spell pickups
        synchronized (spellPickups) {
            spellPickups.clear();
            for (SpawnPoint sp : spellSpawnPoints) {
                int pickupId = nextPickupId.getAndIncrement();
                String spellType = getRandomMapSpellType();
                SpellPickupState sps = new SpellPickupState(
                        pickupId,
                        sp.x,
                        sp.y,
                        spellType
                );
                spellPickups.add(sps);
            }
        }

        // reset powerups
        synchronized (powerups) {
            powerups.clear();
            for (SpawnPoint sp : powerupSpawnPoints) {
                int pickupId = nextPowerupId.getAndIncrement();
                String powerupType = getRandomMapPowerupType();
                PowerupState pus = new PowerupState(
                    pickupId,
                    sp.x,
                    sp.y,
                    powerupType
                );
                powerups.add(pus);
            }
        }

        currentRoundState = RoundState.IN_PROGRESS;
        System.out.println("Round " + currentRound + " started.");
    }

    private void endRound(int winnerID){
        currentRoundState = RoundState.POST_ROUND;
        postRoundTimer = POST_ROUND_DELAY;
        lastRoundWinnerId = winnerID;

        if (winnerID != -1) {
            winCount.merge(winnerID, 1, Integer::sum);
            int wins = winCount.get(winnerID);
            PlayerState p = players.get(winnerID);
            if (p != null) {
                p.roundsWon = wins;
            }
            System.out.println("Round " + currentRound + " won by player " + winnerID + " (Total Wins: " + wins + ")");
            if (wins >= ROUNDS_TO_WIN) {
                matchWinnerId = winnerID;
                currentRoundState = RoundState.MATCH_OVER;
                postRoundTimer = POST_ROUND_DELAY;
                System.out.println("player " + winnerID + " won the match");
            }
        } else {
            System.out.println("Round " + currentRound + " ended with no winner.");
        }
    }
}
