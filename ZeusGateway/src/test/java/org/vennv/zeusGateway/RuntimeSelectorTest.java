package org.vennv.zeusGateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RuntimeSelectorTest {
    @Test
    void selectsLegacyThroughOneThirteen() {
        assertTrue(RuntimeSelector.useLegacy("1.8.8-R0.1-SNAPSHOT"));
        assertTrue(RuntimeSelector.useLegacy("1.13.2-R0.1-SNAPSHOT"));
        assertFalse(RuntimeSelector.useLegacy("1.14.4-R0.1-SNAPSHOT"));
        assertFalse(RuntimeSelector.useLegacy("1.21.11-R0.1-SNAPSHOT"));
        assertFalse(RuntimeSelector.useLegacy("26.2-R0.1-SNAPSHOT"));
    }

    @Test
    void malformedVersionFailsClosedToLowestApi() {
        assertTrue(RuntimeSelector.useLegacy(null));
        assertTrue(RuntimeSelector.useLegacy("unknown"));
    }
}
