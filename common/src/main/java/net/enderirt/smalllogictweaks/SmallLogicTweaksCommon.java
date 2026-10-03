package net.enderirt.smalllogictweaks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VI: Hằng số dùng chung cho mọi modloader. Code trong module common chỉ được tham chiếu lớp này,
 *     không tham chiếu entrypoint của từng loader.
 * EN: Constants shared by every modloader. Code in the common module must reference this class,
 *     never a loader-specific entrypoint.
 */
public final class SmallLogicTweaksCommon {
    public static final String MOD_ID = "small_logic_tweaks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private SmallLogicTweaksCommon() {}

    /**
     * VI: Khởi tạo phần chung, gọi đầu tiên trong entrypoint của mọi loader (trước khi đăng ký event).
     * EN: Common initialization, called first by every loader's entrypoint (before registering events).
     */
    public static void init() {
        SmallLogicTweaksConfig.load();
    }
}
