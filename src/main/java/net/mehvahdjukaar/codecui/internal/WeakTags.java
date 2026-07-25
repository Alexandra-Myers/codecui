package net.mehvahdjukaar.codecui.internal;

import com.google.common.collect.MapMaker;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Map;

// Backing store for the construction-mixin side channels (FieldOfTags, XmapTags, RecordFieldTags,
// SchemaTags). Two rules, both paid for with a heap dump showing 4.4M live FieldOfTags entries:
//
// 1. Keys must compare by IDENTITY. Codecs are not reliably identity-comparable: DFU's
//    OptionalFieldCodec (what optionalFieldOf(name) returns) plus the record codecs (ListCodec,
//    EitherCodec, XorCodec, UnboundedMapCodec, DispatchedMapCodec) all implement structural
//    equals/hashCode. In a plain WeakHashMap every structurally equal wrapper in the game
//    collapses onto one entry held by a weak ref to whichever instance inserted first, so
//    collecting THAT instance silently drops the tag of the live equal ones and the field quietly
//    degrades to raw JSON. Structural hashing also walks the codec graph on every put/get.
//    MapMaker.weakKeys() switches to identity equivalence and hands back a ConcurrentMap, so the
//    synchronizedMap wrapper goes away too.
//
// 2. A stored value must not be able to reach its own key. A weak map still holds values
//    STRONGLY, so a value with any path back to its key resurrects the entry forever. Codec
//    graphs really do close that loop: vanilla's "MutableObject + self-reference" idiom puts a
//    fieldOf wrapper inside the reach of its own inner codec (Brain.codec, run fresh on every mob
//    spawn - that was the 4.4M). So codec references inside values go through weakRef(). It costs
//    nothing: a wrapper always strongly holds the codec it was built from, so the weak ref stays
//    valid for exactly as long as the key does.
final class WeakTags {

    static <K, V> Map<K, V> identityKeyed() {
        return new MapMaker().weakKeys().makeMap();
    }

    static <T> @Nullable WeakReference<T> weakRef(@Nullable T value) {
        return value == null ? null : new WeakReference<>(value);
    }

    static <T> @Nullable T deref(@Nullable WeakReference<T> ref) {
        return ref == null ? null : ref.get();
    }
}
