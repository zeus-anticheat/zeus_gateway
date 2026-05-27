# Packet Lifecycle

This document traces the full lifecycle of a packet from game event to UDP
wire, explaining every step that a custom implementation would need to
replicate.

## Step 1: Event Interception

The plugin intercepts a game event through one of these mechanisms:

| Mechanism                  | Platform          | Example                    |
|---------------------------|-------------------|----------------------------|
| ProtocolLib PacketAdapter  | Paper/Spigot      | POSITION, ARM_ANIMATION    |
| Bukkit EventHandler        | Paper/Spigot/Folia| PlayerJoinEvent            |
| Paper EventHandler         | Paper/Folia       | PrePlayerAttackEntityEvent |
| Fabric API Callback        | Fabric            | AttackEntityCallback       |
| Per-tick polling            | Fabric            | Position, armor, effects   |

All listeners operate at `EventPriority.MONITOR` (Bukkit) or equivalent --
they only observe events, never cancel them.

## Step 2: Data Extraction

From the event object, the plugin extracts:

- **Player identity**: UUID string and username.
- **Timestamp**: `System.currentTimeMillis()` at the moment of extraction.
- **Payload data**: Specific to each packet type (position, entity state,
  item data, etc.).

Platform-specific data (e.g. Bukkit `ItemStack`, Fabric `net.minecraft.item.ItemStack`)
is immediately converted to the protocol's platform-agnostic types
(`org.vennv.utils.Item`, `org.vennv.utils.Armor`, etc.).

## Step 3: Packet Construction

A new instance of the appropriate ZeusProtocolJava packet class is created:

```java
PacketPlayerPosition packet = new PacketPlayerPosition(
    timestamp, uid, name, cancelled,
    x, y, z, eyeX, eyeY, eyeZ,
    yaw, pitch, height, onGround
);
```

The packet object holds all data in Java primitive fields. No encoding
happens at this point.

## Step 4: Queue Insertion

The packet is pushed to the static `PacketQueue`:

```java
PacketQueue.push(packet);
```

This is a `ConcurrentLinkedQueue.add()` call -- O(1) and lock-free.

## Step 5: Batch Draining

The `BatchSender` thread wakes up every 5 ms and drains up to `batch-size`
packets:

```java
PacketEncode packet = PacketQueue.poll();
client.send(packet);
```

## Step 6: Binary Encoding

Inside `ProxyClient.send()`, the packet's `encode()` method is called:

```java
ByteArrayOutputStream out = new ByteArrayOutputStream();
packet.encode(out);
byte[] payload = out.toByteArray();
```

The `encode()` method:

1. Calls `encodePlayerInfo(out)` to write the common header.
2. Writes packet-specific fields using `ByteBufferUtil` methods.

All writes use `DataOutputStream` wrapping the `ByteArrayOutputStream`,
ensuring big-endian byte order for all multi-byte values.

## Step 7: UDP Transmission

The encoded bytes are sent as a single UDP datagram:

```java
DatagramPacket udp = new DatagramPacket(payload, payload.length, proxyAddress);
socket.send(udp);
```

## Step 8: Proxy Reception

zeus_proxy (Rust) receives the datagram on a UDP socket. It reads the first
byte to determine the packet ID, then deserialises the remaining bytes
according to the binary protocol specification (see `01-binary-protocol.md`).

---

## Timing Considerations

- Event listeners run on the Minecraft server's main thread (Bukkit events)
  or on Netty I/O threads (ProtocolLib packet listeners).
- `PacketQueue.push()` is non-blocking.
- `BatchSender` runs on its own daemon thread, separate from the game thread.
- The 5 ms sleep interval means there is up to 5 ms of latency between an
  event and its transmission.
- If the queue is empty, the BatchSender wakes up and immediately sleeps
  again, consuming minimal CPU.

## Implementing a Custom Collector

To build a compatible collector for a new platform (e.g. Velocity proxy,
Bedrock via Geyser, or a non-Java implementation), you must:

1. Intercept the same set of events for your platform.
2. Extract identical fields (positions, entity states, item data, etc.).
3. Perform the same value mappings (DamageCause, EffectType, BlockFace,
   ServerBoundPlayerCommandActions).
4. Compute the same derived values (eye position, surrounding blocks,
   vehicle steer projection, TPS estimation).
5. Encode each packet in the exact binary format described in
   `01-binary-protocol.md`.
6. Send each packet as a single UDP datagram to the proxy.
