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

Target Pool: cde:connection
Name: cde:door
Target: cde:door
Turns into: minecraft:air
Orientation: Pointing OUTWARD from the structure (away from the room/hallway)

6. Template Categories & Naming
Dungeon templates are placed in config/cde/structures and parsed by the mod based on naming prefixes:

Category	File Prefix	Description / Rules
Rooms	[theme]_room_*.nbt	Dimensions must be a multiple of 7x7 cells. Can contain multiple exits.
Hallways	[theme]_hallway_*.nbt	Hallways are auto-categorized by jigsaw count (2 = Straight/Corner, 3 = T-junction, 4 = Cross).
Ends	[theme]_end_*.nbt	Used to cap dead-end corridors and unused room exits. Must have exactly 1 jigsaw.

Please make sure that themes don't contain the words "room", "hallway" or "end" or the engine might be confused.

7. Palette Custom Blocks & Themes

A. Dynamic Palette Swap (Optional)
If you want your templates to dynamically change materials depending on the selected dungeon theme, build them using the custom cde mod blocks. The engine replaces these blocks at runtime with the dungeon's specific palette configuration:

cde:palette_a $\rightarrow$ Primary Floor/Wall block
cde:palette_b $\rightarrow$ Secondary Accent block
cde:palette_c $\rightarrow$ Trim/Decorative block
cde:palette_d $\rightarrow$ Pillar/Special block
cde:hazard $\rightarrow$ Hazard block (Water, Lava, or Void depending on layout/theme)

B. Standard Blocks (Default)
The use of palette blocks is entirely optional. You can build your templates with any standard vanilla Minecraft blocks (such as Stone Bricks, Spruce Wood, or Glass). Any vanilla blocks in the template will not be replaced and will render in the world exactly as they were built. This allows you to mix static themed areas with dynamic theme swapping.

C. Door Blocks
cde implements custom door blocks for builders to use.

cde:treasure_door $\rightarrow$ Treasure Door block (Unlockable with a Treasure Key)
cde:boss_door $\rightarrow$ Boss Door block (Opens when approaching, only unlocks once all enemies on the floor are defeated)

8. Custom Spawn Blocks
When building dungeon templates, do not place vanilla entities (like chests or mobs) or standard Cobblemon spawners. Instead, place cde's custom utility blocks, which the engine replaces with actual gameplay elements at generation time:

Pokemon Spawn Block (cde:pokemon_spawn):
Purpose: Marks the physical block position where a wild Pokémon should be spawned.
Behavior: When the dungeon floor generates, the engine queries the active floor's JSON spawn table, selects a Pokémon, spawns it on top of this block, and replaces this marker block with air.

Item Spawn Block (cde:item_spawn):
Purpose: Marks the physical block position where a standard floor ground item should be spawned.
Behavior: When the dungeon floor generates, the engine queries the active floor's JSON loot table, spawns standard items with a chance at this position, and replaces this marker block.

Treasure Spawn Block (cde:treasure_spawn):
Purpose: Similar to Item Spawn, but used for high-value treasure loot.
Behavior: Uses a separate, configurable high-tier loot table. Always spawns exactly one premium item, and is replaced with air upon generation.

Boss Spawn Block (cde:boss_spawn):
Purpose: Defines the primary spawn point for a Boss on a boss floor.
Behavior: Only one monster (the boss) will be spawned here upon entering the floor. Spawning logic reads from the boss configuration data.

Minion Spawn Block (cde:minion_spawn):
Purpose: Defines spawn points for Minions alongside the Boss.
Behavior: Functions similarly to Boss Spawn but pulls from a distinct minion pool. All markers are cleared on generation.

Player Spawn Block (cde:player_spawn):
Purpose: Defines where players start when entering the floor.
Behavior: The generator will prioritize these blocks over random floor spots. If placed, players will be teleported here instead of a random location. Replaced with air upon generation.

End Stair Spawn Block (cde:end_stair_spawn):
Purpose: Marks the location where the final exit stairs should appear on the End Floor.
Behavior: Replaced by actual functional stairs that allow the player to complete the dungeon successfully. If missing, stairs generate randomly.

9. Saving with Structure Blocks
To export your built rooms and hallways into .nbt templates:

Alignment: Place the Structure Block at the corner of your template. Ensure the bounding box covers exactly the 7x7 grid bounds of the structure.
Settings:
Show Bounding Box: Enabled (verify the white outline encompasses the whole build).
Include Entities: Disabled (vanilla entities are ignored; place custom spawn blocks instead).
Saving: Save the structure, which exports it as an .nbt file under your world's generated/cde/structures/ folder.
Mod Setup: Copy the exported .nbt file into the mod's configuration directory: config/cde/structures/ using the correct file prefix (e.g. cave_room_hub.nbt).

10. Boss Rooms & Treasure Rooms (End floors)
Boss floors and Treasure floors are generated using the exact same room-placement algorithm as regular floors. To ensure the generator picks the correct, unique room for a boss or treasure floor:
- Unique Theme: You must create a dedicated "theme" specifically for these floors (e.g., `boss_arena_1` or `treasure_vault`). 
- File Naming: Name your boss room template `boss_arena_1_room_01.nbt`. 
- Config Selection: In the Dungeon Editor UI, when configuring the Boss Floor or End Floor, select your dedicated theme (e.g., `boss_arena_1`) for that floor. The generator will then exclusively use rooms with that theme, effectively ensuring your custom Boss/Treasure room is placed.
- Components: Boss rooms should typically include `cde:boss_spawn` and `cde:minion_spawn` blocks. Treasure rooms should include `cde:treasure_spawn` blocks and the `cde:end_stair_spawn` block for the final exit. 

Remember: If a Boss or Treasure theme doesn't have an associated hallway template (e.g., `boss_arena_1_hallway...`), the generator will fail if it tries to spawn hallways. Keep the room count min/max settings tight (e.g. Min 1, Max 1) for these special floors to prevent the algorithm from attempting to place connecting hallways!
