package net.enderirt.smalllogictweaks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.enderirt.smalllogictweaks.core.config.BaseModConfig;
import net.enderirt.smalllogictweaks.core.config.ConfigEntry;
import net.enderirt.smalllogictweaks.core.error.SltError;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

public class SmallLogicTweaksConfig extends BaseModConfig {
    // Khởi tạo Logger riêng cho phân hệ cấu hình để dễ dàng lọc log khi debug
    private static final Logger LOGGER = LoggerFactory.getLogger("SmallLogicTweaks/Config");

    // ==========================================
    // --- SYSTEM & DEBUG CONFIGURATION ---
    // ==========================================
    public String _comment_ENABLE_DEBUG_LOGS;
    @ConfigEntry(comment = "Enable or disable general debug logs for the mod.")
    public boolean ENABLE_DEBUG_LOGS = false;

    public String _comment_ENABLE_TIMBER_DEBUG_LOGS;
    @ConfigEntry(comment = "Enable or disable technical logs for the tree chopper feature.")
    public boolean ENABLE_TIMBER_DEBUG_LOGS = false;

    // ==========================================
    // --- DATA-DRIVEN TWEAKS CONFIGURATION ---
    // ==========================================
    public String _comment_ENABLE_CHARCOAL_TO_BLACK_DYE;
    @ConfigEntry(comment = "Allow crafting Black Dye directly from Charcoal.")
    public boolean ENABLE_CHARCOAL_TO_BLACK_DYE = true;

    public String _comment_ENABLE_COAL_TO_BLACK_DYE;
    @ConfigEntry(comment = "Allow crafting Black Dye directly from Coal.")
    public boolean ENABLE_COAL_TO_BLACK_DYE = false;

    public String _comment_ENABLE_JUNGLE_SUSTAINABILITY;
    @ConfigEntry(comment = "Increase the drop rate of Jungle Saplings from Jungle Leaves.")
    public boolean ENABLE_JUNGLE_SUSTAINABILITY = true;

    // ==========================================
    // --- BONE MEAL TWEAK CONFIGURATION ---
    // ==========================================
    public String _comment_ENABLE_BONE_MEAL_TWEAK;
    @ConfigEntry(comment = "Enable using Bone Meal on dirt to turn it into grass or mycelium.")
    public boolean ENABLE_BONE_MEAL_TWEAK = true;

    public String _comment_REQUIRE_NEIGHBOR_SOURCE;
    @ConfigEntry(comment = "If true, requires at least one matching grass/mycelium block in a 3x3x3 area.")
    public boolean REQUIRE_NEIGHBOR_SOURCE = false;

    public String _comment_ALLOW_ALL_DIRT_TYPES;
    @ConfigEntry(comment = "If true, allows Bone Meal to work on coarse dirt, rooted dirt. If false, only normal dirt.")
    public boolean ALLOW_ALL_DIRT_TYPES = false;

    // ==========================================
    // --- TIMBER TWEAK CONFIGURATION ---
    // ==========================================
    public String _comment_ENABLE_TIMBER_TWEAK;
    @ConfigEntry(comment = "Master switch for Timber feature.")
    public boolean ENABLE_TIMBER_TWEAK = true;

    public String _comment_ENABLE_AUTO_LEAVES_DECAY;
    @ConfigEntry(comment = "Make leaves decay instantly when a tree is cut down using Timber.")
    public boolean ENABLE_AUTO_LEAVES_DECAY = true;

    public String _comment_MAX_LOG_HORIZONTAL_RADIUS;
    @ConfigEntry(comment = "Maximum horizontal distance (X/Z axis) to search for connected logs. NOT RECOMMENT TO CHANGE [Default: 5]", minInt = 1, maxInt = 8, defaultInt = 5)
    public int MAX_LOG_HORIZONTAL_RADIUS = 5;

    public String _comment_MAX_LEAF_DISTANCE;
    @ConfigEntry(comment = "Maximum distance from the log to search for connected leaves (Vanilla default). NOT RECOMMENT TO CHANGE [Default: 7]", minInt = 1, maxInt = 15, defaultInt = 7)
    public int MAX_LEAF_DISTANCE = 7;

    public String _comment_MIN_LEAVES_FOR_TREE;
    @ConfigEntry(comment = "Minimum number of connected leaves required to validate a valid tree. Acts as a safety check for player houses. [Default: 4]", minInt = 1, maxInt = 100, defaultInt = 4)
    public int MIN_LEAVES_FOR_TREE = 4;

