package com.kuzhi.findme.api.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Client extension point for addon commands shown in FindMe's companion command wheel. */
public final class FindMeClientCommandActionRegistry {
    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    private FindMeClientCommandActionRegistry() {
    }

    public static void register(ResourceLocation id, int order,
                                Function<CompanionCommandTarget, Component> label,
                                Predicate<CompanionCommandTarget> available,
                                Predicate<CompanionCommandTarget> activate) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(available, "available");
        Objects.requireNonNull(activate, "activate");
        ENTRIES.removeIf(entry -> entry.id.equals(id));
        ENTRIES.add(new Entry(id, order, label, available, activate));
    }

    public static List<Entry> entries() {
        ArrayList<Entry> result = new ArrayList<>(ENTRIES);
        result.sort(Comparator.comparingInt(Entry::order).thenComparing(entry -> entry.id.toString()));
        return List.copyOf(result);
    }

    public record Entry(ResourceLocation id, int order,
                        Function<CompanionCommandTarget, Component> label,
                        Predicate<CompanionCommandTarget> available,
                        Predicate<CompanionCommandTarget> activate) {
        public Component label(CompanionCommandTarget target) {
            return this.label.apply(target);
        }

        public boolean available(CompanionCommandTarget target) {
            return this.available.test(target);
        }

        public boolean activate(CompanionCommandTarget target) {
            return this.activate.test(target);
        }
    }
}
