package net.enderirt.smalllogictweaks.core.error;

/**
 * Danh mục mã lỗi định danh chuẩn hóa cho Small Logic Tweaks.
 */
public enum SltError implements IModError {
    // --- PHÂN HỆ CẤU HÌNH (CFG: 001 - 099) ---
    CFG_CORRUPTED("SLT-CFG-001", "Config file corrupted or invalid, resetting to default: {}"),
    CFG_SAVE_FAILED("SLT-CFG-002", "Failed to save config file atomically: {}"),
    CFG_OUT_OF_BOUNDS("SLT-CFG-003", "Config parameter '{}' value ({}) is out of bounds [{}, {}]. Resetting to default: {}"),

    // --- PHÂN HỆ MẠNG & BẢO MẬT (NET: 100 - 199) ---
    NET_PARSE_FAILED("SLT-NET-101", "Client failed to parse configuration packet from server: {}"),
    NET_TAMPERED_PAYLOAD("SLT-NET-102", "Security failsafe triggered: received invalid/out-of-bounds config payload for '{}'. Feature disabled locally."),

    // --- PHÂN HỆ LOGIC GAME (LOGIC: 200 - 299) ---
    TIMBER_ABORT_OVERLOAD("SLT-TIMBER-201", "Timber tree felling aborted due to excessive block volume: {}"),
    PHANTOM_LOGIC_ERROR("SLT-PHANTOM-301", "Encountered error during phantom spawn evaluation: {}");

    private final String code;
    private final String messageTemplate;

    SltError(String code, String messageTemplate) {
        this.code = code;
        this.messageTemplate = messageTemplate;
    }

    @Override
    public String getCode() {
        return this.code;
    }

    @Override
    public String getMessageTemplate() {
        return this.messageTemplate;
    }
}