    public String _comment_DECAY_THRESHOLD;
    @ConfigEntry(comment = "Distance threshold for leaf decay. At 7 (Vanilla), leaves decay normally when completely disconnected. Lower values (1-6) force leaves to decay closer to the log. [Default: 6]", minInt = 1, maxInt = 7, defaultInt = 6)
    public int DECAY_THRESHOLD = 6;

    // ==========================================
    // --- POISONOUS POTATO TWEAK CONFIGURATION ---
    // ==========================================
    public String _comment_ENABLE_POISONOUS_POTATO_COMPOST;
    @ConfigEntry(comment = "Enable using Poisonous potato with Composter")
    public boolean ENABLE_POISONOUS_POTATO_COMPOST = true;

    public String _comment_ENABLE_POISONOUS_POTATO_BREWING;
    @ConfigEntry(comment = "Enable using Poisonous potato to make potion of poison")
    public boolean ENABLE_POISONOUS_POTATO_BREWING = true;

    // ==========================================
    // --- HYDRO-HARDENING TWEAK CONFIGURATION ---
    // ==========================================
    public String _comment_ENABLE_HYDRO_HARDENING;
    @ConfigEntry(comment = "Enable using Water Bottles to instantly harden Concrete Powder.")
    public boolean ENABLE_HYDRO_HARDENING = true;

    public String _comment_ENABLE_SPLASH_HARDENING;
    @ConfigEntry(comment = "Enable thrown splash water bottles to harden concrete powder in a 3x3x3 area.")
    public boolean ENABLE_SPLASH_HARDENING = true;

    // ==========================================
    // --- PICKAXE DIRT REVERSION CONFIGURATION ---
    // ==========================================
    public String _comment_ENABLE_PICKAXE_DIRT_REVERSION;
    @ConfigEntry(comment = "Enable right-clicking Farmland or Dirt Path with a Pickaxe to revert it into normal Dirt.")
    public boolean ENABLE_PICKAXE_DIRT_REVERSION = true;

    // ==========================================
    // --- PHANTOM TWEAK CONFIGURATION ---
    // ==========================================
    public String _comment_ENABLE_END_PHANTOM;
    @ConfigEntry(comment = "Enable spawn phantom in the end instead of Overworld")
    public boolean ENABLE_END_PHANTOM = true;

    public String _comment_PHANTOM_SPAWN_CHECK_INTERVAL;
    @ConfigEntry(comment = "How often (in ticks) the game checks to spawn Phantoms for each player in The End. [Default: 1200 / 1 minute]", minInt = 20, maxInt = 12000, defaultInt = 1200)
    public int PHANTOM_CHECK_COOLDOWN = 1200;

    public String _comment_PHANTOM_THRESHOLD_PRE_ELYTRA;
    @ConfigEntry(comment = "The amount of ticks a player must stay awake (insomnia) before Phantoms can spawn. Before obtain Elytra [Default: 144000 / 6 in-game days]", minInt = 1, maxInt = 2400000, defaultInt = 144000)
    public int PHANTOM_THRESHOLD_PRE_ELYTRA = 144000;

    public String _comment_PHANTOM_THRESHOLD_POST_ELYTRA;
    @ConfigEntry(comment = "The amount of ticks a player must stay awake (insomnia) before Phantoms can spawn. After obtain Elytra [Default: 72000 / 3 in-game days]", minInt = 1, maxInt = 2400000, defaultInt = 72000)
    public int PHANTOM_THRESHOLD_POST_ELYTRA = 72000;

    public String _comment_PHANTOM_MIN_COUNT;
    @ConfigEntry(comment = "The minimum number of Phantoms that can spawn in a single wave. [Default: 1]", minInt = 1, maxInt = 100, defaultInt = 1)
    public int PHANTOM_MIN_COUNT = 1;

    public String _comment_PHANTOM_MAX_COUNT;
    @ConfigEntry(comment = "The maximum number of Phantoms that can spawn in a single wave. [Default: 4]", minInt = 1, maxInt = 100, defaultInt = 4)
    public int PHANTOM_MAX_COUNT = 4;

    public String _comment_PHANTOM_MIN_SPAWN_HEIGHT;
    @ConfigEntry(comment = "The minimum height (in blocks) above the player where Phantoms will spawn. [Default: 20]", minInt = 0, maxInt = 320, defaultInt = 20)
    public int PHANTOM_MIN_SPAWN_HEIGHT = 20;

