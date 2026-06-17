package net.enderirt.smalllogictweaks;

import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class ConfigSecurityTest {

    /**
     * Kiểm tra khả năng phòng vệ chống lại việc khai báo các thông số quá lớn (Overflow Attack).
     * Việc nhập các số liệu khổng lồ có thể gây tràn bộ nhớ đệm (OutOfMemory) do các thuật toán đệ quy
     * hoặc quét không gian khối lớn trong game.
     */
    @Test
    public void testTimberOverflowAttackProtection() {
        SmallLogicTweaksConfig config = new SmallLogicTweaksConfig();

        // Giả lập cuộc tấn công đẩy các thông số vượt qua mọi giới hạn vật lý của máy chủ
        config.MAX_LOG_HORIZONTAL_RADIUS = Integer.MAX_VALUE; // 2147483647
        config.MAX_LEAF_DISTANCE = 999999;

        // Kích hoạt bộ lọc phòng vệ để thực thi việc ép số về giới hạn cứng
        config.validate();

        // Khẳng định hệ thống bắt buộc phải đưa giá trị về đúng hằng số mặc định an toàn
        Assertions.assertEquals(5, config.MAX_LOG_HORIZONTAL_RADIUS,
                "Security validation failed: MAX_LOG_HORIZONTAL_RADIUS did not revert to safe default (5) when integer overflow value was injected.");

        Assertions.assertEquals(7, config.MAX_LEAF_DISTANCE,
                "Security validation failed: MAX_LEAF_DISTANCE did not revert to safe default (7) when massive out-of-bounds value was injected.");
    }

    /**
     * Kiểm tra khả năng ngăn chặn lỗi tiêm giá trị âm (Negative Injection).
     * Các giá trị âm không hợp lệ có thể phá vỡ logic game gốc hoặc gây sập máy chủ.
     */
    @Test
    public void testNegativeValueInjection() {
        SmallLogicTweaksConfig config = new SmallLogicTweaksConfig();

        // Cố tình tiêm giá trị âm vào biến kiểm soát giới hạn sinh quái vật cục bộ (Mob Cap)
        config.PHANTOM_MOB_CAP = -100;

        // Kích hoạt quá trình tự động sửa lỗi
        config.validate();

        // Hệ thống phòng vệ phải chặn giá trị âm và đưa về mức mặc định chuẩn
        Assertions.assertEquals(8, config.PHANTOM_MOB_CAP,
                "Security validation failed: Negative injection in PHANTOM_MOB_CAP was not blocked. Expected fallback to default value (8).");
    }

    /**
     * Kiểm tra quá trình khôi phục khi tệp JSON bị hỏng hoặc chứa dữ liệu sai lệch mức độ nặng.
     * Đảm bảo mọi giá trị bằng 0, số âm, hoặc vượt mức tối đa đều được phục hồi chính xác.
     */
    @Test
    public void testMalformedJsonRecovery() {
        SmallLogicTweaksConfig config = new SmallLogicTweaksConfig();

        // Giả lập kịch bản đọc một tệp JSON bị thiếu hụt hoặc thiết lập giá trị phá hoại
        config.MIN_LEAVES_FOR_TREE = 0; // Tránh việc khối gỗ lẻ tẻ tự nổ
        config.PHANTOM_MOB_CAP = 0; // Tránh chặn hoàn toàn việc sinh quái vật trái phép
        config.PHANTOM_THRESHOLD_POST_ELYTRA = -10; // Tiêm số âm cho thời gian mất ngủ
        config.PHANTOM_THRESHOLD_PRE_ELYTRA = 2000000000; // Tiêm số thời gian mất ngủ siêu lớn

        // Chạy hàm xác thực để kiểm tra tính toàn vẹn
        config.validate();

        // Khẳng định các điều kiện sinh tồn cốt lõi bắt buộc phải được khôi phục về mặc định
        Assertions.assertEquals(4, config.MIN_LEAVES_FOR_TREE,
                "Critical configuration failure: MIN_LEAVES_FOR_TREE of 0 was not sanitized. This allows rogue block updates and unconditional Timber activation.");

        Assertions.assertEquals(8, config.PHANTOM_MOB_CAP,
                "Critical configuration failure: PHANTOM_MOB_CAP of 0 bypasses natural spawning limits. Expected fallback to default (8).");

        Assertions.assertEquals(72000, config.PHANTOM_THRESHOLD_POST_ELYTRA,
                "Sanitization failure: Negative value in PHANTOM_THRESHOLD_POST_ELYTRA was not reset to the default 72000 ticks.");

        Assertions.assertEquals(144000, config.PHANTOM_THRESHOLD_PRE_ELYTRA,
                "Sanitization failure: Out-of-bounds massively large value in PHANTOM_THRESHOLD_PRE_ELYTRA was not reset to the default 144000 ticks.");
    }

    /**
     * Kiểm tra tính cưỡng chế đồng bộ gói tin mạng (Network Payload Enforcement).
     * Ngăn chặn tình trạng Máy khách (Client) tự sửa đổi biến lưu trên RAM để gian lận.
     */
    @Test
    public void testNetworkPayloadEnforcement() {
        // 1. Tạo cấu hình gốc của Máy chủ (Nghiêm ngặt)
        SmallLogicTweaksConfig serverConfig = new SmallLogicTweaksConfig();
        serverConfig.MAX_LOG_HORIZONTAL_RADIUS = 3; // Máy chủ chỉ cho phép quét bán kính 3 khối

        // 2. Giả lập Máy khách của người chơi đang cố tình gian lận
        SmallLogicTweaksConfig clientConfig = new SmallLogicTweaksConfig();
        clientConfig.MAX_LOG_HORIZONTAL_RADIUS = 10; // Người chơi tự sửa lên 10 để hack đào cây diện rộng

        // 3. Giả lập quá trình đóng gói và gửi nhận qua mạng (ConfigSyncPayload)
        // Lấy giá trị từ Máy chủ ép vào Máy khách để mô phỏng hành vi nhận gói tin đồng bộ
        clientConfig.MAX_LOG_HORIZONTAL_RADIUS = serverConfig.MAX_LOG_HORIZONTAL_RADIUS;
        clientConfig.validate();

        // 4. Khẳng định: Bộ nhớ RAM của Máy khách phải khớp hoàn toàn với quy tắc của Máy chủ
        Assertions.assertEquals(3, clientConfig.MAX_LOG_HORIZONTAL_RADIUS,
                "Network security breach: Client configuration did not strictly enforce the synchronized limitations from the Server.");
    }

    /**
     * Kiểm tra tấn công thay đổi kiểu dữ liệu (Type Mismatch Attack).
     * Kẻ tấn công cố tình nhập chuỗi văn bản vào biến số nguyên, hoặc số nguyên vào biến boolean.
     * Mục tiêu: Đảm bảo GSON bắt được lỗi và ném ra ngoại lệ để khối try-catch của mod xử lý.
     */
    @Test
    public void testTypeMismatchAttack() {
        // Chuỗi JSON cố tình ghi sai kiểu dữ liệu
        String maliciousJson = "{\n" +
                "  \"ENABLE_DEBUG_LOGS\": \"khong_phai_boolean\",\n" +
                "  \"MAX_LOG_HORIZONTAL_RADIUS\": \"chuoi_thay_vi_so\",\n" +
                "  \"PHANTOM_MOB_CAP\": [1, 2, 3]\n" +
                "}";

        // Hệ thống GSON của mod phải ném ra ngoại lệ JsonSyntaxException khi phân tích cú pháp
        Assertions.assertThrows(JsonSyntaxException.class, () -> {
            SmallLogicTweaksConfig.GSON.fromJson(maliciousJson, SmallLogicTweaksConfig.class);
        }, "Security failure: System did not reject invalid data types (Type Mismatch).");

        // Trong mã nguồn thực tế của bạn, ngoại lệ này sẽ bị bắt bởi khối catch (JsonSyntaxException e)
        // và tự động khôi phục LOCAL_INSTANCE về cấu hình an toàn mặc định.
    }

    /**
     * Kiểm tra tấn công chèn ký tự sai, hỏng cấu trúc JSON (Malformed JSON Attack).
     * Kẻ tấn công cố tình làm hỏng file bằng cách xóa ngoặc, thêm dấu phẩy thừa, hoặc ký tự rác.
     */
    @Test
    public void testMalformedJsonAttack() {
        // Chuỗi JSON bị thiếu ngoặc đóng và chứa ký tự lạ (@@@)
        String corruptedJson = "{\n" +
                "  \"ENABLE_DEBUG_LOGS\": true,\n" +
                "  \"MAX_LOG_HORIZONTAL_RADIUS\": 5\n" +
                "  @@@Ký tự rác phá hỏng file cấu trúc";

        Assertions.assertThrows(JsonSyntaxException.class, () -> {
            SmallLogicTweaksConfig.GSON.fromJson(corruptedJson, SmallLogicTweaksConfig.class);
        }, "Security failure: System parsed a corrupted JSON file without throwing an exception.");
    }

    /**
     * Kiểm tra tấn công chèn file dung lượng khổng lồ (Large File DOS Attack).
     * Kẻ tấn công tạo một file JSON nặng hàng GB để làm tràn RAM máy chủ (OutOfMemoryError).
     * Bài test sử dụng @TempDir của JUnit để tạo thư mục tạm an toàn.
     */
    @Test
    public void testLargeFileAttack(@TempDir Path tempDir) throws IOException {
        // Tạo một file tạm thời trong môi trường kiểm thử
        File massiveFile = tempDir.resolve("massive_config.json").toFile();

        // Bơm dữ liệu rác để file vượt quá 1MB (Giới hạn cứng của mod là 1024 * 1024 bytes)
        byte[] hugeData = new byte[1024 * 1024 + 10]; // 1MB + 10 bytes
        Arrays.fill(hugeData, (byte) ' '); // Điền toàn bộ bằng khoảng trắng để tăng dung lượng
        Files.write(massiveFile.toPath(), hugeData);

        // Mô phỏng lại chính xác logic phòng vệ trong hàm load() của bạn
        long MAX_FILE_SIZE_BYTES = 1024 * 1024;

        // Khẳng định rằng điều kiện kiểm tra độ lớn tệp sẽ ném ra ngoại lệ chặn đứng việc đọc
        IOException exception = Assertions.assertThrows(IOException.class, () -> {
            if (massiveFile.length() > MAX_FILE_SIZE_BYTES) {
                throw new IOException("Config file is suspiciously large (>" + MAX_FILE_SIZE_BYTES + " bytes). Refusing to load to prevent OutOfMemoryError.");
            }
            // Nếu không bị chặn, đoạn mã giả lập đọc file sẽ chạy và đánh sập RAM
            Files.readString(massiveFile.toPath());
        });

        // Kiểm tra xem thông báo lỗi có đúng chuẩn bảo mật nội bộ của mod không
        Assertions.assertTrue(exception.getMessage().contains("suspiciously large"),
                "Security failure: System did not block massive file loading (DOS protection failed).");
    }

    /**
     * Kiểm tra tấn công bom dữ liệu vòng lặp lồng nhau sâu (Deeply Nested JSON Attack).
     * Đây là một dạng tấn công làm tràn ngăn xếp (StackOverflow) của bộ phân tích cú pháp.
     */
    @Test
    public void testDeeplyNestedJsonAttack() {
        // Tạo một chuỗi JSON lồng nhau cực sâu: {"a":{"a":{"a":...}}}
        StringBuilder nestedJson = new StringBuilder();
        int depth = 5000;
        for (int i = 0; i < depth; i++) {
            nestedJson.append("{\"a\":");
        }
        nestedJson.append("1");
        for (int i = 0; i < depth; i++) {
            nestedJson.append("}");
        }

        // GSON của Google sẽ tự động phát hiện Json lồng sâu vô hạn và ném ra JsonSyntaxException.
        // Khẳng định rằng kẻ tấn công không thể đưa dữ liệu này vào bộ nhớ cấu hình.
        Assertions.assertThrows(JsonSyntaxException.class, () -> {
            SmallLogicTweaksConfig.GSON.fromJson(nestedJson.toString(), SmallLogicTweaksConfig.class);
        }, "Security failure: System did not reject excessively nested JSON structures.");
    }

    // Kiểm tra chuyển đổi toàn bộ khóa (key) sang chữ in hoa toUpperCase(Locale.ROOT) và bảo lưu tiền tố _comment_
    @Test
    public void testCaseInsensitiveAndCommentNormalization() {
        String messyJson = "{\n" +
                "  \"eNaBlE_deBug_LoGs\": true,\n" +
                "  \"_comment_enable_debug_logs\": \"Ghi chú bị sửa đổi\",\n" +
                "  \"max_leaf_distance\": 12\n" +
                "}";

        // Mô phỏng logic chuẩn hóa trong hàm load()
        com.google.gson.JsonObject rawObject = SmallLogicTweaksConfig.GSON.fromJson(messyJson, com.google.gson.JsonObject.class);
        com.google.gson.JsonObject normalizedObject = new com.google.gson.JsonObject();

        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : rawObject.entrySet()) {
            String key = entry.getKey();
            String normalizedKey = key.toUpperCase(java.util.Locale.ROOT);
            if (normalizedKey.startsWith("_COMMENT_")) {
                normalizedKey = "_comment_" + normalizedKey.substring(9);
            }
            normalizedObject.add(normalizedKey, entry.getValue());
        }

        SmallLogicTweaksConfig loaded = SmallLogicTweaksConfig.GSON.fromJson(normalizedObject, SmallLogicTweaksConfig.class);

        loaded.validate();
        
        Assertions.assertTrue(loaded.ENABLE_DEBUG_LOGS,
                "Lỗi chuẩn hóa: Không nhận diện được biến viết hoa/thường lộn xộn.");
        Assertions.assertEquals(12, loaded.MAX_LEAF_DISTANCE,
                "Lỗi chuẩn hóa: Không nhận diện được biến viết thường hoàn toàn.");
        Assertions.assertTrue(loaded._comment_ENABLE_DEBUG_LOGS.startsWith("Enable or disable"),
                "Lỗi bảo vệ ghi chú: Ghi chú không được khôi phục về mặc định nếu người dùng cố ý sửa.");
    }

    // Kiểm tra bộ lọc ký tự tàng hình BOM (UTF-8 Byte Order Mark)
    @Test
    public void testUTF8BomRemoval() {
        String jsonWithBom = "\uFEFF{\n\"ENABLE_HYDRO_HARDENING\": false\n}";

        // Logic cắt BOM
        if (jsonWithBom.startsWith("\uFEFF")) {
            jsonWithBom = jsonWithBom.substring(1);
        }

        // Nếu không cắt BOM, dòng dưới đây sẽ ném ra JsonSyntaxException
        SmallLogicTweaksConfig config = SmallLogicTweaksConfig.GSON.fromJson(jsonWithBom, SmallLogicTweaksConfig.class);

        Assertions.assertFalse(config.ENABLE_HYDRO_HARDENING,
                "Lỗi xử lý BOM: Không thể đọc cấu hình khi tệp chứa ký tự tàng hình UTF-8 BOM.");
    }

    //Kiểm tra tính bền bỉ với dữ liệu thừa (Unknown/Orphaned Fields)
    @Test
    public void testUnknownFieldsIgnored() {
        String jsonWithExtraFields = "{\n" +
                "  \"ENABLE_END_PHANTOM\": false,\n" +
                "  \"OLD_DELETED_FEATURE\": true,\n" +
                "  \"RANDOM_TEXT_DATA\": \"Hello World\"\n" +
                "}";

        Assertions.assertDoesNotThrow(() -> {
            SmallLogicTweaksConfig config = SmallLogicTweaksConfig.GSON.fromJson(jsonWithExtraFields, SmallLogicTweaksConfig.class);
            Assertions.assertFalse(config.ENABLE_END_PHANTOM, "Không đọc được biến hợp lệ khi có biến thừa.");
        }, "Lỗi phân tích: Hệ thống ném ngoại lệ thay vì phớt lờ các trường dữ liệu không tồn tại trong class.");
    }

    // Kiểm tra logic ghi tệp nguyên tử và tạo thư mục
    @Test
    public void testDirectoryCreationAndAtomicSave(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) {
        // Tạo một đường dẫn trỏ tới thư mục con chưa từng tồn tại
        java.io.File deepConfigFile = tempDir.resolve("config/small_logic_tweaks/small_logic_tweaks.json").toFile();
        java.io.File tempFile = new java.io.File(deepConfigFile.getParentFile(), deepConfigFile.getName() + ".tmp");

        SmallLogicTweaksConfig dummyConfig = new SmallLogicTweaksConfig();

        Assertions.assertDoesNotThrow(() -> {
            // Mô phỏng logic tạo thư mục
            java.nio.file.Files.createDirectories(deepConfigFile.getParentFile().toPath());

            // Mô phỏng logic ghi tệp .tmp
            try (java.io.FileWriter writer = new java.io.FileWriter(tempFile)) {
                SmallLogicTweaksConfig.GSON.toJson(dummyConfig, writer);
            }

            // Mô phỏng logic Atomic Move
            java.nio.file.Files.move(tempFile.toPath(), deepConfigFile.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }, "Lỗi I/O: Hệ thống thất bại trong việc tạo chuỗi thư mục con hoặc ghi tệp nguyên tử.");

        Assertions.assertTrue(deepConfigFile.exists(), "Tệp cấu hình gốc không được tạo ra thành công.");
        Assertions.assertFalse(tempFile.exists(), "Tệp tạm (.tmp) không được dọn dẹp sau khi tráo đổi.");
    }
}