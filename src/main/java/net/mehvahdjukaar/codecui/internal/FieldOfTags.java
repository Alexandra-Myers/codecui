package net.mehvahdjukaar.codecui.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Map;

// Lazy side-channel for Codec.fieldOf / optionalFieldOf: the fieldOf wrapper -> (field name, inner
// codec, optionality, default), recorded at construction. The resolver resolves the inner FRESH at
// lookup time so a companion registered after construction still wins. See WeakTags for why the
// map is identity-keyed and why the inner codec is held weakly.
public final class FieldOfTags {

    public record Entry(String name, Codec<?> innerCodec, boolean optional, @Nullable Object defaultValue) {}

    // Map-resident form of Entry. defaultValue stays strong: it's a decoded leaf (number, string,
    // enum, ItemStack) with no path back into the codec graph.
    private record Stored(String name, WeakReference<Codec<?>> innerCodec, boolean optional,
                          @Nullable Object defaultValue) {}

    private static final Map<MapCodec<?>, Stored> ENTRIES = WeakTags.identityKeyed();

    public static void put(MapCodec<?> wrapped, String name, Codec<?> innerCodec, boolean optional,
                           @Nullable Object defaultValue) {
        if (wrapped == null || innerCodec == null) return;
        ENTRIES.put(wrapped, new Stored(name, new WeakReference<>(innerCodec), optional, defaultValue));
    }

    public static @Nullable Entry get(MapCodec<?> wrapped) {
        if (wrapped == null) return null;
        Stored stored = ENTRIES.get(wrapped);
        if (stored == null) return null;
        Codec<?> inner = stored.innerCodec().get();
        if (inner == null) return null;
        return new Entry(stored.name(), inner, stored.optional(), stored.defaultValue());
    }
}
