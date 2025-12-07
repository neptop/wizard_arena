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
        public boolean blink; // blink ability input
        public int selectedSpellSlot; // selected spell slot (0-3)
        public boolean pickupSpell; // pickup spell input
    }

    // server to client on each snapshot
    public static class Snapshot implements Msg {
        public int tick; // server tick number
        public long serverTime; // server timestamp
        public List<PlayerState> players;
        public List<ProjectileState> projectiles;
        public List<SpellPickupState> spellPickups;
        public List<PowerupState> powerups;

        public int roundNumber; // current round number
        public float roundTimeRemaining; // time remaining in the current round before sudden death
        public int roundState; // 0 = waiting, 1 = in progress, 2 = post round, 3 = match over
        public int roundWinnerId; // id of the player who won the round, -1 if no winner yet
        public int matchWinnerId; // id of the player who won the match, -1 if no winner yet
        public float postRoundTimeRemaining; // time remaining in post round state

        public Snapshot() {}
    }

    // player game state
    public static class PlayerState implements Serializable {
        public int id;
        public float x;
        public float y;
        public float hp;
        public int lastProcessedInput; // last input seq processed by server
        public float blinkCooldown; // time remaining for blink ability cooldown
        public boolean alive;
        public int roundsWon;
        public int selectedSpellSlot;
        public float speedMultiplier;
        public float speedBoostDuration;
        public float overdriveDuration;

        // debuff/ DOT tracking
        public float poisonDuration;
        public float burnDuration;
        public float combinedDotDuration;
        public float dotTickTimer;

        public PlayerState(int id, float x, float y, float hp) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.hp = hp;
            this.lastProcessedInput = 0;
            this.blinkCooldown = 0f;
            this.alive = true;
            this.roundsWon = 0;
            this.selectedSpellSlot = 0;
            this.speedMultiplier = 1.0f;
            this.overdriveDuration = 0f;

            // debuffs
            this.poisonDuration = 0f;
            this.burnDuration = 0f;
            this.combinedDotDuration = 0f;
            this.dotTickTimer = 0f;
        }
    }

    // projectile game state
    public static class ProjectileState implements Msg {
        public int id;
        public float x;
        public float y;
        public float vx;
        public float vy;
        public int ownerId; // id of the player who cast the projectile
        public float maxLifetime; // maximum lifetime of the projectile in seconds // TEMP
        public float damage;
        public String spellType;

        public ProjectileState() {}
        public ProjectileState(int id, float x, float y, float vx, float vy) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.ownerId = -1;
            this.maxLifetime = 0f; // TEMP
            this.damage = 0f;
            this.spellType = "";
        }
    }

    public static class SpellPickupState implements Msg {
        public int id;
        public float x;
        public float y;
        public String spellType;
        public SpellPickupState() {}
        public SpellPickupState(int id, float x, float y, String spellType) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.spellType = spellType;
        }
    }

    public static class PowerupState implements Msg {
        public int id;
        public float x;
        public float y;
        public String powerupType;
        public PowerupState() {}
        public PowerupState(int id, float x, float y, String powerupType) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.powerupType = powerupType;
        }
    }

    public static class SpawnSpellPickupRequest implements Msg {
        public int playerId;
        public String spellType;
        public SpawnSpellPickupRequest() {}
        public SpawnSpellPickupRequest(int playerId, String spellType) {
            this.playerId = playerId;
            this.spellType = spellType;
        }
    }

    public static class SpawnPowerupRequest implements Msg {
        public int playerId;
        public String powerupType;
        public SpawnPowerupRequest() {}
        public SpawnPowerupRequest(int playerId, String powerupType) {
            this.playerId = playerId;
            this.powerupType = powerupType;
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

    // player lobby info
    public static class LobbyPlayerInfo implements Serializable {
        public int id;
        public String name;
        public boolean isHost;
        public LobbyPlayerInfo() {}
        public LobbyPlayerInfo(int id, String name, boolean isHost) {
            this.id = id;
            this.name = name;
            this.isHost = isHost;
        }
    }

    public static class LobbyUpdate implements Msg {
        public List<LobbyPlayerInfo> players;
        public String hostIP;

        public LobbyUpdate() {}
        public LobbyUpdate(List<LobbyPlayerInfo> players, String hostIP) {
            this.players = players;
            this.hostIP = hostIP;
        }
    }

    public static class StartGame implements Msg {
        public StartGame() {}
    }

    public static class DamageRequest implements Msg {
        public int playerId;
        public float amount;
        public DamageRequest() {}
        public DamageRequest(int playerId, float amount) {
            this.playerId = playerId;
            this.amount = amount;
        }
    }

    public static class HealRequest implements Msg {
        public int playerId;
        public float amount;
        public HealRequest() {}
        public HealRequest(int playerId, float amount) {
            this.playerId = playerId;
            this.amount = amount;
        }
    }

    public static class GodmodeRequest implements Msg {
        public int playerId;
        public boolean enabled;
        public GodmodeRequest() {}
        public GodmodeRequest(int playerId, boolean enabled) {
            this.playerId = playerId;
            this.enabled = enabled;
        }
    }

}
