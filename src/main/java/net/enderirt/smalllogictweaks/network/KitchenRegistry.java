package net.enderirt.smalllogictweaks.network;

import net.minecraft.core.BlockPos;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KitchenRegistry {
    public record StoveKey(Object dimension, BlockPos pos) {}

    public static class StoveReservation {
        public final UUID entityId;
        public final long gameTime;

        public StoveReservation(UUID entityId, long gameTime) {
            this.entityId = entityId;
            this.gameTime = gameTime;
        }
    }

    private static final ConcurrentHashMap<StoveKey, StoveReservation> RESERVATIONS = new ConcurrentHashMap<>();

    public static boolean tryReserve(Object dimension, BlockPos heatSourcePos, UUID entityId, long currentTick) {
        if (heatSourcePos == null || entityId == null) {
            return false;
        }

        // Tự động dọn dẹp các đặt chỗ cũ đã hết hạn để tránh rò rỉ bộ nhớ (Unbounded Map Growth)
        if (RESERVATIONS.size() > 256) {
            RESERVATIONS.entrySet().removeIf(entry -> entry.getValue().gameTime < currentTick - 100);
        }

        StoveKey key = new StoveKey(dimension, heatSourcePos.immutable());
        StoveReservation reservation = RESERVATIONS.get(key);
        if (reservation == null || reservation.gameTime < currentTick) {
            RESERVATIONS.put(key, new StoveReservation(entityId, currentTick));
            return true;
        }
        if (reservation.entityId.equals(entityId)) {
            // Đã được giữ bởi chính entity này trong tick hiện tại, cập nhật lại
            RESERVATIONS.put(key, new StoveReservation(entityId, currentTick));
            return true;
        }
        return false;
    }

    // Phương thức quá tải hỗ trợ tương thích ngược cho unit test và trường hợp mặc định
    public static boolean tryReserve(BlockPos heatSourcePos, UUID entityId, long currentTick) {
        return tryReserve("default", heatSourcePos, entityId, currentTick);
    }

    public static void clear() {
        RESERVATIONS.clear();
    }
}
