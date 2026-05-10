# Small Logic Tweaks

Small Logic Tweaks is a minimalist quality-of-life modification that seamlessly refines foundational survival mechanics through intuitive environmental interactions and balanced, immersive resource-gathering systems.

## ✨ Features

### 🦴 Bone Meal Tweak
* **Environment Interaction:** Apply Bone Meal directly to Dirt blocks to convert them into Grass Blocks.
* **Biome Awareness:** Automatically generates Mycelium when used within Mushroom Fields biomes.
* **Vanilla Integration:** Features native sound effects and particle systems for a seamless feel.

### 🪓 Timber Enchantment
* **Mechanic:** A specialized Axe enchantment that allows players to fell entire trees by breaking a single block.
* **Harvest-Centric Logic:** Specifically designed for natural tree harvesting. The algorithm identifies a "tree" by detecting wood blocks in close proximity to **Natural Leaves** (blocks where `persistent=false`).
* **Progression:** Enhances both utility and combat potency across 3 levels:
    * **Level I:** 16-block limit, **+1.0** Attack Damage.
    * **Level II:** 64-block limit, **+1.5** Attack Damage.
    * **Level III:** 128-block limit, **+2.0** Attack Damage.
* **Balance & Safety:** * **Efficiency Trade-off:** Mutually exclusive with the Efficiency enchantment. This creates a natural "safety buffer"—since the tool is slow for manual mining, it is unsuitable (and discouraging) for precise construction tasks where an Efficiency axe is standard.
    * **Leaf Dependency:** The logic prioritizes natural leaf presence. **Caution:** If wooden structures are built directly adjacent to natural leaves (e.g., a treehouse), the mod will treat the structure as part of the tree.
    * **Control:** Hold `Shift` to bypass the enchantment and mine normally.

## ⚙️ Technical Information
* **Platform:** Fabric
* **Environment:** Required on both **Client** and **Server** for synchronized animations and logic.
* **Status:** Early Beta.

### ⚠️ Performance & Synchronization
* **Latency Sensitivity:** High-latency environments (high ping) may encounter minor "ghost blocks" or visual desync during extensive Timber operations.
* **Server Stress:** Processing a "Timber III" execution (up to 128 blocks) triggers a significant volume of simultaneous block updates. On high-population or low-end servers, this may result in temporary MSPT (milliseconds per tick) spikes.
* **Version Parity:** Both client and server must run the exact same mod version to ensure enchantment levels, block-breaking logic, and particle effects remain perfectly synchronized.

## 📜 License
This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

## 🤝 Contribution & Feedback
As a spontaneous passion project, suggestions for more logical tweaks are always welcome. Please report any issues or performance concerns via the GitHub Issue Tracker.

---
Note: Finally, an axe that understands that if it can "delete" a forest, it can certainly "delete" a skeleton. Balance restored.