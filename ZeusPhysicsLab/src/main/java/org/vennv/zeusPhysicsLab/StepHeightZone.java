package org.vennv.zeusPhysicsLab;

public record StepHeightZone(
    String blockName,
    String collisionHeight,
    String instruction,
    int minX,
    int maxX,
    int y,
    int minZ,
    int maxZ,
    int order
) {
    public boolean contains(int x, int y, int z) {
        return y >= this.y && y <= this.y + 2 && x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
}
