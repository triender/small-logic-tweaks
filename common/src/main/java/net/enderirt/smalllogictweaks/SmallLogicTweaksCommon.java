package net.enderirt.smalllogictweaks;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.MixinEnvironment;
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

    /**
     * VI: Chỉ chạy khi có {@code -Dslt.mixinAudit=true} (boot test của scripts/deploy.ps1). Ép nạp mọi class mà mixin
     *     nhắm tới để mixin lệch target (do loader patch class gốc) lỗi ngay lúc khởi động thay vì lúc người chơi gặp.
     *     Mỗi loader gọi hàm này khi server khởi động xong.
     * EN: Only runs with {@code -Dslt.mixinAudit=true} (scripts/deploy.ps1 boot tests). Forces every mixin target class
     *     to load so a mixin whose target was patched by the loader fails at startup instead of in front of a player.
     *     Each loader calls this once the server has started.
     */
    public static void runMixinAuditIfRequested() {
        if (!Boolean.getBoolean("slt.mixinAudit")) {
            return;
        }
        LOGGER.info("[Mixin audit] Forcing all mixin targets to load...");
        MixinEnvironment.getCurrentEnvironment().audit();
        LOGGER.info("[Mixin audit] PASSED: all mixin targets applied.");
    }
}
