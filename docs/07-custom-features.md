# Custom Feature Packets

This document describes the `PacketPlayerCustomFeature` system, which allows
third-party developers to inject arbitrary numeric metrics into the Zeus
analysis pipeline.

## Purpose

The custom feature system exists so that server operators can extend the ML
feature vector without modifying zeus_plugins or zeus_proxy source code.
Examples of custom features include:

- Click speed (CPS)
- Aim accuracy percentages
- Custom macro detection scores
- Economy transaction amounts
- Mini-game specific metrics

## Packet ID

`0x20` -- `PacketPlayerCustomFeature`

## Binary Format

```
[common header]
+0    4    i32     category_id
+4    4    i32     feature_id
+8    8    f64     feature_value
```

## Categories

Categories are predefined to align with zeus_proxy's analysis modules:

| ID | Name         | zeus_proxy Module    |
|----|-------------|----------------------|
| 1  | COMBAT      | Combat extractor     |
| 2  | MOVEMENT    | Movement extractor   |
| 3  | INTERACT    | Interact extractor   |
| 4  | TRANSACTION | Transaction extractor|
| 5  | OTHER       | Uncategorised        |

## Feature ID

The `feature_id` is a zero-based index within the category. zeus_proxy uses
this to dynamically size the feature vector for each category. For example,
if a server sends features with `(category=1, feature_id=0)` and
`(category=1, feature_id=3)`, the proxy's combat feature vector will
automatically expand to accommodate indices 0 through 3.

Feature IDs should be assigned consecutively starting from 0 for best
efficiency, but gaps are tolerated.

## Usage (Java API)

### From a Bukkit Plugin (Paper/Spigot/Folia)

```java
import org.vennv.packets.PacketPlayerCustomFeature;
import org.vennv.utils.CustomFeatureCategory;
import org.vennv.zeusGateway.provider.PacketQueue;

public void sendCPS(Player player, double cps) {
    long timestamp = System.currentTimeMillis();
    String uid = player.getUniqueId().toString();
    String name = player.getName();

    PacketPlayerCustomFeature packet = new PacketPlayerCustomFeature(
        timestamp, uid, name,
        CustomFeatureCategory.COMBAT,  // category
        0,                             // feature_id (index 0 = CPS)
        cps                            // value
    );
    PacketQueue.push(packet);
}
```

### From a Fabric Mod

```java
import org.vennv.packets.PacketPlayerCustomFeature;
import org.vennv.utils.CustomFeatureCategory;
import org.vennv.zeusFabric.provider.PacketQueue;

// Same API, different PacketQueue import
PacketQueue.push(new PacketPlayerCustomFeature(
    timestamp, uid, name,
    CustomFeatureCategory.MOVEMENT, 0, someValue
));
```

## What zeus_proxy Does With Custom Features

1. Decodes the `category_id` and `feature_id` from the packet.
2. Routes the value to the appropriate analysis module based on category.
3. The module stores the value at the given feature_id index in its dynamic
   feature vector.
4. During ML inference, the custom features are appended to the built-in
   features for that category.
5. If the feature_id exceeds the current vector size, the vector is
   automatically resized with zero-fill for missing indices.

## Design Decisions

- **Integer IDs instead of string keys**: Using numeric category and feature
  IDs saves bandwidth (8 bytes instead of potentially 50+ for string names)
  and eliminates typo bugs.
- **Fire-and-forget**: Custom features are pushed synchronously into the
  queue. No callback, no response, no confirmation.
- **No registration required**: A new feature_id is recognised automatically
  the first time it is sent. There is no handshake or schema declaration.