    public String _comment_PHANTOM_MAX_SPAWN_HEIGHT;
    @ConfigEntry(comment = "The maximum height (in blocks) above the player where Phantoms will spawn. [Default: 35]", minInt = 0, maxInt = 320, defaultInt = 35)
    public int PHANTOM_MAX_SPAWN_HEIGHT = 35;

    public String _comment_PHANTOM_MOB_CAP;
    @ConfigEntry(comment = "The maximum number of Phantoms that can exist at one time. [Default: 8]", minInt = 1, maxInt = 100, defaultInt = 8)
    public int PHANTOM_MOB_CAP = 8;

    // ==========================================
    // --- AESTHETIC KITCHEN TWEAK CONFIGURATION ---
    // ==========================================
    public String _comment_ENABLE_AESTHETIC_KITCHEN;
    @ConfigEntry(comment = "Master switch for the Aesthetic Kitchen system.")
    public boolean ENABLE_AESTHETIC_KITCHEN = true;

    public String _comment_ENABLE_DRY_ROASTING;
    @ConfigEntry(comment = "Allow roasting raw food on covered stoves (magma, fire, lava).")
    public boolean ENABLE_DRY_ROASTING = true;

    public String _comment_ENABLE_CAULDRON_BOILING;
    @ConfigEntry(comment = "Allow boiling raw food in cauldrons with heat source underneath.")
    public boolean ENABLE_CAULDRON_BOILING = true;

    public String _comment_DEFAULT_COOK_TIME;
    @ConfigEntry(comment = "Default cooking time (in ticks) for items in the magma_cookable tag that are not explicitly defined in magmaCookTimes.", minInt = 1, maxInt = 72000, defaultInt = 240)
    public int DEFAULT_COOK_TIME = 240;

    public String _comment_magmaCookTimes;
    @ConfigEntry(comment = "Custom cooking times (in ticks) for raw foods cooked on heat sources.")
    public Map<String, Integer> magmaCookTimes = new java.util.LinkedHashMap<>();
    {
        magmaCookTimes.put("minecraft:kelp", 120);
        magmaCookTimes.put("minecraft:potato", 240);
        magmaCookTimes.put("minecraft:cod", 240);
        magmaCookTimes.put("minecraft:salmon", 240);
        magmaCookTimes.put("minecraft:beef", 360);
        magmaCookTimes.put("minecraft:porkchop", 360);
        magmaCookTimes.put("minecraft:chicken", 360);
        magmaCookTimes.put("minecraft:mutton", 360);
        magmaCookTimes.put("minecraft:rabbit", 360);
    }

    public String _comment_aestheticCookResults;
    @ConfigEntry(comment = "Custom cooking results mapping (raw item ID -> cooked item ID) for Aesthetic Kitchen.")
    public Map<String, String> aestheticCookResults = new java.util.LinkedHashMap<>();

    public int getCookTime(net.minecraft.world.item.Item item) {
        String key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
        if (this.magmaCookTimes != null && this.magmaCookTimes.containsKey(key)) {
            return this.magmaCookTimes.get(key);
        }
        return this.DEFAULT_COOK_TIME;
    }

    public static net.minecraft.world.item.ItemStack getCookedResult(net.minecraft.world.level.Level level, net.minecraft.world.item.ItemStack rawStack) {
        return net.enderirt.smalllogictweaks.util.KitchenHelper.getCookedResult(level, rawStack);
    }

    // ==========================================
    // --- SYSTEM CORE CONFIGURATION MANAGEMENT ---
    // ==========================================
    public SmallLogicTweaksConfig() {
        validate();
    }

    // Cấu hình vật lý: Chỉ dùng để lưu/đọc file trên ổ cứng cục bộ
    public static volatile SmallLogicTweaksConfig LOCAL_INSTANCE = new SmallLogicTweaksConfig();

    // Cấu hình RAM: Thực thể trực tiếp quyết định luật chơi trong thời gian thực
    public static volatile SmallLogicTweaksConfig ACTIVE_INSTANCE = new SmallLogicTweaksConfig();

    // Khởi tạo bộ dựng Gson với tính năng Pretty Printing để tệp JSON tự động xuống dòng thụt lề đẹp mắt
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static File getConfigFile() {
        return new File(FabricLoader.getInstance().getConfigDir().toFile(), "small_logic_tweaks.json");
    }

    public static void load() {
        LOCAL_INSTANCE = loadOrCreate(
                getConfigFile(),
                SmallLogicTweaksConfig.class,
                SmallLogicTweaksConfig::new,
                GSON,
                LOGGER,
                SltError.CFG_CORRUPTED,
                SltError.CFG_SAVE_FAILED
        );
        ACTIVE_INSTANCE = LOCAL_INSTANCE;
    }

