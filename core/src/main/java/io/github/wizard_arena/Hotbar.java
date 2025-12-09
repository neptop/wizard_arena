package io.github.wizard_arena;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.scenes.scene2d.Actor;

public class Hotbar extends Actor {
    Texture hotbarTexture;
    Texture hotbarSelected;
    public Sprite hotbarSelectedSprite;
    Texture spell1Texture;

    public Hotbar(float x, float y){
        hotbarTexture = new Texture(Gdx.files.internal("hotbar.png"));
        hotbarSelected = new Texture(Gdx.files.internal("hotbar_selected.png"));
        hotbarSelectedSprite = new Sprite(hotbarSelected);
        spell1Texture = new Texture(Gdx.files.internal("ball.png"));

        setPosition(Gdx.graphics.getWidth() - 288, 0);
    }

    @Override
    public void draw(Batch batch, float parentAlpha){
        batch.draw(hotbarTexture, getX(), getY());
    }
}
