package net.enderirt.smalllogictweaks.core.error;

import org.slf4j.Logger;

/**
 * Interface chuẩn cho toàn bộ hệ thống mã lỗi định danh trong ecosystem modding.
 */
public interface IModError {
    /**
     * Trả về mã lỗi định danh duy nhất (ví dụ: SLT-CFG-001).
     */
    String getCode();

    /**
     * Trả về mẫu thông điệp lỗi (chứa placeholder '{}' của SLF4J).
     */
    String getMessageTemplate();

    /**
     * Ghi log mức ERROR kèm mã lỗi tự động.
     */
    default void logError(Logger logger, Object... args) {
        logger.error("[" + getCode() + "] " + getMessageTemplate(), args);
    }

    /**
     * Ghi log mức WARN kèm mã lỗi tự động.
     */
    default void logWarn(Logger logger, Object... args) {
        logger.warn("[" + getCode() + "] " + getMessageTemplate(), args);
    }

    /**
     * Ghi log mức INFO kèm mã lỗi tự động.
     */
    default void logInfo(Logger logger, Object... args) {
        logger.info("[" + getCode() + "] " + getMessageTemplate(), args);
    }
}
