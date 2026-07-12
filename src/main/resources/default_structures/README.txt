Place any default .nbt schematics in this folder. When the mod launches, it will automatically copy these files into the server's `config/cdde/structures` directory if they do not already exist.

### Custom Palette Blocks
When building structures in Creative, use the following CDDE-specific blocks:
- **Palette A / B / C / D**: Will be swapped dynamically with the dungeon theme blocks.
- **Hazard**: Replaced with either lava, water, or void depending on the dungeon layout.
- **Treasure Door**: A door that can be opened using a Treasure Key item. Connected blocks are destroyed together.

### Spawning Blocks (Invisible on load)
These blocks are removed when a dungeon generates and are used to record spawn positions:
- **Pokemon Spawn Block**: Used to define spawn positions for normal Pokémon inside a room.
- **Item Spawn Block**: Defines the location for standard floor loot/items.
- **Treasure Spawn Block**: Similar to Item Spawn, but meant for high-value configurable treasure loot.
- **Boss Spawn Block**: Defines the main spawn point for a Boss on boss floors.
- **Minion Spawn Block**: Defines spawn points for Minions on boss floors.
