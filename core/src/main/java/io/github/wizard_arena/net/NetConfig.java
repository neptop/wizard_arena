package io.github.wizard_arena.net;

public class NetConfig {
    public static final int TCP_PORT = 25565; // Default TCP port for the game server
    public static final int MIN_TCP_PORT = 20000; // Minimum TCP port
    public static final int MAX_TCP_PORT = 30000; // Maximum TCP port
    public static final int SNAPSHOT_RATE = 32; // snapshots per second
    public static final int TICK_RATE = 64; // ticks per second
    public static final int TIMEOUT_MS = 5000; // client connection timeout in milliseconds
    private NetConfig() {}
}
