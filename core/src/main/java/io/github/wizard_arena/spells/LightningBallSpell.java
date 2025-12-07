package io.github.wizard_arena.spells;

import io.github.wizard_arena.net.Messages.*;
import java.util.List;

public class LightningBallSpell extends BaseSpell {

    private static final float PROJECTILE_SPEED = 750f;
    private static final float MAX_LIFETIME = 8f; // TODO: TEMP REMOVE LATER

    public LightningBallSpell() {
        super(5.0f); // cd time
    }

    public float getDamage() {
        return 30f;
    }

    @Override
    public String getTypeId() {
        return "lightning";
    }

    @Override
    public void spawnProjectile(
        List<ProjectileState> projectiles,
        PlayerState caster,
        float aimX,
        float aimY,
        int projectileId) {

        float lenSq = aimX * aimX + aimY * aimY; // prevent division by zero
        if (lenSq < 0.0001f) { // should not happen, but just in case
            return; // do not spawn projectile
        }

        // normalize aim direction
        float invLen = (float) (1.0 / Math.sqrt(lenSq));
        float dirX = aimX * invLen;
        float dirY = aimY * invLen;
        float velX = dirX * PROJECTILE_SPEED;
        float velY = dirY * PROJECTILE_SPEED;

        // create projectile state at caster position
        // TODO: offset from caster position to avoid self-collision
        ProjectileState proj = new ProjectileState(
            projectileId,
            caster.x,
            caster.y,
            velX,
            velY
        );
        proj.ownerId = caster.id;
        proj.maxLifetime = MAX_LIFETIME;
        proj.damage = getDamage();
        proj.spellType = getTypeId();
        projectiles.add(proj);
    }
}
