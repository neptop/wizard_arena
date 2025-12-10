package io.github.wizard_arena;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.scenes.scene2d.Actor;

public class Hotbar extends Actor {
    Texture hotbarTexture;
    Texture hotbarSelected;
    public Sprite hotbarSelectedSprite;

    private String [] spellTypes = new String[4];

    private final float[] cooldownRemaining = new float[4];
    private final float[] cooldownMax = new float[4];

    private BitmapFont font;
    private static Texture overlayTex;
    private static final float EPS = 0.0001f;

    private Texture starterBallTex;
    private Texture poisonBallTex;
    private Texture fireBallTex;
    private Texture lightningBallTex;

    public Hotbar(float x, float y){
        hotbarTexture = new Texture(Gdx.files.internal("hotbar.png"));
        hotbarSelected = new Texture(Gdx.files.internal("hotbar_selected.png"));
        hotbarSelectedSprite = new Sprite(hotbarSelected);
        starterBallTex = new Texture(Gdx.files.internal("star_spell_icon.png"));
        poisonBallTex = new Texture(Gdx.files.internal("poison_ball.png"));
        fireBallTex = new Texture(Gdx.files.internal("fireball_spell_icon.png"));
        lightningBallTex = new Texture(Gdx.files.internal("lightning_ball.png"));

        setPosition(Gdx.graphics.getWidth() - 288, 0);

        if (overlayTex == null) {
            Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pm.setColor(Color.WHITE);
            pm.fill();
            overlayTex = new Texture(pm);
            pm.dispose();
        }
        for (int i = 0; i < cooldownRemaining.length; i++) {
            cooldownRemaining[i] = 0;
            cooldownMax[i] = 0;
        }

        font = new BitmapFont();
        font.setColor(Color.WHITE);
    }

    public void setSpellTypes(String[] types) {
        if (types == null) return;
        int n = Math.min(types.length, spellTypes.length);
        for (int i = 0; i < n; i++) {
            spellTypes[i] = types[i];
        }
    }

    public void setCooldowns(float[] remaining, float[] max){
        if (remaining == null || max == null) return;
        int n = cooldownRemaining.length;
        for (int i = 0; i < n; i++) {
            cooldownRemaining[i] = (i < remaining.length) ? Math.max(0f, remaining[i]) : 0f;
            cooldownMax[i] = (i < max.length) ? Math.max(0f, max[i]) : 0f;
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

        // draw cooldown overlays
        float iconSize = poisonBallTex.getWidth();
        for (int i = 0; i < spellTypes.length; i++) {
            float rem = cooldownRemaining[i];
            float max = cooldownMax[i];
            if (max <= EPS || rem <= EPS) continue;

            float ratio = rem / max;
            if (ratio < 0f) ratio = 0f;
            if (ratio > 1f) ratio = 1f;

            float x = baseX + i * slotWidth;
            float y = baseY;
            float oHeight = iconSize * ratio;
            float oY = y + (iconSize - oHeight);

            Color prevColor = batch.getColor().cpy();

            // draw transparent overlay
            batch.setColor(0f, 0f, 0f, 0.6f); // transparent grey
            batch.draw(overlayTex, x, oY, iconSize, oHeight);
            batch.setColor(prevColor);

            // draw cooldown text
            int secs = (int)Math.ceil(rem);
            if (secs > 0) {
                String text = Integer.toString(secs);
                float textX = x + iconSize / 2f - 4f * text.length();
                float textY = y + iconSize / 2f + 4f;
                font.draw(batch, text, textX, textY);
            }
        }
    }
}
