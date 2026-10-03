# Small Logic Tweaks

**Minecraft logic is weird. This mod makes it feel consistent—one interaction at a time.**

Small Logic Tweaks is a lightweight Vanilla+ mod for Fabric and NeoForge focused on intuitive mechanics that align with existing player behavior, without introducing tech progression, automation chains, or progression skips.

## Project Goals

Vanilla Minecraft naturally encourages players to:
- restore terrain
- build utility spaces (kitchens, workshops)
- clear forests
- execute large-scale builds

This mod targets friction points where default mechanics feel unintuitive, while preserving Vanilla pacing and effort.

## Current Features

### Bone Meal: Instant Greenery

Use Bone Meal directly on **Dirt** to bootstrap natural surface recovery.

- **Instant Surface Conversion**: Dirt can be converted immediately to start natural spread.
- **Biome-Aware Output**: In Mushroom biomes, Dirt converts to **Mycelium** instead of Grass.
- **Vanilla-Consistent Feedback**: Uses native particles, sounds, and interaction flow.

Design intent: remove the awkward dependency on transporting Silk Touch grass blocks just to begin restoration.

### Dirt Cycle: Pickaxe Reversion

Right-click **Farmland** or **Dirt Path** with any **Pickaxe** to restore it back into standard **Dirt**.

- **Closed Soil Loop**: Complements the Bone Meal tweak by completing the lifecycle of soil (`Dirt` ↔ `Path` / `Farmland` ↔ `Grass`).
- **Intuitive Mechanical Parity**: Fills the right-click interaction gap for pickaxes, giving players a non-destructive way to forgive misclicks or decommission farm plots without breaking and re-placing blocks.
- **Vanilla-Standard Wear**: Costs 1 durability per use, respects the Unbreaking enchantment, and breaks the tool naturally when depleted.
- **Data-Driven Tag**: Fully customizable via the `#small_logic_tweaks:pickaxe_reversion` block tag.

### Timber

Tree-felling logic optimized for forestry workflows.

- Breaking the base log can fell the full connected natural tree.
- Targeting is constrained by natural leaf-state checks to reduce accidental structure destruction.
- Hold **Shift** to temporarily bypass Timber behavior for precise manual mining.
- Timber tools receive a modest combat bonus for long harvesting sessions.

Balance constraints:
- larger trees require proportionally longer clearing time
- Timber is intentionally incompatible with **Efficiency** to preserve precision-building tradeoffs
- large cuts may transiently increase server load

### Jungle Sustainability

Jungle biome recovery support via adjusted sapling economics.

- slightly improved Jungle Sapling drop behavior
- stronger long-term renewability after extensive deforestation

### Charcoal -> Black Dye

Introduces a practical identity for Charcoal.

- craft **Black Dye** directly from **Charcoal**
- keeps recipe scope simple and Vanilla-adjacent

### Hydro-Hardening

Instant concrete hydration through direct manual interaction.

- right-click **Concrete Powder** with a **Water Bottle** to instantly harden it into **Concrete** (returns an empty Glass Bottle)
- splash water bottles harden Concrete Powder in a localized 3x3x3 splash area with natural drop-off and line-of-sight awareness

### Aesthetic Kitchen

Physics-based, container-free cooking on functional heat sources.

- roast raw foods directly on heat sources (Magma Blocks, Fire, Lava), even when covered by Trapdoors, Pressure Plates, Slabs, or Carpets
- boil food stacks inside heated Water Cauldrons with sequential popping
- dynamic thermal simulation: insulation via wool carpets, linear heat decay when pushed away, and strict stove reservations to prevent mass-cooking exploits

### Void Insomnia

Dimension-aware Phantom ecology.

- Phantoms no longer disturb Overworld builders; they exclusively spawn in **The End** dimension
- dynamic insomnia scaling based on whether the player has acquired an Elytra

### Poisonous Potato Integration

Organic loop integration for otherwise dead-end agricultural waste.

