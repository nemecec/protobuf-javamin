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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import dev.nemecec.protobuf.javamin.ByteString;
import dev.nemecec.protobuf.javamin.CodedOutputStream;
import dev.nemecec.protobuf.javamin.InvalidProtocolBufferException;
import dev.nemecec.protobuf.javamin.UninitializedMessageException;
import dev.nemecec.protobuf.javamin.integration.gen.Color;
import dev.nemecec.protobuf.javamin.integration.gen.Sample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Drives a real .proto file through the codegen → runtime → codegen→runtime
 * loop. Each test sets fields via the generated Builder, serializes, and
 * deserializes back; the assertion is that every getter reads back what was set.
 *
 * <p>We don't check byte-for-byte against Google here — that comparison lives
 * in the runtime module's {@code CodedOutputStreamCompatTest}. This test
 * proves our codegen + runtime agree with each other.
 */
class SampleRoundtripTest {

  @Test
  @DisplayName("scalar fields, optional + required")
  void scalars() throws Exception {
    Sample original = Sample.newBuilder()
        .setId(42)
        .setName("hello")
        .setCreatedAtMillis(1_700_000_000_000L)
        .setActive(true)
        .setWeight(3.14)
        .setPayload(ByteString.copyFromUtf8("payload-bytes"))
        .build();

    Sample roundtrip = Sample.parseFrom(original.toByteArray());

    assertThat(roundtrip.hasId()).isTrue();
    assertThat(roundtrip.getId()).isEqualTo(42);
    assertThat(roundtrip.getName()).isEqualTo("hello");
    assertThat(roundtrip.getCreatedAtMillis()).isEqualTo(1_700_000_000_000L);
    assertThat(roundtrip.getActive()).isTrue();
    assertThat(roundtrip.getWeight()).isEqualTo(3.14);
    assertThat(roundtrip.getPayload().toStringUtf8()).isEqualTo("payload-bytes");
  }

  @Test
  @DisplayName("required field missing on build() → UninitializedMessageException")
  void requiredFieldMissingOnBuild() {
    assertThatThrownBy(() -> Sample.newBuilder().setId(1).build())
        .isInstanceOf(UninitializedMessageException.class);
  }

  @Test
  @DisplayName("required field missing on parseFrom() → InvalidProtocolBufferException")
  void requiredFieldMissingOnParse() throws Exception {
    // Hand-craft wire bytes that include only field 1 (id), omitting required field 2 (name).
    // Tag for field 1, varint wire type = (1 << 3) | 0 = 8 = 0x08; value 7 = 0x07.
    byte[] wire = new byte[]{0x08, 0x07};
    assertThatThrownBy(() -> Sample.parseFrom(wire))
        .isInstanceOf(InvalidProtocolBufferException.class);
  }

  @Test
  @DisplayName("repeated string fields preserve order and count")
  void repeatedStrings() throws Exception {
    Sample original = Sample.newBuilder()
        .setName("required")
        .addTags("alpha")
        .addTags("beta")
        .addAllTags(Arrays.asList("gamma", "delta"))
        .build();

    Sample roundtrip = Sample.parseFrom(original.toByteArray());
    assertThat(roundtrip.getTagsCount()).isEqualTo(4);
    assertThat(roundtrip.getTagsList()).containsExactly("alpha", "beta", "gamma", "delta");
  }

  @Test
  @DisplayName("nested message — single + repeated")
  void nested() throws Exception {
    Sample original = Sample.newBuilder()
        .setName("required")
        .setInner(Sample.Inner.newBuilder().setValue(7).setLabel("deep").build())
        .addItems(Sample.Inner.newBuilder().setValue(1).setLabel("a").build())
        .addItems(Sample.Inner.newBuilder().setValue(2).setLabel("b").build())
        .build();

    Sample roundtrip = Sample.parseFrom(original.toByteArray());

    assertThat(roundtrip.hasInner()).isTrue();
    assertThat(roundtrip.getInner().getValue()).isEqualTo(7);
    assertThat(roundtrip.getInner().getLabel()).isEqualTo("deep");

    assertThat(roundtrip.getItemsCount()).isEqualTo(2);
    assertThat(roundtrip.getItems(0).getValue()).isEqualTo(1);
    assertThat(roundtrip.getItems(0).getLabel()).isEqualTo("a");
    assertThat(roundtrip.getItems(1).getValue()).isEqualTo(2);
    assertThat(roundtrip.getItems(1).getLabel()).isEqualTo("b");
  }