    public static void save() {
        saveAtomically(getConfigFile(), LOCAL_INSTANCE, GSON, LOGGER, SltError.CFG_SAVE_FAILED);
        if (ACTIVE_INSTANCE.ENABLE_DEBUG_LOGS) {
            LOGGER.info("Successfully saved config file securely to: {}", getConfigFile().getAbsolutePath());
        }
    }

    @Override
    public void validate() {
        // Tự động chuẩn hóa toàn bộ các trường có chú thích @ConfigEntry (khôi phục comment và kiểm tra biên số nguyên)
        this.validateAnnotatedFields(LOGGER, SltError.CFG_OUT_OF_BOUNDS);

        // Xử lý các ràng buộc chéo phụ thuộc giữa nhiều biến (Cross-field dependencies)
        if (this.PHANTOM_MAX_COUNT < this.PHANTOM_MIN_COUNT || this.PHANTOM_MAX_COUNT > 100) {
            SltError.CFG_OUT_OF_BOUNDS.logError(LOGGER, "PHANTOM_MAX_COUNT", this.PHANTOM_MAX_COUNT, this.PHANTOM_MIN_COUNT, 100, this.PHANTOM_MIN_COUNT);
            this.PHANTOM_MAX_COUNT = this.PHANTOM_MIN_COUNT;
        }

        if (this.PHANTOM_MAX_SPAWN_HEIGHT < this.PHANTOM_MIN_SPAWN_HEIGHT || this.PHANTOM_MAX_SPAWN_HEIGHT > 320) {
            SltError.CFG_OUT_OF_BOUNDS.logError(LOGGER, "PHANTOM_MAX_SPAWN_HEIGHT", this.PHANTOM_MAX_SPAWN_HEIGHT, this.PHANTOM_MIN_SPAWN_HEIGHT, 320, this.PHANTOM_MIN_SPAWN_HEIGHT);
            this.PHANTOM_MAX_SPAWN_HEIGHT = this.PHANTOM_MIN_SPAWN_HEIGHT;
        }
    }

    public boolean fallbackFailsafe(SmallLogicTweaksConfig rawReceived) {
        if (rawReceived == null) return false;
        boolean tampered = false;

        // 1. Chạy bộ lọc chuẩn hóa giá trị hiện tại
        this.validate();

        // 2. Kiểm tra chéo: Tính năng TIMBER
        if (this.MAX_LOG_HORIZONTAL_RADIUS != rawReceived.MAX_LOG_HORIZONTAL_RADIUS ||
                this.MAX_LEAF_DISTANCE != rawReceived.MAX_LEAF_DISTANCE ||
                this.MIN_LEAVES_FOR_TREE != rawReceived.MIN_LEAVES_FOR_TREE ||
                this.DECAY_THRESHOLD != rawReceived.DECAY_THRESHOLD) {

            // Cấu hình Timber bị hỏng/độc hại -> Tắt hoàn toàn ở Client
            this.ENABLE_TIMBER_TWEAK = false;
            tampered = true;
            SltError.NET_TAMPERED_PAYLOAD.logWarn(LOGGER, "TIMBER");
        }

        // 3. Kiểm tra chéo: Tính năng PHANTOM
        if (this.PHANTOM_MOB_CAP != rawReceived.PHANTOM_MOB_CAP ||
                this.PHANTOM_MIN_COUNT != rawReceived.PHANTOM_MIN_COUNT ||
                this.PHANTOM_MAX_COUNT != rawReceived.PHANTOM_MAX_COUNT ||
                this.PHANTOM_CHECK_COOLDOWN != rawReceived.PHANTOM_CHECK_COOLDOWN ||
                this.PHANTOM_THRESHOLD_PRE_ELYTRA != rawReceived.PHANTOM_THRESHOLD_PRE_ELYTRA ||
                this.PHANTOM_THRESHOLD_POST_ELYTRA != rawReceived.PHANTOM_THRESHOLD_POST_ELYTRA ||
                this.PHANTOM_MIN_SPAWN_HEIGHT != rawReceived.PHANTOM_MIN_SPAWN_HEIGHT ||
                this.PHANTOM_MAX_SPAWN_HEIGHT != rawReceived.PHANTOM_MAX_SPAWN_HEIGHT) {

            this.ENABLE_END_PHANTOM = false;
            tampered = true;
            SltError.NET_TAMPERED_PAYLOAD.logWarn(LOGGER, "PHANTOM");
        }

        return tampered;
    }
}
