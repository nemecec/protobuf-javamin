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
package dev.nemecec.protobuf.javamin;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.CodedOutputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pin our wire-format encoder against Google's reference implementation. Each
 * test encodes the same value with both encoders and asserts the byte arrays
 * are identical. If anything ever drifts, this is the canary.
 *
 * <p>The values cover the boundary cases that actually matter for varints:
 * single-byte vs multi-byte; signed-vs-unsigned reinterpretations; zigzag for
 * sint32/sint64; negative int32 sign-extending to 10 bytes.
 */
class CodedOutputStreamCompatTest {

  @Test
  @DisplayName("varint int32 — single, multi-byte, negative-sign-extended")
  void int32Varints() throws Exception {
    for (int v : new int[]{0, 1, 127, 128, 16383, 16384, 0x7FFFFFFF, -1, -128, Integer.MIN_VALUE}) {
      assertThat(ours(out -> out.writeInt32(1, v)))
          .as("int32 = %d", v)
          .isEqualTo(theirs(out -> out.writeInt32(1, v)));
    }
  }

  @Test
  void int64Varints() throws Exception {
    for (long v : new long[]{0L, 1L, 127L, 128L, Long.MAX_VALUE, -1L, -128L, Long.MIN_VALUE}) {
      assertThat(ours(out -> out.writeInt64(1, v)))
          .as("int64 = %d", v)
          .isEqualTo(theirs(out -> out.writeInt64(1, v)));
    }
  }

  @Test
  @DisplayName("zigzag sint32 / sint64")
  void zigzag() throws Exception {
    for (int v : new int[]{0, 1, -1, 2, -2, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
      assertThat(ours(out -> out.writeSInt32(1, v))).isEqualTo(theirs(out -> out.writeSInt32(1, v)));
    }
    for (long v : new long[]{0L, 1L, -1L, 2L, -2L, Long.MAX_VALUE, Long.MIN_VALUE}) {
      assertThat(ours(out -> out.writeSInt64(1, v))).isEqualTo(theirs(out -> out.writeSInt64(1, v)));
    }
  }

  @Test
  void fixed32And64() throws Exception {
    assertThat(ours(out -> out.writeFixed32(1, 0xDEADBEEF))).isEqualTo(theirs(out -> out.writeFixed32(1, 0xDEADBEEF)));
    assertThat(ours(out -> out.writeFixed64(1, 0xCAFEBABEDEADBEEFL))).isEqualTo(theirs(out -> out.writeFixed64(1, 0xCAFEBABEDEADBEEFL)));
    assertThat(ours(out -> out.writeFloat(2, 3.14f))).isEqualTo(theirs(out -> out.writeFloat(2, 3.14f)));
    assertThat(ours(out -> out.writeDouble(3, 2.718281828))).isEqualTo(theirs(out -> out.writeDouble(3, 2.718281828)));
  }

  @Test
  @DisplayName("strings — ASCII, multibyte, surrogate pair")
  void strings() throws Exception {
    List<String> samples = Arrays.asList(
        "",
        "hello",
        "héllo",                // 2-byte UTF-8 chars
        "日本語",                 // 3-byte
        new String(Character.toChars(0x1F600)) // 4-byte (emoji, surrogate pair)
    );
    for (String s : samples) {
      assertThat(ours(out -> out.writeString(1, s))).as("'%s'", s).isEqualTo(theirs(out -> out.writeString(1, s)));
    }
  }

  @Test
  void bytes() throws Exception {
    byte[] payload = {0, 1, 2, 3, 4, (byte) 0xFF};
    ByteString ours = ByteString.copyFrom(payload);
    com.google.protobuf.ByteString theirs = com.google.protobuf.ByteString.copyFrom(payload);
    assertThat(ours(out -> out.writeBytes(1, ours))).isEqualTo(theirs(out -> out.writeBytes(1, theirs)));
  }

  @Test
  @DisplayName("computeXxxSize matches actual emitted byte count")
  void sizesAgreeWithEmittedBytes() throws Exception {
    int actualBytes = ours(out -> {
      out.writeInt32(1, 12345);
      out.writeString(2, "hello");
      out.writeBool(3, true);
    }).length;
    int computed =
        dev.nemecec.protobuf.javamin.CodedOutputStream.computeInt32Size(1, 12345)
            + dev.nemecec.protobuf.javamin.CodedOutputStream.computeStringSize(2, "hello")
            + dev.nemecec.protobuf.javamin.CodedOutputStream.computeBoolSize(3, true);
    assertThat(computed).isEqualTo(actualBytes);
  }

  @Test
  @DisplayName("computeStringSize across all UTF-8 byte-width tiers — agrees with Google and with the actual encoded length")
  void computeStringSizeUtf8Tiers() {
    // Two arguments per case: string, expected number of UTF-8 bytes for the body.
    // The expected count is independently verified by `s.getBytes(UTF_8).length`.
    String[] samples = {
        "",                                            // empty
        "hello",                                       // pure ASCII (1 byte/char)
        "héllo",                                       // 2-byte char in BMP
        "日本語",                                        // 3-byte chars in BMP
        new String(Character.toChars(0x1F600)),       // surrogate pair → 4 UTF-8 bytes
        "a" + new String(Character.toChars(0x1F600)) + "b",  // pair + ASCII surrounding
        "\uD83D",                                      // unpaired high surrogate → U+FFFD = 3 bytes
        "\uDE00",                                      // unpaired low surrogate → U+FFFD = 3 bytes
    };
    for (String s : samples) {
      int actualUtf8Bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
      int oursTotal = dev.nemecec.protobuf.javamin.CodedOutputStream.computeStringSize(1, s);
      int theirsTotal = com.google.protobuf.CodedOutputStream.computeStringSize(1, s);

      // Tag is 1 byte for field number 1, plus a varint length prefix, plus the body.
      int expectedTotal = 1
          + dev.nemecec.protobuf.javamin.CodedOutputStream.computeRawVarint32Size(actualUtf8Bytes)
          + actualUtf8Bytes;

      assertThat(oursTotal).as("ours vs actual encoded length for '%s'", s).isEqualTo(expectedTotal);
      assertThat(oursTotal).as("ours vs Google for '%s'", s).isEqualTo(theirsTotal);
    }
  }

  // --- harness ---

  @FunctionalInterface
  interface OursFn {
    void apply(dev.nemecec.protobuf.javamin.CodedOutputStream out) throws Exception;
  }

  @FunctionalInterface
  interface TheirsFn {
    void apply(CodedOutputStream out) throws Exception;
  }

  private static byte[] ours(OursFn fn) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    dev.nemecec.protobuf.javamin.CodedOutputStream out =
        dev.nemecec.protobuf.javamin.CodedOutputStream.newInstance(baos);
    fn.apply(out);
    out.flush();
    return baos.toByteArray();
  }

  private static byte[] theirs(TheirsFn fn) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    CodedOutputStream out = CodedOutputStream.newInstance(baos);
    fn.apply(out);
    out.flush();
    return baos.toByteArray();
  }
}
