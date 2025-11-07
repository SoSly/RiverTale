package org.sosly.rivertale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RiverTaleTest {

    @Test
    @DisplayName("Mod ID should be rivertale")
    void testModId() {
        assertEquals("rivertale", RiverTale.MOD_ID);
    }

    @Test
    @DisplayName("Logger should be initialized")
    void testLoggerExists() {
        assertNotNull(RiverTale.LOGGER);
    }
}