package io.github.wizard_arena;

import com.badlogic.gdx.InputProcessor;

// all this class's purpose is to implement the scroll wheel for spell swapping
public class InputCore implements InputProcessor {

    Player player;

    public InputCore (Player player){
        this.player = player;
    }

    @Override
    public boolean keyDown(int keycode) {
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        // positive for scrolling down
        if (amountY == 1){
            int slot = player.getSelectedSpellSlot();
            int newSlot = slot + 1;
            if (newSlot > 3){
                player.setSelectedSpellSlot(0);
            } else {
                player.setSelectedSpellSlot(newSlot);
            }
        } else {
            int slot = player.getSelectedSpellSlot();
            int newSlot = slot - 1;
            if (newSlot < 0){
                player.setSelectedSpellSlot(3);
            } else {
                player.setSelectedSpellSlot(newSlot);
            }
        }
        return true;
    }
}
