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

import dev.nemecec.protobuf.javamin.ByteString;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cross-encoder roundtrip: build a message with one runtime, serialize, parse
 * with the other, and verify every field reads back equal. Covers every field
 * type, cardinality, nested message, and enum interaction in
 * {@code Sample.proto}.
 *
 * <p>The two codegens emit code into different packages with no shared types:
 * <ul>
 *   <li>{@code dev.nemecec.protobuf.javamin.integration.gen} — produced by our
 *       protobuf-javamin codegen, depending on the {@code dev.nemecec.protobuf.javamin}
 *       runtime;
 *   <li>{@code dev.nemecec.protobuf.javamin.integration.crosscheck.gen} — produced
 *       by stock {@code protoc} with the standard Java codegen, depending on
 *       {@code com.google.protobuf}.
 * </ul>
 *
 * <p>Field-level wire-format compatibility is already pinned in
 * {@code CodedOutputStreamCompatTest} / {@code CodedInputStreamCompatTest} in
 * the runtime module. This test extends that to full message bytes and so will
 * catch regressions in anything between — tag emission, oneof selection, repeated
 * layout, length-delimiting of embedded messages, and so on.
 */
class CrossEncoderRoundtripTest {

  // Aliases for readability — the two trees both have a top-level `Sample` and a
  // nested `Inner`, distinguished by package.
  private static final class JM {
    private static final Class<dev.nemecec.protobuf.javamin.integration.gen.Sample> SAMPLE =
        dev.nemecec.protobuf.javamin.integration.gen.Sample.class;
  }

  private static final class GG {
    private static final Class<dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Sample> SAMPLE =
        dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Sample.class;
  }

  @Test
  @DisplayName("javamin → bytes → google: every field reads back equal")
  void javaminEncodedParsesInGoogle() throws Exception {
    dev.nemecec.protobuf.javamin.integration.gen.Sample src =
        dev.nemecec.protobuf.javamin.integration.gen.Sample.newBuilder()
            .setId(42)
            .setName("hello")
            .setCreatedAtMillis(1_700_000_000_000L)
            .setActive(true)
            .setWeight(3.14)
            .setPayload(ByteString.copyFromUtf8("payload-bytes"))
            .setFavoriteColor(dev.nemecec.protobuf.javamin.integration.gen.Color.GREEN)
            .setInner(dev.nemecec.protobuf.javamin.integration.gen.Sample.Inner.newBuilder()
                .setValue(7).setLabel("deep").build())
            .addItems(dev.nemecec.protobuf.javamin.integration.gen.Sample.Inner.newBuilder()
                .setValue(1).setLabel("a").build())
            .addItems(dev.nemecec.protobuf.javamin.integration.gen.Sample.Inner.newBuilder()
                .setValue(2).setLabel("b").build())
            .addTags("alpha")
            .addTags("beta")
            .addColorPalette(dev.nemecec.protobuf.javamin.integration.gen.Color.RED)
            .addColorPalette(dev.nemecec.protobuf.javamin.integration.gen.Color.BLUE)
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
    dev.nemecec.protobuf.javamin.integration.gen.Sample dst =
        dev.nemecec.protobuf.javamin.integration.gen.Sample.parseFrom(wire);

    assertThat(dst.getId()).isEqualTo(99);
    assertThat(dst.getName()).isEqualTo("world");
    assertThat(dst.getCreatedAtMillis()).isEqualTo(1_800_000_000_000L);
    assertThat(dst.getActive()).isFalse();
    assertThat(dst.getWeight()).isEqualTo(2.71828);
    assertThat(dst.getPayload().toStringUtf8()).isEqualTo("from-google");
    assertThat(dst.getFavoriteColor())
        .isEqualTo(dev.nemecec.protobuf.javamin.integration.gen.Color.BLUE);

    assertThat(dst.hasInner()).isTrue();
    assertThat(dst.getInner().getValue()).isEqualTo(99);
    assertThat(dst.getInner().getLabel()).isEqualTo("nested-google");

    assertThat(dst.getItemsCount()).isEqualTo(2);
    assertThat(dst.getItems(0).getValue()).isEqualTo(11);
    assertThat(dst.getItems(0).getLabel()).isEqualTo("x");
    assertThat(dst.getItems(1).getValue()).isEqualTo(22);
    assertThat(dst.getItems(1).getLabel()).isEqualTo("y");

    assertThat(dst.getTagsList()).containsExactly("gamma", "delta");
    assertThat(dst.getColorPaletteList()).containsExactly(
        dev.nemecec.protobuf.javamin.integration.gen.Color.GREEN,
        dev.nemecec.protobuf.javamin.integration.gen.Color.RED);
  }

  @Test
  @DisplayName("byte-for-byte: javamin and google produce identical encodings of the same content")
  void identicalEncodings() throws Exception {
    // Same field values built into both codegens. Wire bytes should match
    // exactly because both write fields in declaration order with identical
    // tag-and-value emission. This is the strongest cross-encoder property
    // and guarantees that `parseFrom` symmetry isn't accidentally papering
    // over an asymmetry in how either side writes.
    dev.nemecec.protobuf.javamin.integration.gen.Sample javamin =
        dev.nemecec.protobuf.javamin.integration.gen.Sample.newBuilder()
            .setId(7)
            .setName("symmetric")
            .setActive(true)
            .addTags("one")
            .addTags("two")
            .addItems(dev.nemecec.protobuf.javamin.integration.gen.Sample.Inner.newBuilder()
                .setValue(42).setLabel("nested").build())
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
