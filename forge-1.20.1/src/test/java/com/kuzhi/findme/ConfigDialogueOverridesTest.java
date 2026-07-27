package com.kuzhi.findme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigDialogueOverridesTest {
    @Test
    void groupsRepeatedContextsAndKeepsExplicitlyDisabledContexts() {
        Map<String, List<String>> parsed = Config.parseDialogueOverrides(List.of(
                "mount_summon=call.one|reply.one",
                "MOUNT_SUMMON=call.two|reply.two",
                "home_storage=",
                "invalid"));

        assertEquals(List.of("call.one|reply.one", "call.two|reply.two"),
                parsed.get("mount_summon"));
        assertTrue(parsed.containsKey("home_storage"));
        assertTrue(parsed.get("home_storage").isEmpty());
        assertFalse(parsed.containsKey("invalid"));
    }
}
