package io.github.wizard_arena;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import io.github.wizard_arena.net.Client;
import io.github.wizard_arena.net.Messages;
import io.github.wizard_arena.net.NetConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class Player extends Sprite {

    //  Speed stuff

    float xVelocity;
    float yVelocity;
    float speed;

    //  Blink Stuff
    private static final float BLINK_TILES = 17f;
    private static final float BLINK_DISTANCE = 4f;

    private float blinkTimer = 0f;
    public boolean blinked;
    private boolean canBlink; // input flag to send to server

    //  general timer
    private float stateTime;

    //  online stuff
    public Messages.InputUpdate inputUpdate;
    public Client client;

    // client-side prediction
    private int nextInputSeq = 1;
    private final List<Messages.InputUpdate> pendingInputs = new ArrayList<>();

    // remote player interpolation
    private float interpStartX;
    private float interpStartY;
    private float interpTargetX;
    private float interpTargetY;
    private float interpT = 1f; // interpolation factor, 1 = at target

    // health
    public float health = 100f;
    public float maxHealth = 100f;

    // status
    private boolean alive = true;
    private int roundsWon = 0;

    // spell slots
    private int selectedSpellSlot = 0;

    // cheats
    public boolean godMode = false;

    //  collision detection
    public Tile[][] map;
    public int tileSize = 64;
    public Rectangle bounds;
    public int playerSize = 48;

    public Player(wizardGame game, float speed, Client client, int spawnX, int spawnY, Tile[][] map) {
        super(game.assets.get("red_wizard.png", Texture.class));
        this.speed = speed;
        blinkTimer = 0f;
        stateTime = 0f;
        this.client = client;

        setX(spawnX * 64);
        setY(spawnY * 64);

        this.maxHealth = 100f;
        this.health = maxHealth;
        this.map = map;
        this.bounds = new Rectangle(spawnX, spawnY, playerSize, playerSize);
    }

    public void checkCollision(float dx, float dy, int tileSize) {
        float centerX = getX() + tileSize / 2f;
        float centerY = getY() + tileSize / 2f;

        // Horizontal movement
        if (dx != 0) {
            float moveDistance;
            if(blinked) {
                // Check each tile in the blink path
                moveDistance = findMaxValidDistance(centerX, centerY, dx, 0,
                    (dx * BLINK_DISTANCE) * tileSize, tileSize);
            } else {
                moveDistance = dx * (playerSize / 4f);
            }

            float newCenterX = centerX + moveDistance;
            float newX = newCenterX - playerSize / 2f;
            float newY = centerY - playerSize / 2f;
            Rectangle testBounds = new Rectangle(newX, newY, playerSize, playerSize);

            int nextTileX = (int)((testBounds.x + tileSize/2f) / tileSize);
            int tileY = (int)((testBounds.y + tileSize/2f) / tileSize);

            if (isColliding(testBounds, nextTileX, tileY)) {
                xVelocity = 0;
            } else {
                if(blinked)
                    xVelocity = moveDistance / tileSize; // this will be the number of tiles moved
                else
                    xVelocity = dx;
            }
        } else {
            xVelocity = 0;
        }

        // Vertical movement
        if (dy != 0) {
            float moveDistance;
            if(blinked) {
                moveDistance = findMaxValidDistance(centerX, centerY, 0, dy,
                    (dy * BLINK_DISTANCE) * tileSize, tileSize);
            } else {
                moveDistance = dy * (playerSize / 4f);
            }


            float newCenterY = centerY + moveDistance;
            float newY = newCenterY - playerSize / 2f;
            float newX = centerX - playerSize / 2f;
            Rectangle testBounds = new Rectangle(newX, newY, playerSize, playerSize);

            int nextTileY = (int)((testBounds.y + tileSize/2f) / tileSize);
            int tileX = (int)((testBounds.x + tileSize/2f) / tileSize);

            if (isColliding(testBounds, tileX, nextTileY)) {
                yVelocity = 0;
            } else {
                if(blinked)
                    yVelocity = moveDistance / tileSize;
                else
                    yVelocity = dy;
            }
        } else {
            yVelocity = 0;
        }
    }

    // finds the max travel distance before hitting a wall
    private float findMaxValidDistance(float startX, float startY, float dirX, float dirY,
                                       float targetDistance, int tileSize) {

        float currentDistance = 0;
        float lastValidDistance = 0;

        int steps = (int)Math.ceil(Math.abs(targetDistance) / tileSize);

        float sign = targetDistance >= 0 ? 1 : -1;

        for (int i = 1; i <= steps; i++) {
            currentDistance = Math.min(i * tileSize, Math.abs(targetDistance)) * sign;

            float testCenterX = startX + (dirX != 0 ? currentDistance : 0);
            float testCenterY = startY + (dirY != 0 ? currentDistance : 0);

            float testX = testCenterX - playerSize / 2f;
            float testY = testCenterY - playerSize / 2f;
            Rectangle testBounds = new Rectangle(testX, testY, playerSize, playerSize);

            int tileX = (int)((testBounds.x + tileSize/2f) / tileSize);
            int tileY = (int)((testBounds.y + tileSize/2f) / tileSize);

            if (isColliding(testBounds, tileX, tileY)) {
                // Hit a wall, return the last valid distance
                return lastValidDistance;
            }
            lastValidDistance = currentDistance;
        }

        // No collision found, return the full target distance
        return targetDistance;
    }

    private boolean isColliding(Rectangle testBounds, int tileX, int tileY) {
        // Check current and adjacent tiles
        for (int y = tileY - 1; y <= tileY + 1; y++) {
            for (int x = tileX - 1; x <= tileX + 1; x++) {
                if (x >= 0 && x < map[0].length && y >= 0 && y < map.length) {
                    if (!map[y][x].navigable && testBounds.overlaps(map[y][x].bounds)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    public void movement(float time) {

        int dX = 0;
        int dY = 0;

        if(Gdx.input.isKeyPressed(Input.Keys.W))
            dY = 1;
        if(Gdx.input.isKeyPressed(Input.Keys.S))
            dY = -1;
        if(Gdx.input.isKeyPressed(Input.Keys.A))
            dX = -1;
        if(Gdx.input.isKeyPressed(Input.Keys.D))
            dX = 1;


        blink(time);

        checkCollision( dX, dY, tileSize);
        // debugging output
        /*if (dX != 0 || dY != 0) {
            System.out.println("local input: (" + dX + ", " + dY + ")");
        } */
    }

    public void blink(float delta) {
        if(blinkTimer > 0) {
            blinkTimer -= delta;
            if(blinkTimer <= 0 ) {
                blinkTimer = 0;
            }
        }

        blinked = false;
        canBlink = false;

        if(blinkTimer <= 0f && Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
            blinked = true;
            stateTime = 0f;
            blinkTimer = 2.0f;
            canBlink = true;
        }
    }

    public void update(boolean hudOpen, OrthographicCamera camera) {

        float time = Gdx.graphics.getDeltaTime();
        stateTime += time;

        // dead so skip input processing
        if(!alive){
            return;
        }

        if (hudOpen) {
            blink(time); // still update blink cooldown while hud is open
            return; // skip movement and input sending when hud is open
        }

        // spell slot hotkeys
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) selectedSpellSlot = 0;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) selectedSpellSlot = 1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) selectedSpellSlot = 2;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4)) selectedSpellSlot = 3;

        // read inputs
        movement(time);

        // local movement update for client-side prediction
        if(blinked){
            setX(getX() + xVelocity * tileSize);
            setY(getY() + yVelocity * tileSize);

        }
        else {
            setX(getX() + xVelocity * speed * time);
            setY(getY() + yVelocity * speed * time);
        }





        float aimX = 0f;
        float aimY = 0f;

        if (camera != null) {
            Vector3 mouse = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0f); // mouse position in screen coords
            camera.unproject(mouse); // convert to world coords
            float dx = mouse.x - getX();
            float dy = mouse.y - getY();
            float lenSq = dx * dx + dy * dy;
            if (lenSq > 0.0001f) { // prevent division by zero
                float invLen = (float) (1.0 / Math.sqrt(lenSq));
                aimX = dx * invLen;
                aimY = dy * invLen;
            }
        }

        boolean cast = Gdx.input.isButtonJustPressed(Buttons.LEFT);
        boolean pickUpSpell = Gdx.input.isKeyJustPressed(Input.Keys.E);

        // send input to server
        if (client != null) {
            Messages.InputUpdate iu = new Messages.InputUpdate();
            iu.dt = time;
            iu.moveX = xVelocity;
            iu.moveY = yVelocity;
            iu.clientId = (client != null ? client.clientId : -1);
            iu.seq = nextInputSeq++;
            iu.dt = time; // delta time since last input

            iu.aimX = aimX;
            iu.aimY = aimY;
            iu.castSpell = cast; // spell casting input

            iu.blink = blinked; // blink ability input
            iu.selectedSpellSlot = selectedSpellSlot;
            iu.pickupSpell = pickUpSpell;

            pendingInputs.add(iu); // store input for client-side prediction

            try {
                  client.send(iu);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // this is called when a new server snapshot is received
    public void applyServerState(float serverX, float serverY, int lastProcessedInput) {
        // correct position based on server state
        setX(serverX);
        setY(serverY);

        // reapply pending inputs that the server hasn't processed yet
        int i = 0;
        while (i < pendingInputs.size()) {
            Messages.InputUpdate input = pendingInputs.get(i);

            if (input.seq <= lastProcessedInput) {
                // this input has already been processed by the server
                pendingInputs.remove(i);
            } else {
                // reapply this input

                if(input.blink) {
                    setX(getX() + input.moveX * tileSize);
                    setY(getY() + input.moveY * tileSize);
                }
                else {
                    setX(getX() + input.moveX * speed * input.dt);
                    setY(getY() + input.moveY * speed * input.dt);
                }
                // reapply blink if it was used





                i++;
            }
        }
    }

    // check if this player is local (controlled by this client)
    public boolean isLocal() {
        return client != null;
    }

    // called when receiving a snapshot for remote players
    public void startInter(float serverX, float serverY) {
        interpStartX = getX();
        interpStartY = getY();
        interpTargetX = serverX;
        interpTargetY = serverY;
        interpT = 0f;
    }

    // called each frame to update interpolation for remote players
    public void updateRemote(float dt) {
        if (interpT >= 1f) {
            return; // already at target
        }

        float interpSpeed = NetConfig.SNAPSHOT_RATE;
        interpT += dt * interpSpeed;
        if (interpT > 1f) {
            interpT = 1f;
        }

        float newX = interpStartX + (interpTargetX - interpStartX) * interpT;
        float newY = interpStartY + (interpTargetY - interpStartY) * interpT;
        setX(newX);
        setY(newY);
    }

    public void setGodMode(boolean godMode) {
        this.godMode = godMode;
    }

    public boolean isGodMode() {
        return godMode;
    }

    public void setHealth(float health) {
        this.health = health;
    }

    public float getHealth() {
        return health;
    }

    public float getMaxHealth() {
        return maxHealth;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public int getRoundsWon() {
        return roundsWon;
    }

    public void setRoundsWon(int roundsWon) {
        this.roundsWon = roundsWon;
    }

    public int getSelectedSpellSlot() {
        return selectedSpellSlot;
    }
}
