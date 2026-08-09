package com.kuzhi.findme.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ContractCeremonyTimelineTest {
    @Test
    void stagesMeetWithoutGaps() {
        assertEquals(ContractCeremonyTimeline.Stage.FOCUS, ContractCeremonyTimeline.stage(0));
        assertEquals(ContractCeremonyTimeline.Stage.FOCUS,
                ContractCeremonyTimeline.stage(ContractCeremonyTimeline.FOCUS_END_TICK - 1));
        assertEquals(ContractCeremonyTimeline.Stage.NAME,
                ContractCeremonyTimeline.stage(ContractCeremonyTimeline.FOCUS_END_TICK));
        assertEquals(ContractCeremonyTimeline.Stage.RESPONSE,
                ContractCeremonyTimeline.stage(ContractCeremonyTimeline.NAME_END_TICK));
        assertEquals(ContractCeremonyTimeline.Stage.VOW,
                ContractCeremonyTimeline.stage(ContractCeremonyTimeline.RESPONSE_END_TICK));
        assertEquals(ContractCeremonyTimeline.Stage.VOW,
                ContractCeremonyTimeline.stage(ContractCeremonyTimeline.DURATION_TICKS));
    }

    @Test
    void segmentProgressIsBounded() {
        assertEquals(0.0f, ContractCeremonyTimeline.segmentProgress(5.0f, 10, 20));
        assertEquals(0.5f, ContractCeremonyTimeline.segmentProgress(15.0f, 10, 20));
        assertEquals(1.0f, ContractCeremonyTimeline.segmentProgress(25.0f, 10, 20));
        assertEquals(0.0f, ContractCeremonyTimeline.segmentProgress(9.0f, 10, 10));
        assertEquals(1.0f, ContractCeremonyTimeline.segmentProgress(10.0f, 10, 10));
    }
}
