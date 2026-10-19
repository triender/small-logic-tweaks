package net.enderirt.smalllogictweaks;

import net.enderirt.smalllogictweaks.core.error.SltError;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class SltErrorValidationTest {

    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("^SLT-[A-Z]+-[0-9]{3}$");

    @Test
    public void testErrorCodesFollowNamingConvention() {
        for (SltError error : SltError.values()) {
            Assertions.assertNotNull(error.getCode(), "Mã lỗi không được null: " + error.name());
            Assertions.assertTrue(ERROR_CODE_PATTERN.matcher(error.getCode()).matches(),
                    "Mã lỗi '" + error.getCode() + "' của " + error.name() + " phải tuân thủ chuẩn SLT-[SUBSYSTEM]-[CODE], ví dụ: SLT-CFG-001");
        }
    }

    @Test
    public void testErrorCodesUniqueness() {
        Set<String> uniqueCodes = new HashSet<>();
        for (SltError error : SltError.values()) {
            // NET_TAMPERED_PAYLOAD là bí danh tương thích ngược của NET_OUT_OF_BOUNDS_PAYLOAD, cùng dùng SLT-NET-102
            if (error == SltError.NET_TAMPERED_PAYLOAD) {
                Assertions.assertEquals("SLT-NET-102", error.getCode());
                continue;
            }

            boolean added = uniqueCodes.add(error.getCode());
            Assertions.assertTrue(added, "Phát hiện mã lỗi bị trùng lặp trong hệ thống: " + error.getCode() + " tại " + error.name());
        }
    }

    @Test
    public void testMessageTemplatesNotEmpty() {
        for (SltError error : SltError.values()) {
            Assertions.assertNotNull(error.getMessageTemplate(), "Mẫu thông điệp không được null: " + error.name());
            Assertions.assertFalse(error.getMessageTemplate().trim().isEmpty(),
                    "Mẫu thông điệp không được để trống: " + error.name());
        }
    }

    @Test
    public void testLoggingMethodsSafety() {
        var logger = LoggerFactory.getLogger(SltErrorValidationTest.class);
        for (SltError error : SltError.values()) {
            Assertions.assertDoesNotThrow(() -> {
                error.logInfo(logger, "test_param");
                error.logWarn(logger, "test_param");
                error.logError(logger, "test_param");
            }, "Các phương thức ghi log của " + error.name() + " không được ném ngoại lệ");
        }
    }
}
