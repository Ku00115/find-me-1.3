package com.kuzhi.findme.common;

/** Controls whether a successful manual binding uses the contract cinematic. */
public enum BindingAnimationPolicy {
    INHERIT,
    ALWAYS,
    FIRST_TYPE,
    NEVER;

    public BindingAnimationPolicy effective(BindingAnimationPolicy fallback) {
        return this == INHERIT ? fallback : this;
    }
}
