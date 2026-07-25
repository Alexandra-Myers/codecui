package net.mehvahdjukaar.codecui.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.mehvahdjukaar.codecui.Schema;

import java.util.Map;

// Side-channel storage for schemas attached at codec construction time, weak by key so GC'd codecs
// don't hold their tags alive. Best-effort: a missing tag just means the resolver falls back to its
// structural tiers. See WeakTags for why the maps are identity-keyed.
//
// Unlike the other side channels the value here can't be weakened - a Schema is built for this map
// and nothing else references it. So a Schema that embeds its own key codec (Schema.Opaque over the
// codec being tagged, or a Schema.Custom whose widgetDef is bound to it) pins that entry for good.
// Harmless for the static game codecs curated at bootstrap; don't do it for a codec built per
// instance.
public final class SchemaTags {
    private static final Map<Codec<?>, Schema<?>> CODEC_SCHEMAS = WeakTags.identityKeyed();
    private static final Map<MapCodec<?>, Schema<?>> MAP_CODEC_SCHEMAS = WeakTags.identityKeyed();

    public static <A> void tag(Codec<A> codec, Schema<A> schema) {
        if (codec == null || schema == null) return;
        CODEC_SCHEMAS.put(codec, schema);
    }

    public static <A> void tag(MapCodec<A> codec, Schema<A> schema) {
        if (codec == null || schema == null) return;
        MAP_CODEC_SCHEMAS.put(codec, schema);
    }

    @SuppressWarnings("unchecked")
    public static <A> Schema<A> lookup(Codec<A> codec) {
        if (codec == null) return null;
        return (Schema<A>) CODEC_SCHEMAS.get(codec);
    }

    @SuppressWarnings("unchecked")
    public static <A> Schema<A> lookupMap(MapCodec<A> codec) {
        if (codec == null) return null;
        return (Schema<A>) MAP_CODEC_SCHEMAS.get(codec);
    }
}
