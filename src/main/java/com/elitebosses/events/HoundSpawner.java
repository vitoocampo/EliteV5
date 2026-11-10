package com.elitebosses.events;

import com.elitebosses.entity.HoundmasterEntity;
import com.elitebosses.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class HoundSpawner {
    private HoundSpawner() {
    }

    /** Invoca al Sabueso exactamente en 'pos'. */
    public static HoundmasterEntity spawnAt(ServerLevel level, Vec3 pos) {
        HoundmasterEntity hound = ModEntities.HOUNDMASTER.get().create(level);
        if (hound == null) {
            return null;
        }
        hound.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360.0F, 0.0F);
        hound.finalizeSpawn(level, level.getCurrentDifficultyAt(hound.blockPosition()), MobSpawnType.COMMAND, null, null);
        level.addFreshEntity(hound);
        return hound;
    }

    /** Lo invoca a 'distance' bloques del jugador, en una direccion al azar. */
    public static HoundmasterEntity spawnFar(ServerLevel level, ServerPlayer player, double distance) {
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
        int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
        level.getChunk(x >> 4, z >> 4); // carga (o genera) el chunk de destino
        double y;
        if (level.dimensionType().hasCeiling()) {
            y = player.getY();
        } else {
            y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        }
        return spawnAt(level, new Vec3(x + 0.5D, y, z + 0.5D));
    }
}
