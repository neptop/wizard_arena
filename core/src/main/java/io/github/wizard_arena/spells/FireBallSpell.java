package io.github.wizard_arena.spells;

import io.github.wizard_arena.net.Messages.*;
import java.util.List;

public class FireBallSpell extends BaseSpell {

    private static final float PROJECTILE_SPEED = 1000f;

    public FireBallSpell() {
        super(5.0f); // cd time
    }

    public float getDamage() {
        return 20f;
    }

    @Override
    public boolean orientOnCast() { return true; }

    @Override
    public String getTypeId() {
        return "fire";
    }

    @Override
    public void spawnProjectile(
        List<ProjectileState> projectiles,
        PlayerState caster,
        boolean [][] wallGrid,
        float aimX,
        float aimY,
        int projectileId) {
        spawnBasicProjectile(
            projectiles,
            caster,
            wallGrid,
            aimX,
            aimY,
            projectileId,
            PROJECTILE_SPEED);
    }
}
