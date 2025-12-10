package io.github.wizard_arena.spells;

import io.github.wizard_arena.net.Messages.*;
import java.util.List;

public class LightningBallSpell extends BaseSpell {

    private static final float PROJECTILE_SPEED = 750f;

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
    public boolean isPiercing() {
        return true;
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
