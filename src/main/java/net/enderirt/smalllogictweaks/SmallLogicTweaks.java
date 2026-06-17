package net.enderirt.smalllogictweaks;

import net.enderirt.smalllogictweaks.network.ConfigSyncPayload;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmallLogicTweaks implements ModInitializer {
	public static final String MOD_ID = "small_logic_tweaks";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);

		// Lắng nghe sự kiện người chơi tham gia
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			// Đóng gói cấu hình cục bộ của Server
			String serverConfigJson = SmallLogicTweaksConfig.GSON.toJson(SmallLogicTweaksConfig.LOCAL_INSTANCE);

			// Gửi xuống Client bằng field handler.player (không có ngoặc đơn)
			ServerPlayNetworking.send(handler.player, new ConfigSyncPayload(serverConfigJson));
		});
		// 1. Luôn tải cấu hình đầu tiên
		SmallLogicTweaksConfig.load();

		// 2. Khởi tạo các module dựa trên cấu hình đã tải
		TweaksConfigCondition.initialize();

		if (SmallLogicTweaksConfig.ACTIVE_INSTANCE.ENABLE_DEBUG_LOGS) {
			LOGGER.info("Small Logic Tweaks Mod initialization completed.");
		}
		SmallLogicTweaksEvents.register();
	}
}