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

// Two `Sample` types coexist on the test classpath: this file imports the
// javamin-generated one as the un-qualified `Sample` / `Color`, and refers to
// the Google-generated one by full FQN so the difference stays visible at every
// call site.
import dev.nemecec.protobuf.javamin.ByteString;
import dev.nemecec.protobuf.javamin.integration.gen.Color;
import dev.nemecec.protobuf.javamin.integration.gen.Sample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cross-encoder roundtrip: build a message with one runtime, serialize, parse
 * with the other, and verify every field reads back equal. Covers every field
 * type, cardinality, nested message, and enum interaction in {@code Sample.proto}.
 *
 * <p>Two generated trees coexist on the test classpath, one per encoder:
 * <ul>
 *   <li>{@code dev.nemecec.protobuf.javamin.integration.gen.*} — produced by our
 *       codegen, depending on {@code dev.nemecec.protobuf.javamin}.</li>
 *   <li>{@code dev.nemecec.protobuf.javamin.integration.crosscheck.gen.*} —
 *       produced by stock {@code protoc} with the standard Java codegen,
 *       depending on {@code com.google.protobuf}.</li>
 * </ul>
 *
 * <p>Field-level wire-format compatibility is already pinned in
 * {@code CodedOutputStreamCompatTest} / {@code CodedInputStreamCompatTest} in
 * the runtime module. This test extends that to full message bytes and so will
 * catch regressions in anything between — tag emission, oneof selection,
 * repeated layout, length-delimiting of embedded messages, and so on.
 */
class CrossEncoderRoundtripTest {

  // Shorthand for the Google-side types. They have the same simple names as the
  // javamin-side ones, just in a different package; importing both would
  // collide, so we leave the Google side fully qualified at use sites and
  // alias only the parent class here.
  private static final class G {
    static final Class<dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Sample> SAMPLE =
        dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Sample.class;
  }

  @Test
  @DisplayName("javamin → bytes → google: every field reads back equal")
  void javaminEncodedParsesInGoogle() throws Exception {
    Sample src = Sample.newBuilder()
        .setId(42)
        .setName("hello")
        .setCreatedAtMillis(1_700_000_000_000L)
        .setActive(true)
        .setWeight(3.14)
        .setPayload(ByteString.copyFromUtf8("payload-bytes"))
        .setFavoriteColor(Color.GREEN)
        .setInner(Sample.Inner.newBuilder().setValue(7).setLabel("deep").build())
        .addItems(Sample.Inner.newBuilder().setValue(1).setLabel("a").build())
        .addItems(Sample.Inner.newBuilder().setValue(2).setLabel("b").build())
        .addTags("alpha")
        .addTags("beta")
        .addColorPalette(Color.RED)
        .addColorPalette(Color.BLUE)
        .build();

    byte[] wire = src.toByteArray();
    dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Sample dst =
        dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Sample.parseFrom(wire);

    assertThat(dst.getId()).isEqualTo(42);
    assertThat(dst.getName()).isEqualTo("hello");
    assertThat(dst.getCreatedAtMillis()).isEqualTo(1_700_000_000_000L);
    assertThat(dst.getActive()).isTrue();
    assertThat(dst.getWeight()).isEqualTo(3.14);
    assertThat(dst.getPayload().toStringUtf8()).isEqualTo("payload-bytes");
    assertThat(dst.getFavoriteColor())
        .isEqualTo(dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Color.GREEN);

    assertThat(dst.hasInner()).isTrue();
    assertThat(dst.getInner().getValue()).isEqualTo(7);
    assertThat(dst.getInner().getLabel()).isEqualTo("deep");

    assertThat(dst.getItemsCount()).isEqualTo(2);
    assertThat(dst.getItems(0).getValue()).isEqualTo(1);
    assertThat(dst.getItems(0).getLabel()).isEqualTo("a");
    assertThat(dst.getItems(1).getValue()).isEqualTo(2);
    assertThat(dst.getItems(1).getLabel()).isEqualTo("b");

    assertThat(dst.getTagsList()).containsExactly("alpha", "beta");
    assertThat(dst.getColorPaletteList()).containsExactly(
        dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Color.RED,
        dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Color.BLUE);

    // Class metadata sanity check — guards against accidentally importing
    // both Sample types into one source file (which Java forbids), in which
    // case this test would silently start comparing javamin-vs-javamin.
    assertThat(dst.getClass()).isEqualTo(G.SAMPLE);
  }

