# Cobblemon Dungeon Battle Engine (CDBE)
## Comprehensive Move JSON Configuration Guide

This guide details every available field you can use when defining custom real-time attacks. Moves are defined in JSON files inside `data/cdbe/moves/`.

### Root Object (`RealTimeMove`)

The root JSON object defines the base move properties.

| Property | Type | Default | Description |
|---|---|---|---|
| `cobblemon_move_id` | String | (Required) | The official Cobblemon move ID (e.g. `cobblemon:tackle`). |
| `cooldown_turns` | Float | `0.0` | Base cooldown in turns. |
| `is_sound_move` | Boolean | `false` | If `true`, the move interacts with sound-based abilities and items (e.g., Throat Spray). |
| `hp_cost_percent` | Float | `0.0` | Drains this % of max HP to cast (e.g. Substitute uses 25.0). |
| `hp_cost_flat` | Int | `0` | Drains a flat amount of HP to cast. |
| `phases` | List | (Required) | A list of `MovePhase` objects defining the exact sequence of the attack. |

---

### Move Phase Object (`MovePhase`)

A single move can have multiple phases. Phases execute sequentially. You can use multiple phases to create multi-step attacks (e.g. charge up -> dash -> hit -> retreat).

#### Base Phase Settings
| Property | Type | Default | Description |
|---|---|---|---|
| `attack_type` | String | `null` | The attack strategy. Valid types: `MELEE`, `PROJECTILE`, `BEAM`, `AURA`, `TARGETED`, `SELF`, `SELF_AURA`, `CONE`, `WAVE`, `DOMAIN`, `HAZARD`, `DASH`, `TELEPORT`, `COLUMN`, `CRAWL`, `VANISH`. |
| `chargeup_ticks` | Int | `0` | Delay in ticks (20 = 1 sec) *before* this phase fires. Perfect for telegraphing or sequenced animations! |
| `attack_duration_turns` | Float | `0.0` | Locks the caster in the attack animation/recharge for this duration. |
| `attack_message` | String | `null` | A custom message sent to the player (e.g., `"started charging!"`). |
| `base_power` | Int | `0` | Overrides the Cobblemon base power. Set to `0` to use the default. |
| `accuracy` | Int | `null` | Overrides Cobblemon accuracy (1-100). Use `-1` for perfect homing. |
| `range` | Float | `1.0` | Range in blocks for Melee, Aura, Projectile, Cone, etc. |
| `multiple_targets` | Boolean | `false` | If `true`, hits all valid targets in range. If `false`, hits only the closest. |
| `hits_friendlies` | Boolean | `false` | If `true`, the attack will damage teammates. |

#### Sound & Visuals
| Property | Type | Default | Description |
|---|---|---|---|
| `sound_effect` | String | `null` | Custom sound identifier (e.g., `minecraft:entity.dragon_fireball.explode`). |
| `particles` | ParticleData | `null` | The primary visual particle of the phase. |
| `extra_particles` | List<ParticleData>| `null` | Used to spawn multiple particle combinations simultaneously! |
| `on_hit_particles` | ParticleData | `null` | Particles that ONLY spawn exactly on the target if they fail their evasion roll and take damage. |

#### Projectile Specific
| Property | Type | Default | Description |
|---|---|---|---|
| `homing_strength` | Float | `null` | Unused in the current homing algorithm (uses perfect homing if `accuracy` is `-1`). |
| `projectile_speed` | Float | `null` | Reserved for future physical projectile entities. |

---

### Particle Object (`ParticleData`)

Defines a specific visual effect. Note: Cobblemon particles MUST be prefixed with `cobblemon:` (e.g., `cobblemon:explosion`).

| Property | Type | Default | Description |
|---|---|---|---|
| `type` | String | (Required) | The particle ID. Can be Vanilla (`flame`, `crit`, `explosion`) or Cobblemon Snowstorm (`cobblemon:absorb_actor`). |
| `count` | Int | `1` | Number of vanilla particles to spawn. Has no effect on Cobblemon particles. |
| `bind_to_caster` | Boolean | `false` | If `true`, the particle locks onto the attacker. Excellent for charge-up animations or auras! |
| `bind_to_target` | Boolean | `false` | If `true`, the particle locks onto the victim. (If the attack misses, it plays on an invisible dummy at max range). |

> [!TIP]
> **How to create Beams**
> If you set BOTH `bind_to_caster` and `bind_to_target` to `true` on the same particle, the engine automatically recognizes it as a beam and stretches the particle between the caster and the target (or the dummy if it misses).

> [!TIP]
> **How do Locators work?**
> You do NOT need to define specific bones (like "mouth" or "right_arm") in your JSON! The engine automatically looks for standard semantic aliases like `"special"`, `"physical"`, or `"target"`. 
> The client-side Pokémon models (e.g. Braixen) natively map `"special"` to their stick, while Charizard maps `"special"` to his mouth. Because of this, standard Cobblemon particles will magically attach to the correct body part for every Pokémon automatically!

---

### Advanced Sub-Objects
Many attacks rely on complex sub-components inside the Phase object.

#### 1. Status Effects (`status_effects` & `self_status_effects`)
List of `StatusEffectData` applied to the target or the caster.
| Property | Type | Default | Description |
|---|---|---|---|
| `type` | String | (Required) | The status effect ID (e.g., `burn`, `paralysis`, `defense_down`, `substitute`). |
| `duration_turns` | Float | (Required) | How long the status lasts. |
| `chance` | Float | `100.0` | Percentage chance to apply (0-100). |
| `immunity_cooldown_turns` | Float | `null` | Time until this status can be applied again. |

