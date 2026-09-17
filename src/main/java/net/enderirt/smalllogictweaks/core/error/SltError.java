package net.enderirt.smalllogictweaks.core.error;

/**
 * VI: Danh mục mã lỗi định danh chuẩn hóa cho Small Logic Tweaks.
 * EN: Standardized error code catalog for Small Logic Tweaks.
 */
public enum SltError implements IModError {
    // --- PHÂN HỆ CẤU HÌNH / CONFIG SUBSYSTEM (CFG: 001 - 099) ---
    CFG_CORRUPTED("SLT-CFG-001", "Config file corrupted or invalid, resetting to default: {}"),
    CFG_SAVE_FAILED("SLT-CFG-002", "Failed to save config file atomically: {}"),
    CFG_OUT_OF_BOUNDS("SLT-CFG-003", "Config parameter '{}' value ({}) is out of bounds [{}, {}]. Resetting to default: {}"),

    // --- PHÂN HỆ MẠNG & PHÒNG VỆ BIÊN / NETWORKING & BOUNDS ENFORCEMENT (NET: 100 - 199) ---
    NET_PARSE_FAILED("SLT-NET-101", "Client failed to parse configuration packet from server: {}"),
    NET_OUT_OF_BOUNDS_PAYLOAD("SLT-NET-102", "Defensive validation triggered: received out-of-bounds config payload for '{}'. Feature disabled locally on client."),
    // [VI] Bí danh giữ tương thích ngược / [EN] Backward-compatibility alias
    NET_TAMPERED_PAYLOAD("SLT-NET-102", "Defensive validation triggered: received out-of-bounds config payload for '{}'. Feature disabled locally on client."),

    // --- PHÂN HỆ LOGIC GAME / GAME LOGIC SUBSYSTEM (LOGIC: 200 - 299) ---
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
