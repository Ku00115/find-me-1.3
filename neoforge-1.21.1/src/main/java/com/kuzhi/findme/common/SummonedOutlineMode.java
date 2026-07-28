package com.kuzhi.findme.common;

public enum SummonedOutlineMode {
    OFF(0xFFFFFF), WHITE(0xFFFFFF), BLACK(0x000000);
    private final int color;
    SummonedOutlineMode(int color) { this.color = color; }
    public int color() { return this.color; }
}