#### 2. Mobility (`mobility_data`)
Used with `DASH` or `TELEPORT` attack types, or as recoil/knockback.
| Property | Type | Default | Description |
|---|---|---|---|
| `dash_speed` | Float | `null` | The forward momentum applied. |
| `stop_on_hit` | Boolean | `true` | Unused currently. |
| `teleport_range` | Float | `null` | How far a teleport will search forward. |
| `knockback_force` | Float | `null` | Applied to the target. |
| `retreat_force` | Float | `null` | Knocks the caster backward after the attack completes. |

#### 3. Health Manipulation (`health_manipulation_data`)
Used for lifesteal, recoil, healing, and self-destruct moves.
| Property | Type | Default | Description |
|---|---|---|---|
| `drain_percent` | Float | `0.0` | Heals caster for this % of the total damage dealt (e.g., Giga Drain = `50.0`). |
| `recoil_percent` | Float | `0.0` | Damages caster for this % of the total damage dealt (e.g., Double-Edge). |
| `heal_self_percent` | Float | `0.0` | Heals caster for this % of their MAX HP (e.g., Recover = `50.0`). |
| `cleanses_status` | List<String> | `null` | List of status names to clear (e.g. `["burn"]`, or `["all"]`). |
| `sacrificial` | Boolean | `false` | Reserved for Explosion-style self-faints. |

#### 4. Bypasses (`bypasses`)
Special combat overrides.
| Property | Type | Default | Description |
|---|---|---|---|
| `ignores_protect` | Boolean | `false` | Attack goes through Protect. |
| `breaks_protect` | Boolean | `false` | Attack shatters the target's Protect buff. |
| `ignores_substitute` | Boolean | `false` | Damage goes straight to target's HP, ignoring their active Substitute (e.g., Sound moves). |
| `flat_damage` | Int | `null` | Deals exactly this much damage, ignoring stats and typing (e.g., Dragon Rage). |
| `is_ohko` | Boolean | `false` | Instantly faints target if they are same level or lower. |

#### 5. Impact AoE (`impact_aoe_data`)
Used on Projectile, Column, and Crawl moves to create an explosion upon hitting a surface/target.
| Property | Type | Default | Description |
|---|---|---|---|
| `radius` | Float | (Required) | The size of the explosion blast. |
| `particles` | ParticleData | `null` | The visual explosion effect. |

#### 6. Domains (`domain_data`)
Spawns long-lasting weather or screen zones in the world.
| Property | Type | Default | Description |
|---|---|---|---|
| `type` | String | (Required) | `weather`, `screen_physical`, or `screen_special`. |
| `radius` | Float | `15.0` | The size of the domain zone. |
| `duration_turns` | Float | `5.0` | How long the zone lasts. |
| `weather_condition` | String | `null` | Sets the global battle weather (e.g., `rain`, `sun`). |
| `tick_damage` | TickDamageData| `null` | Periodic damage taken by enemies standing inside. |
| `stat_buffs` | List | `null` | Applies flat stat multipliers to entities inside. |

#### 7. Hazards (`hazard_data`)
Places traps on the ground (Spikes, Stealth Rock, Sticky Web).
| Property | Type | Default | Description |
|---|---|---|---|
| `hazard_type` | String | (Required) | Custom hazard ID. |
| `placement_range` | Float | `15.0` | How far away it can be placed. |
| `max_stacks` | Int | `1` | Max overlapping stacks (e.g., Spikes = 3). |
| `damage_percent` | Float | `12.5` | % Max HP damage dealt when stepped on. |
| `status_effect` | StatusEffectData| `null` | Status applied when stepped on (e.g., Toxic Spikes). |
| `affects_flying` | Boolean | `false` | If false, Flying types and Levitating Pokémon are immune. |

#### 8. Edge Case Actions
| Property | Type | Default | Description |
|---|---|---|---|
| `multi_hit_data` | MultiHitData | `null` | `{"min_hits": 2, "max_hits": 5, "delay_ticks": 5}`. Automatically repeats the phase! |
| `sequential_data` | SequentialData| `null` | Used for Rollout/Fury Cutter. Increases multiplier if used consecutively. |
| `special_state_data`| SpecialStateData|`null`| Used for `protect` definitions and `stat_reset` (Haze). |

#### 9. Vanish Properties (Fly / Dig / Phantom Force)
If `attack_type` is `VANISH`, these handle the disappearing mechanics.
| Property | Type | Default | Description |
|---|---|---|---|
| `vanish_type` | String | `null` | `FLYING`, `DIGGING`, `DIVING`, `SHADOW`, or `COMPLETE`. |
| `hits_vanish_types` | List<String> | `null` | What vanished targets this move can hit (e.g. Earthquake hits `DIGGING`). |
| `double_damage_on_vanish`| Boolean | `false` | Earthquake deals 2x damage if target is `DIGGING`. |
| `skip_with_power_herb`| Boolean | `false` | If true, Power Herb instantly skips the `chargeup_ticks`. |
| `bind_target_to_caster`| Boolean | `false` | Used for Sky Drop to grab the target. |
| `unmount_target` | Boolean | `false` | Drops the grabbed target. |

---

## Abilities Implementation
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
