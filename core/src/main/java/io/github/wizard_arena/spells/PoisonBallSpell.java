package io.github.wizard_arena.spells;

import io.github.wizard_arena.net.Messages.*;
import java.util.List;

public class PoisonBallSpell extends BaseSpell {

    private static final float PROJECTILE_SPEED = 500f;
    private static final float MAX_LIFETIME = 8f; // TODO: TEMP REMOVE LATER

    public PoisonBallSpell() {
        super(8.0f); // cd time
    }

    public float getDamage() {
        return 10f;
    }

    @Override
    public String getTypeId() {
        return "poison";
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

        System.out.println("[SERVER] Spawned projectile " + projectileId +
            " type=" + proj.spellType +
            " owner=" + caster.id +
            " pos=(" + proj.x + "," + proj.y + ")");
    }
}
