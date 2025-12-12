package io.github.wizard_arena.net;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.io.Serializable;
import java.util.Map;
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
        public float lookAngle;
        public boolean castSpell; // spell casting input
        public boolean blink; // blink ability input
        public int selectedSpellSlot; // selected spell slot (0-3)
        public boolean pickupSpell; // pickup spell input
        public int currentAnimationID;
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
        public boolean lavaActive; // is lava hazard active
        public boolean lavaJustStarted;
        public float lavaCenterX;
        public float lavaCenterY;
        public float lavaRadius;

        public Map<Integer, String> playerNames; // mapping of player IDs to names
        public String roundWinnerName;
        public String matchWinnerName;

        public Snapshot() {}
    }

    // player game state
    public static class PlayerState implements Serializable {
        public int id;
        public float x;
        public float y;
        public float lookAngle;
        public float hp;
        public int lastProcessedInput; // last input seq processed by server
        public float blinkCooldown; // time remaining for blink ability cooldown
        public boolean alive;
        public int roundsWon;
        public int selectedSpellSlot;
        public float speedMultiplier;
        public float speedBoostDuration;
        public float overdriveDuration;
        public boolean inLava;
        public float lavaTickTimer;

        // knockback tracking
        public float knockbackX;
        public float knockbackY;
        public boolean isKnockback;

        // debuff/ DOT tracking
        public float poisonDuration;
        public float burnDuration;
        public float combinedDotDuration;
        public float dotTickTimer;
        public float frostDuration;

        public float[] spellCooldownRemaining;
        public float[] spellCooldownMax;

        public String[] spellSlots;

        public int currentAnimationID;
        public PlayerState(int id, float x, float y, float hp) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.hp = hp;
            this.lookAngle = 0f;
            this.lastProcessedInput = 0;
            this.blinkCooldown = 0f;
            this.alive = true;
            this.roundsWon = 0;
            this.selectedSpellSlot = 0;
            this.speedMultiplier = 1.0f;
            this.overdriveDuration = 0f;

            // knockback
            this.knockbackX = 0f;
            this.knockbackY = 0f;
            this.isKnockback = false;

            this.currentAnimationID = 0;
            // debuffs
            this.poisonDuration = 0f;
            this.burnDuration = 0f;
            this.combinedDotDuration = 0f;
            this.dotTickTimer = 0f;
            this.frostDuration = 0f;

            // lava
            this.inLava = false;
            this.lavaTickTimer = 0f;

            // 4 spell slots
            this.spellSlots = new String[4];

            // spell cooldowns
            this.spellCooldownRemaining = new float[4];
            this.spellCooldownMax = new float[4];
        }
    }

    // projectile game state
    public static class ProjectileState implements Msg {
        public int id;
        public float x;
        public float y;
        public float vx;
        public float vy;
        public float castAngle;
        public int ownerId; // id of the player who cast the projectile
        public float damage;
        public boolean piercing;
        public String spellType;

        public ProjectileState() {}
        public ProjectileState(int id, float x, float y, float vx, float vy, float castAngle) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.castAngle = castAngle;
            this.ownerId = -1;
            this.damage = 0f;
            this.piercing = false;
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
        public int hostPort;

        public LobbyUpdate() {}
        public LobbyUpdate(List<LobbyPlayerInfo> players, String hostIP, int hostPort) {
            this.players = players;
            this.hostIP = hostIP;
            this.hostPort = hostPort;
        }
    }

    public static class StartGame implements Msg {
        public String mapFile;
        public StartGame() {}

        public StartGame(String mapFile) {
            this.mapFile = mapFile;
        }
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

    public static class LavaStartRequest implements Msg {
        public int playerId; // for logging purposes
        public LavaStartRequest() {}
        public LavaStartRequest(int playerId) {
            this.playerId = playerId;
        }
    }
}
