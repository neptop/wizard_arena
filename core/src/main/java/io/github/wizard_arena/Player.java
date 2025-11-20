package io.github.wizard_arena;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.wizard_arena.net.Client;
import io.github.wizard_arena.net.Messages;

import java.io.IOException;

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

    public Player(wizardGame game, float speed, Client client, int spawnX, int spawnY) {
        super(game.assets.get("red_wizard.png", Texture.class));
        this.speed = speed;
        blinkTimer = 0f;
        stateTime = 0f;
        this.client = client;

        setX(spawnX * 64);
        setY(spawnY * 64);
        this.inputUpdate = new Messages.InputUpdate();

        inputUpdate.moveX = getX();
        inputUpdate.moveY = getY();
        inputUpdate.clientId = client.clientId;
        inputUpdate.seq = 0; // input sequence number
        inputUpdate.dt = 0; // delta time since last input


        inputUpdate.aimX = 0f; // aiming direction
        inputUpdate.aimY = 0f;
        inputUpdate.castSpell = false; // spell casting input
        inputUpdate.dash = false;
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


        xVelocity = dX * speed;
        yVelocity = dY * speed;

        inputUpdate.moveX = xVelocity;
        inputUpdate.moveY = yVelocity;
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
        try {
            client.send(inputUpdate);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if(!blink) {
            setX(getX() + time * xVelocity);
            setY(getY() + time * yVelocity);
        }
    }
}
