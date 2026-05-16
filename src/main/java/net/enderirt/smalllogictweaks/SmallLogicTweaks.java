package net.enderirt.smalllogictweaks;

import net.fabricmc.api.ModInitializer;

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
		// 1. Luôn tải cấu hình đầu tiên
		SmallLogicTweaksConfig.load();

		// 2. Khởi tạo các module dựa trên cấu hình đã tải
		TweaksConfigCondition.initialize();

		if (SmallLogicTweaksConfig.INSTANCE.ENABLE_DEBUG_LOGS) {
			LOGGER.info("Small Logic Tweaks Mod initialization completed.");
		}
		SmallLogicTweaksEvents.register();
	}
}