package net.enderirt.smalllogictweaks.core.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * VI: Triển khai INetworkHelper cho NeoForge. Gói tin được đăng ký dạng optional nên chỉ gửi khi client
 *     có kênh tương ứng (client vanilla hoặc thiếu mod vẫn vào được server, giống hành vi bên Fabric).
 * EN: INetworkHelper implementation for NeoForge. The payload is registered as optional, so it is only sent
 *     when the client has the channel (vanilla or mod-less clients can still join, matching Fabric behavior).
 */
public class NeoForgeNetworkHelper implements INetworkHelper {

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
