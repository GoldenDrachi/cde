# Cobblemon Dungeon Battle Engine (CDBE)

A real-time action combat engine built as an addon for the Cobblemon mod in Minecraft. CDBE transforms the traditional turn-based Pokemon battle system into a dynamic, fast-paced action combat experience inspired by Mystery Dungeon and Pokepark titles.

## Features

* **Real-Time Combat**: Engage in action-packed real-time battles where you directly control the flow of combat.
* **Player Morphing**: Take direct control of your Pokemon. Scaling hitboxes ensure seamless transitions.
* **Custom Move Engine**: JSON-driven move system allowing developers to create and balance attacks with granular control over hitboxes, damage, recoil, and status effects.
* **Hostile AI**: Encounter wild hostile Pokemon that dynamically target and attack players or other entities.
* **UI**: Active UI elements display attacks, cooldowns, and combat feedback.
* **Status Effects & Items**: Deeply integrated statuses including poison, burn, paralysis, substitute, and weather-dependent multipliers. Uses an extensible subclass architecture for effects.

## Usage & Configuration

The mod generates a configuration file at config/cdbe/config.json.

In config/cdbe/ you can also find folders with moves and ability .json files.
Some moves are already built in, you can change them in the corrsponding folder or ad d more.
"/cdbe reload" command, reloads the jsons directly ingame.

(Abilities are currently not completly implemented)

config/cdbe/ also provides a template.json aswell as a small guide for creating json files.

A detailed combat log can be toggled using the "/cdbe combatlog" command.

## Transparency & AI-Use Disclaimer

This project utilizes Generative AI as a collaborative tool to assist in building the codebase and generating documentation. The AI acts as an assistant under human review and direction.

**Important Note on AI Usage**:
Generative AI is strictly limited to **code and documentation** generation. AI tools will **never** be used for generating any form of art, models, textures, or creative assets for this project.

## Contributing

As this project utilizes a custom JSON schema for abilities and moves, contributors are welcome to help translate standard Cobblemon moves into the real-time engine format. Please see the /moves directory for schema examples.
