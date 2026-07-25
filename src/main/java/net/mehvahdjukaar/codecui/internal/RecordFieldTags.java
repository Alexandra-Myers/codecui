package net.mehvahdjukaar.codecui.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.*;

// Side-channel storage for the ordered (fieldName, fieldCodec) pairs accumulated per
// RecordCodecBuilder, populated by the RCB-construction mixins. See WeakTags for why the maps are
// identity-keyed and why the field codecs are held weakly.
public final class RecordFieldTags {

    // Exactly one of elementCodec / mapCodec is non-null: elementCodec from the
    // of(getter, name, codec) form, mapCodec carrying the whole MapCodec from the
    // of(getter, MapCodec) form (optional / default / lenient variants).
    public record Entry(String name,
                        @Nullable Codec<?> elementCodec,
                        @Nullable MapCodec<?> mapCodec) {}

    // Map-resident form of Entry.
    private record Stored(String name,
                          @Nullable WeakReference<Codec<?>> elementCodec,
                          @Nullable WeakReference<MapCodec<?>> mapCodec) {

        static Stored of(Entry entry) {
            return new Stored(entry.name(), WeakTags.weakRef(entry.elementCodec()),
                    WeakTags.weakRef(entry.mapCodec()));
        }

        // Null once the field codec has been collected, which can only happen after the key died.
        @Nullable Entry toEntry() {
            Codec<?> element = WeakTags.deref(elementCodec);
            MapCodec<?> map = WeakTags.deref(mapCodec);
            if (element == null && map == null) return null;
            return new Entry(name, element, map);
        }
    }

    private static final Map<RecordCodecBuilder<?, ?>, List<Stored>> TAGS = WeakTags.identityKeyed();

    public static void single(RecordCodecBuilder<?, ?> builder, String name, Codec<?> fieldCodec) {
        if (builder == null || fieldCodec == null) return;
        TAGS.put(builder, List.of(new Stored(name, new WeakReference<>(fieldCodec), null)));
    }

    // The on-disk field name comes from the MapCodec's keys(); without one we skip tagging
    // (the field just resolves Opaque).
    public static void singleMap(RecordCodecBuilder<?, ?> builder, MapCodec<?> mapCodec) {
        if (builder == null || mapCodec == null) return;
        String name = extractFirstKey(mapCodec);
        if (name == null) return;
        TAGS.put(builder, List.of(new Stored(name, null, new WeakReference<>(mapCodec))));
    }

    private static @Nullable String extractFirstKey(MapCodec<?> mapCodec) {
        try {
            return mapCodec.keys(JsonOps.INSTANCE)
                    .map(CodecReflection::jsonKeyString)
                    .findFirst()
                    .orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // Used by the Instance.map hook: the Applicative ap5..ap16 defaults pipe the accumulated
    // function position through map, which must not lose the fields gathered so far.
    public static void copy(RecordCodecBuilder<?, ?> from, RecordCodecBuilder<?, ?> to) {
        if (from == null || to == null || from == to) return;
        List<Stored> v = TAGS.get(from);
        if (v != null && !v.isEmpty()) TAGS.put(to, v);
    }

    public static void concat(RecordCodecBuilder<?, ?> result, RecordCodecBuilder<?, ?>... inputs) {
        if (result == null) return;
        ArrayList<Stored> merged = new ArrayList<>();
        for (RecordCodecBuilder<?, ?> in : inputs) {
            if (in == null) continue;
            List<Stored> sub = TAGS.get(in);
            if (sub != null) merged.addAll(sub);
        }
        if (!merged.isEmpty()) {
            TAGS.put(result, List.copyOf(merged));
        }
    }

    // Output side: entries re-keyed by the built MapCodec. The resolver rebuilds the
    // Schema.Record FRESH each lookup, so a companion registered after RCB.build() still wins.

    private static final Map<MapCodec<?>, List<Stored>> BUILT_TAGS = WeakTags.identityKeyed();

    public static void transferBuilt(RecordCodecBuilder<?, ?> builder, MapCodec<?> result) {
        if (builder == null || result == null) return;
        List<Stored> entries = TAGS.get(builder);
        if (entries == null || entries.isEmpty()) return;
        BUILT_TAGS.put(result, entries);
    }

    public static @Nullable List<Entry> getBuilt(MapCodec<?> result) {
        if (result == null) return null;
        List<Stored> stored = BUILT_TAGS.get(result);
        if (stored == null) return null;
        List<Entry> entries = new ArrayList<>(stored.size());
        for (Stored s : stored) {
            Entry entry = s.toEntry();
            if (entry == null) return null;
            entries.add(entry);
        }
        return List.copyOf(entries);
    }
}
