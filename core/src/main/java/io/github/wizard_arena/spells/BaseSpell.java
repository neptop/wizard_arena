package io.github.wizard_arena.spells;

import io.github.wizard_arena.net.Messages.*;
import java.util.List;

public abstract class BaseSpell {
    private final float cooldownTime;
    private float cooldownTimer = 0f;

    public BaseSpell(float cooldownTime) {
        this.cooldownTime = cooldownTime;
    }

    public void update(float dt){
        if (cooldownTimer > 0f) {
            cooldownTimer -= dt;
            if (cooldownTimer < 0f) cooldownTimer = 0f;
        }
    }

    public boolean canCast() {
        return cooldownTimer <= 0f;
    }

    public void onCast() {
        cooldownTimer = cooldownTime;
    }

    public float getCooldownTimer() {
        return cooldownTimer;
    }

    public float getCooldownTime() {
        return cooldownTime;
    }

    public abstract void spawnProjectile(
        List<ProjectileState> projectiles,
        PlayerState caster,
        float aimX,
        float aimY,
        int projectileId);
}
