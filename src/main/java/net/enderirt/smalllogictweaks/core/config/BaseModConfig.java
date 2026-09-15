package net.enderirt.smalllogictweaks.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.enderirt.smalllogictweaks.core.error.IModError;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Lớp trừu tượng nền tảng cung cấp động cơ cấu hình tự động, tái sử dụng cho mọi mod:
 * - Độc lập 100%, không phụ thuộc vào lớp mã lỗi cụ thể của mod (giao tiếp qua IModError).
 * - Tự động quét kiểm tra biên và phục hồi comment theo annotation @ConfigEntry.
 * - Tự động đọc tệp cấu hình, phòng vệ tràn RAM 1MB, xử lý UTF-8 BOM và tự phục hồi.
 * - Tự động chuẩn hóa JsonObject không phân biệt chữ hoa/thường.
 * - Tự động ghi tệp an toàn nguyên tử (.tmp swap).
 */
public abstract class BaseModConfig {

    /**
     * Giới hạn dung lượng tệp tối đa an toàn (1MB) phòng chống tấn công OutOfMemory.
     */
    public static final long MAX_FILE_SIZE_BYTES = 1024 * 1024;

    /**
     * Phương thức bắt buộc lớp con triển khai để thực hiện kiểm tra tính toàn vẹn cấu hình.
     */
    public abstract void validate();

    public void validateAnnotatedFields(Logger logger) {
        validateAnnotatedFields(logger, null);
    }

    /**
     * Tự động quét toàn bộ trường cấu hình có gắn @ConfigEntry để kiểm tra biên và chuẩn hóa.
     */
    public void validateAnnotatedFields(Logger logger, IModError outOfBoundsError) {
        Field[] fields = this.getClass().getFields();

        for (Field field : fields) {
            ConfigEntry entry = field.getAnnotation(ConfigEntry.class);
            if (entry == null) continue;

            try {
                // 1. Tự động phục hồi trường chú thích _comment_<TÊN_BIẾN> nếu tồn tại
                if (!entry.comment().isEmpty()) {
                    try {
                        Field commentField = this.getClass().getField("_comment_" + field.getName());
                        commentField.set(this, entry.comment());
                    } catch (NoSuchFieldException ignored) {
                        // Không có trường comment riêng biệt thì bỏ qua
                    }
                }

                // 2. Kiểm tra biên toán học cho kiểu int
                if (field.getType() == int.class) {
                    int val = field.getInt(this);
                    if (val < entry.minInt() || val > entry.maxInt()) {
                        if (outOfBoundsError != null && logger != null) {
                            outOfBoundsError.logError(
                                    logger,
                                    field.getName(),
                                    val,
                                    entry.minInt(),
                                    entry.maxInt(),
                                    entry.defaultInt()
                            );
                        } else if (logger != null) {
                            logger.error("Config parameter '{}' value ({}) is out of bounds [{}, {}]. Resetting to default: {}",
                                    field.getName(), val, entry.minInt(), entry.maxInt(), entry.defaultInt());
                        }
                        field.setInt(this, entry.defaultInt());
                    }
                }
            } catch (IllegalAccessException e) {
                if (logger != null) {
                    logger.error("Failed to access config field via reflection: {}", field.getName(), e);
                }
            }
        }
    }

    /**
     * Chuẩn hóa cấu trúc JsonObject: Xóa khoảng trắng thừa, đồng nhất chữ hoa cho khóa và bảo lưu tiền tố _comment_.
     */
    public static JsonObject normalizeJsonObject(JsonObject root) {
        JsonObject normalized = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String originalKey = entry.getKey();
            String cleanKey = originalKey.trim().toUpperCase(Locale.ROOT);

            if (cleanKey.startsWith("_COMMENT_")) {
                cleanKey = "_comment_" + cleanKey.substring(9);
            }
            normalized.add(cleanKey, entry.getValue());
        }
        return normalized;
    }

    public static void saveAtomically(File targetFile, Object configInstance, Gson gson, Logger logger) {
        saveAtomically(targetFile, configInstance, gson, logger, null);
    }

    /**
     * Ghi dữ liệu cấu hình xuống đĩa cứng bằng kỹ thuật hoán đổi nguyên tử (Atomic Move via .tmp).
     */
    public static void saveAtomically(File targetFile, Object configInstance, Gson gson, Logger logger, IModError saveFailedError) {
        try {
            if (targetFile.getParentFile() != null) {
                Files.createDirectories(targetFile.getParentFile().toPath());
            }
        } catch (IOException ignored) {}

        File tempFile = new File(targetFile.getParentFile(), targetFile.getName() + ".tmp");

        try {
            try (FileWriter writer = new FileWriter(tempFile)) {
                gson.toJson(configInstance, writer);
            }

            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            if (saveFailedError != null && logger != null) {
                saveFailedError.logError(logger, e.getMessage());
            } else if (logger != null) {
                logger.error("Failed to save config file securely: {}", e.getMessage());
            }
            try {
                Files.deleteIfExists(tempFile.toPath());
            } catch (IOException ignored) {}
        }
    }

    /**
     * Động cơ tải hoặc khởi tạo cấu hình Generic an toàn:
     * - Bảo vệ chống tấn công tệp kích thước lớn (>1MB)
     * - Xóa ký tự UTF-8 BOM
     * - Chuẩn hóa JsonObject
     * - Tự động cứu hộ và phục hồi cấu hình mặc định sạch khi xảy ra lỗi cú pháp
     */
    public static <T extends BaseModConfig> T loadOrCreate(
            File targetFile,
            Class<T> clazz,
            Supplier<T> defaultSupplier,
            Gson gson,
            Logger logger,
            IModError corruptedError,
            IModError saveFailedError
    ) {
        if (targetFile.exists()) {
            try {
                if (targetFile.length() > MAX_FILE_SIZE_BYTES) {
                    throw new IOException("Config file is suspiciously large (>" + MAX_FILE_SIZE_BYTES + " bytes). Refusing to load to prevent OutOfMemoryError.");
                }

                String jsonContent = Files.readString(targetFile.toPath(), StandardCharsets.UTF_8);
                if (jsonContent.startsWith("\uFEFF")) {
                    jsonContent = jsonContent.substring(1);
                }

                JsonObject rawObject = gson.fromJson(jsonContent, JsonObject.class);
                if (rawObject != null) {
                    JsonObject normalizedObject = normalizeJsonObject(rawObject);
                    T loaded = gson.fromJson(normalizedObject, clazz);
                    if (loaded != null) {
                        loaded.validate();
                        saveAtomically(targetFile, loaded, gson, logger, saveFailedError);
                        return loaded;
                    } else {
                        throw new JsonSyntaxException("Config file is empty.");
                    }
                } else {
                    throw new JsonSyntaxException("Config file structure is invalid.");
                }
            } catch (JsonSyntaxException | IOException e) {
                if (corruptedError != null && logger != null) {
                    corruptedError.logError(logger, e.getMessage());
                } else if (logger != null) {
                    logger.error("Config file is corrupted or invalid! Resetting to default configuration. Error: {}", e.getMessage());
                }

                T fallback = defaultSupplier.get();
                fallback.validate();
                saveAtomically(targetFile, fallback, gson, logger, saveFailedError);
                return fallback;
            }
        } else {
            if (logger != null) {
                logger.info("Config file not found, initializing default...");
            }
            T initial = defaultSupplier.get();
            initial.validate();
            saveAtomically(targetFile, initial, gson, logger, saveFailedError);
            return initial;
        }
    }
}
