package net.enderirt.smalllogictweaks;

import net.enderirt.smalllogictweaks.network.KitchenRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class KitchenRegistryTest {

    private static final ResourceKey<?> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("minecraft", "overworld"));
    private static final ResourceKey<?> NETHER =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("minecraft", "the_nether"));

    @BeforeEach
    public void setUp() {
        KitchenRegistry.clear();
    }

    @Test
    public void testSuccessfulReservationOnDifferentTicks() {
        BlockPos stovePos = new BlockPos(10, 64, 10);
        UUID entityA = UUID.randomUUID();
        UUID entityB = UUID.randomUUID();

        // Entity A reserves at tick 100
        boolean resultA = KitchenRegistry.tryReserve(stovePos, entityA, 100);
        Assertions.assertTrue(resultA, "Entity A should successfully reserve the stove at tick 100.");

        // Entity B reserves at tick 101
        boolean resultB = KitchenRegistry.tryReserve(stovePos, entityB, 101);
        Assertions.assertTrue(resultB, "Entity B should successfully reserve the stove at tick 101 because tick 100 has expired.");
    }

    @Test
    public void testParallelRoastingBlockedOnSameTick() {
        BlockPos stovePos = new BlockPos(10, 64, 10);
        UUID entityA = UUID.randomUUID();
        UUID entityB = UUID.randomUUID();

        // Entity A reserves at tick 500
        boolean resultA = KitchenRegistry.tryReserve(stovePos, entityA, 500);
        Assertions.assertTrue(resultA, "Entity A should successfully reserve the stove.");

        // Entity B tries to reserve at the same tick (500)
        boolean resultB = KitchenRegistry.tryReserve(stovePos, entityB, 500);
        Assertions.assertFalse(resultB, "Entity B should fail to reserve because Entity A already holds the reservation for tick 500.");
    }

    @Test
    public void testSelfReleasingReservations() {
        BlockPos stovePos = new BlockPos(10, 64, 10);
        UUID entityA = UUID.randomUUID();
        UUID entityB = UUID.randomUUID();

        // Entity A registers at tick 1000
        boolean resultA = KitchenRegistry.tryReserve(stovePos, entityA, 1000);
        Assertions.assertTrue(resultA, "Entity A should reserve stove at tick 1000.");

        // Entity B registers at tick 1001 (A did not tick / update its reservation at 1001)
        boolean resultB = KitchenRegistry.tryReserve(stovePos, entityB, 1001);
        Assertions.assertTrue(resultB, "Entity B should succeed at tick 1001 because Entity A's reservation expired.");
    }

    @Test
    public void testDimensionIsolation() {
        BlockPos samePos = new BlockPos(10, 64, 10);
        UUID entityOverworld = UUID.randomUUID();
        UUID entityNether = UUID.randomUUID();

        // Entity in Overworld reserves at tick 200
        boolean resultOverworld = KitchenRegistry.tryReserve(OVERWORLD, samePos, entityOverworld, 200);
        Assertions.assertTrue(resultOverworld, "Overworld entity should reserve stove.");

        // Entity in Nether at the EXACT SAME BlockPos reserves at the same tick 200
        boolean resultNether = KitchenRegistry.tryReserve(NETHER, samePos, entityNether, 200);
        Assertions.assertTrue(resultNether, "Nether entity should successfully reserve because dimensions are isolated.");
    }
}
