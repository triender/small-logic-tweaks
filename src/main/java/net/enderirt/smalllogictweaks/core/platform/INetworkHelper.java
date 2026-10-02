package net.enderirt.smalllogictweaks.core.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * VI: Giao diện dịch vụ mạng trừu tượng hóa việc gửi gói tin giữa các Modloader.
 * EN: Network service interface abstracting packet transmission across modloaders.
 */
public interface INetworkHelper {

    /**
     * VI: Gửi gói tin tùy chỉnh tới một người chơi cụ thể phía server.
     * EN: Sends a custom packet payload to a specific player on the server.
     *
     * @param player  VI: Người chơi đích / EN: Target player
     * @param payload VI: Nội dung gói tin / EN: Custom packet payload
     */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);
}
