package org.vennv.zeusFabric.task;

import org.vennv.packets.PacketCollisionWindow;
import org.vennv.packets.PacketCollisionWindow.Cell;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ChunkSnapshotSemantics {
    public static final int RADIUS = PacketCollisionWindow.COLLISION_WINDOW_RADIUS;
    public static final int EDGE = PacketCollisionWindow.COLLISION_WINDOW_EDGE;
    public static final int CELL_COUNT = PacketCollisionWindow.COLLISION_WINDOW_CELLS;

    public record Center(int x, int y, int z) {
        public static Center floor(double x, double y, double z) {
            return new Center(floorCoordinate(x), floorCoordinate(y), floorCoordinate(z));
        }
    }

    public record State(
            long generation,
            long sequence,
            String worldIdentity,
            Center center,
            List<Cell> cells) {
        public State {
            if (generation <= 0) {
                throw new IllegalArgumentException("collision generation must be positive");
            }
            if (sequence < 0) {
                throw new IllegalArgumentException("collision sequence must not be negative");
            }
            if ((worldIdentity == null) != (center == null)) {
                throw new IllegalArgumentException("collision world and center must both be present or absent");
            }
            if ((sequence == 0) != (center == null)) {
                throw new IllegalArgumentException("collision sequence must match committed state");
            }
            cells = exactCells(cells);
        }

        public static State empty(long generation) {
            return new State(generation, 0, null, null, unknownCells());
        }

        public boolean committed() {
            return sequence > 0;
        }
    }

    private ChunkSnapshotSemantics() {}

    public static int index(int dx, int dy, int dz) {
        if (Math.abs((long) dx) > RADIUS
                || Math.abs((long) dy) > RADIUS
                || Math.abs((long) dz) > RADIUS) {
            throw new IllegalArgumentException("collision window offset is out of range");
        }
        return (dy + RADIUS) * EDGE * EDGE + (dz + RADIUS) * EDGE + dx + RADIUS;
    }

    public static int[] offsets(int index) {
        if (index < 0 || index >= CELL_COUNT) {
            throw new IllegalArgumentException("collision cell index is out of range");
        }
        int dy = index / (EDGE * EDGE);
        int remainder = index % (EDGE * EDGE);
        int dz = remainder / EDGE;
        int dx = remainder % EDGE;
        return new int[] {dx - RADIUS, dy - RADIUS, dz - RADIUS};
    }

    public static int[] position(Center center, int index) {
        Objects.requireNonNull(center, "center");
        int[] offsets = offsets(index);
        return new int[] {
            Math.addExact(center.x(), offsets[0]),
            Math.addExact(center.y(), offsets[1]),
            Math.addExact(center.z(), offsets[2])
        };
    }

    public static List<Integer> allIndices() {
        List<Integer> indices = new ArrayList<>(CELL_COUNT);
        for (int index = 0; index < CELL_COUNT; index++) {
            indices.add(index);
        }
        return List.copyOf(indices);
    }

    public static boolean overlaps(Center left, Center right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        return Math.abs((long) left.x() - right.x()) < EDGE
                && Math.abs((long) left.y() - right.y()) < EDGE
                && Math.abs((long) left.z() - right.z()) < EDGE;
    }

    public static List<Integer> entering(Center baseCenter, Center center) {
        Objects.requireNonNull(baseCenter, "baseCenter");
        Objects.requireNonNull(center, "center");
        if (!overlaps(baseCenter, center)) {
            throw new IllegalArgumentException("collision window delta centers do not overlap");
        }
        long shiftX = (long) center.x() - baseCenter.x();
        long shiftY = (long) center.y() - baseCenter.y();
        long shiftZ = (long) center.z() - baseCenter.z();
        List<Integer> entering = new ArrayList<>();
        for (int index = 0; index < CELL_COUNT; index++) {
            int[] offsets = offsets(index);
            if (Math.abs(shiftX + offsets[0]) > RADIUS
                    || Math.abs(shiftY + offsets[1]) > RADIUS
                    || Math.abs(shiftZ + offsets[2]) > RADIUS) {
                entering.add(index);
            }
        }
        return List.copyOf(entering);
    }

    public static List<Cell> reuse(
            State previous,
            Center center) {
        Objects.requireNonNull(previous, "previous");
        if (!previous.committed()) {
            throw new IllegalArgumentException("collision overlap reuse requires committed state");
        }
        return reuse(previous.center(), previous.cells(), center);
    }

    public static List<Cell> reuse(
            Center baseCenter,
            List<Cell> baseCells,
            Center center) {
        Objects.requireNonNull(baseCenter, "baseCenter");
        Objects.requireNonNull(center, "center");
        List<Cell> previous = exactCells(baseCells);
        if (!overlaps(baseCenter, center)) {
            throw new IllegalArgumentException("collision window delta centers do not overlap");
        }
        List<Cell> cells = new ArrayList<>(unknownCells());
        for (int newIndex = 0; newIndex < CELL_COUNT; newIndex++) {
            int[] newOffsets = offsets(newIndex);
            long oldDx = (long) center.x() + newOffsets[0] - baseCenter.x();
            long oldDy = (long) center.y() + newOffsets[1] - baseCenter.y();
            long oldDz = (long) center.z() + newOffsets[2] - baseCenter.z();
            if (Math.abs(oldDx) <= RADIUS
                    && Math.abs(oldDy) <= RADIUS
                    && Math.abs(oldDz) <= RADIUS) {
                cells.set(newIndex, previous.get(index((int) oldDx, (int) oldDy, (int) oldDz)));
            }
        }
        return List.copyOf(cells);
    }

    public static boolean contains(Center center, int x, int y, int z) {
        Objects.requireNonNull(center, "center");
        return Math.abs((long) x - center.x()) <= RADIUS
                && Math.abs((long) y - center.y()) <= RADIUS
                && Math.abs((long) z - center.z()) <= RADIUS;
    }

    public static List<Cell> unknownCells() {
        List<Cell> cells = new ArrayList<>(CELL_COUNT);
        for (int index = 0; index < CELL_COUNT; index++) {
            cells.add(Cell.unknown());
        }
        return List.copyOf(cells);
    }

    private static List<Cell> exactCells(List<Cell> cells) {
        Objects.requireNonNull(cells, "cells");
        if (cells.size() != CELL_COUNT) {
            throw new IllegalArgumentException("collision state must contain exactly 729 cells");
        }
        List<Cell> snapshot = new ArrayList<>(CELL_COUNT);
        for (Cell cell : cells) {
            snapshot.add(Objects.requireNonNull(cell, "cell"));
        }
        return List.copyOf(snapshot);
    }

    private static int floorCoordinate(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("collision window coordinate is not finite");
        }
        double floor = Math.floor(value);
        if (floor < Integer.MIN_VALUE || floor > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("collision window coordinate is out of range");
        }
        return (int) floor;
    }
}
