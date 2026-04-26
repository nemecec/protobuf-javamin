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
package dev.nemecec.protobuf.javamin.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the two helpers that translate proto2 {@code [default = ...]} values
 * from descriptor form into Java source literals. Both are exercised
 * end-to-end via {@code DefaultedRoundtripTest}, but a focused unit test
 * pins the corner cases (control chars, every C-escape, hex/octal width
 * boundaries) without recompiling the integration-tests module.
 */
class DefaultLiteralEscapesTest {

  @Test
  @DisplayName("javaStringEscape — printables pass through, specials and controls escape")
  void javaStringEscapeBehaviour() {
    // Plain ASCII passes through unchanged.
    assertThat(MessageWriter.javaStringEscape("hello world")).isEqualTo("hello world");

    // The seven shorthand escapes recognised by Java string literals.
    assertThat(MessageWriter.javaStringEscape("\\")).isEqualTo("\\\\");
    assertThat(MessageWriter.javaStringEscape("\"")).isEqualTo("\\\"");
    assertThat(MessageWriter.javaStringEscape("\n")).isEqualTo("\\n");
    assertThat(MessageWriter.javaStringEscape("\r")).isEqualTo("\\r");
    assertThat(MessageWriter.javaStringEscape("\t")).isEqualTo("\\t");
    assertThat(MessageWriter.javaStringEscape("\b")).isEqualTo("\\b");
    assertThat(MessageWriter.javaStringEscape("\f")).isEqualTo("\\f");

    // Other low-control chars fall through to the backslash-u path.
    assertThat(MessageWriter.javaStringEscape(String.valueOf((char) 0x01)))
        .isEqualTo("\\u0001");
    assertThat(MessageWriter.javaStringEscape(String.valueOf((char) 0x7F)))
        .isEqualTo("\\u007f");

    // High Unicode chars are passed through (Java source files are UTF-8).
    assertThat(MessageWriter.javaStringEscape("«")).isEqualTo("«");

    // A realistic mix — the kind of value a descriptor might hand back for
    // [default = "say \"hi\"\nthere"]:
    assertThat(MessageWriter.javaStringEscape("say \"hi\"\nthere"))
        .isEqualTo("say \\\"hi\\\"\\nthere");
  }

  @Test
  @DisplayName("cEscapeUnescape — C-style escapes decoded to raw bytes")
  void cEscapeUnescapeBehaviour() {
    // No escapes — chars round-trip as bytes (descriptor stores one byte per char).
    assertThat(MessageWriter.cEscapeUnescape("abc"))
        .containsExactly((byte) 'a', (byte) 'b', (byte) 'c');

    // The standard single-letter C escapes.
    assertThat(MessageWriter.cEscapeUnescape("\\a")).containsExactly((byte) 0x07);
    assertThat(MessageWriter.cEscapeUnescape("\\b")).containsExactly((byte) 0x08);
    assertThat(MessageWriter.cEscapeUnescape("\\f")).containsExactly((byte) 0x0C);
    assertThat(MessageWriter.cEscapeUnescape("\\n")).containsExactly((byte) 0x0A);
    assertThat(MessageWriter.cEscapeUnescape("\\r")).containsExactly((byte) 0x0D);
    assertThat(MessageWriter.cEscapeUnescape("\\t")).containsExactly((byte) 0x09);
    assertThat(MessageWriter.cEscapeUnescape("\\v")).containsExactly((byte) 0x0B);
    assertThat(MessageWriter.cEscapeUnescape("\\\\")).containsExactly((byte) 0x5C);
    assertThat(MessageWriter.cEscapeUnescape("\\'")).containsExactly((byte) 0x27);
    assertThat(MessageWriter.cEscapeUnescape("\\\"")).containsExactly((byte) 0x22);
    assertThat(MessageWriter.cEscapeUnescape("\\?")).containsExactly((byte) 0x3F);

    // Hex escapes — 1 or 2 digits, lower or upper case.
    assertThat(MessageWriter.cEscapeUnescape("\\x7")).containsExactly((byte) 0x07);
    assertThat(MessageWriter.cEscapeUnescape("\\xab")).containsExactly((byte) 0xab);
    assertThat(MessageWriter.cEscapeUnescape("\\xAB")).containsExactly((byte) 0xab);
    // Three-digit hex stops after two digits — the third is a literal byte.
    assertThat(MessageWriter.cEscapeUnescape("\\xab1"))
        .containsExactly((byte) 0xab, (byte) '1');

    // Octal escapes — 1–3 digits.
    assertThat(MessageWriter.cEscapeUnescape("\\0")).containsExactly((byte) 0x00);
    assertThat(MessageWriter.cEscapeUnescape("\\12")).containsExactly((byte) 0x0A);
    assertThat(MessageWriter.cEscapeUnescape("\\012")).containsExactly((byte) 0x0A);
    assertThat(MessageWriter.cEscapeUnescape("\\377")).containsExactly((byte) 0xff);
    // Four-digit octal stops after three digits.
    assertThat(MessageWriter.cEscapeUnescape("\\01234"))
        .containsExactly((byte) 0x0A, (byte) '3', (byte) '4');

    // Realistic descriptor-stored bytes default — high-byte values escaped:
    assertThat(MessageWriter.cEscapeUnescape("\\xab\\xcd\\012"))
        .containsExactly((byte) 0xab, (byte) 0xcd, (byte) 0x0a);
  }

  @Test
  @DisplayName("cEscapeUnescape — malformed input throws IllegalStateException")
  void cEscapeUnescapeRejectsMalformedInput() {
    assertThatThrownBy(() -> MessageWriter.cEscapeUnescape("trailing\\"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> MessageWriter.cEscapeUnescape("\\xZZ"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> MessageWriter.cEscapeUnescape("\\q"))
        .isInstanceOf(IllegalStateException.class);
  }
}