- compost Poisonous Potatoes in the Composter at the standard 65% success rate
- brew with an Awkward Potion in the Brewing Stand to create Potion of Poison

### Anvil: Memory Preserve

Separating routine maintenance from magical progression.

- repairing tool durability with raw materials or like-items no longer increases Prior Work Penalty (`RepairCost`)
- renaming tools no longer increases `RepairCost`
- only applying new enchantments or upgrading enchantment tiers scales penalty cost

### Weightless Creatures (MC-248332)

Delicate footsteps for hovering and ethereal companions.

- floating, winged, and ethereal creatures (bees, allays, phantoms, ghasts, parrots, vexes) no longer trample or destroy Turtle Eggs
- fully customizable via the `#small_logic_tweaks:cannot_break_turtle_eggs` entity type tag

## Design Philosophy

Core principle:

> Improve interaction flow without removing gameplay effort.

Small Logic Tweaks focuses strictly on repairing local mechanical contradictions with minimal semantic surface area, preserving Vanilla pacing and natural player intent.

Implementation constraints:
- manual systems over automation
- immersive interactions over excessive feature layering
- support for Vanilla play patterns, not replacement of progression

Tool identity is intentional:
- **Efficiency** for precision building/mining
- **Timber** for forestry-scale harvesting

## Installation & Environment Compatibility

| Feature | Environment | Datapack compatible | Description |
| :--- | :--- | :---: | :--- |
| **Charcoal / Coal -> Black dye** | Server-only | Yes | Vanilla data-driven recipe JSON. |
| **Jungle leaf rebalancing** | Server-only | Yes | Vanilla loot table JSON. |
| **Poisonous potato brewing** | Server-only | Yes | Vanilla data-driven brewing recipe (26.3+). |
| **Poisonous potato composting** | Server-only | Partial | Handled server-side on composter interactions. |
| **Weightless creatures** | Server-only | Tag-driven | Server handles egg trampling via entity tags. |
| **Pickaxe soil reversion** | Server-only | No | Server converts blocks and decrements durability. |
| **Bone meal soil regeneration** | Server-only | No | Server converts dirt and triggers native particles. |
| **Hydro-hardening** | Server-only | No | Server transforms concrete powder on hydration. |
| **Aesthetic kitchen cooking** | Server-only | No | Physics-based cooking entirely tracked on the server. |
| **Void insomnia (The End)** | Server-only | No | Server tracks insomnia timer and phantom spawning. |
| **Anvil: Memory preserve** | Client & server | No | Recommended on client for seamless level UI sync. |
| **Timber** | Client & server | No | Required on client to sync progressive mining fatigue. |

> [!NOTE]
> Server-only features allow players with pure vanilla Minecraft clients to join and enjoy the tweaks without installing any client mods.

## Compatibility

- Platform targets: **Fabric** and **NeoForge** (separate downloads; pick the one matching your loader)
  - Fabric build requires Fabric API
  - Quilt: the Fabric build runs on Quilt Loader 0.31+ (currently beta); Quilt Loader 0.30.x is not supported
  - NeoForge build for Minecraft 26.3 is a beta while NeoForge 26.3 itself is in beta
- Style target: Vanilla-like gameplay
- Designed to be lightweight and minimally invasive
- Intended to coexist with most QoL and world-generation mods
- Multiplayer requires installation on both client and server for synchronized behavior (client and server must use the same loader)

## Roadmap (Planned / Investigating)

The following concepts are being explored and may release in any order.

### Sawmill
- improves plank and byproduct yield relative to baseline crafting recipes
- improved decorative workflow support
- potential future Fletching Table integration

### Acoustic Glass
- dampens sound and vibrations passing through while remaining visually transparent
- high-tier redstone utility using Echo Shards to eliminate bulky wool encasements

## Feedback & Contributions

Suggestions for additional logical Vanilla-style tweaks are welcome.

Please use the GitHub Issue Tracker for:
- bug reports
- balance concerns
- compatibility issues
- performance problems

## License

Licensed under the **MIT License**. See [LICENSE](LICENSE).
