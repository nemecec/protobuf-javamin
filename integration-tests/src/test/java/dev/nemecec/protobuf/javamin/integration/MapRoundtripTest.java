/*
 * Copyright (C) 2026 Neeme Praks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.nemecec.protobuf.javamin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nemecec.protobuf.javamin.ByteString;
import dev.nemecec.protobuf.javamin.integration.gen.Sample;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the codegen's {@code map<K, V>} support across all four field shapes
 * declared in Sample.proto: scalar→scalar, scalar→string, scalar→message, and
 * non-string-keyed scalar→bytes. Both directions of the cross-encoder roundtrip
 * are exercised against stock {@code protobuf-java}.
 *
 * <p>Note: byte-for-byte equality between encoders is intentionally NOT
 * asserted for map fields. The proto spec leaves map iteration order undefined,
 * and stock {@code protobuf-java} and javamin can pick different orders even
 * for the same logical content. {@link Map#equals} is by-content and is the
 * right level of assertion here.
 */
class MapRoundtripTest {

  @Test
  @DisplayName("put/get/contains/remove/clear semantics on each map field")
  void mapAccessorSemantics() {
    Sample.Builder b = Sample.newBuilder()
        .setName("required")
        .putStringToInt("alpha", 1)
        .putStringToInt("beta", 2)
        .putIntToString(7, "seven")
        .putStringToInner("nested", Sample.Inner.newBuilder().setValue(42).setLabel("x").build())
        .putLongToBytes(99L, ByteString.copyFromUtf8("payload"));

    // size + contains
    assertThat(b.getStringToIntCount()).isEqualTo(2);
    assertThat(b.containsStringToInt("alpha")).isTrue();
    assertThat(b.containsStringToInt("missing")).isFalse();

    // getOrDefault + getOrThrow
    assertThat(b.getStringToIntOrDefault("alpha", -1)).isEqualTo(1);
    assertThat(b.getStringToIntOrDefault("missing", -1)).isEqualTo(-1);
    assertThat(b.getStringToIntOrThrow("beta")).isEqualTo(2);
    assertThatThrownBy(() -> b.getStringToIntOrThrow("missing"))
        .isInstanceOf(IllegalArgumentException.class);

    // remove
    b.removeStringToInt("alpha");
    assertThat(b.containsStringToInt("alpha")).isFalse();
    assertThat(b.getStringToIntCount()).isEqualTo(1);

    // clear empties the storage back to the singleton.
    b.clearStringToInt();
    assertThat(b.getStringToIntCount()).isEqualTo(0);
    assertThat(b.getStringToIntMap()).isEmpty();

    // Build and verify the rest of the maps are intact.
    Sample s = b.build();
    assertThat(s.getIntToStringMap()).containsOnly(org.assertj.core.data.MapEntry.entry(7, "seven"));
    assertThat(s.getStringToInnerCount()).isEqualTo(1);
    assertThat(s.getStringToInnerOrThrow("nested").getValue()).isEqualTo(42);
    assertThat(s.getLongToBytesOrThrow(99L).toStringUtf8()).isEqualTo("payload");
  }

