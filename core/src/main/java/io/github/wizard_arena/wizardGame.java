package io.github.wizard_arena;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.wizard_arena.screens.MainMenuScreen;


public class wizardGame extends Game {

    public AssetManager assets;
    public SpriteBatch batch;

    @Override
    public void create() {
        assets = new AssetManager();
        batch = new SpriteBatch();

        String RSC_RED_WIZARD_IMG = "red_wizard.png";

        assets.load(RSC_RED_WIZARD_IMG, Texture.class);
        assets.finishLoading();

        setScreen(new MainMenuScreen(this));


    }

    public void dispose() {
        super.dispose();
        if (batch != null) batch.dispose();
        if (assets != null) assets.dispose();
    }
}
