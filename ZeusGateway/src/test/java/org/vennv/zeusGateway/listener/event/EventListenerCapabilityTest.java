package org.vennv.zeusGateway.listener.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.vennv.zeusGateway.listener.RawCaptureCapability;

class EventListenerCapabilityTest {
    @Test
    void successfulRawListenerDisablesOnlyItsMatchingFallback() {
        EventListener listener = new EventListener(
                EnumSet.of(RawCaptureCapability.CLICK_WINDOW, RawCaptureCapability.ATTACK_ENTITY));

        assertFalse(listener.isFallbackEnabled(RawCaptureCapability.CLICK_WINDOW));
        assertFalse(listener.isFallbackEnabled(RawCaptureCapability.ATTACK_ENTITY));
        assertTrue(listener.isFallbackEnabled(RawCaptureCapability.HELD_ITEM));
    }

    @Test
    void rawPlayerCommandControlsToggleFallbacksIndependently() {
        EventListener withRawCommand = new EventListener(
                EnumSet.of(RawCaptureCapability.PLAYER_COMMAND));
        EventListener withoutRawCommand = new EventListener();

        assertFalse(withRawCommand.isFallbackEnabled(RawCaptureCapability.PLAYER_COMMAND));
        assertTrue(withoutRawCommand.isFallbackEnabled(RawCaptureCapability.PLAYER_COMMAND));
    }
}
