package io.github.wizard_arena;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class Player extends Sprite {

    //  Speed stuff

    float xVelocity;
    float yVelocity;
    float speed;

    //  Blink Stuff

    private float blinkTimer;
    private boolean blinked;

    //  general timer
    private float stateTime;

    //  online stuff


    public Player(wizardGame game, float speed) {
        super(game.assets.get("red_wizard.png", Texture.class));
        this.speed = speed;
        blinkTimer = 0f;
        stateTime = 0f;
    }

    public boolean movement(float delta) {

        int dX = 0;
        int dY = 0;

        float blinkDistance = 100f;
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
            blinkTimer = 2f;
        }
        return blinked;
    }

    public void update(boolean hudOpen) {

        float time = Gdx.graphics.getDeltaTime();

        boolean blink = movement(time);

        if(!blink) {
            setX(getX() + time * xVelocity);
            setY(getY() + time * yVelocity);
        }
    }
}
