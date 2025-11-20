package io.github.wizard_arena;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class Tile {

    private int x;
    private int y;
    private Texture tile_type;
    private boolean navigable;

    public Tile(wizardGame game, int x, int y, Texture tile_type, boolean navigable) {

        this.tile_type = tile_type;
        this.x = x;
        this.y = y;
        this.navigable = navigable;
    }


    public void render(SpriteBatch batch) {
        batch.draw(tile_type, y * 64, x * 64);
    }
}
