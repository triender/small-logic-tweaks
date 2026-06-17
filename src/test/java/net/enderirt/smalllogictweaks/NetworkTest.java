package net.enderirt.smalllogictweaks;

import io.netty.buffer.Unpooled;
import net.enderirt.smalllogictweaks.network.ConfigSyncPayload;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NetworkTest {

    @Test
    public void testConfigSyncPayloadCodec() {
        // 1. Tạo chuỗi JSON giả lập chứa cấu hình
        String mockJson = "{\"MAX_LOG_HORIZONTAL_RADIUS\": 10, \"ENABLE_DEBUG_LOGS\": true}";
        ConfigSyncPayload originalPayload = new ConfigSyncPayload(mockJson);

        // 2. Khởi tạo một bộ đệm byte (ByteBuf) mô phỏng luồng dữ liệu mạng
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        // 3. Ghi dữ liệu vào bộ đệm (Mô phỏng hành vi Server đóng gói gửi đi)
        originalPayload.write(buf);

        // 4. Đọc dữ liệu từ bộ đệm (Mô phỏng hành vi Client nhận và giải mã)
        ConfigSyncPayload decodedPayload = new ConfigSyncPayload(buf);

        // 5. Khẳng định dữ liệu bảo toàn 100%
        Assertions.assertEquals(mockJson, decodedPayload.jsonConfig(),
                "Lỗi CODEC: Dữ liệu cấu hình bị biến dạng hoặc suy hao trong quá trình truyền tải mạng (Serialization/Deserialization failure).");
    }
}