package org.vennv.zeusFabric.provider;

import org.vennv.packets.PacketPlayerInput;

public final class MovementSemanticsTest {
    public static void main(String[] args) {
        MovementSemantics.PacketContext positionLook = MovementSemantics.packetContext(7L, true, true);
        require(positionLook.sequence() == 7L, "sequence changed");
        require(positionLook.hasPosition() && positionLook.hasLook(), "movement metadata changed");

        require(MovementSemantics.eyeX(12.5) == 12.5, "eye X mismatch");
        require(MovementSemantics.eyeY(64.0, 1.62) == 65.62, "eye Y mismatch");
        require(MovementSemantics.eyeZ(-3.25) == -3.25, "eye Z mismatch");

        int input = Byte.toUnsignedInt(MovementSemantics.rawPacketInputFlags(0x41));
        require(input == (0x41 | PacketPlayerInput.TRUSTED_CAPTURE), "raw input trust mismatch");

        MovementSemantics.EmissionGate gate = new MovementSemantics.EmissionGate();
        require(!gate.begin(false, true, true, 1.0, 2.0, 3.0, 4.0f, 5.0f),
                "off-thread movement became eligible");
        gate.accept();
        require(gate.complete().isEmpty(), "off-thread movement emitted");
        require(!gate.begin(true, true, false, Double.POSITIVE_INFINITY, 2.0, 3.0, 0.0f, 0.0f),
                "non-finite movement became eligible");
        gate.accept();
        require(gate.complete().isEmpty(), "non-finite movement emitted");
        require(gate.begin(true, true, true, 1.0, 2.0, 3.0, 4.0f, 5.0f),
                "finite movement rejected before vanilla");
        require(gate.complete().isEmpty(), "vanilla-rejected movement emitted");
        require(gate.begin(true, true, false, 1.0, 2.0, 3.0, 0.0f, 0.0f),
                "accepted movement not eligible");
        gate.accept();
        MovementSemantics.PacketContext accepted = gate.complete()
                .orElseThrow(() -> new AssertionError("accepted movement missing"));
        require(accepted.sequence() == 1L, "first accepted sequence mismatch");
        require(accepted.hasPosition() && !accepted.hasLook(), "accepted inclusion mismatch");
        require(gate.complete().isEmpty(), "accepted movement emitted twice");
        require(gate.begin(true, false, true, 0.0, 0.0, 0.0, 7.0f, 8.0f),
                "second accepted movement not eligible");
        gate.accept();
        require(gate.complete().orElseThrow().sequence() == 2L, "accepted sequence not contiguous");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
