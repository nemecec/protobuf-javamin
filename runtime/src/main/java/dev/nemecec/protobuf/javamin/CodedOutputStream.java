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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Low-level proto wire-format encoder. Generated message code calls this from
 * {@code writeTo(CodedOutputStream)}.
 *
 * <p>Two backing modes:
 * <ul>
 *   <li>{@link #newInstance(byte[])} writes into a caller-supplied {@code byte[]}.
 *       The caller is expected to size the array via {@link #computeRawVarint32Size(int)}
 *       and the {@code computeXxxSize(...)} family, then verify {@link #spaceLeft()}
 *       is zero after encode.</li>
 *   <li>{@link #newInstance(OutputStream)} buffers writes and flushes to the stream
 *       on overflow / {@link #flush()}.</li>
 * </ul>
 *
 * <p>The static {@code computeXxxSize(...)} methods are wire-format-exact: their
 * return values match the byte count produced by the corresponding {@code writeXxx}
 * call. Generated {@code getSerializedSize()} uses these to pre-size buffers.
 */
public final class CodedOutputStream {
  private final byte[] buffer;
  private final int limit;
  private int position;
  private final OutputStream output; // null when writing into a fixed byte[]

  private CodedOutputStream(byte[] buffer, int offset, int length) {
    this.buffer = buffer;
    this.limit = offset + length;
    this.position = offset;
    this.output = null;
  }

  private CodedOutputStream(OutputStream output, byte[] buffer) {
    this.buffer = buffer;
    this.limit = buffer.length;
    this.position = 0;
    this.output = output;
  }

  public static CodedOutputStream newInstance(byte[] flat) {
    return new CodedOutputStream(flat, 0, flat.length);
  }

  public static CodedOutputStream newInstance(byte[] flat, int offset, int length) {
    return new CodedOutputStream(flat, offset, length);
  }

  public static CodedOutputStream newInstance(OutputStream output) {
    return new CodedOutputStream(output, new byte[4096]);
  }

  public int spaceLeft() {
    return limit - position;
  }

  public void flush() throws IOException {
    if (output != null && position > 0) {
      output.write(buffer, 0, position);
      position = 0;
    }
  }

  // --- field writers (tag + value) ---------------------------------------

  public void writeInt32(int fieldNumber, int value) throws IOException {
    writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
    writeInt32NoTag(value);
  }

  public void writeInt64(int fieldNumber, long value) throws IOException {
    writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
    writeInt64NoTag(value);
  }

  public void writeUInt32(int fieldNumber, int value) throws IOException {
    writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
    writeUInt32NoTag(value);
  }

  public void writeUInt64(int fieldNumber, long value) throws IOException {
    writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
    writeUInt64NoTag(value);
  }

  public void writeSInt32(int fieldNumber, int value) throws IOException {
    writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
    writeSInt32NoTag(value);
  }

  public void writeSInt32NoTag(int value) throws IOException {
    writeUInt32NoTag(encodeZigZag32(value));
  }

  public void writeSInt64(int fieldNumber, long value) throws IOException {
    writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
    writeSInt64NoTag(value);
  }

  public void writeSInt64NoTag(long value) throws IOException {
    writeUInt64NoTag(encodeZigZag64(value));
  }

  public void writeBool(int fieldNumber, boolean value) throws IOException {
    writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
    writeBoolNoTag(value);
  }

  public void writeBoolNoTag(boolean value) throws IOException {
    writeRawByte(value ? 1 : 0);
  }

  public void writeEnum(int fieldNumber, int value) throws IOException {
    writeTag(fieldNumber, WireFormat.WIRETYPE_VARINT);
    writeEnumNoTag(value);
  }

  public void writeEnumNoTag(int value) throws IOException {
    writeInt32NoTag(value);
  }

  public void writeFixed32(int fieldNumber, int value) throws IOException {
    writeTag(fieldNumber, WireFormat.WIRETYPE_FIXED32);
    writeFixed32NoTag(value);
  }

  public void writeSFixed32(int fieldNumber, int value) throws IOException {
    writeTag(fieldNumber, WireFormat.WIRETYPE_FIXED32);
    writeSFixed32NoTag(value);
  }

  public void writeSFixed32NoTag(int value) throws IOException {
    writeFixed32NoTag(value);
  }

  public void writeFloat(int fieldNumber, float value) throws IOException {
    writeTag(fieldNumber, WireFormat.WIRETYPE_FIXED32);
    writeFloatNoTag(value);
  }

  public void writeFloatNoTag(float value) throws IOException {
    writeFixed32NoTag(Float.floatToRawIntBits(value));
  }

  public void writeFixed64(int fieldNumber, long value) throws IOException {
    writeTag(fieldNumber, WireFormat.WIRETYPE_FIXED64);
    writeFixed64NoTag(value);
  }

  public void writeSFixed64(int fieldNumber, long value) throws IOException {
    writeTag(fieldNumber, WireFormat.WIRETYPE_FIXED64);
    writeSFixed64NoTag(value);
  }

  public void writeSFixed64NoTag(long value) throws IOException {
    writeFixed64NoTag(value);
  }

  public void writeDouble(int fieldNumber, double value) throws IOException {
    writeTag(fieldNumber, WireFormat.WIRETYPE_FIXED64);
    writeDoubleNoTag(value);
  }

  public void writeDoubleNoTag(double value) throws IOException {
    writeFixed64NoTag(Double.doubleToRawLongBits(value));
  }

  public void writeString(int fieldNumber, String value) throws IOException {
    writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
    byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
    writeUInt32NoTag(utf8.length);
    writeRawBytes(utf8);
  }

  public void writeBytes(int fieldNumber, ByteString value) throws IOException {
    writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
    byte[] data = value.internalArray();
    writeUInt32NoTag(data.length);
    writeRawBytes(data);
  }

  public void writeByteArray(int fieldNumber, byte[] value) throws IOException {
    writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
    writeUInt32NoTag(value.length);
    writeRawBytes(value);
  }

  /** Write an embedded message: tag + length + body (the caller serializes the body). */
  public void writeMessage(int fieldNumber, MessageLite value) throws IOException {
    writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
    writeUInt32NoTag(value.getSerializedSize());
    value.writeTo(this);
  }

  // --- raw / no-tag writers ----------------------------------------------

  public void writeTag(int fieldNumber, int wireType) throws IOException {
    writeUInt32NoTag(WireFormat.makeTag(fieldNumber, wireType));
  }

  public void writeInt32NoTag(int value) throws IOException {
    if (value >= 0) {
      writeUInt32NoTag(value);
    } else {
      // Negative int32 sign-extends to int64 on the wire.
      writeUInt64NoTag(value);
    }
  }

  public void writeInt64NoTag(long value) throws IOException {
    writeUInt64NoTag(value);
  }

  public void writeUInt32NoTag(int value) throws IOException {
    while ((value & ~0x7F) != 0) {
      writeRawByte((value & 0x7F) | 0x80);
      value >>>= 7;
    }
    writeRawByte(value);
  }

  public void writeUInt64NoTag(long value) throws IOException {
    while ((value & ~0x7FL) != 0) {
      writeRawByte(((int) value & 0x7F) | 0x80);
      value >>>= 7;
    }
    writeRawByte((int) value);
  }

  public void writeFixed32NoTag(int value) throws IOException {
    writeRawByte(value & 0xFF);
    writeRawByte((value >> 8) & 0xFF);
    writeRawByte((value >> 16) & 0xFF);
    writeRawByte((value >> 24) & 0xFF);
  }

  public void writeFixed64NoTag(long value) throws IOException {
    writeRawByte((int) value & 0xFF);
    writeRawByte((int) (value >> 8) & 0xFF);
    writeRawByte((int) (value >> 16) & 0xFF);
    writeRawByte((int) (value >> 24) & 0xFF);
    writeRawByte((int) (value >> 32) & 0xFF);
    writeRawByte((int) (value >> 40) & 0xFF);
    writeRawByte((int) (value >> 48) & 0xFF);
    writeRawByte((int) (value >> 56) & 0xFF);
  }

  public void writeRawByte(int value) throws IOException {
    if (position == limit) {
      if (output == null) {
        throw new IOException("CodedOutputStream byte[] buffer overflow at position=" + position);
      }
      flush();
    }
    buffer[position++] = (byte) value;
  }

  public void writeRawBytes(byte[] value) throws IOException {
    writeRawBytes(value, 0, value.length);
  }

  public void writeRawBytes(byte[] value, int offset, int length) throws IOException {
    if (output == null) {
      // Flat-buffer mode: must fit.
      if (limit - position < length) {
        throw new IOException("CodedOutputStream byte[] buffer would overflow by "
            + (length - (limit - position)) + " bytes");
      }
      System.arraycopy(value, offset, buffer, position, length);
      position += length;
    } else {
      // Stream mode: write in chunks bounded by buffer size.
      int remaining = length;
      int sourcePos = offset;
      while (remaining > 0) {
        if (position == limit) flush();
        int chunk = Math.min(remaining, limit - position);
        System.arraycopy(value, sourcePos, buffer, position, chunk);
        position += chunk;
        sourcePos += chunk;
        remaining -= chunk;
      }
    }
  }

  // --- size computation --------------------------------------------------
  // Used by generated getSerializedSize(). Each computeXxxSize returns the
  // exact byte count writeXxx would produce.

  public static int computeTagSize(int fieldNumber) {
    return computeRawVarint32Size(WireFormat.makeTag(fieldNumber, 0));
  }

  public static int computeRawVarint32Size(int value) {
    if ((value & (~0 << 7)) == 0) return 1;
    if ((value & (~0 << 14)) == 0) return 2;
    if ((value & (~0 << 21)) == 0) return 3;
    if ((value & (~0 << 28)) == 0) return 4;
    return 5;
  }

  public static int computeRawVarint64Size(long value) {
    if ((value & (~0L << 7)) == 0) return 1;
    if ((value & (~0L << 14)) == 0) return 2;
    if ((value & (~0L << 21)) == 0) return 3;
    if ((value & (~0L << 28)) == 0) return 4;
    if ((value & (~0L << 35)) == 0) return 5;
    if ((value & (~0L << 42)) == 0) return 6;
    if ((value & (~0L << 49)) == 0) return 7;
    if ((value & (~0L << 56)) == 0) return 8;
    if ((value & (~0L << 63)) == 0) return 9;
    return 10;
  }

  public static int computeInt32SizeNoTag(int value) {
    return value >= 0 ? computeRawVarint32Size(value) : 10;
  }

  public static int computeInt64SizeNoTag(long value) {
    return computeRawVarint64Size(value);
  }

  public static int computeUInt32SizeNoTag(int value) {
    return computeRawVarint32Size(value);
  }

  public static int computeUInt64SizeNoTag(long value) {
    return computeRawVarint64Size(value);
  }

  public static int computeSInt32SizeNoTag(int value) {
    return computeRawVarint32Size(encodeZigZag32(value));
  }

  public static int computeSInt64SizeNoTag(long value) {
    return computeRawVarint64Size(encodeZigZag64(value));
  }

  public static int computeBoolSizeNoTag(boolean value) {
    return 1;
  }

  public static int computeEnumSizeNoTag(int value) {
    return computeInt32SizeNoTag(value);
  }

  public static int computeFixed32SizeNoTag(int value) {
    return 4;
  }

  public static int computeSFixed32SizeNoTag(int value) {
    return 4;
  }

  public static int computeFloatSizeNoTag(float value) {
    return 4;
  }

  public static int computeFixed64SizeNoTag(long value) {
    return 8;
  }

  public static int computeSFixed64SizeNoTag(long value) {
    return 8;
  }

  public static int computeDoubleSizeNoTag(double value) {
    return 8;
  }

  public static int computeInt32Size(int fieldNumber, int value) {
    return computeTagSize(fieldNumber) + computeInt32SizeNoTag(value);
  }

  public static int computeInt64Size(int fieldNumber, long value) {
    return computeTagSize(fieldNumber) + computeInt64SizeNoTag(value);
  }

  public static int computeUInt32Size(int fieldNumber, int value) {
    return computeTagSize(fieldNumber) + computeUInt32SizeNoTag(value);
  }

  public static int computeUInt64Size(int fieldNumber, long value) {
    return computeTagSize(fieldNumber) + computeUInt64SizeNoTag(value);
  }

  public static int computeSInt32Size(int fieldNumber, int value) {
    return computeTagSize(fieldNumber) + computeSInt32SizeNoTag(value);
  }

  public static int computeSInt64Size(int fieldNumber, long value) {
    return computeTagSize(fieldNumber) + computeSInt64SizeNoTag(value);
  }

  public static int computeBoolSize(int fieldNumber, boolean value) {
    return computeTagSize(fieldNumber) + computeBoolSizeNoTag(value);
  }

  public static int computeEnumSize(int fieldNumber, int value) {
    return computeTagSize(fieldNumber) + computeEnumSizeNoTag(value);
  }

  public static int computeFixed32Size(int fieldNumber, int value) {
    return computeTagSize(fieldNumber) + computeFixed32SizeNoTag(value);
  }

  public static int computeSFixed32Size(int fieldNumber, int value) {
    return computeTagSize(fieldNumber) + computeSFixed32SizeNoTag(value);
  }

  public static int computeFloatSize(int fieldNumber, float value) {
    return computeTagSize(fieldNumber) + computeFloatSizeNoTag(value);
  }

  public static int computeFixed64Size(int fieldNumber, long value) {
    return computeTagSize(fieldNumber) + computeFixed64SizeNoTag(value);
  }

  public static int computeSFixed64Size(int fieldNumber, long value) {
    return computeTagSize(fieldNumber) + computeSFixed64SizeNoTag(value);
  }

  public static int computeDoubleSize(int fieldNumber, double value) {
    return computeTagSize(fieldNumber) + computeDoubleSizeNoTag(value);
  }

  public static int computeStringSize(int fieldNumber, String value) {
    // We explicitly encode here (rather than predict the byte count from char
    // codepoints) because {@code String.getBytes(UTF_8)} replaces unpaired
    // surrogates with the JDK-default replacement (one byte, '?', on every JDK
    // we've checked) and that's what {@link #writeString} will emit. Trying to
    // predict the count from codepoints risks disagreeing with the encoder for
    // edge-case inputs and producing buffer-size mismatches at write time.
    int utf8Bytes = value.getBytes(StandardCharsets.UTF_8).length;
    return computeTagSize(fieldNumber) + computeRawVarint32Size(utf8Bytes) + utf8Bytes;
  }

  public static int computeBytesSize(int fieldNumber, ByteString value) {
    int len = value.size();
    return computeTagSize(fieldNumber) + computeRawVarint32Size(len) + len;
  }

  public static int computeByteArraySize(int fieldNumber, byte[] value) {
    return computeTagSize(fieldNumber) + computeRawVarint32Size(value.length) + value.length;
  }

  public static int computeMessageSize(int fieldNumber, MessageLite value) {
    int size = value.getSerializedSize();
    return computeTagSize(fieldNumber) + computeRawVarint32Size(size) + size;
  }

  // --- helpers -----------------------------------------------------------

  private static int encodeZigZag32(int n) {
    return (n << 1) ^ (n >> 31);
  }

  private static long encodeZigZag64(long n) {
    return (n << 1) ^ (n >> 63);
  }

}
