package com.kuzhi.findme.api;

public record CompanionMagicState(float mana, float maxMana) {
    public static final CompanionMagicState EMPTY = new CompanionMagicState(0.0F, 0.0F);

    public CompanionMagicState {
        if (!Float.isFinite(maxMana)) maxMana = 0.0F;
        if (!Float.isFinite(mana)) mana = 0.0F;
        maxMana = Math.max(0.0F, maxMana);
        mana = Math.max(0.0F, Math.min(mana, maxMana));
    }

    public boolean available() {
        return maxMana > 0.0F;
    }
}
