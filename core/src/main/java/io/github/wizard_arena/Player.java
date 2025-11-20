package io.github.wizard_arena;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.wizard_arena.net.Client;
import io.github.wizard_arena.net.Messages;
import io.github.wizard_arena.net.NetConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Player extends Sprite {

    //  Speed stuff

    float xVelocity;
    float yVelocity;
    float speed;

    //  Blink Stuff

    private float blinkTimer;
    public boolean blinked;

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


    public Player(wizardGame game, float speed, Client client, int spawnX, int spawnY) {
        super(game.assets.get("red_wizard.png", Texture.class));
        this.speed = speed;
        blinkTimer = 0f;
        stateTime = 0f;
        this.client = client;

        setX(spawnX * 64);
        setY(spawnY * 64);
    }

    public boolean movement() {

        int dX = 0;
        int dY = 0;

        float blinkDistance = 200f;
        boolean blinked = blink();

        if(Gdx.input.isKeyPressed(Input.Keys.W)) {
            dY = 1;
        }
        if(Gdx.input.isKeyPressed(Input.Keys.S)) {
            dY = -1;
        }
        if(Gdx.input.isKeyPressed(Input.Keys.A)) {
            dX = -1;
        }
        if(Gdx.input.isKeyPressed(Input.Keys.D)) {
            dX = 1;
        }

        xVelocity = dX;
        yVelocity = dY;

        // debugging output
        /*if (dX != 0 || dY != 0) {
            System.out.println("local input: (" + dX + ", " + dY + ")");
        } */

        if(blinked) {
            setX(getX() + dX * blinkDistance );
            setY(getY() + dY * blinkDistance );
        }
        return blinked;
    }

    public boolean blink() {
        float delta = Gdx.graphics.getDeltaTime();
        if(blinkTimer > 0) {
            blinkTimer -= delta;
            if(blinkTimer <= 0 ) {
                blinkTimer = 0;
            }
            blinked = false;
        }
        if(blinkTimer <= 0 && !blinked && Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) {
            blinked = true;
            stateTime = 0f;
            blinkTimer = 2.4f;
        }
        return blinked;
    }

    public void update(boolean hudOpen) {

        float time = Gdx.graphics.getDeltaTime();

        boolean blink = movement();

        // local movement update
        setX(getX() + xVelocity * speed * time);
        setY(getY() + yVelocity * speed * time);

        // send input to server
        if (client != null) {
            Messages.InputUpdate iu = new Messages.InputUpdate();
            iu.dt = time;
            iu.moveX = xVelocity;
            iu.moveY = yVelocity;
            iu.clientId = (client != null ? client.clientId : -1);
            iu.seq = nextInputSeq++;
            iu.dt = time; // delta time since last input


            iu.aimX = 0f; // aiming direction
            iu.aimY = 0f;
            iu.castSpell = false; // spell casting input
            iu.dash = false;

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
                setX(getX() + input.moveX * speed * input.dt);
                setY(getY() + input.moveY * speed * input.dt);
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

}
