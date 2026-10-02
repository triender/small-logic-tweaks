package net.enderirt.smalllogictweaks.core.platform;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * VI: Triển khai INetworkHelper dành riêng cho hệ sinh thái Fabric Networking.
 * EN: INetworkHelper implementation dedicated to Fabric Networking ecosystem.
 */
public class FabricNetworkHelper implements INetworkHelper {

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }
}