  @Test
  void enums() throws Exception {
    Sample original = Sample.newBuilder()
        .setName("required")
        .setFavoriteColor(Color.GREEN)
        .build();

    Sample roundtrip = Sample.parseFrom(original.toByteArray());
    assertThat(roundtrip.hasFavoriteColor()).isTrue();
    assertThat(roundtrip.getFavoriteColor()).isEqualTo(Color.GREEN);
    assertThat(roundtrip.getFavoriteColorValue()).isEqualTo(1);
  }

  @Test
  @DisplayName("repeated enum — typed adders, typed list view, raw int side-door")
  void repeatedEnum() throws Exception {
    Sample original = Sample.newBuilder()
        .setName("required")
        .addColorPalette(Color.RED)
        .addColorPalette(Color.BLUE)
        .addAllColorPalette(Arrays.asList(Color.GREEN, Color.RED))
        .addColorPaletteValue(2)  // BLUE via the int side door
        .build();

    Sample roundtrip = Sample.parseFrom(original.toByteArray());

    assertThat(roundtrip.getColorPaletteCount()).isEqualTo(5);
    assertThat(roundtrip.getColorPaletteList())
        .containsExactly(Color.RED, Color.BLUE, Color.GREEN, Color.RED, Color.BLUE);
    assertThat(roundtrip.getColorPaletteValueList())
        .containsExactly(0, 2, 1, 0, 2);
    assertThat(roundtrip.getColorPalette(2)).isEqualTo(Color.GREEN);
    assertThat(roundtrip.getColorPaletteValue(0)).isEqualTo(0);
  }

  @Test
  @DisplayName("delimited stream — write multiple, read back in order")
  void delimitedStream() throws Exception {
    Sample a = Sample.newBuilder().setName("first").setId(1).build();
    Sample b = Sample.newBuilder().setName("second").setId(2).build();
    Sample c = Sample.newBuilder().setName("third").setId(3).build();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    a.writeDelimitedTo(baos);
    b.writeDelimitedTo(baos);
    c.writeDelimitedTo(baos);

    ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
    assertThat(Sample.parseDelimitedFrom(in).getName()).isEqualTo("first");
    assertThat(Sample.parseDelimitedFrom(in).getName()).isEqualTo("second");
    assertThat(Sample.parseDelimitedFrom(in).getName()).isEqualTo("third");
    assertThat(Sample.parseDelimitedFrom(in)).isNull(); // EOF
  }

  @Test
  @DisplayName("equals/hashCode — same content compares equal across instances; differing content does not")
  void equalsAndHashCode() {
    Sample a = Sample.newBuilder()
        .setName("required")
        .setId(7)
        .addTags("alpha")
        .addItems(Sample.Inner.newBuilder().setValue(1).setLabel("a").build())
        .build();
    Sample b = Sample.newBuilder()
        .setName("required")
        .setId(7)
        .addTags("alpha")
        .addItems(Sample.Inner.newBuilder().setValue(1).setLabel("a").build())
        .build();
    Sample different = Sample.newBuilder()
        .setName("required")
        .setId(8)  // <- different
        .build();

    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
    assertThat(a).isNotEqualTo(different);

    // hashCode is well-defined for a message with no optional fields set:
    assertThat(Sample.newBuilder().setName("x").build())
        .isEqualTo(Sample.newBuilder().setName("x").build());
  }

  @Test
  @DisplayName("toString lists only set fields, with quoted strings and typed enums")
  void toStringRenderingIncludesOnlySetFields() {
    Sample s = Sample.newBuilder()
        .setId(7)
        .setName("hello")
        .setFavoriteColor(Color.GREEN)
        .addTags("alpha")
        .addColorPalette(Color.RED)
        .build();
    String rendered = s.toString();

    // Set fields appear, with strings quoted and enums typed:
    assertThat(rendered).contains("id=7");
    assertThat(rendered).contains("name=\"hello\"");
    assertThat(rendered).contains("favoriteColor=GREEN");
    assertThat(rendered).contains("tags=[alpha]");
    assertThat(rendered).contains("colorPalette=[RED]");
    // Unset fields are absent:
    assertThat(rendered).doesNotContain("active");
    assertThat(rendered).doesNotContain("payload");
    // Wraps in ClassName{ ... }:
    assertThat(rendered).startsWith("Sample{").endsWith("}");
  }

