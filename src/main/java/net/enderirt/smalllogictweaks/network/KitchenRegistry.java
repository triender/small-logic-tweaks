package net.enderirt.smalllogictweaks.network;

import net.minecraft.core.BlockPos;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KitchenRegistry {
    public static class StoveReservation {
        public final UUID entityId;
        public final long gameTime;

        public StoveReservation(UUID entityId, long gameTime) {
            this.entityId = entityId;
            this.gameTime = gameTime;
        }
    }

    private static final ConcurrentHashMap<BlockPos, StoveReservation> RESERVATIONS = new ConcurrentHashMap<>();

    public static boolean tryReserve(BlockPos heatSourcePos, UUID entityId, long currentTick) {
        StoveReservation reservation = RESERVATIONS.get(heatSourcePos);
        if (reservation == null || reservation.gameTime < currentTick) {
            RESERVATIONS.put(heatSourcePos, new StoveReservation(entityId, currentTick));
            return true;
        }
        if (reservation.entityId.equals(entityId)) {
            // Already reserved by this entity in the current tick, keep it updated
            RESERVATIONS.put(heatSourcePos, new StoveReservation(entityId, currentTick));
            return true;
        }
        return false;
    }

    public static void clear() {
        RESERVATIONS.clear();
    }
}
