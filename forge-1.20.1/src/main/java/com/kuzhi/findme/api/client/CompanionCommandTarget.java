package com.kuzhi.findme.api.client;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.network.CompanionListPacket;

/** Immutable client-side view passed to command and addon ability actions. wheelIndex may be -1. */
public record CompanionCommandTarget(CompanionKind kind, int wheelIndex, CompanionListPacket.Entry entry) {
}
