# Small Logic Tweaks

**Minecraft logic is weird. This mod makes it feel consistent—one interaction at a time.**

Small Logic Tweaks is a lightweight Vanilla+ Fabric mod focused on intuitive mechanics that align with existing player behavior, without introducing tech progression, automation chains, or progression skips.

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

## Design Philosophy

Core principle:

> Improve interaction flow without removing gameplay effort.

Interested in the architectural psychology and player behavior behind these changes? Read our full design treatise in [DESIGN.md](DESIGN.md).

Implementation constraints:
- manual systems over automation
- immersive interactions over excessive feature layering
- support for Vanilla play patterns, not replacement of progression

Tool identity is intentional:
- **Efficiency** for precision building/mining
- **Timber** for forestry-scale harvesting

## Compatibility

- Platform target: **Fabric**
- Style target: Vanilla-like gameplay
- Designed to be lightweight and minimally invasive
- Intended to coexist with most QoL and world-generation mods
- Multiplayer requires installation on both client and server for synchronized behavior

## Roadmap (Planned / Investigating)

The following concepts are being explored and may release in any order.

### Sawmill
- improves plank and byproduct yield relative to baseline crafting recipes
- improved decorative workflow support
- potential future Fletching Table integration

### Sniffer: Archaeological Flora
- Sniffers can occasionally uncover mod-specific rare botanical relic items/seeds

### Anvil: Structural Penalty
- durability repairs no longer increase `RepairCost`
- renaming no longer increases `RepairCost`
- enchantment upgrades still scale long-term cost

### Allay: Music & Memory
- freed Allays (e.g., released from captivity structures) persist the UUID of the first releasing/interacting player as their bond reference
- music interactions can register a persistent anchor location for nearby Allays
- duplicated Allays require new bonding

### Weightless Creatures
- lightweight hovering mobs no longer break Turtle Eggs

## Feedback & Contributions

Suggestions for additional logical Vanilla-style tweaks are welcome.

Please use the GitHub Issue Tracker for:
- bug reports
- balance concerns
- compatibility issues
- performance problems

## License

Licensed under the **MIT License**. See [LICENSE](LICENSE).
