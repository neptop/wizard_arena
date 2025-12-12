package io.github.wizard_arena.spells;

import io.github.wizard_arena.net.Messages.*;
import java.util.List;

public class MagicMissileSpell extends BaseSpell {

    private static final float PROJECTILE_SPEED = 700f;

    public MagicMissileSpell() {
        super(0.5f); // cd time
    }

    public float getDamage() {
        return 5f;
    }

    @Override
    public boolean orientOnCast() { return true; }

    @Override
    public String getTypeId() {
        return "magic";
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
