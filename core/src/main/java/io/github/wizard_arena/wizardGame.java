package io.github.wizard_arena;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.wizard_arena.screens.MainMenuScreen;


public class wizardGame extends Game {

    AssetManager assets;
    SpriteBatch batch;

    @Override
    public void create() {
        assets = new AssetManager();
        batch = new SpriteBatch();
        setScreen(new MainMenuScreen(this));
    }

    public void dispose() {
        super.dispose();
        if (batch != null) batch.dispose();
        if (assets != null) assets.dispose();
    }
}
