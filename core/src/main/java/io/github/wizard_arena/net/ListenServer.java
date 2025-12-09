package io.github.wizard_arena.net;

import io.github.wizard_arena.MapLoader;
import io.github.wizard_arena.SpawnPoint;
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
    private static final float BLINK_DISTANCE = 200f; // units

    private static final float MAX_HP = 100f;
    private final Set<Integer> godModePlayers = ConcurrentHashMap.newKeySet();

    private final Map<Integer, PlayerState> players = new ConcurrentHashMap<>(); // Map of clientId to PlayerState
    private final List<ProjectileState> projectiles = Collections.synchronizedList(new ArrayList<>()); // List of active projectiles

    private final List<SpawnPoint> spawnPoints;
    private final Random random = new Random();

    private static final int MAX_SPELL_SLOTS = 4;
    private final Map<Integer, BaseSpell[]> playerSpellInventories = new ConcurrentHashMap<>();
    private final AtomicInteger nextProjectileId = new AtomicInteger(1);

    private final List<SpellPickupState> spellPickups = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger nextPickupId = new AtomicInteger(1);
    private static final float SPELL_PICKUP_RADIUS = 80f;

    private final List<PowerupState> powerups = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger nextPowerupId = new AtomicInteger(1);
    private static final float POWERUP_PICKUP_RADIUS = 64f;

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
        this.spawnPoints = MapLoader.loadSpawnPoints("map1.txt");
    }

    // helper method to choose a LAN IP address
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
                                                    spell.spawnProjectile(projectiles, p, iu.aimX, iu.aimY, projectileId);
                                                    if (!overdrive) {
                                                        spell.onCast(); // start cooldown if overdrive powerup not active
                                                    }
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

            // update round logic
            updateGame(dt);

            // update powerup timers
            updatePowerupTimers(dt);

            // update spells
            updateSpells(dt);

            // update status effects
            updateStatusEffects(dt);

            // update projectiles
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

        LobbyUpdate lobbyUpdate = new LobbyUpdate(info, getHostIP());
        for (ClientHandler client : clients.values()) {
            sendTo(client, lobbyUpdate);
        }
    }

    public void sendStartGame() {
        if (currentRoundState == RoundState.WAITING){
            startNewRound();
        }

        StartGame startGame = new StartGame();
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
                // TODO: check if enough players to start
                break;
            case IN_PROGRESS:
                roundTimer -= dt;
                if (roundTimer <= 0f) {
                    int aliveCount = 0;
                    int lastAliveId = -1;
                    for (PlayerState p : players.values()) {
                        if (p.alive) {
                            aliveCount++;
                            lastAliveId = p.id;
                        }
                    }
                    if (aliveCount == 1) {
                        endRound(lastAliveId);
                    } else {
                        endRound(-1); // no winner
                    }
                } else {
                    checkPlayersAlive();
                }
                break;
            case POST_ROUND:
                postRoundTimer -= dt;
                if (postRoundTimer <= 0f) {
                    startNewRound();
                }
                break;
            case MATCH_OVER:
                // TODO: handle match over state by waiting for restart or lobby return
                break;
        }
    }

    // update projectiles per tick
    private void updateProjectiles(float dt) {
        synchronized (projectiles) {
            Iterator<ProjectileState> it = projectiles.iterator();
            while (it.hasNext()) {
                ProjectileState proj = it.next();
                proj.x += proj.vx * dt;
                proj.y += proj.vy * dt;

                // TEMP: lifetime countdown
                proj.maxLifetime -= dt;
                if (proj.maxLifetime <= 0f) {
                    System.out.println("Projectile " + proj.id + " removed");
                    it.remove();
                }
            }
        }
    }

    private void updateSpells(float dt){
        for (BaseSpell[] inv : playerSpellInventories.values()) {
            for (BaseSpell spell : inv) {
                if (spell != null) {
                    spell.update(dt);
                }
            }
        }
    }

    private void giveSpellToPlayer(int playerId, BaseSpell newSpell, String spellType, float dropX, float dropY){
        BaseSpell[] inv = playerSpellInventories.get(playerId);
        if (inv == null ) return;

        // if empty slot, add spell there
        for(int i = 0; i < MAX_SPELL_SLOTS; i++){
            if (inv[i] == null) {
                inv[i] = newSpell;
                System.out.println("Gave spell to player " + playerId + " in slot " + i);
                return;
            }
        }

        // inventory full, replace currently selected slot and drop old spell
        PlayerState ps = players.get(playerId);
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

        // reset players
        for (PlayerState p : players.values()) {
            p.hp = MAX_HP;
            p.alive = true;
            p.blinkCooldown = 0f;
            p.roundsWon = winCount.getOrDefault(p.id, 0);
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
                postRoundTimer = 0f;
                System.out.println("player " + winnerID + " won the match");
            }
        } else {
            System.out.println("Round " + currentRound + " ended with no winner.");
        }
    }
}
