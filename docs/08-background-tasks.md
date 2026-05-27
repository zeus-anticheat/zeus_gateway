# Background Tasks

This document describes the background tasks and periodic processes running within `zeus_plugins`. These tasks ensure reliable data delivery and state synchronisation between the Minecraft server and the Zeus proxy.

## 1. BatchSender (Network Layer)

**Availability**: ZeusGateway, ZeusFabric

The `BatchSender` is a high-priority daemon thread dedicated to draining the internal `PacketQueue` and transmitting data over UDP.

### How it works:
- **Blocking Queue**: It uses `PacketQueue.take()`, which blocks the thread until a packet is available. This prevents "spinning" and minimizes CPU usage when the server is idle.
- **Immediate Transmission**: Although named "BatchSender", it currently sends packets as soon as they are available (one UDP datagram per packet) to ensure the lowest possible latency for anti-cheat analysis.
- **Error Handling**: It is wrapped in a robust try-catch block to prevent the thread from dying if a network error occurs.

---

## 2. UpdateTPS (Performance Monitoring)

**Availability**: ZeusGateway (separate task), ZeusFabric (integrated in tick)

TPS (Ticks Per Second) is a critical metric for anti-cheat analysis, as many movement and combat checks depend on the server's processing speed.

### ZeusGateway Implementation:
- **Frequency**: Every 20 ticks (1 second at 20 TPS).
- **Strategy**:
  1. **Direct API**: Tries to read the 1-minute TPS average from the server via reflection (works on Paper and Spigot 1.19+).
  2. **EMA Fallback**: If the API is unavailable, it measures the wall-clock time between runs and computes an Exponential Moving Average (EMA) with $\alpha=0.2$ to smooth out temporary spikes.
- **Clamping**: The value is always clamped between `0.0` and `20.0`.

### ZeusFabric Implementation:
- **Frequency**: Every server tick.
- **Strategy**: Measures the nano-time delta between ticks and applies an EMA.
- **Packet**: Pushes a `PacketTPSServer` to the queue.

---

## 3. ResyncTask (State Recovery)

**Availability**: ZeusGateway (Bukkit/Paper/Folia)

The `ResyncTask` is a vital "insurance" mechanism that periodically re-broadcasts the current state of all online players.

### Why it exists:
If the Zeus backend (Rust) restarts or misses a few UDP packets (since UDP is unreliable), it might lose track of a player's state (e.g., whether they are sprinting, what potion effects they have, or their current gamemode).

### How it works:
- **Frequency**: Every 200 ticks (10 seconds) with a 5-second initial delay.
- **Data Synchronised**:
  - **Join State**: Re-sends `PacketPlayerJoin` to ensure the proxy knows the player exists.
  - **Potion Effects**: Re-sends `PacketPlayerEffect` for every active effect.
  - **Movement State**: Re-sends `PacketServerBoundPlayerCommand` for current Sprinting/Sneaking status.
  - **GameMode**: Re-sends `PacketPlayerChangeMode`.

---

## Summary Table

| Task | Thread | Frequency | Purpose |
|------|--------|-----------|---------|
| **BatchSender** | Async Daemon | Continuous (Blocking) | Drains queue and sends UDP packets |
| **UpdateTPS** | Async Task | 20 ticks (Bukkit) / 1 tick (Fabric) | Monitors server health and timing |
| **ResyncTask** | Async Task | 10 seconds (Bukkit only) | Recovers state for backend after restarts |
