package net.enderirt.smalllogictweaks;

import net.enderirt.smalllogictweaks.core.platform.Services;
import net.enderirt.smalllogictweaks.network.ConfigSyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import org.slf4j.Logger;

public class SmallLogicTweaks implements ModInitializer {
	public static final String MOD_ID = SmallLogicTweaksCommon.MOD_ID;

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = SmallLogicTweaksCommon.LOGGER;

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);

		// Lắng nghe sự kiện người chơi tham gia
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				// Đóng gói cấu hình cục bộ của Server và gửi xuống Client qua abstraction SPI
				Services.NETWORK.sendToPlayer(handler.player, ConfigSyncPayload.fromLocalConfig()));
		// 1. Luôn tải cấu hình đầu tiên
		SmallLogicTweaksCommon.init();

		// 2. Khởi tạo các module dựa trên cấu hình đã tải
		TweaksConfigCondition.initialize();

		if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_DEBUG_LOGS) {
			LOGGER.info("Small Logic Tweaks Mod initialization completed.");
		}
		SmallLogicTweaksEvents.register();

		// Kiểm tra mixin cho boot test (chỉ chạy khi có -Dslt.mixinAudit=true)
		ServerLifecycleEvents.SERVER_STARTED.register(server -> SmallLogicTweaksCommon.runMixinAuditIfRequested());
	}
}