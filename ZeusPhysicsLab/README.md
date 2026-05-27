# Zeus Physics Lab

A PaperMC plugin that generates a comprehensive physics and behavior testing laboratory for the Zeus Anti-Cheat system. It automatically builds a physical world space containing over 80 specific testing stations covering movement, combat, interaction, vehicles, external forces, and cross-feature scenarios.

This tool is used to generate ground-truth "smoke" datasets: human players or bots run through these generated courses, and their interactions are captured by `ZeusGateway` as verified training and baseline data for ML models and heuristic checks.

## Features

- **Automated Generation**: Builds an organized grid of isolated testing stations via `/zeuslab generate`.
- **Extensive Coverage**: Generates courses for block interactions (ice, slime, cobweb, water, powdered snow), stair/slab transitions, combat dummies, vehicle paths, inventory bursts, and edge-case mechanics (spear attacks, wind charges, etc.).
- **Data Collection Ready**: Each station includes start/stop command block hooks to demarcate data recording sessions.
- **Manifest Export**: Generates a JSON manifest (`/zeuslab manifest`) used by automated test drivers (like Mineflayer bots) to navigate and execute the entire test suite autonomously.
- **Verification**: Automatically verifies that all expected stations generated correctly without chunk-loading issues via `/zeuslab verify`.

## Commands

Requires OP or the `zeusphysicslab.admin` permission.

- `/zeuslab generate` - Generates all station plots, command blocks, and markers at the configured origin (or your location).
- `/zeuslab reset` - Clears the generated stations and restores empty air.
- `/zeuslab list [category|id]` - Lists all available stations.
- `/zeuslab tp <station_id|number>` - Teleports you directly to a specific station for manual testing.
- `/zeuslab verify` - Validates that the expected signs and command blocks exist in the world.
- `/zeuslab manifest` - Exports the station routing manifest to `plugins/ZeusPhysicsLab/zeus_physics_lab_manifest.json`.

## Configuration (`config.yml`)

```yaml
# ZeusPhysicsLab generation origin.
# Leave world empty to use the command sender's current world.
world: ""
origin:
  explicit: false
  x: 0
  y: 80
  z: 0
```
If `explicit` is false, running `/zeuslab generate` will build the lab starting at your current position.

## Building

Requires JDK 21+ and Maven.

```bash
mvn clean package
```

The compiled artifact will be located at `target/zeus_physics_lab-1.0-SNAPSHOT.jar`. Drop it into your Paper 1.21.11+ server's `plugins/` folder.

## Architecture

- **`ZeusPhysicsLab.java`**: Main plugin class and command handler.
- **`LabGenerator.java`**: The builder engine. Places blocks, paths, signs, command blocks, and entities using Bukkit/Paper APIs.
- **`StationCatalog.java`**: The declarative definitions for all 80+ testing routes, grouped by category (Movement, Combat, Interact, Vehicle, Network, etc.).
- **`LabPlate.java`**: Defines the physical dimensions and bounding boxes of individual testing plots to prevent intersection.