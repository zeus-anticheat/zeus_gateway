package org.vennv.zeusFabric.task;

import org.vennv.packets.PacketCollisionWindow;
import org.vennv.packets.PacketCollisionWindow.Cell;

import java.util.ArrayList;
import java.util.List;

public final class ChunkSnapshotSemanticsTest {
    public static void main(String[] args) {
        canonicalIndexesAndFloorCenters();
        exactBoundedState();
        enteringUnionAndOverlapReuse();
        nonOverlapFallback();
        containment();
    }

    private static void canonicalIndexesAndFloorCenters() {
        require(ChunkSnapshotSemantics.index(-4, -4, -4) == 0, "minimum index changed");
        require(ChunkSnapshotSemantics.index(0, 0, 0) == 364, "center index changed");
        require(ChunkSnapshotSemantics.index(4, 4, 4) == 728, "maximum index changed");

        ChunkSnapshotSemantics.Center center = ChunkSnapshotSemantics.Center.floor(-0.01, -1.01, 15.99);
        require(center.equals(new ChunkSnapshotSemantics.Center(-1, -2, 15)), "center did not floor coordinates");
        require(equal(ChunkSnapshotSemantics.position(center, 0), -5, -6, 11), "minimum position changed");
        require(equal(ChunkSnapshotSemantics.position(center, 728), 3, 2, 19), "maximum position changed");
    }

    private static void exactBoundedState() {
        List<Cell> cells = new ArrayList<>(ChunkSnapshotSemantics.unknownCells());
        cells.set(0, Cell.knownAir());
        cells.set(728, Cell.knownBlock("minecraft:stone[axis=y]"));
        ChunkSnapshotSemantics.State state = new ChunkSnapshotSemantics.State(
                7,
                1,
                "minecraft:overworld",
                new ChunkSnapshotSemantics.Center(0, 64, 0),
                cells);
        cells.clear();

        require(state.cells().size() == PacketCollisionWindow.COLLISION_WINDOW_CELLS,
                "state is not exactly 729 cells");
        require(state.cells().get(0).getType() == PacketCollisionWindow.CellType.KNOWN_AIR,
                "air semantics changed");
        require("minecraft:stone[axis=y]".equals(state.cells().get(728).getBlockState()),
                "exact block state changed");
        requireThrows(() -> new ChunkSnapshotSemantics.State(
                8, 1, "minecraft:overworld", state.center(), List.of()),
                "short state accepted");
        requireThrows(() -> state.cells().add(Cell.unknown()), "state cells are mutable");
    }

    private static void enteringUnionAndOverlapReuse() {
        ChunkSnapshotSemantics.Center origin = new ChunkSnapshotSemantics.Center(0, 64, 0);
        List<Cell> cells = new ArrayList<>(ChunkSnapshotSemantics.unknownCells());
        for (int index = 0; index < cells.size(); index++) {
            cells.set(index, Cell.knownBlock("state-" + index));
        }
        ChunkSnapshotSemantics.State state = new ChunkSnapshotSemantics.State(
                1, 1, "minecraft:overworld", origin, cells);

        ChunkSnapshotSemantics.Center axis = new ChunkSnapshotSemantics.Center(1, 64, 0);
        require(ChunkSnapshotSemantics.entering(origin, axis).size() == 81,
                "axis entering plane is not exact");
        List<Cell> axisCells = ChunkSnapshotSemantics.reuse(state, axis);
        require(unknownCount(axisCells) == 81, "axis reuse did not preserve exact overlap");
        require("state-364".equals(axisCells.get(ChunkSnapshotSemantics.index(-1, 0, 0)).getBlockState()),
                "axis overlap mapped wrong world cell");

        ChunkSnapshotSemantics.Center diagonal = new ChunkSnapshotSemantics.Center(1, 64, 1);
        require(ChunkSnapshotSemantics.entering(origin, diagonal).size() == 153,
                "diagonal entering union is not exact");
        require(unknownCount(ChunkSnapshotSemantics.reuse(state, diagonal)) == 153,
                "diagonal reuse did not preserve exact overlap");
    }

    private static void nonOverlapFallback() {
        ChunkSnapshotSemantics.Center origin = new ChunkSnapshotSemantics.Center(0, 64, 0);
        ChunkSnapshotSemantics.Center distant = new ChunkSnapshotSemantics.Center(9, 64, 0);
        require(!ChunkSnapshotSemantics.overlaps(origin, distant), "nonoverlap treated as delta");
        requireThrows(() -> ChunkSnapshotSemantics.entering(origin, distant),
                "nonoverlap entering set accepted");
        require(ChunkSnapshotSemantics.allIndices().size() == 729, "full fallback is not 729 cells");
    }

    private static void containment() {
        ChunkSnapshotSemantics.Center center = new ChunkSnapshotSemantics.Center(15, 64, 15);
        require(ChunkSnapshotSemantics.contains(center, 11, 60, 11), "minimum bound missing");
        require(ChunkSnapshotSemantics.contains(center, 19, 68, 19), "maximum bound missing");
        require(!ChunkSnapshotSemantics.contains(center, 20, 68, 19), "outside bound included");
    }

    private static int unknownCount(List<Cell> cells) {
        int count = 0;
        for (Cell cell : cells) {
            if (cell.getType() == PacketCollisionWindow.CellType.UNKNOWN) {
                count++;
            }
        }
        return count;
    }

    private static boolean equal(int[] value, int x, int y, int z) {
        return value.length == 3 && value[0] == x && value[1] == y && value[2] == z;
    }

    private static void requireThrows(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException | UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
