package io.github.wizard_arena;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class Player extends Sprite {

    float xVelocity;
    float yVelocity;
    float speed;


    public Player(wizardGame game, float speed) {
        super(game.assets.get("red_wizard.png", Texture.class));
        this.speed = speed;
    }

    public void movement() {

        int dX = 0;
        int dY = 0;

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
    }

    public void update(boolean hudOpen) {

        float time = Gdx.graphics.getDeltaTime();

        movement();

        setX(getX() + time * xVelocity);
        setY(getY() + time * yVelocity);
    }

    

}
