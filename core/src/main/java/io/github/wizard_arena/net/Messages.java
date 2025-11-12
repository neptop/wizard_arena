package io.github.wizard_arena.net;

import java.io.Serializable;
import java.util.List;

public class Messages {
    public interface Msg extends Serializable {}

    // client to server
    public static class JoinRequest implements Msg {
        public String playerName;
        public JoinRequest() {}
        public JoinRequest(String playerName) {
            this.playerName = playerName;
        }
    }

    // server to client
    public static class JoinResponse implements Msg {
        public boolean accepted;
        public String reason; // if not accepted
        public int clientId;
        public long serverTime; // server timestamp
        public JoinResponse() {}
        public JoinResponse(boolean accepted, String reason, int clientId, long serverTime) {
            this.accepted = accepted;
            this.reason = reason;
            this.clientId = clientId;
            this.serverTime = serverTime;
        }
    }

    // client to server on each frame
    public static class InputUpdate implements Msg {
        public int clientId;
        public int seq; // input sequence number
        public float dt; // delta time since last input
        public float moveX; // movement input
        public float moveY;
        public float aimX; // aiming direction
        public float aimY;
        public boolean castSpell; // spell casting input
        public boolean dash;
    }

    // server to client on each snapshot
    public static class Snapshot implements Msg {
        public int tick; // server tick number
        public long serverTime; // server timestamp
        public List<PlayerState> players;
        public List<ProjectileState> projectiles;
        public Snapshot() {}
    }

    // player game state
    public static class PlayerState implements Serializable {
        public int id;
        public float x;
        public float y;
        public float hp;
        public PlayerState() {}
        public PlayerState(int id, float x, float y, float hp) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.hp = hp;
        }
    }

    // projectile game state
    public static class ProjectileState implements Msg {
        public int id;
        public float x;
        public float y;
        public float vx;
        public float vy;
        public ProjectileState() {}
        public ProjectileState(int id, float x, float y, float vx, float vy) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
        }
    }

    // client to server ping message
    public static class Ping implements Msg {
        public long clientTime;
        public Ping() {}
        public Ping(long clientTime) {
            this.clientTime = clientTime;
        }
    }

    // server to client pong message
    public static class Pong implements Msg {
        public long clientTime;
        public long serverTime;
        public Pong() {}
        public Pong(long clientTime, long serverTime) {
            this.clientTime = clientTime;
            this.serverTime = serverTime;
        }
    }
}
