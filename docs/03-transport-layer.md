# Transport Layer

This document describes how zeus_plugins transmits data to zeus_proxy at the
network level.

## Protocol

- **Transport**: UDP (User Datagram Protocol).
- **One packet per datagram**: Each Zeus protocol packet is encoded into
  exactly one UDP datagram. There is no multi-packet framing, no length
  prefix at the datagram level, and no batching within a single datagram.
- **No acknowledgement**: The protocol is fire-and-forget. If a datagram is
  lost, the data is simply missing. zeus_proxy must be tolerant of gaps.
- **No encryption**: Data is sent in plaintext. The assumption is that the
  plugin and proxy run on the same host or a trusted network.

## ProxyClient

Both ZeusGateway and ZeusFabric contain a `ProxyClient` class that wraps
`java.net.DatagramSocket`.

### Encoding Flow

```
PacketEncode.encode(ByteArrayOutputStream out)
  -> byte[] payload = out.toByteArray()
  -> DatagramPacket udp = new DatagramPacket(payload, payload.length, proxyAddress)
  -> socket.send(udp)
```

The encoding is performed inline in the send call. There is no pre-allocated
buffer pool; each packet allocates a new `ByteArrayOutputStream`.

### Error Handling

- ZeusGateway: Suppresses repeated errors after the first failure and logs
  a restoration message when sending succeeds again. This prevents log spam
  when the proxy is temporarily down.
- ZeusFabric: Prints stack traces on failure. Simpler but noisier.

### Host Resolution

- ZeusGateway auto-corrects `0.0.0.0` to `127.0.0.1` with a warning.
- ZeusFabric does not perform this correction; the operator must configure
  a valid destination address.

## PacketQueue

`PacketQueue` is a static `ConcurrentLinkedQueue<PacketEncode>` shared
across all listener threads. Any thread may push a packet, and only the
BatchSender thread drains it.

- **Thread safety**: `ConcurrentLinkedQueue` is lock-free.
- **Backpressure**: There is no queue size limit. If the proxy is unreachable
  and the server is busy, the queue will grow unbounded. In practice, the
  5 ms drain interval and batch-size limit keep it small.

## BatchSender

A daemon thread that runs an infinite loop:

```
while (true) {
    drain up to batch-size packets from PacketQueue
    for each packet: ProxyClient.send(packet)
    sleep 5 ms
}
```

- **Drain rate**: Up to `batch-size` (default 100) packets per 5 ms cycle.
  This yields a theoretical maximum of 20,000 packets/second.
- **Thread**: Runs as a daemon thread so it does not prevent JVM shutdown.
- **On Fabric**: The `stop()` method sets a `volatile boolean` flag that
  causes the loop to exit cleanly on server shutdown.
- **On Bukkit**: The thread runs indefinitely (no explicit stop mechanism
  beyond daemon thread status and JVM exit).

## What zeus_proxy Needs to Know

1. Each UDP datagram is exactly one Zeus packet.
2. The first byte of the datagram is the packet ID.
3. The packet ID determines the layout of the rest of the datagram.
4. Packet ID `0x12` (TPS) has a special layout: `[1 byte id][8 byte f64]`.
5. All other packets begin with the common header.
6. There is no session establishment, handshake, or authentication.
7. Multiple Minecraft servers can send to the same proxy concurrently; the
   proxy must disambiguate by player UUID and/or source address.
8. Datagrams can arrive out of order (UDP property); the `timestamp` field
   enables reordering if needed.
9. Typical packet sizes:
   - Minimum (Join/Leave/Death/BlockChangeAck): ~50 bytes.
   - Position: ~110 bytes.
   - ChunkData: variable and split by exact encoded size.
   - Every emitted datagram is at most 65,507 bytes.