  @Test
  @DisplayName("packed repeated primitives — varint, fixed32, fixed64 — encode/decode roundtrip")
  void packedRepeatedPrimitives() throws Exception {
    Sample original = Sample.newBuilder()
        .setName("required")
        .addPackedInts(1)
        .addPackedInts(-2)
        .addAllPackedInts(Arrays.asList(300, 400_000))
        .addPackedFixed32S(0xDEADBEEF)
        .addPackedFixed32S(0x01020304)
        .addPackedDoubles(3.14)
        .addPackedDoubles(2.71828)
        .addPackedDoubles(Double.NaN)
        .build();

    Sample roundtrip = Sample.parseFrom(original.toByteArray());

    assertThat(roundtrip.getPackedIntsList()).containsExactly(1, -2, 300, 400_000);
    assertThat(roundtrip.getPackedFixed32SList()).containsExactly(0xDEADBEEF, 0x01020304);
    assertThat(roundtrip.getPackedDoublesCount()).isEqualTo(3);
    assertThat(roundtrip.getPackedDoubles(0)).isEqualTo(3.14);
    assertThat(roundtrip.getPackedDoubles(1)).isEqualTo(2.71828);
    assertThat(Double.isNaN(roundtrip.getPackedDoubles(2))).isTrue();
  }

  @Test
  @DisplayName("packed-encoded bytes parse into a field whose schema declares unpacked")
  void packedBytesParseIntoUnpackedSchemaField() throws Exception {
    // Spec: a parser must accept either form regardless of [packed=true] on the
    // schema. colorPalette (field 11) is declared unpacked; we emit it as a
    // single length-delimited block and verify the unpacked-schema parser
    // tolerates the packed wire form.
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    CodedOutputStream out = CodedOutputStream.newInstance(baos);
    out.writeString(2, "required");
    out.writeTag(11, 2); // (fieldNumber=11, WIRETYPE_LENGTH_DELIMITED)
    out.writeUInt32NoTag(5); // 5 enum values, each one varint byte
    out.writeEnumNoTag(0); // RED
    out.writeEnumNoTag(2); // BLUE
    out.writeEnumNoTag(1); // GREEN
    out.writeEnumNoTag(0); // RED
    out.writeEnumNoTag(2); // BLUE
    out.flush();

    Sample s = Sample.parseFrom(baos.toByteArray());
    assertThat(s.getColorPaletteList())
        .containsExactly(Color.RED, Color.BLUE, Color.GREEN, Color.RED, Color.BLUE);
  }

  @Test
  @DisplayName("unpacked-encoded bytes parse into a field whose schema declares [packed=true]")
  void unpackedBytesParseIntoPackedSchemaField() throws Exception {
    // The reverse tolerance: schema says [packed=true] but the peer emitted the
    // unpacked form (per-element tags). The field reader's switch must include
    // the unpacked tag so this still resolves.
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    CodedOutputStream out = CodedOutputStream.newInstance(baos);
    out.writeString(2, "required");
    out.writeInt32(12, 1);   // packedInts (field 12) — unpacked tag = 12<<3 | 0
    out.writeInt32(12, -2);
    out.writeInt32(12, 300);
    out.flush();

    Sample s = Sample.parseFrom(baos.toByteArray());
    assertThat(s.getPackedIntsList()).containsExactly(1, -2, 300);
  }

  @Test
  @DisplayName("packed encoding is byte-efficient: single tag + length, no per-element tags")
  void packedEncodingShape() throws Exception {
    // Three-element packedInts (1, 2, 3) should encode as:
    //   tag(field=12, LD) [1 byte: 0x62]
    //   length [1 byte: 3]
    //   payload [3 bytes: 0x01, 0x02, 0x03]
    // = 5 bytes total for the field. An unpacked encoding of the same content
    // would cost 6 bytes (3 tags + 3 values).
    Sample s = Sample.newBuilder()
        .setName("x")
        .addPackedInts(1).addPackedInts(2).addPackedInts(3)
        .build();
    byte[] wire = s.toByteArray();

    // The packedInts portion of the wire (after `name` field):
    //   name = "x": tag(0x12), length(0x01), 'x'
    //   packedInts: tag(0x62), length(0x03), 0x01, 0x02, 0x03
    assertThat(wire).containsSequence((byte) 0x62, (byte) 0x03,
        (byte) 0x01, (byte) 0x02, (byte) 0x03);
  }

  @Test
  @DisplayName("getSerializedSize matches actual encoded length")
  void serializedSizeMatchesEncodedLength() throws Exception {
    Sample s = Sample.newBuilder()
        .setName("required-name")
        .setId(123)
        .addTags("x")
        .addTags("yz")
        .addItems(Sample.Inner.newBuilder().setValue(99).setLabel("nested").build())
        .addPackedInts(1).addPackedInts(-1).addPackedInts(400_000)
        .addPackedFixed32S(0xCAFEBABE)
        .addPackedDoubles(1.5).addPackedDoubles(-2.5)
        .build();

    assertThat(s.toByteArray().length).isEqualTo(s.getSerializedSize());
  }
}
