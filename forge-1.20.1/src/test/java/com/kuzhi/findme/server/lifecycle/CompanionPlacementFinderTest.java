package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class CompanionPlacementFinderTest {
    @Test
    void verticalSearchPrefersCurrentAndHigherGroundBeforeLowerGround() {
        int[] offsets = new int[7];
        for (int index = 0; index < offsets.length; index++) {
            offsets[index] = CompanionPlacementFinder.verticalSearchOffset(index);
        }

        assertArrayEquals(new int[]{0, 1, -1, 2, -2, 3, -3}, offsets);
    }
}