  @Test
  @DisplayName("google → bytes → javamin: every field reads back equal")
  void googleEncodedParsesInJavamin() throws Exception {
    dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Sample src =
        dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Sample.newBuilder()
            .setId(99)
            .setName("world")
            .setCreatedAtMillis(1_800_000_000_000L)
            .setActive(false)
            .setWeight(2.71828)
            .setPayload(com.google.protobuf.ByteString.copyFromUtf8("from-google"))
            .setFavoriteColor(dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Color.BLUE)
            .setInner(dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Sample.Inner.newBuilder()
                .setValue(99).setLabel("nested-google").build())
            .addItems(dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Sample.Inner.newBuilder()
                .setValue(11).setLabel("x").build())
            .addItems(dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Sample.Inner.newBuilder()
                .setValue(22).setLabel("y").build())
            .addTags("gamma")
            .addTags("delta")
            .addColorPalette(dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Color.GREEN)
            .addColorPalette(dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Color.RED)
            .build();

    byte[] wire = src.toByteArray();
    Sample dst = Sample.parseFrom(wire);

    assertThat(dst.getId()).isEqualTo(99);
    assertThat(dst.getName()).isEqualTo("world");
    assertThat(dst.getCreatedAtMillis()).isEqualTo(1_800_000_000_000L);
    assertThat(dst.getActive()).isFalse();
    assertThat(dst.getWeight()).isEqualTo(2.71828);
    assertThat(dst.getPayload().toStringUtf8()).isEqualTo("from-google");
    assertThat(dst.getFavoriteColor()).isEqualTo(Color.BLUE);

    assertThat(dst.hasInner()).isTrue();
    assertThat(dst.getInner().getValue()).isEqualTo(99);
    assertThat(dst.getInner().getLabel()).isEqualTo("nested-google");

    assertThat(dst.getItemsCount()).isEqualTo(2);
    assertThat(dst.getItems(0).getValue()).isEqualTo(11);
    assertThat(dst.getItems(0).getLabel()).isEqualTo("x");
    assertThat(dst.getItems(1).getValue()).isEqualTo(22);
    assertThat(dst.getItems(1).getLabel()).isEqualTo("y");

    assertThat(dst.getTagsList()).containsExactly("gamma", "delta");
    assertThat(dst.getColorPaletteList()).containsExactly(Color.GREEN, Color.RED);
  }

  @Test
  @DisplayName("byte-for-byte: javamin and google produce identical encodings of the same content")
  void identicalEncodings() {
    // Same field values built into both codegens. Wire bytes should match
    // exactly because both write fields in declaration order with identical
    // tag-and-value emission. This is the strongest cross-encoder property
    // and guarantees that `parseFrom` symmetry isn't accidentally papering
    // over an asymmetry in how either side writes.
    Sample javamin = Sample.newBuilder()
        .setId(7)
        .setName("symmetric")
        .setActive(true)
        .addTags("one")
        .addTags("two")
        .addItems(Sample.Inner.newBuilder().setValue(42).setLabel("nested").build())
        .build();
    dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Sample google =
        dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Sample.newBuilder()
            .setId(7)
            .setName("symmetric")
            .setActive(true)
            .addTags("one")
            .addTags("two")
            .addItems(dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Sample.Inner.newBuilder()
                .setValue(42).setLabel("nested").build())
            .build();

    assertThat(javamin.toByteArray()).isEqualTo(google.toByteArray());
  }
}
