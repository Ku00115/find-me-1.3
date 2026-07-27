package com.kuzhi.findme.server.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Deterministic NBT representation shared by migration, backups, and diagnostics. */
public final class NbtFingerprint {
    private NbtFingerprint() {
    }

    public static String sha256(Tag tag) {
        return sha256(canonical(tag));
    }

    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static String canonical(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            ArrayList<String> keys = new ArrayList<>(compound.getAllKeys());
            Collections.sort(keys);
            StringBuilder result = new StringBuilder("{");
            for (String key : keys) {
                if (result.length() > 1) result.append(',');
                result.append(key).append(':').append(canonical(compound.get(key)));
            }
            return result.append('}').toString();
        }
        if (tag instanceof ListTag list) {
            StringBuilder result = new StringBuilder("[");
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) result.append(',');
                result.append(canonical(list.get(index)));
            }
            return result.append(']').toString();
        }
        return tag == null ? "" : tag.toString();
    }

    static Optional<String> firstDifference(Tag expected, Tag actual) {
        return firstDifference("$", expected, actual);
    }

    private static Optional<String> firstDifference(String path, Tag expected, Tag actual) {
        if (expected == null || actual == null) {
            return expected == actual
                    ? Optional.empty()
                    : Optional.of(path + " expected=" + describe(expected) + " actual=" + describe(actual));
        }
        if (expected.getId() != actual.getId()) {
            return Optional.of(path + " type expected=" + expected.getId() + " actual=" + actual.getId());
        }
        if (expected instanceof CompoundTag expectedCompound && actual instanceof CompoundTag actualCompound) {
            ArrayList<String> keys = new ArrayList<>(expectedCompound.getAllKeys());
            for (String key : actualCompound.getAllKeys()) {
                if (!keys.contains(key)) keys.add(key);
            }
            Collections.sort(keys);
            for (String key : keys) {
                if (!expectedCompound.contains(key)) {
                    return Optional.of(path + "." + key + " unexpected=" + describe(actualCompound.get(key)));
                }
                if (!actualCompound.contains(key)) {
                    return Optional.of(path + "." + key + " missing; expected=" + describe(expectedCompound.get(key)));
                }
                Optional<String> difference = firstDifference(
                        path + "." + key,
                        expectedCompound.get(key),
                        actualCompound.get(key)
                );
                if (difference.isPresent()) return difference;
            }
            return Optional.empty();
        }
        if (expected instanceof ListTag expectedList && actual instanceof ListTag actualList) {
            if (expectedList.size() != actualList.size()) {
                return Optional.of(path + " size expected=" + expectedList.size() + " actual=" + actualList.size());
            }
            for (int index = 0; index < expectedList.size(); index++) {
                Optional<String> difference = firstDifference(
                        path + "[" + index + "]",
                        expectedList.get(index),
                        actualList.get(index)
                );
                if (difference.isPresent()) return difference;
            }
            return Optional.empty();
        }
        return expected.equals(actual)
                ? Optional.empty()
                : Optional.of(path + " expected=" + describe(expected) + " actual=" + describe(actual));
    }

    private static String describe(Tag tag) {
        if (tag == null) return "<missing>";
        String value = tag.toString();
        return value.length() <= 240 ? value : value.substring(0, 237) + "...";
    }
}
