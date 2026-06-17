# 🚀 First Beta (Version 1.0.0)

Welcome to the 1.0.0 release of Small Logic Tweaks! This mod is dedicated to refining foundational survival mechanics through simple and intuitive improvements to the vanilla experience.

### ✨ Key Features

**🦴 Bone Meal Logic:**
* **Dirt Conversion:** Apply Bone Meal to Dirt blocks to instantly convert them into Grass Blocks.
* **Biome Awareness:** Automatically generates Mycelium instead of Grass when used within Mushroom Fields biomes.

**🪓 Timber Enchantment:**
* **Tree Felling:** A specialized enchantment for axes that allows you to fell entire trees by breaking a single base block.
* **Progressive Levels:** 3 levels available. Block limits are set at 16, 64, and 128 blocks respectively.
* **Combat Potency:** Each level grants +1.0, +1.5, or +2.0 bonus Attack Damage.
* **Intelligent Safety:** The algorithm specifically targets natural leaves (persistent=false) to protect your house and decorative wooden structures.
* **Precision Control:** Hold Shift to bypass the enchantment and mine blocks one by one.

### ⚙️ Technical & Balance Notes

* **Vanilla Balance:** Timber is mutually exclusive with the Efficiency enchantment to maintain game progression.
* **Performance:** Optimized leaf-processing logic is included; however, expect minor MSPT spikes when felling massive trees at Level III.
* **Requirement:** This mod must be installed on both the Client and Server.

**Status:** Early Beta. Feedback is welcome!

---
# 🍂 Leaf Destruction Update (Version 1.1.0)

Welcome to the 1.1.0 release of Small Logic Tweaks! This update seamlessly integrates leaf destruction into the Timber mechanics, making tree felling much more efficient for everyday survival.

### ✨ Key Features

**🍂 Integrated Leaf Destruction:**
* **Instant Leaf Decay:** Leaves belonging to felled trees are now instantly destroyed alongside the logs, dropping saplings and apples immediately.
* **Smart Canopy Protection:** Employs an intelligent distance-simulation algorithm to accurately detect and protect leaves that are still connected to or shared with neighboring trees.

### ⚙️ Technical & Balance Notes

* **Timber Balance:** Increased the Level III block limit from 128 to 140 to fully support massive mega-trees, such as Mega Jungle and Mega Spruce variants.

**Status:** Beta. Feedback is welcome!

---
# 🌑 Charcoal, 🌴 Jungle & ⚙️ Config Update (Version 1.2.0)

Welcome to the 1.2.0 release of Small Logic Tweaks! This update adds new logic tweaks for charcoal and jungle trees, expands bone meal customization, along with a configurable settings file.

### ✨ Key Features

**🌑 Charcoal & Jungle Tweaks:**
* **Charcoal Dye:** Charcoal can now be crafted directly into Black Dye to give it a unique practical use.
* **Jungle Sustainability:** Slightly increased the drop rate of Jungle Saplings from Jungle Leaves to help keep the biome renewable.

### ⚙️ Technical & Balance Notes

**⚙️ Configuration System:**
* **Config File:** Added `small_logic_tweaks.json` to let you toggle individual features and adjust values like search radius or decay thresholds.
* **Bone Meal Options:** Added config settings to require a nearby spreading source block before allowing conversion (default: false) and to expand conversion logic to all blocks within the Dirt Tag (default: false).
* **Error Handling:** The system automatically handles formatting mistakes, case-insensitivity, and missing text to avoid crashes.

**Status:** Beta. Feedback is welcome!

---
# 🥔 Poisonous Potato & 💧 Hydro Hardening Update (Version 1.3.0)

Welcome to the 1.3 release of Small Logic Tweaks! This update introduces exciting new uses for the Poisonous Potato and a concrete hardening mechanic, alongside some neat under-the-hood polish.

### ✨ Key Features

**💧 Hydro Hardening:**
* **Water Bottle Interaction:** Right-click on Concrete Powder with a Water Bottle to instantly harden it into solid Concrete. You will get an empty Glass Bottle back.
* **Splash Potion AOE:** Throwing a Splash Water Bottle will harden Concrete Powder in a localized area!
    * *Realistic Splash:* The block directly hit is guaranteed to harden. Surrounding blocks have a 40% chance to get wet and harden, creating a natural splash effect.
    * *Smart Physics:* Water will no longer unrealistically phase through solid walls.

**🥔 Poisonous Potato Tweaks:**
* **Composting:** You can now throw Poisonous Potatoes into a Composter (65% chance to increase the compost level).
* **Brewing:** Brew it with an Awkward Potion to create a Potion of Poison. Finally, a practical use for it!

### ⚙️ Technical & Fixes

* **Timber Config Sync:** The Timber mechanic now correctly reads and applies your custom limits (like search radius and decay threshold) from the `small_logic_tweaks.json` config file.
* **Under-the-hood Polish:** Optimized internal block-checking mechanics for smoother server performance.

**Status:** Beta. Feedback is welcome!

---
# 🌌 Void Insomnia & 🛡️ Network Security Update (Version 1.4.0)

Welcome to the 1.4.0 release of Small Logic Tweaks! This update introduces a complete relogic to Phantom spawning, optimizes Timber's underlying performance, and implements rigorous server-client configuration security.

### ✨ Key Features

**🌌 Void Insomnia:**
* **Dimension Shift:** Phantoms will no longer terrorize the Overworld. They now exclusively spawn in The End dimension to challenge players navigating the void.
* **Dynamic Insomnia Thresholds:** Spawning logic now intelligently adapts to your game progression, featuring separate insomnia thresholds based on whether you have acquired an Elytra.
* **Full Customization:** Phantom mob caps, spawn heights, minimum/maximum pack sizes, and check cooldowns are now fully adjustable via the configuration file.

**🪓 Timber Optimization:**
* **Performance Boost:** Optimized internal block calculations to prevent lag and maintain smooth performance when felling massive trees.

### ⚙️ Technical & Security

* **Network Failsafe & Sync:** The server now strictly enforces and synchronizes its local configuration on all connected clients. A new intelligent client-side failsafe automatically disables features if corrupted or malicious out-of-bounds values are detected.
* **Bulletproof Config Management:** Enhanced the configuration engine with atomic saving (`.tmp` file swapping) to completely prevent data corruption, automated boundary correction for invalid inputs, and strict file size limits to guard against large file DOS attacks.
* **Coal to Black Dye Recipe:** Added a new configuration toggle (`ENABLE_COAL_TO_BLACK_DYE`, default: false) alongside a dynamic resource condition. This allows server owners to enable crafting Black Dye directly from Coal without leaving orphaned recipes in the game's registry when disabled.

**Status:** Beta. Feedback is welcome!

---
# 🐛 Hotfix (Version 1.4.1)

Welcome to the 1.4.1 patch release of Small Logic Tweaks! This minor update addresses loot table drops for Jungle Leaves and enhances GameTest automated testing reliability.

### ✨ Key Fixes

**🌴 Jungle Leaves Loot Table:**
* **Standard Drops Restored:** Fixed an issue where shears, Silk Touch, and sticks were not working correctly with Jungle Leaves. You can now get the leaves themselves and standard stick drops again.

**🧪 Test Stability:**
* **Deterministic GameTests:** Implemented a failsafe for Mock Players in the GameTest framework to guarantee stable phantom spawning logic, resolving test instability caused by random ticks.

**Status:** Stable.