  @Test
  @DisplayName("null keys and values are rejected on put")
  void putRejectsNulls() {
    Sample.Builder b = Sample.newBuilder().setName("required");
    assertThatThrownBy(() -> b.putStringToInt(null, 1))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> b.putStringToInner("k", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("getXxxMap() returns an unmodifiable view")
  void getMapReturnsUnmodifiableView() {
    Sample s = Sample.newBuilder()
        .setName("required")
        .putStringToInt("alpha", 1)
        .build();
    assertThatThrownBy(() -> s.getStringToIntMap().put("beta", 2))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("putAll merges entries from a source map")
  void putAllMergesEntries() {
    Map<String, Integer> source = new LinkedHashMap<>();
    source.put("alpha", 1);
    source.put("beta", 2);

    Sample s = Sample.newBuilder()
        .setName("required")
        .putAllStringToInt(source)
        .putStringToInt("gamma", 3)
        .build();

    assertThat(s.getStringToIntMap())
        .containsEntry("alpha", 1)
        .containsEntry("beta", 2)
        .containsEntry("gamma", 3)
        .hasSize(3);
  }

  @Test
  @DisplayName("roundtrip: put → toByteArray → parseFrom returns equal content for every map field")
  void wireRoundtripPreservesContent() throws Exception {
    Sample original = Sample.newBuilder()
        .setName("required")
        .putStringToInt("alpha", 1).putStringToInt("beta", -2)
        .putIntToString(7, "seven").putIntToString(8, "eight")
        .putStringToInner("a", Sample.Inner.newBuilder().setValue(1).setLabel("x").build())
        .putStringToInner("b", Sample.Inner.newBuilder().setValue(2).setLabel("y").build())
        .putLongToBytes(99L, ByteString.copyFromUtf8("payload-1"))
        .putLongToBytes(100L, ByteString.copyFromUtf8("payload-2"))
        .build();

    Sample roundtrip = Sample.parseFrom(original.toByteArray());

    assertThat(roundtrip.getStringToIntMap())
        .containsExactlyInAnyOrderEntriesOf(original.getStringToIntMap());
    assertThat(roundtrip.getIntToStringMap())
        .containsExactlyInAnyOrderEntriesOf(original.getIntToStringMap());
    assertThat(roundtrip.getStringToInnerMap())
        .containsExactlyInAnyOrderEntriesOf(original.getStringToInnerMap());
    assertThat(roundtrip.getLongToBytesMap())
        .containsExactlyInAnyOrderEntriesOf(original.getLongToBytesMap());

    // equals / hashCode are content-equal regardless of iteration order.
    assertThat(roundtrip).isEqualTo(original);
    assertThat(roundtrip.hashCode()).isEqualTo(original.hashCode());
  }

  @Test
  @DisplayName("empty maps emit zero bytes (per-entry encode skips them entirely)")
  void emptyMapsEmitNoBytes() {
    // An entirely empty Sample (only the required `name` set) should still
    // serialise to just the `name` field. No tag bytes for any of the four
    // empty map fields.
    Sample s = Sample.newBuilder().setName("x").build();
    byte[] wire = s.toByteArray();

    // Just `name` field: tag(0x12=field 2 LD), length(1), 'x' = 3 bytes total.
    assertThat(wire).hasSize(3);
  }

  @Test
  @DisplayName("getSerializedSize matches actual encoded length for messages with maps")
  void serializedSizeMatchesEncodedLength() throws Exception {
    Sample s = Sample.newBuilder()
        .setName("required")
        .putStringToInt("a", 1).putStringToInt("bbb", 400_000)
        .putIntToString(-1, "negative-key")
        .putStringToInner("nested", Sample.Inner.newBuilder().setValue(7).setLabel("deep").build())
        .putLongToBytes(0L, ByteString.copyFromUtf8("zero-key-payload"))
        .build();

    assertThat(s.toByteArray().length).isEqualTo(s.getSerializedSize());
  }

  @Test
  @DisplayName("cross-encoder: javamin → bytes → google preserves every map's content")
  void crossEncoderJavaminToGoogle() throws Exception {
    Sample src = Sample.newBuilder()
        .setName("required")
        .putStringToInt("k1", 1).putStringToInt("k2", -2)
        .putIntToString(42, "answer")
        .putStringToInner("nested", Sample.Inner.newBuilder().setValue(7).setLabel("deep").build())
        .putLongToBytes(99L, ByteString.copyFromUtf8("payload"))
        .build();

    dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Sample dst =
        dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Sample.parseFrom(src.toByteArray());

    assertThat(dst.getStringToIntMap()).containsEntry("k1", 1).containsEntry("k2", -2).hasSize(2);
    assertThat(dst.getIntToStringMap()).containsEntry(42, "answer").hasSize(1);
    assertThat(dst.getStringToInnerCount()).isEqualTo(1);
    assertThat(dst.getStringToInnerOrThrow("nested").getValue()).isEqualTo(7);
    assertThat(dst.getStringToInnerOrThrow("nested").getLabel()).isEqualTo("deep");
    assertThat(dst.getLongToBytesOrThrow(99L).toStringUtf8()).isEqualTo("payload");
  }

  @Test
  @DisplayName("cross-encoder: google → bytes → javamin preserves every map's content")
  void crossEncoderGoogleToJavamin() throws Exception {
    Map<String, Integer> stringToInt = new HashMap<>();
    stringToInt.put("k1", 1);
    stringToInt.put("k2", -2);

    dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Sample src =
        dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Sample.newBuilder()
            .setName("required")
            .putAllStringToInt(stringToInt)
            .putIntToString(42, "answer")
            .putStringToInner("nested",
                dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Sample.Inner.newBuilder()
                    .setValue(7).setLabel("deep").build())
            .putLongToBytes(99L, com.google.protobuf.ByteString.copyFromUtf8("payload"))
            .build();

    Sample dst = Sample.parseFrom(src.toByteArray());

    assertThat(dst.getStringToIntMap()).containsEntry("k1", 1).containsEntry("k2", -2).hasSize(2);
    assertThat(dst.getIntToStringMap()).containsEntry(42, "answer").hasSize(1);
    assertThat(dst.getStringToInnerCount()).isEqualTo(1);
    assertThat(dst.getStringToInnerOrThrow("nested").getValue()).isEqualTo(7);
    assertThat(dst.getStringToInnerOrThrow("nested").getLabel()).isEqualTo("deep");
    assertThat(dst.getLongToBytesOrThrow(99L).toStringUtf8()).isEqualTo("payload");
  }
}
