package io.github.wizard_arena;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;

public class Tile {

    public int x;
    public int y;
    private Texture tile_type;
    public boolean navigable;
    public Rectangle bounds;
    private int tileSize = 64;

    public Tile(wizardGame game, int x, int y, Texture tile_type, boolean navigable) {

        this.tile_type = tile_type;
        this.x = x;
        this.y = y;
        this.navigable = navigable;
        this.bounds = new Rectangle(y * tileSize, x * tileSize, tileSize, tileSize);
    }


    public void render(SpriteBatch batch) {
        batch.draw(tile_type, y * 64, x * 64);

    }
}
