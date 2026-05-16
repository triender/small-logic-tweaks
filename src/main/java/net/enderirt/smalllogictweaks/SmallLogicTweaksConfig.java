package net.enderirt.smalllogictweaks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;

public class SmallLogicTweaksConfig {
    // Khởi tạo Logger riêng cho phân hệ cấu hình để dễ dàng lọc log khi debug
    private static final Logger LOGGER = LoggerFactory.getLogger("SmallLogicTweaks/Config");

    // Đặt giới hạn dung lượng cứng là 1MB (1024 * 1024 bytes).
    // DỰ PHÒNG: Ngăn chặn tình trạng tệp JSON bị nhồi nhét văn bản rác vô hạn (vài trăm MB hoặc hàng GB),
    // khiến hàm đọc tệp ép RAM nạp dữ liệu quá mức dẫn đến sập toàn bộ máy ảo Java (OutOfMemoryError).
    private static final long MAX_FILE_SIZE_BYTES = 1024 * 1024;

    // ==========================================
    // --- SYSTEM & DEBUG CONFIGURATION ---
    // ==========================================
    public String _comment_ENABLE_DEBUG_LOGS = "Enable or disable general debug logs for the mod.";
    public boolean ENABLE_DEBUG_LOGS = false;

    public String _comment_ENABLE_TIMBER_DEBUG_LOGS = "Enable or disable technical logs for the tree chopper feature.";
    public boolean ENABLE_TIMBER_DEBUG_LOGS = false;

    // ==========================================
    // --- DATA-DRIVEN TWEAKS CONFIGURATION ---
    // ==========================================
    public String _comment_ENABLE_CHARCOAL_TO_BLACK_DYE = "Allow crafting Black Dye directly from Charcoal.";
    public boolean ENABLE_CHARCOAL_TO_BLACK_DYE = true;

    public String _comment_ENABLE_JUNGLE_SUSTAINABILITY = "Increase the drop rate of Jungle Saplings from Jungle Leaves.";
    public boolean ENABLE_JUNGLE_SUSTAINABILITY = true;

    // ==========================================
    // --- BONE MEAL TWEAK CONFIGURATION ---
    // ==========================================
    public String _comment_ENABLE_BONE_MEAL_TWEAK = "Enable using Bone Meal on dirt to turn it into grass or mycelium.";
    public boolean ENABLE_BONE_MEAL_TWEAK = true;

    public String _comment_REQUIRE_NEIGHBOR_SOURCE = "If true, requires at least one matching grass/mycelium block in a 3x3x3 area.";
    public boolean REQUIRE_NEIGHBOR_SOURCE = false;

    public String _comment_ALLOW_ALL_DIRT_TYPES = "If true, allows Bone Meal to work on coarse dirt, rooted dirt. If false, only normal dirt.";
    public boolean ALLOW_ALL_DIRT_TYPES = false;

    // ==========================================
    // --- TIMBER TWEAK CONFIGURATION ---
    // ==========================================
    public String _comment_ENABLE_AUTO_LEAVES_DECAY = "Make leaves decay instantly when a tree is cut down using Timber.";
    public boolean ENABLE_AUTO_LEAVES_DECAY = true;

    public String _comment_MAX_LOG_HORIZONTAL_RADIUS = "Maximum horizontal distance (X/Z axis) to search for connected logs. NOT RECOMMENT TO CHANGE [Default: 5]";
    public int MAX_LOG_HORIZONTAL_RADIUS = 5;

    public String _comment_MAX_LEAF_DISTANCE = "Maximum distance from the log to search for connected leaves (Vanilla default). NOT RECOMMENT TO CHANGE [Default: 7]";
    public int MAX_LEAF_DISTANCE = 7;

    public String _comment_MIN_LEAVES_FOR_TREE = "Minimum number of connected leaves required to validate a valid tree. Acts as a safety check for player houses. [Default: 4]";
    public int MIN_LEAVES_FOR_TREE = 4;

    public String _comment_DECAY_THRESHOLD = "Distance threshold for leaf decay. At 7 (Vanilla), leaves decay normally when completely disconnected. Lower values (1-6) force leaves to decay closer to the log. [Default: 6]";
    public int DECAY_THRESHOLD = 6;

    // ==========================================
    // --- SYSTEM CORE CONFIGURATION MANAGEMENT ---
    // ==========================================

    // Thực thể tĩnh duy nhất nắm giữ trạng thái cấu hình đang hoạt động của Mod toàn cục
    public static SmallLogicTweaksConfig INSTANCE = new SmallLogicTweaksConfig();

