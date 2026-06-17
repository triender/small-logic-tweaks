package net.enderirt.smalllogictweaks.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ConfigSyncPayload(String jsonConfig) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ConfigSyncPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("small_logic_tweaks", "config_sync"));

    public static final StreamCodec<FriendlyByteBuf, ConfigSyncPayload> CODEC = CustomPacketPayload.codec(
            ConfigSyncPayload::write,
            ConfigSyncPayload::new
    );

    public ConfigSyncPayload(FriendlyByteBuf buf) {
        this(buf.readUtf(32767));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.jsonConfig, 32767);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}