package io.github.wizard_arena.spells;

import io.github.wizard_arena.net.Messages.*;
import java.util.List;

public class PoisonBallSpell extends BaseSpell {

    private static final float PROJECTILE_SPEED = 600f;

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
