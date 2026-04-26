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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Decode bytes that Google's encoder produced, with our decoder. If the wire
 * formats match (proven separately by {@link CodedOutputStreamCompatTest}),
 * decoding their output exercises every read path against canonical input.
 */
class CodedInputStreamCompatTest {

  @Test
  @DisplayName("read varint int32 — boundary values")
  void int32Varints() throws Exception {
    for (int v : new int[]{0, 1, 127, 128, 16383, 16384, 0x7FFFFFFF, -1, -128, Integer.MIN_VALUE}) {
      byte[] wire = encodedByGoogle(out -> out.writeInt32(1, v));
      CodedInputStream in = CodedInputStream.newInstance(wire);
      int tag = in.readTag();
      assertThat(WireFormat.getTagFieldNumber(tag)).isEqualTo(1);
      assertThat(in.readInt32()).as("int32 = %d", v).isEqualTo(v);
      assertThat(in.isAtEnd()).isTrue();
    }
  }

  @Test
  void multipleFields() throws Exception {
    byte[] wire = encodedByGoogle(out -> {
      out.writeInt32(1, 42);
      out.writeString(2, "hello");
      out.writeBool(3, true);
      out.writeDouble(4, 2.718281828);
    });
    CodedInputStream in = CodedInputStream.newInstance(wire);

    int tag;
    int got1 = -1; String got2 = null; boolean got3 = false; double got4 = 0;
    while ((tag = in.readTag()) != 0) {
      switch (WireFormat.getTagFieldNumber(tag)) {
        case 1: got1 = in.readInt32(); break;
        case 2: got2 = in.readString(); break;
        case 3: got3 = in.readBool(); break;
        case 4: got4 = in.readDouble(); break;
        default: in.skipField(tag);
      }
    }
    assertThat(got1).isEqualTo(42);
    assertThat(got2).isEqualTo("hello");
    assertThat(got3).isTrue();
    assertThat(got4).isEqualTo(2.718281828);
  }

  @Test
  @DisplayName("skipField walks past unknown wire types without consuming subsequent fields")
  void skipUnknown() throws Exception {
    byte[] wire = encodedByGoogle(out -> {
      out.writeInt32(1, 100);
      out.writeString(2, "skip-me");      // we'll pretend field 2 is unknown
      out.writeFixed64(3, 0xDEADBEEFL);
      out.writeFloat(4, 1.5f);             // unknown
      out.writeBool(5, true);
    });
    CodedInputStream in = CodedInputStream.newInstance(wire);
    int got1 = -1; long got3 = 0; boolean got5 = false;
    int tag;
    while ((tag = in.readTag()) != 0) {
      switch (WireFormat.getTagFieldNumber(tag)) {
        case 1: got1 = in.readInt32(); break;
        case 3: got3 = in.readFixed64(); break;
        case 5: got5 = in.readBool(); break;
        default: in.skipField(tag);  // fields 2 and 4
      }
    }
    assertThat(got1).isEqualTo(100);
    assertThat(got3).isEqualTo(0xDEADBEEFL);
    assertThat(got5).isTrue();
  }

  @Test
  @DisplayName("malformed varint > 10 bytes is rejected, not silently truncated")
  void malformedOverlongVarint() {
    // 11 continuation bytes followed by a terminator. A valid varint is at
    // most 10 bytes, so this must throw rather than return a garbage value
    // and leave the stream pointing past the malformed run.
    byte[] wire = new byte[12];
    for (int i = 0; i < 11; i++) wire[i] = (byte) 0xFF;
    wire[11] = 0x01;
    CodedInputStream in = CodedInputStream.newInstance(wire);
    org.junit.jupiter.api.Assertions.assertThrows(
        InvalidProtocolBufferException.class, in::readRawVarint32);
  }

  @Test
  void streamingInputWithRefill() throws Exception {
    // Construct ~10 KB of fields so the decoder has to refill its 4 KB buffer multiple times.
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    CodedOutputStream out = CodedOutputStream.newInstance(baos);
    for (int i = 0; i < 1000; i++) {
      out.writeInt32(1, i);
    }
    out.flush();
    byte[] wire = baos.toByteArray();

    CodedInputStream in = CodedInputStream.newInstance(new ByteArrayInputStream(wire));
    int n = 0, tag;
    while ((tag = in.readTag()) != 0) {
      assertThat(in.readInt32()).isEqualTo(n++);
    }
    assertThat(n).isEqualTo(1000);
  }

  @FunctionalInterface
  interface TheirsFn {
    void apply(CodedOutputStream out) throws Exception;
  }

  private static byte[] encodedByGoogle(TheirsFn fn) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    CodedOutputStream out = CodedOutputStream.newInstance(baos);
    fn.apply(out);
    out.flush();
    return baos.toByteArray();
  }
}
