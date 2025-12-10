package io.github.wizard_arena.spells;

import io.github.wizard_arena.net.Messages.*;
import java.util.List;

public class PoisonBallSpell extends BaseSpell {

    private static final float PROJECTILE_SPEED = 500f;

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
        spawnBasicProjectile(
            projectiles,
            caster,
            aimX,
            aimY,
            projectileId,
            PROJECTILE_SPEED);
    }
}
