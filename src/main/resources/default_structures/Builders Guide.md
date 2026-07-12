Cobblemon Dungeon — Structure Builder Guide
This guide details the specifications and design patterns required for building compatible structure templates (.nbt files) for the custom procedural dungeon engine.

1. The 7x7 Grid System
The dungeon layout generator operates on a 2D grid of super-cells. Each cell measures 7 × 7 blocks in the Minecraft world. Corridors, exits, and junctions are mapped exactly to the center of these 7x7 cell corridors to ensure zero-overlap and flawless connectivity.

2. Jigsaw Block Alignment & Positioning
Jigsaw blocks define the connection exits of rooms, hallways, and junctions. To connect properly, exits must align with the corridor centers:
The exit Jigsaw block must be placed in the exact middle of the 7-block wide entrance (which is the 4th block when counting from the corner of the 7x7 cell).<
Jigsaw Blocks are always set on the floor, they will be replaced with air afterwards.

3. Hallway sizes
Hallways can be either length. The Algorithm automatically chooses the best length to fill it's calculated paths.
To make sure we can always reach a connection, there should always be a 1-lenght hallway per theme.
(For 1-length Hallways we only use one Jigsaw Block. It will be used as two connectors in code).

4. Room Sizes & Entrances
To ensure rooms fit cleanly within the procedural generator:

7x7 Multiples: Rooms must have dimensions that are a direct multiple of the 7 x 7$ cell size (e.g., 7x7, 14x14, 21x21, etc.).
Entrances: Any entrance jigsaw must be aligned to the grid corridors (the perpendicular coordinate of the entrance jigsaw must satisfy the middle of the grid rule).

Build Exits only show where an exit is saved to place. The code doesn't choose a "fitting" room by exit count, but connects to exits or blocks them.
If you room has 4 exits, maybe only 2 will be connected and the others will be blocked, but hallways schould never open a "new" exit.

5. Jigsaw Settings in Minecraft
When placing Jigsaw blocks inside templates, configure them as follows:

Target Pool: cdde:connection
Name: cdde:door
Target: cdde:door
Turns into: minecraft:air
Orientation: Pointing OUTWARD from the structure (away from the room/hallway)

6. Template Categories & Naming
Dungeon templates are placed in config/cdde/structures and parsed by the mod based on naming prefixes:

Category	File Prefix	Description / Rules
Rooms	[theme]_room_*.nbt	Dimensions must be a multiple of 7x7 cells. Can contain multiple exits.
Hallways	[theme]_hallway_*.nbt	Hallways are auto-categorized by jigsaw count (2 = Straight/Corner, 3 = T-junction, 4 = Cross).
Ends	[theme]_end_*.nbt	Used to cap dead-end corridors and unused room exits. Must have exactly 1 jigsaw.

7. Palette Custom Blocks & Themes

A. Dynamic Palette Swap (Optional)
If you want your templates to dynamically change materials depending on the selected dungeon theme, build them using the custom CDDE mod blocks. The engine replaces these blocks at runtime with the dungeon's specific palette configuration:

cdde:palette_a $\rightarrow$ Primary Floor/Wall block
cdde:palette_b $\rightarrow$ Secondary Accent block
cdde:palette_c $\rightarrow$ Trim/Decorative block
cdde:palette_d $\rightarrow$ Pillar/Special block
cdde:hazard $\rightarrow$ Hazard block (Water, Lava, or Void depending on layout/theme)
cdde:treasure_door $\rightarrow$ Treasure Door block (Unlockable with a Treasure Key)

B. Standard Blocks (Default)
The use of palette blocks is entirely optional. You can build your templates with any standard vanilla Minecraft blocks (such as Stone Bricks, Spruce Wood, or Glass). Any vanilla blocks in the template will not be replaced and will render in the world exactly as they were built. This allows you to mix static themed areas with dynamic theme swapping.

8. Custom Spawn Blocks
When building dungeon templates, do not place vanilla entities (like chests or mobs) or standard Cobblemon spawners. Instead, place CDDE's custom utility blocks, which the engine replaces with actual gameplay elements at generation time:

Pokemon Spawn Block (cdde:pokemon_spawn):
Purpose: Marks the physical block position where a wild Pokémon should be spawned.
Behavior: When the dungeon floor generates, the engine queries the active floor's JSON spawn table, selects a Pokémon, spawns it on top of this block, and replaces this marker block with air.
Item Spawn Block (cdde:item_spawn):
Purpose: Marks the physical block position where a standard floor ground item should be spawned.
Behavior: When the dungeon floor generates, the engine queries the active floor's JSON loot table, spawns standard items with a chance at this position, and replaces this marker block.

Treasure Spawn Block (cdde:treasure_spawn):
Purpose: Similar to Item Spawn, but used for high-value treasure loot.
Behavior: Uses a separate, configurable high-tier loot table. Always spawns exactly one premium item, and is replaced with air upon generation.

Boss Spawn Block (cdde:boss_spawn):
Purpose: Defines the primary spawn point for a Boss on a boss floor.
Behavior: Only one monster (the boss) will be spawned here upon entering the floor. Spawning logic reads from the boss configuration data.

Minion Spawn Block (cdde:minion_spawn):
Purpose: Defines spawn points for Minions alongside the Boss.
Behavior: Functions similarly to Boss Spawn but pulls from a distinct minion pool. All markers are cleared on generation.

9. Saving with Structure Blocks
To export your built rooms and hallways into .nbt templates:

Alignment: Place the Structure Block at the corner of your template. Ensure the bounding box covers exactly the $7 \times 7$ grid bounds of the structure.
Settings:
Show Bounding Box: Enabled (verify the white outline encompasses the whole build).
Include Entities: Disabled (vanilla entities are ignored; place custom spawn blocks instead).
Saving: Save the structure, which exports it as an .nbt file under your world's generated/cdde/structures/ folder.
Mod Setup: Copy the exported .nbt file into the mod's configuration directory: config/cdde/structures/ using the correct file prefix (e.g. cave_room_hub.nbt).

