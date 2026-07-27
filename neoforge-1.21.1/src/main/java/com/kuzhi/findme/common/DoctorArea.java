package com.kuzhi.findme.common;

public enum DoctorArea {
    RECORDS,
    ORGANIZATION,
    LIFECYCLE,
    SNAPSHOTS,
    WORLD,
    HOMES,
    OPERATIONS,
    RECOVERY;

    public static DoctorArea fromLegacy(String area) {
        return switch (area == null ? "" : area) {
            case "record", "metadata" -> RECORDS;
            case "team", "wheel" -> ORGANIZATION;
            case "deployment" -> LIFECYCLE;
            case "snapshot" -> SNAPSHOTS;
            case "world", "temporary" -> WORLD;
            case "home" -> HOMES;
            case "operation" -> OPERATIONS;
            case "backup", "vault" -> RECOVERY;
            default -> RECORDS;
        };
    }
}
