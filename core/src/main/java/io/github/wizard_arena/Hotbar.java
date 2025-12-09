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

    private String [] spellTypes = new String[4];

    private Texture starterBallTex;
    private Texture poisonBallTex;
    private Texture fireBallTex;
    private Texture lightningBallTex;

    public Hotbar(float x, float y){
        hotbarTexture = new Texture(Gdx.files.internal("hotbar.png"));
        hotbarSelected = new Texture(Gdx.files.internal("hotbar_selected.png"));
        hotbarSelectedSprite = new Sprite(hotbarSelected);
        starterBallTex = new Texture(Gdx.files.internal("ball.png"));
        poisonBallTex = new Texture(Gdx.files.internal("poison_ball.png"));
        fireBallTex = new Texture(Gdx.files.internal("ball.png"));
        lightningBallTex = new Texture(Gdx.files.internal("lightning_ball.png"));

        setPosition(Gdx.graphics.getWidth() - 288, 0);
    }

    public void setSpellTypes(String[] types) {
        if (types == null) return;
        int n = Math.min(types.length, spellTypes.length);
        for (int i = 0; i < n; i++) {
            spellTypes[i] = types[i];
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha){
        batch.draw(hotbarTexture, getX(), getY());

        // Draw spell icons on top of hotbar
        float baseX = getX() + 30f;
        float baseY = getY() + 65f;
        float slotWidth = hotbarSelectedSprite.getWidth();

        for (int i = 0; i < spellTypes.length; i++) {
            Texture spellTex = null;
            if (spellTypes[i] == null) continue;
            switch (spellTypes[i]) {
                case "starter":
                    spellTex = starterBallTex;
                    break;
                case "poison":
                    spellTex = poisonBallTex;
                    break;
                case "fire":
                    spellTex = fireBallTex;
                    break;
                case "lightning":
                    spellTex = lightningBallTex;
                    break;
                default:
                    break;
            }
            float x = baseX + i * slotWidth;
            float y = baseY;
            batch.draw(spellTex, x, y);
        }
    }
}
