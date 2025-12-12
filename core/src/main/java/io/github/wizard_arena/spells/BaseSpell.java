package io.github.wizard_arena.spells;

import com.badlogic.gdx.math.Rectangle;
import io.github.wizard_arena.net.Messages.*;
import java.util.List;




public abstract class BaseSpell {
    private final float cooldownTime;
    private float cooldownTimer = 0f;

    private static final float SPAWN_OFFSET = 64f; // offset to spawn projectile in front of caster

    public BaseSpell(float cooldownTime) {
        this.cooldownTime = cooldownTime;
    }

    public abstract String getTypeId();

    public void update(float dt){
        if (cooldownTimer > 0f) {
            cooldownTimer -= dt;

        }
        if (cooldownTimer < 0f) cooldownTimer = 0f;
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

    // should the projectile be rotated based on player look direction?
    public boolean orientOnCast() { return false; }

    public void putOnCooldown() {
        this.cooldownTimer = this.cooldownTime;
    }

    protected void spawnBasicProjectile(
        List<ProjectileState> projectiles,
        PlayerState caster,
        boolean [][] wallGrid,
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

        //Calculate rotation of projectile
        float castAngle = 0f;
        if (orientOnCast()){
            double angleRad = Math.atan2(dirY, dirX);
            double angleDeg = Math.toDegrees(angleRad);
            castAngle = (float)angleDeg;
        }
        float velX = dirX * projectileSpeed;
        float velY = dirY * projectileSpeed;
        float spawnX = caster.x + dirX * SPAWN_OFFSET;
        float spawnY = caster.y + dirY * SPAWN_OFFSET;
        float spellSize = SPAWN_OFFSET / 2f;


        float centerX = spawnX + spellSize / 2f;
        float centerY = spawnY + spellSize / 2f;
        Rectangle ballRect = new Rectangle(spawnX,spawnY, spellSize, spellSize);
        int TILE_SIZE = 64;
        int tileX = (int)(centerX / TILE_SIZE);
        int tileY = (int)(centerY / TILE_SIZE);

        boolean spawnProjectile = true;
        for (int ty = tileY - 1; ty <= tileY + 1; ty++) {
            for (int tx = tileX - 1; tx <= tileX + 1; tx++) {
                Rectangle adjTile = new Rectangle(tx * TILE_SIZE, ty * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                if (wallGrid[ty][tx] && ballRect.overlaps(adjTile)) {
                    spawnProjectile = false;
                }
            }
        }


        if(spawnProjectile) {
            ProjectileState proj = new ProjectileState(
                projectileId,
                spawnX,
                spawnY,
                velX,
                velY,
                castAngle
            );
            proj.ownerId = caster.id;
            proj.damage = getDamage();
            proj.spellType = getTypeId();
            proj.piercing = isPiercing();
            projectiles.add(proj);
        }
        else cooldownTimer = 0f;
    }


    public abstract void spawnProjectile(
        List<ProjectileState> projectiles,
        PlayerState caster,
        boolean[][] wallGrid,
        float aimX,
        float aimY,
        int projectileId);
}
