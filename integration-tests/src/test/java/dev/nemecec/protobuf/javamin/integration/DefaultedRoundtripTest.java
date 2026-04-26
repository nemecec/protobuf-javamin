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
import dev.nemecec.protobuf.javamin.integration.gen.Defaulted;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the codegen's handling of proto2 {@code [default = ...]} across every
 * applicable type. Three properties matter:
 *
 * <ol>
 *   <li>An untouched message exposes the declared defaults via its getters,
 *       while {@code hasXxx()} returns {@code false} (i.e. the value is "the
 *       default" but the field was never set on the wire).</li>
 *   <li>{@code clearXxx()} on the builder resets the storage back to the
 *       declared default, not the type-default.</li>
 *   <li>An unset defaulted field emits zero bytes on the wire (proto2 spec —
 *       only set fields are serialised). This keeps wire compatibility with
 *       stock {@code protobuf-java} and is also what makes defaults useful:
 *       a peer reading from an older sender just sees the local default.</li>
 * </ol>
 */
class DefaultedRoundtripTest {

  @Test
  @DisplayName("untouched message reads back declared defaults; has-flags are all false")
  void untouchedReadsDeclaredDefaults() {
    Defaulted d = Defaulted.newBuilder().build();

    // Numeric integers, signed and unsigned. The unsigned-max defaults map to
    // the signed bit pattern (-1) on the Java side; the wire round-trip is
    // covered separately by the cross-encoder check below.
    assertThat(d.getI32()).isEqualTo(-7);
    assertThat(d.getI64()).isEqualTo(9_000_000_000L);
    assertThat(d.getU32()).isEqualTo(-1);                 // 4294967295 unsigned
    assertThat(d.getU64()).isEqualTo(-1L);                // 2^64-1 unsigned
    assertThat(d.getSi32()).isEqualTo(-42);
    assertThat(d.getSi64()).isEqualTo(-42L);
    assertThat(d.getF32()).isEqualTo(0x12345678);
    assertThat(d.getF64()).isEqualTo(0x123456789ABCDEFL);
    assertThat(d.getSf32()).isEqualTo(-1);
    assertThat(d.getSf64()).isEqualTo(-1L);
    assertThat(d.getFp()).isEqualTo(3.14F);
    assertThat(d.getDp()).isEqualTo(2.71828);
    assertThat(d.getB()).isTrue();

    // String with embedded newline and quotes — survives the codegen's
    // descriptor-text → Java-literal re-escape.
    assertThat(d.getS()).isEqualTo("hello\nworld \"quoted\"");

    // Bytes default with high bytes — descriptor stores the C-escape form,
    // codegen unescapes at build time and emits a byte[] literal.
    assertThat(d.getBy()).isEqualTo(ByteString.copyFrom(
        new byte[]{(byte) 0xab, (byte) 0xcd, (byte) 0x0a}));

    // Enum defaults reference the Hue.BLUE_VALUE constant in the generated
    // code; the public getter returns the typed enum.
    assertThat(d.getE()).isEqualTo(Defaulted.Hue.BLUE);
    assertThat(d.getEValue()).isEqualTo(Defaulted.Hue.BLUE_VALUE);

    // Float specials — descriptor stores literal "nan" / "inf" / "-inf".
    assertThat(Float.isNaN(d.getFpNan())).isTrue();
    assertThat(d.getFpInf()).isEqualTo(Float.POSITIVE_INFINITY);
    assertThat(d.getFpMinf()).isEqualTo(Float.NEGATIVE_INFINITY);
    assertThat(Double.isNaN(d.getDpNan())).isTrue();

    // No has-flag is set on a fresh-default message.
    assertThat(d.hasI32()).isFalse();
    assertThat(d.hasS()).isFalse();
    assertThat(d.hasBy()).isFalse();
    assertThat(d.hasE()).isFalse();
    assertThat(d.hasFpNan()).isFalse();
  }

  @Test
  @DisplayName("clearXxx() returns to the declared default, not the type default")
  void clearReturnsToDeclaredDefault() {
    Defaulted.Builder b = Defaulted.newBuilder()
        .setI32(123)
        .setS("explicitly set")
        .setB(false);
    assertThat(b.hasI32()).isTrue();
    assertThat(b.getI32()).isEqualTo(123);
    assertThat(b.getS()).isEqualTo("explicitly set");
    assertThat(b.getB()).isFalse();

    b.clearI32().clearS().clearB();

    assertThat(b.hasI32()).isFalse();
    assertThat(b.getI32()).isEqualTo(-7);
    assertThat(b.hasS()).isFalse();
    assertThat(b.getS()).isEqualTo("hello\nworld \"quoted\"");
    assertThat(b.hasB()).isFalse();
    assertThat(b.getB()).isTrue();
  }

  @Test
  @DisplayName("unset defaulted fields emit zero bytes on the wire")
  void unsetDefaultedFieldsEmitNoBytes() {
    // proto2 spec: only fields with hasXxx() == true are written. Defaults are
    // a read-side concern. A message built with no setters serializes to an
    // empty byte sequence regardless of how many [default=...] fields it has.
    byte[] wire = Defaulted.newBuilder().build().toByteArray();
    assertThat(wire).isEmpty();
  }

  @Test
  @DisplayName("explicitly-set fields parse back even when the value matches the declared default")
  void explicitlySetMatchingDefaultPreservesHasFlag() throws Exception {
    // A field set to a value that happens to equal the declared default still
    // gets emitted (proto2 spec) and reads back with hasXxx() == true on the
    // other side. This matters when the receiver wants to distinguish "sender
    // explicitly chose the default" from "sender didn't set it at all".
    Defaulted src = Defaulted.newBuilder().setI32(-7).build();
    assertThat(src.hasI32()).isTrue();
    assertThat(src.toByteArray()).isNotEmpty();

    Defaulted dst = Defaulted.parseFrom(src.toByteArray());
    assertThat(dst.hasI32()).isTrue();
    assertThat(dst.getI32()).isEqualTo(-7);
  }

  @Test
  @DisplayName("cross-encoder: an unset defaulted message produces identical bytes in both codegens")
  void crossEncoderUnset() {
    byte[] javamin = Defaulted.newBuilder().build().toByteArray();
    byte[] google = dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Defaulted
        .newBuilder().build().toByteArray();
    assertThat(javamin).isEqualTo(google);
    assertThat(javamin).isEmpty();
  }

  @Test
  @DisplayName("cross-encoder: explicitly-set defaulted fields produce identical bytes in both codegens")
  void crossEncoderSet() {
    Defaulted javamin = Defaulted.newBuilder()
        .setI32(-7)               // matches the declared default
        .setS("hello\nworld \"quoted\"")
        .setBy(ByteString.copyFrom(new byte[]{(byte) 0xab, (byte) 0xcd, (byte) 0x0a}))
        .setE(Defaulted.Hue.BLUE)
        .setFpNan(Float.NaN)
        .build();
    dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Defaulted google =
        dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Defaulted.newBuilder()
            .setI32(-7)
            .setS("hello\nworld \"quoted\"")
            .setBy(com.google.protobuf.ByteString.copyFrom(
                new byte[]{(byte) 0xab, (byte) 0xcd, (byte) 0x0a}))
            .setE(dev.nemecec.protobuf.javamin.integration.crosscheck.gen.Defaulted.Hue.BLUE)
            .setFpNan(Float.NaN)
            .build();

    assertThat(javamin.toByteArray()).isEqualTo(google.toByteArray());
  }
}
