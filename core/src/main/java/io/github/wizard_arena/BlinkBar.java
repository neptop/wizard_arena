package io.github.wizard_arena;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;

public class BlinkBar extends Actor {
    private NinePatchDrawable background;
    private NinePatchDrawable fill;
    private float blinkRatio;

    public BlinkBar(float x, float y, float width, float height) {
        // create a basic 1x1 Pixmap object that stretches to fill the entire healthbar
        Pixmap fillColor = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        fillColor.setColor(Color.PURPLE); // Set your desired color
        fillColor.fill();
        Texture fillTexture = new Texture(fillColor);
        NinePatch fillPatch = new NinePatch(fillTexture, 0, 0, 0, 0);
        this.fill = new NinePatchDrawable(fillPatch);

        Pixmap bgColor = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bgColor.setColor(Color.DARK_GRAY); // Set your desired color
        bgColor.fill();
        Texture bgTexture = new Texture(bgColor);
        NinePatch bgPatch = new NinePatch(bgTexture, 0, 0, 0, 0);
        this.background = new NinePatchDrawable(bgPatch);
        setBounds(x, y, width, height);
    }

    // updates the ratio of health from 0 - 1 for the bar to display
    public void setBlinkRatio(float current, float max) {
        if (current <= 0) {
            this.blinkRatio = 1.0f;
        } else {
            // have to invert ratio since timer goes down to 0
            this.blinkRatio = (max - current) / max;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha){
        background.draw(batch, getX(), getY(), getWidth(), getHeight());
        fill.draw(batch, getX(), getY(), getWidth() * blinkRatio, getHeight());
    }
}
