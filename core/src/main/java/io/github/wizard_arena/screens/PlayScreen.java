package io.github.wizard_arena.screens;

import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import io.github.wizard_arena.wizardGame;

public class PlayScreen extends ScreenAdapter {
    private final wizardGame game;

    public PlayScreen(wizardGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        // do later
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    @Override
    public void resize(int width, int height) {
        // do later
    }

    @Override
    public void dispose() {
        // do later
    }
}
