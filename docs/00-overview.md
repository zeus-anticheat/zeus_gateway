# Zeus Plugins -- Overview

## Purpose

zeus_plugins is a collection of Minecraft server-side plugins and mods that
capture player behaviour data at the game-tick level and stream it over UDP to
a remote analysis server (zeus_proxy, written in Rust). The plugins themselves
perform no cheat detection; they are purely data collectors.

## Modules

The project contains three modules:

| Module              | Role                                      | Build System |
|---------------------|-------------------------------------------|-------------|
| ZeusProtocolJava    | Shared, platform-agnostic binary codec    | Maven       |
| ZeusGateway         | Bukkit-based plugin (Paper/Spigot/Folia)  | Maven       |
| ZeusFabric          | Fabric server-side mod                    | Gradle      |

### ZeusProtocolJava

A zero-dependency Java library that defines every packet type the system uses.
Each packet class knows how to serialise itself into a compact binary format
via `ByteArrayOutputStream`. The library also includes shared enumerations
(`DamageCause`, `EffectType`, `Hand`, etc.) and data structures (`EntityState`,
`Effect`, `Item`, `ItemStack`, `Armor`, `Armors`, `RelativeBlock`).

Both ZeusGateway and ZeusFabric depend on this library. It is shaded
(embedded) into the final JAR/mod so operators never need to install it
separately.

### ZeusGateway

The `ZeusGateway` Java 8 artifact auto-detects whether it is running
on Paper, Spigot, or Folia at startup. Tested support is published only from
`support-matrix.json`; the Java 8 legacy runtime is bundled with the unified artifact.
The modern runtime registers:

1. **ProtocolLib packet listeners** (optional) -- intercepts raw client-bound
   and server-bound packets for high-precision data (position, keep-alive,
   swing hand, block digging, steer vehicle, etc.).
2. **Bukkit event listeners** -- standard event handlers available on all
   Bukkit-derived servers.
3. **Platform schedulers** -- Bukkit scheduling on Paper/Spigot and
   region/entity-owned scheduling on Folia.

When ProtocolLib is absent, the plugin degrades gracefully: some data sources
switch from packet-level to event-level (lower precision but still functional).

### ZeusFabric

An exact-Minecraft-version Fabric server-side mod. The current build adapter
is `ZeusFabric-1.21.11` and its metadata requires Minecraft `=1.21.11`;
targets are not promoted to supported without the manifest gates. Because Fabric does not have ProtocolLib
or Bukkit events, the mod uses Fabric API event callbacks for interactive
events (attack, block place, use item, etc.) and a per-tick state-polling loop
for everything else (position, held item, armor, game mode, effects, sneaking,
sprinting, vehicles, screen handlers, ray trace).

## Data Flow

```
    Minecraft Server (Java)
         |
         v
  +--------------------+
  |  Event / Packet    |   Bukkit events, ProtocolLib packets,
  |  Listeners         |   Fabric API callbacks, tick polling
  +--------------------+
         |
         v  PacketQueue.push(packet)
  +--------------------+
  |  PacketQueue       |   ConcurrentLinkedQueue<PacketEncode>
  +--------------------+
         |
         v  BatchSender drains queue every 5 ms
  +--------------------+
  |  BatchSender       |   Daemon thread, sends up to batch-size
  |  (Thread)          |   packets per cycle
  +--------------------+
         |
         v  ProxyClient.send(packet)
  +--------------------+
  |  ProxyClient       |   Java DatagramSocket
  |  (UDP)             |   Encodes packet -> byte[] -> DatagramPacket
  +--------------------+
         |
         v
  zeus_proxy (Rust)    <-- receives raw UDP datagrams
```

Each packet is independently encoded and sent as a single UDP datagram.
There is no framing, no batching at the wire level, and no acknowledgement.
The protocol is fire-and-forget.

## Configuration

Both modules read a simple config file at startup:

- ZeusGateway: `plugins/ZeusGateway/config.yml`
- ZeusFabric: `config/zeusfabric.properties`

The key settings are:

| Setting      | Default   | Description                           |
|-------------|-----------|---------------------------------------|
| proxy-host  | 0.0.0.0   | Destination host for UDP packets      |
| proxy-port  | 9999      | Destination port for UDP packets      |
| batch-size  | 100       | Max packets drained per 5 ms cycle    |

Note: `0.0.0.0` is auto-corrected to `127.0.0.1` by ZeusGateway's
ProxyClient because `0.0.0.0` is a bind address, not a valid send target.