    // Định nghĩa đường dẫn tệp tin lưu trữ nằm trong thư mục config tiêu chuẩn của Fabric API
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "small_logic_tweaks.json");

    // Khởi tạo bộ dựng Gson với tính năng Pretty Printing để tệp JSON tự động xuống dòng thụt lề đẹp mắt
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        // Kiểm tra xem người chơi đã từng có tệp cấu hình trên ổ đĩa chưa
        if (CONFIG_FILE.exists()) {
            try {
                // DỰ PHÒNG CHỐNG TRÀN RAM: Đo dung lượng tệp trước khi đọc.
                // Nếu tệp lớn một cách bất thường (vượt quá 1MB), chặn đứng hành vi đọc tệp ngay lập tức.
                if (CONFIG_FILE.length() > MAX_FILE_SIZE_BYTES) {
                    throw new IOException("Config file is suspiciously large (>" + MAX_FILE_SIZE_BYTES + " bytes). Refusing to load to prevent OutOfMemoryError.");
                }

                // Đọc toàn bộ nội dung tệp JSON thành một chuỗi String văn bản lớn trong bộ nhớ RAM với bảng mã UTF-8 chuẩn
                String jsonContent = Files.readString(CONFIG_FILE.toPath(), StandardCharsets.UTF_8);

                // DỰ PHÒNG KÝ TỰ ẨN (UTF-8 BOM): Windows Notepad cũ thường chèn ký tự ẩn \uFEFF vào đầu tệp khi lưu.
                // Nếu không loại bỏ byte này, Gson sẽ báo lỗi cú pháp không nhận diện được dấu ngoặc nhọn '{'.
                if (jsonContent.startsWith("\uFEFF")) {
                    jsonContent = jsonContent.substring(1); // Cắt bỏ ký tự ẩn đầu tiên, chỉ giữ lại phần văn bản JSON sạch
                }

                // Chuyển chuỗi văn bản thành một cây đối tượng cấu trúc JsonObject thô để bóc tách từng cặp khóa-giá trị
                JsonObject rawObject = GSON.fromJson(jsonContent, JsonObject.class);

                if (rawObject != null) {
                    // Tạo một JsonObject mới để chứa dữ liệu sau khi đã được chuẩn hóa chữ Hoa/Thường
                    JsonObject normalizedObject = new JsonObject();

                    // DỰ PHÒNG LỖI VIẾT HOA/VIẾT THƯỜNG (Case-Insensitivity): Duyệt qua từng khóa trong file JSON của người dùng
                    for (Map.Entry<String, JsonElement> entry : rawObject.entrySet()) {
                        String key = entry.getKey();
                        // Chuyển toàn bộ tên biến về chữ in hoa chuẩn hóa theo hệ thống của Java (Locale.ROOT để tránh lỗi phân vùng ngôn ngữ)
                        String normalizedKey = key.toUpperCase(Locale.ROOT);

                        // Xử lý biệt lệ cho các trường ghi chú: Hệ thống Java dùng chữ thường "_comment_..."
                        // Nếu khóa bắt đầu bằng "_COMMENT_", ta đổi lại thành "_comment_" kèm phần đuôi viết hoa để khớp với thuộc tính lớp
                        if (normalizedKey.startsWith("_COMMENT_")) {
                            normalizedKey = "_comment_" + normalizedKey.substring(9);
                        }

                        // Đưa cặp khóa đã chuẩn hóa và giá trị gốc vào đối tượng JSON sạch
                        normalizedObject.add(normalizedKey, entry.getValue());
                    }

                    // Ép kiểu JsonObject đã chuẩn hóa hoàn toàn thành đối tượng Java chuyên dụng (Thực thể tạm thời `loaded`)
                    SmallLogicTweaksConfig loaded = GSON.fromJson(normalizedObject, SmallLogicTweaksConfig.class);

                    if (loaded != null) {
                        // DỰ PHÒNG LỖI SAI BIÊN LOGIC: Tiến hành kiểm tra và sửa đổi các thông số số nguyên ngay trên thực thể tạm
                        loaded.validate();

                        // DỰ PHÒNG XUNG ĐỘT LUỒNG (Race Condition): Sau khi dữ liệu đã sạch 100%, mới hoán đổi tham chiếu vào biến INSTANCE toàn cục.
                        // Việc này đảm bảo các luồng game khác không bao giờ đọc phải dữ liệu rác hoặc dữ liệu lỗi trong quá trình nạp file.
                        INSTANCE = loaded;

                        // Đồng bộ ngược lại cấu trúc sạch (đã sửa lỗi biên, điền thiếu comment nếu có) đè lên đĩa cứng
                        save();

                        if (INSTANCE.ENABLE_DEBUG_LOGS) {
                            LOGGER.info("Successfully loaded, normalized, and self-healed config file.");
                        }
                    } else {
                        // Ném ngoại lệ cú pháp nếu quá trình ánh xạ đối tượng trả về null (Ví dụ người chơi nhập mảng rỗng `[]`)
                        throw new JsonSyntaxException("Config file is empty.");
                    }
                } else {
                    // Ném ngoại lệ nếu cấu trúc gốc không khớp định dạng của một khối JSON lồng `{}`
                    throw new JsonSyntaxException("Config file structure is invalid.");
                }
            } catch (JsonSyntaxException | IOException e) {
                // DỰ PHÒNG LỖI FILE HỎNG/SAI CÚ PHÁP: Nếu người chơi gõ thiếu dấu phẩy, điền chữ vào biến số...
                // Khối catch này sẽ chặn đứng lỗi sập game (Crash), in log chi tiết thông báo lỗi ra màn hình console.
                LOGGER.error("Config file is corrupted or invalid! Resetting to default configuration. Error: {}", e.getMessage());

                // Khôi phục lại trạng thái mod về mặc định của nhà phát triển trực tiếp trên RAM để cứu vãn phiên chơi
                INSTANCE = new SmallLogicTweaksConfig();

                // Ghi đè lại file mặc định sạch lên đĩa cứng để tự sửa lỗi cho các lần khởi động game sau
                save();
            }
        } else {
            // Trường hợp file không tồn tại (Lần đầu chạy mod), in log thông báo và sinh file cấu hình mặc định
            if (INSTANCE.ENABLE_DEBUG_LOGS) {
                LOGGER.info("Config file not found, initializing default...");
            }
            save();
        }
    }

    public static void save() {
        // DỰ PHÒNG MẤT THƯ MỤC: Nếu thư mục chứa file cấu hình bị xóa mất (hoặc chưa sinh ra), tự động tạo lại các tầng thư mục
        try {
            Files.createDirectories(CONFIG_FILE.getParentFile().toPath());
        } catch (IOException e) {
            // Xử lý hoặc ghi log lỗi không thể tạo thư mục
        }

        // Định nghĩa đường dẫn cho tệp tin tạm thời có đuôi `.tmp`
        File tempFile = new File(CONFIG_FILE.getParentFile(), CONFIG_FILE.getName() + ".tmp");

        try {
            // BƯỚC 1 CỦA GHI NGUYÊN TỬ (Atomic Save): Ghi toàn bộ dữ liệu cấu hình hiện tại vào tệp tạm thời `.tmp` trước.
            // DỰ PHÒNG MẤT ĐIỆN/ĐẦY Ổ CỨNG GIỮA CHỪNG: Nếu quá trình ghi tệp bị đứt quãng tại đây, tệp gốc (.json) của người chơi vẫn an toàn tuyệt đối.
            try (FileWriter writer = new FileWriter(tempFile)) {
                GSON.toJson(INSTANCE, writer);
            }

            // BƯỚC 2 CỦA GHI NGUYÊN TỬ: Ra lệnh cho hệ điều hành thực hiện tráo đổi (Move) tệp `.tmp` đè lên tệp gốc `.json`.
            // Thao tác ATOMIC_MOVE diễn ra ở tầng nhân hệ điều hành trong tích tắc (vài phần triệu giây), loại bỏ hoàn toàn nguy cơ tệp tin bị cắt cụt dữ liệu (0 byte) khi mất điện đột ngột.
            Files.move(tempFile.toPath(), CONFIG_FILE.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            if (INSTANCE.ENABLE_DEBUG_LOGS) {
                LOGGER.info("Successfully saved config file securely to: {}", CONFIG_FILE.getAbsolutePath());
            }
        } catch (IOException e) {
            // DỰ PHÒNG LỖI KHÓA FILE HỆ ĐIỀU HÀNH: Nếu ổ cứng bị đóng băng (Read-only) hoặc đầy dung lượng hoàn toàn, in log lỗi
            LOGGER.error("Failed to save config file securely: {}", e.getMessage());

            // Hậu kiểm an toàn: Nếu tệp tạm thời vẫn đang lơ lửng trên ổ đĩa do lỗi ghi giữa chừng, thực hiện xóa bỏ để tránh rác thư mục
            // HẬU KIỂM AN TOÀN
            try {
                Files.deleteIfExists(tempFile.toPath());
            } catch (IOException err) {
                // Xử lý hoặc ghi log lỗi không thể xóa file tạm
            }
        }
    }

    private void validate() {
        // GHI ĐÈ VÔ ĐIỀU KIỆN: Tự động phục hồi toàn bộ nội dung hướng dẫn của nhà phát triển
        // Cho dù người dùng xóa, gán null, hay viết sai lệch nội dung, hệ thống sẽ luôn khôi phục về dạng chuẩn.
        this._comment_ENABLE_DEBUG_LOGS = "Enable or disable general debug logs for the mod.";
        this._comment_ENABLE_TIMBER_DEBUG_LOGS = "Enable or disable technical logs for the tree chopper feature.";
        this._comment_ENABLE_CHARCOAL_TO_BLACK_DYE = "Allow crafting Black Dye directly from Charcoal.";
        this._comment_ENABLE_JUNGLE_SUSTAINABILITY = "Increase the drop rate of Jungle Saplings from Jungle Leaves.";
        this._comment_ENABLE_BONE_MEAL_TWEAK = "Enable using Bone Meal on dirt to turn it into grass or mycelium.";
        this._comment_REQUIRE_NEIGHBOR_SOURCE = "If true, requires at least one matching grass/mycelium block in a 3x3x3 area.";
        this._comment_ALLOW_ALL_DIRT_TYPES = "If true, allows Bone Meal to work on coarse dirt, rooted dirt. If false, only normal dirt.";
        this._comment_ENABLE_AUTO_LEAVES_DECAY = "Make leaves decay instantly when a tree is cut down using Timber.";
        this._comment_MAX_LOG_HORIZONTAL_RADIUS = "Maximum horizontal distance (X/Z axis) to search for connected logs. NOT RECOMMENT TO CHANGE [Default: 5]";
        this._comment_MAX_LEAF_DISTANCE = "Maximum distance from the log to search for connected leaves (Vanilla default). NOT RECOMMENT TO CHANGE [Default: 7]";
        this._comment_MIN_LEAVES_FOR_TREE = "Minimum number of connected leaves required to validate a valid tree. Acts as a safety check for player houses. [Default: 4]";
        this._comment_DECAY_THRESHOLD = "Distance threshold for leaf decay. At 7 (Vanilla), leaves decay normally when completely disconnected. Lower values (1-6) force leaves to decay closer to the log. [Default: 6]";

        // DỰ PHÒNG LỖI PHẠM VI TOÁN HỌC (Out of Bounds): Khống chế bán kính quét khối gỗ từ 1 đến 15 khối.
        // Nếu đặt số âm hoặc số quá lớn (Ví dụ: 99999), thuật toán tìm kiếm đệ quy sẽ làm tràn bộ nhớ đệm máy chủ và sập game ngay lập tức.
        if (this.MAX_LOG_HORIZONTAL_RADIUS < 1 || this.MAX_LOG_HORIZONTAL_RADIUS > 15) {
            LOGGER.error("Invalid value for 'MAX_LOG_HORIZONTAL_RADIUS' ({}). Must be between 1 and 15. Resetting to default: 5", this.MAX_LOG_HORIZONTAL_RADIUS);
            this.MAX_LOG_HORIZONTAL_RADIUS = 5; // Ép chỉ số lỗi quay về giá trị an toàn mặc định
        }

        // Khống chế khoảng cách quét khối lá cây từ 1 đến 15 khối để giữ hiệu năng CPU ổn định khi chặt cây lớn
        if (this.MAX_LEAF_DISTANCE < 1 || this.MAX_LEAF_DISTANCE > 15) {
            LOGGER.error("Invalid value for 'MAX_LEAF_DISTANCE' ({}). Must be between 1 and 15. Resetting to default: 7", this.MAX_LEAF_DISTANCE);
            this.MAX_LEAF_DISTANCE = 7;
        }

        // Chặn lỗi số âm đối với số lượng lá tối thiểu yêu cầu để nhận diện một cây tự nhiên
        if (this.MIN_LEAVES_FOR_TREE < 0) {
            LOGGER.error("Invalid value for 'MIN_LEAVES_FOR_TREE' ({}). Cannot be negative. Resetting to default: 4", this.MIN_LEAVES_FOR_TREE);
            this.MIN_LEAVES_FOR_TREE = 4;
        }

        // DỰ PHÒNG CHO ĐỊNH DẠNG MINECRAFT BLOCKSTATE: Thuộc tính distance của lá cây nguyên bản Minecraft chỉ chạy từ 1 đến 7.
        // Bất kỳ con số nào nằm ngoài khoảng [1-7] này khi truyền vào BlockState sẽ lập tức ném lỗi IllegalArgumentException và gây crash thế giới chơi game.
        if (this.DECAY_THRESHOLD < 1 || this.DECAY_THRESHOLD > 7) {
            LOGGER.error("Invalid value for 'DECAY_THRESHOLD' ({}). Must be between 1 and 7. Resetting to default: 6", this.DECAY_THRESHOLD);
            this.DECAY_THRESHOLD = 6;
        }
    }
}