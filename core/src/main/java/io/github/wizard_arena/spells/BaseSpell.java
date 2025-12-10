package io.github.wizard_arena.spells;

import io.github.wizard_arena.net.Messages.*;
import java.util.List;

public abstract class BaseSpell {
    private final float cooldownTime;
    private float cooldownTimer = 0f;

    private static final float SPAWN_OFFSET = 80f; // offset to spawn projectile in front of caster

    public BaseSpell(float cooldownTime) {
        this.cooldownTime = cooldownTime;
    }

    public abstract String getTypeId();

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

    public float getDamage() {
        return 0f; // overridden in each spell
    }

    public boolean isPiercing() {
        return false;
    }

    protected void spawnBasicProjectile(
        List<ProjectileState> projectiles,
        PlayerState caster,
        float aimX,
        float aimY,
        int projectileId,
        float projectileSpeed) {

        float lenSq = aimX * aimX + aimY * aimY; // prevent division by zero
        if (lenSq < 0.0001f) { // should not happen,
            return; // do not spawn projectile
        }

        // normalize aim direction
        float invLen = (float) (1.0 / Math.sqrt(lenSq));
        float dirX = aimX * invLen;
        float dirY = aimY * invLen;
        float velX = dirX * projectileSpeed;
        float velY = dirY * projectileSpeed;
        float spawnX = caster.x + dirX * SPAWN_OFFSET;
        float spawnY = caster.y + dirY * SPAWN_OFFSET;

        ProjectileState proj = new ProjectileState(
            projectileId,
            spawnX,
            spawnY,
            velX,
            velY
        );
        proj.ownerId = caster.id;
        proj.damage = getDamage();
        proj.spellType = getTypeId();
        proj.piercing = isPiercing();
        projectiles.add(proj);
    }

    public abstract void spawnProjectile(
        List<ProjectileState> projectiles,
        PlayerState caster,
        float aimX,
        float aimY,
        int projectileId);
}
