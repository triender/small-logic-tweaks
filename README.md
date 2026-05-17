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

## Design Philosophy

Core principle:

> Improve interaction flow without removing gameplay effort.

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

### Hydro-Hardening
- Water Bottles harden Concrete Powder directly into Concrete

### Magma Kitchen
- dropped food can cook on Magma Blocks
- unattended food can burn

### Sawmill
- less wasteful wood processing
- improved decorative workflow support
- potential future Fletching Table integration

### Sniffer: Archeological Flora
- Sniffers can occasionally discover rare botanical relics

### Anvil: Structural Penalty
- durability repairs no longer increase `RepairCost`
- renaming no longer increases `RepairCost`
- enchantment upgrades still scale long-term cost

### Allay: Music & Memory
- rescued Allays remember first rescuer
- music can permanently anchor nearby Allays
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
