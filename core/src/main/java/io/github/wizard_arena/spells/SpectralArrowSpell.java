package io.github.wizard_arena.spells;

import io.github.wizard_arena.net.Messages.*;
import java.util.List;

public class SpectralArrowSpell extends BaseSpell {

    private static final float PROJECTILE_SPEED = 600f;

    public SpectralArrowSpell() {
        super(1.0f); // cd time
    }

    public float getDamage() {
        return 10f;
    }

    @Override
    public String getTypeId() {
        return "arrow";
    }

    @Override
    public boolean isPiercing() {
        return true;
    }

    @Override
    public boolean orientOnCast() { return true; }

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
