# CDBE JSON Configuration Guide

This is the comprehensive documentation for creating and modifying Moves and Abilities in the Cobblemon Dungeon Battle Engine.

## Directory Structure
- `data/cdbe/moves/`: Contains all Real-Time Move JSON files.
- `data/cdbe/abilities/`: Contains all Ability JSON files.
- `data/cdbe/template.json`: The full reference template for a move.

---

## 1. Abilities Implementation
Abilities hook into combat events to trigger behaviors dynamically.

```json
{
  "cobblemon_ability_id": "cobblemon:intimidate",
  "on_switch_in": [],
  "on_damage_taken": [],
  "on_damage_dealt": [],
  "on_status_applied": []
}
```

### Event Hooks
- **`on_switch_in`**: Triggered immediately when the Pokémon is sent out or spawns.
- **`on_damage_taken`**: Triggered when the Pokémon receives damage.
- **`on_damage_dealt`**: Triggered when the Pokémon successfully damages an opponent.
- **`on_status_applied`**: Triggered when the Pokémon inflicts or receives a status condition.

### Ability Actions
Inside the event hooks, you can define specific actions the ability takes:
- **`stat_modifier`**:
  - `target`: `self`, `enemies`, `allies`
  - `radius`: Range of effect (in blocks).
  - `stat`: `attack`, `defense`, `special_attack`, `special_defense`, `speed`.
  - `multiplier`: E.g., `0.67` (Intimidate).
- **`status_effect`**: Applies a status condition (`burn`, `paralysis`, etc.).
- **`weather_setter`**: Modifies domain weather (`rain`, `sun`, `hail`, `sandstorm`).
- **`execute_move`**: Directly triggers an invisible attack phase (e.g., executing a self-heal).

---

## 2. Moves Implementation

A Move defines physical attack execution in the real-time engine. Moves are broken down into an array of **phases**. This phase system is the core architectural feature that allows complex, multi-stage sequences.

### The Phase System (`phases` array)
Because real-time action combat requires precise timing, an attack isn't just a single instantaneous event—it's a sequence. 
By defining multiple blocks inside the `phases` array, you string actions together sequentially:
1. **Charge & Strike**: e.g., Solar Beam uses an initial phase with an `attack_duration_turns` and an `attack_message` ("is absorbing sunlight!"), which upon completion advances to the next phase and fires a `BEAM` attack type.
2. **Multi-Part Attacks**: e.g., Fly has a phase that vanishes the caster into the `AIR` (invulnerable), followed by a delayed phase that executes a `MELEE` attack crashing down.
3. **Hit-and-Run**: e.g., U-Turn executes a `MELEE` damage phase, immediately followed by a `TELEPORT` or `DASH` phase to retreat.
Each phase executes in order. A single move can have any combination of phases (e.g., Dash -> Attack -> Retreat).

### Core Move Properties
- `cobblemon_move_id` *(String)*: The target Cobblemon move (e.g., `cobblemon:flamethrower`).
- `cooldown_turns` *(Float)*: Cooldown delay.

### Phase Execution: `AttackTypeEnum`
The `attack_type` determines how the engine physically executes the move in the world.
- `MELEE`: Short raycast or overlapping bounding box directly in front of the caster.
- `PROJECTILE`: Spawns a physical moving entity that travels forward and impacts.
- `BEAM`: Instantaneous long-range raycast.
- `AURA`: Radius check around the caster.
- `TARGETED`: Homes in or instantly hits a locked target.
- `SELF`: Affects only the caster (buffs, heals).
- `SELF_AURA`: Affects the caster and friendly targets in a radius.
- `CONE`: Conical damage area in front of the caster.
- `WAVE`: Wide line or wave moving forward.
- `DOMAIN`: Persistent area of effect (Weather, Light Screen).
- `HAZARD`: Stationary physical trap on the ground (Spikes).
- `DASH`: Caster rapidly moves forward to attack.
- `TELEPORT`: Instant relocation of the caster.
- `COLUMN`: Targeted, but with a vertical visual indicator (Thunderbolt, Eruption).
- `CRAWL`: Ground-hugging projectile.
- `VANISH`: Despawns or makes the caster invisible/invulnerable (Dig, Fly).

### Base Combat Properties
- `base_power` *(Int)*: Standard damage power.
- `accuracy` *(Int)*: 1-100 hit chance.
- `range` *(Float)*: Max execution distance in blocks.
- `attack_duration_turns` *(Float)*: Time this phase takes to execute (useful for Charge and Recharge phases).
- `attack_message` *(String)*: Custom message printed to combat log when this phase begins (e.g., "is charging energy!").
- `is_sound_move` *(Boolean)*: If true, bypasses Substitute and triggers sound abilities.
- `hp_cost_percent` *(Float)*: Subtracts % of max HP upon cast (Substitute, Belly Drum).
- `hp_cost_flat` *(Int)*: Subtracts flat HP.

### Status Effects
Defined in `status_effects` (for target) and `self_status_effects` (for caster).
- `type`: `burn`, `poison`, `paralysis`, `sleep`, `freeze`, `substitute`, `protect`, `endure`.
- `duration_turns`: How long the effect lasts.
- `chance`: 0.0 to 1.0 probability.
- **Special Statuses**:
  - `substitute`: Creates a secondary health pool shielding the caster from damage and status conditions equal to `hp_cost_percent`.
  - `protect`: Blocks incoming attacks entirely for its duration.
  - `endure`: Prevents health from dropping below 1 HP.

### Vanish & Sky Drop Properties
Moves that remove the caster from the map (Dig, Fly, Dive).
- `vanish_type`: The state the caster enters (`AIR`, `GROUND`, `WATER`, `SHADOW`).
- `hits_vanish_types`: Array of vanish types this move can hit (e.g., Earthquake hits `GROUND`, Twister hits `AIR`).
- `double_damage_on_vanish`: If true, deals 2x damage if hitting a vanished target.
- `bind_target_to_caster`: (Sky Drop) The target entity vanishes alongside the caster.
- `skip_with_power_herb`: Skips the charge phase if the user holds a Power Herb.

### Multi-Hit & Sequential Data
- `multi_hit_data`:
  - `min_hits` & `max_hits`: Number of times the attack hits.
  - `delay_ticks`: Delay between hits.
- `sequential_data` (Rollout, Fury Cutter):
  - `multiplier`: Damage multiplier per stack.
  - `max_stacks`: Maximum multiplier stack limit.

### Hazard Data
- `hazard_type`: `spikes`, `stealth_rock`, `toxic_spikes`.
- `ring_radius`: Radius of the trap on the floor.
- `max_stacks`: How many times the hazard can be stacked.
- `vanishes_when_touched_by_type`: Automatically cleanses if touched by a specific type (e.g., `poison` cleanses `toxic_spikes`).

### Domain Data
- `type`: `weather`, `screen_physical`, `screen_special`.
- `radius`: Sphere of effect.
- `weather_condition`: `rain`, `sun`, `sandstorm`, `hail`.
- `elemental_modifiers`: Map of elemental type multipliers (e.g., `{"water": 1.5, "fire": 0.5}`).

### Bypasses
- `ignores_protect`: True for moves like Feint.
- `breaks_protect`: Completely destroys active protect states.
- `ignores_substitute`: Sound moves or specific infiltrator moves.
- `is_ohko`: One-Hit-KO moves (Fissure, Sheer Cold).
