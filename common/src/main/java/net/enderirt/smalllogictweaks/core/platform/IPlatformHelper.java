package net.enderirt.smalllogictweaks.core.platform;

import java.nio.file.Path;

/**
 * VI: Giao diện dịch vụ nền tảng (Platform Service Interface) trừu tượng hóa các tương tác với Modloader.
 * EN: Platform Service Interface abstracting interactions with specific Modloaders (Fabric, NeoForge, Forge).
 */
public interface IPlatformHelper {

    /**
     * VI: Lấy đường dẫn thư mục chứa cấu hình của game (thường là .minecraft/config).
     * EN: Returns the game's configuration directory path (usually .minecraft/config).
     *
     * @return VI: Đường dẫn thư mục config / EN: Path to config directory
     */
    Path getConfigDirectory();

    /**
     * VI: Kiểm tra xem một mod cụ thể có đang được tải hay không.
     * EN: Checks if a specific mod is currently loaded in the runtime.
     *
     * @param modId VI: Mã định danh mod / EN: Mod identifier
     * @return      VI: True nếu mod đã tải / EN: True if mod is loaded
     */
    boolean isModLoaded(String modId);
}
