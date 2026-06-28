# Cobblemon Dungeon Battle Engine (CDBE)

A real-time action combat engine built as an addon for the Cobblemon mod in Minecraft. CDBE transforms the traditional turn-based Pokemon battle system into a dynamic, fast-paced action combat experience inspired by Mystery Dungeon and Pokepark titles.

## Features

* **Real-Time Combat**: Engage in action-packed real-time battles where you directly control the flow of combat.
* **Player Morphing**: Take direct control of your Pokemon. Scaling hitboxes and safe-space checks ensure seamless transitions.
* **Custom Move Engine**: JSON-driven move system allowing data-pack developers to create and balance attacks with granular control over hitboxes, damage, recoil, and status effects.
* **Hostile AI**: Encounter wild hostile Pokemon that dynamically target and attack players or other entities.
* **Immersive Visuals**: First-person perspective while morphed, utilizing the actual dimensions and animations of the active Pokemon. Active UI elements display abilities, cooldowns, and combat feedback.
* **Status Effects & Items**: Deeply integrated statuses including poison, burn, paralysis, substitute, and weather-dependent multipliers. Uses an extensible subclass architecture for effects.

## Usage & Configuration

The mod generates a configuration file at config/cdbe/config.json.
Key features like Player Morphing and Hostile Spawns can be customized or toggled via this configuration file.

* Press your designated Morph Key to take control of your active Pokemon.
* A detailed combat log can be toggled using the /cdbe combatlog command.

## Transparency & AI-Use Disclaimer

This project utilizes Generative AI as a collaborative tool to assist in building the codebase and generating documentation. The AI acts as an assistant under human review and direction.

**Important Note on AI Usage**:
Generative AI is strictly limited to **code and documentation** generation. AI tools will **never** be used for generating any form of art, models, textures, or creative assets for this project.

## Contributing

As this project utilizes a custom JSON schema for abilities and moves, contributors are welcome to help translate standard Cobblemon moves into the real-time engine format. Please see the /moves directory for schema examples.