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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Low-level proto wire-format decoder. Generated message {@code mergeFrom(CodedInputStream)}
 * is the only caller.
 *
 * <p>Two backing modes:
 * <ul>
 *   <li>{@link #newInstance(byte[])}: zero-copy reads against a fully-buffered array.</li>
 *   <li>{@link #newInstance(InputStream)}: pulls bytes through a 4 KB refill buffer.</li>
 * </ul>
 *
 * <p>{@link #pushLimit(int)} / {@link #popLimit(int)} bound a sub-region for parsing
 * embedded messages without slicing buffers.
 */
public final class CodedInputStream {
  private static final int BUFFER_SIZE = 4096;
  private static final int DEFAULT_RECURSION_LIMIT = 100;
  private static final int DEFAULT_SIZE_LIMIT = Integer.MAX_VALUE;

  private final byte[] buffer;
  private int bufferPos;
  private int bufferSize;
  /**
   * Total bytes consumed from previous fills. {@code totalBytesRead - bufferSize + bufferPos}
   * is the running absolute position. We need it because limits are expressed
   * as absolute byte offsets so the same bookkeeping works whether the source is
   * a flat array or a streaming refill.
   */
  private int bufferSizeAfterLimit;
  private int totalBytesRetired;
  private int currentLimit = Integer.MAX_VALUE;
  private int recursionDepth;
  private final int recursionLimit = DEFAULT_RECURSION_LIMIT;
  private int sizeLimit = DEFAULT_SIZE_LIMIT;
  private int lastTag;

  private final InputStream input; // null if wrapping a byte[]

  private CodedInputStream(byte[] buffer, int offset, int length) {
    this.buffer = buffer;
    this.bufferPos = offset;
    this.bufferSize = offset + length;
    this.input = null;
  }

  private CodedInputStream(InputStream input) {
    this.input = input;
    this.buffer = new byte[BUFFER_SIZE];
    this.bufferPos = 0;
    this.bufferSize = 0;
  }

  public static CodedInputStream newInstance(byte[] flat) {
    return new CodedInputStream(flat, 0, flat.length);
  }

  public static CodedInputStream newInstance(byte[] flat, int offset, int length) {
    return new CodedInputStream(flat, offset, length);
  }

  public static CodedInputStream newInstance(InputStream input) {
    return new CodedInputStream(input);
  }

  public void setSizeLimit(int sizeLimit) {
    this.sizeLimit = sizeLimit;
  }

  // --- top-level read loop helpers ---------------------------------------

  /**
   * Read the next field tag. Returns 0 if the stream has ended cleanly (i.e. at
   * a real message boundary), so generated parsers can write
   * {@code while ((tag = input.readTag()) != 0)}.
   */
  public int readTag() throws IOException {
    if (isAtEnd()) {
      lastTag = 0;
      return 0;
    }
    lastTag = readRawVarint32();
    if (WireFormat.getTagFieldNumber(lastTag) == 0) {
      throw InvalidProtocolBufferException.invalidTag();
    }
    return lastTag;
  }

  public int getLastTag() {
    return lastTag;
  }

  /** Skip an unknown field whose tag was just consumed. Returns false at end-group. */
  public boolean skipField(int tag) throws IOException {
    switch (WireFormat.getTagWireType(tag)) {
      case WireFormat.WIRETYPE_VARINT:
        readRawVarint64();
        return true;
      case WireFormat.WIRETYPE_FIXED64:
        readRawLittleEndian64();
        return true;
      case WireFormat.WIRETYPE_LENGTH_DELIMITED:
        skipRawBytes(readRawVarint32());
        return true;
      case WireFormat.WIRETYPE_START_GROUP:
        skipMessage();
        // A matching END_GROUP must follow. We don't validate the field number
        // matches the START_GROUP tag — proto2 spec only requires the wire type
        // pair, and groups are deprecated and not used in our schemas.
        return true;
      case WireFormat.WIRETYPE_END_GROUP:
        return false;
      case WireFormat.WIRETYPE_FIXED32:
        readRawLittleEndian32();
        return true;
      default:
        throw InvalidProtocolBufferException.invalidWireType();
    }
  }

  private void skipMessage() throws IOException {
    while (true) {
      int tag = readTag();
      if (tag == 0 || !skipField(tag)) return;
    }
  }

  // --- typed readers (no tag — caller already consumed the tag) -----------

  public int readInt32() throws IOException {
    return readRawVarint32();
  }

  public long readInt64() throws IOException {
    return readRawVarint64();
  }

  public int readUInt32() throws IOException {
    return readRawVarint32();
  }

  public long readUInt64() throws IOException {
    return readRawVarint64();
  }

  public int readSInt32() throws IOException {
    return decodeZigZag32(readRawVarint32());
  }

  public long readSInt64() throws IOException {
    return decodeZigZag64(readRawVarint64());
  }

  public boolean readBool() throws IOException {
    return readRawVarint64() != 0;
  }

  public int readEnum() throws IOException {
    return readRawVarint32();
  }

  public int readFixed32() throws IOException {
    return readRawLittleEndian32();
  }

  public int readSFixed32() throws IOException {
    return readRawLittleEndian32();
  }

  public float readFloat() throws IOException {
    return Float.intBitsToFloat(readRawLittleEndian32());
  }

  public long readFixed64() throws IOException {
    return readRawLittleEndian64();
  }

  public long readSFixed64() throws IOException {
    return readRawLittleEndian64();
  }

  public double readDouble() throws IOException {
    return Double.longBitsToDouble(readRawLittleEndian64());
  }

  public String readString() throws IOException {
    int size = readRawVarint32();
    if (size < 0) throw InvalidProtocolBufferException.negativeSize();
    if (size == 0) return "";
    if (size <= remainingInBuffer()) {
      String s = new String(buffer, bufferPos, size, StandardCharsets.UTF_8);
      bufferPos += size;
      return s;
    }
    return new String(readRawBytesSlowPath(size), StandardCharsets.UTF_8);
  }

  public ByteString readBytes() throws IOException {
    int size = readRawVarint32();
    if (size < 0) throw InvalidProtocolBufferException.negativeSize();
    if (size == 0) return ByteString.EMPTY;
    if (size <= remainingInBuffer()) {
      byte[] copy = new byte[size];
      System.arraycopy(buffer, bufferPos, copy, 0, size);
      bufferPos += size;
      return ByteString.wrap(copy);
    }
    return ByteString.wrap(readRawBytesSlowPath(size));
  }

  public byte[] readByteArray() throws IOException {
    int size = readRawVarint32();
    if (size < 0) throw InvalidProtocolBufferException.negativeSize();
    if (size == 0) return new byte[0];
    if (size <= remainingInBuffer()) {
      byte[] copy = new byte[size];
      System.arraycopy(buffer, bufferPos, copy, 0, size);
      bufferPos += size;
      return copy;
    }
    return readRawBytesSlowPath(size);
  }

  /**
   * Read an embedded message into the supplied target. The caller will typically
   * pass {@code newBuilder()} for that message type or a fresh instance.
   */
  public <T extends MessageLite> void readMessage(MessageLite.Builder<T, ?> target) throws IOException {
    if (recursionDepth >= recursionLimit) {
      throw new InvalidProtocolBufferException("Protocol message had too many levels of nesting.");
    }
    int length = readRawVarint32();
    int oldLimit = pushLimit(length);
    recursionDepth++;
    try {
      target.mergeFrom(this);
      if (!isAtEnd()) {
        throw InvalidProtocolBufferException.truncatedMessage();
      }
    } finally {
      // Restore depth + limit even if the inner parse threw, so the same
      // CodedInputStream remains usable (e.g. for callers that catch parse
      // failures and continue to drain the surrounding message).
      recursionDepth--;
      popLimit(oldLimit);
    }
  }

  // --- raw varint / fixed reads ------------------------------------------

  /** Read a varint; the negative-int32 sign-extended-to-int64 case is
   *  handled by the caller-side readInt32() trimming via cast. */
  public int readRawVarint32() throws IOException {
    // Hot path: fits in the buffer and is at most 5 bytes.
    int pos = bufferPos;
    if (bufferSize != pos) {
      int x;
      if ((x = buffer[pos++]) >= 0) {
        bufferPos = pos;
        return x;
      } else if (bufferSize - pos < 9) {
        return (int) readRawVarint64SlowPath();
      } else if ((x ^= (buffer[pos++] << 7)) < 0) {
        x ^= (~0 << 7);
      } else if ((x ^= (buffer[pos++] << 14)) >= 0) {
        x ^= (~0 << 7) ^ (~0 << 14);
      } else if ((x ^= (buffer[pos++] << 21)) < 0) {
        x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
      } else {
        int y = buffer[pos++];
        x ^= y << 28;
        x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
        // For a sign-extended negative int32 (10-byte varint), bytes 5..8 are
        // continuation (high bit set) and byte 9 is the terminator (high bit
        // clear) — so the && short-circuits at byte 9 and we exit cleanly.
        // If all five checks pass, the input claims a varint > 10 bytes, which
        // is malformed for any 64-bit value. Bail to the slow path which throws.
        if (y < 0
            && buffer[pos++] < 0
            && buffer[pos++] < 0
            && buffer[pos++] < 0
            && buffer[pos++] < 0
            && buffer[pos++] < 0) {
          bufferPos = pos;
          throw InvalidProtocolBufferException.malformedVarint();
        }
      }
      bufferPos = pos;
      return x;
    }
    return (int) readRawVarint64SlowPath();
  }

  public long readRawVarint64() throws IOException {
    int shift = 0;
    long result = 0;
    while (shift < 64) {
      byte b = readRawByte();
      result |= ((long) (b & 0x7F)) << shift;
      if ((b & 0x80) == 0) return result;
      shift += 7;
    }
    throw InvalidProtocolBufferException.malformedVarint();
  }

  private long readRawVarint64SlowPath() throws IOException {
    return readRawVarint64();
  }

  public int readRawLittleEndian32() throws IOException {
    return (readRawByte() & 0xFF)
        | ((readRawByte() & 0xFF) << 8)
        | ((readRawByte() & 0xFF) << 16)
        | ((readRawByte() & 0xFF) << 24);
  }

  public long readRawLittleEndian64() throws IOException {
    return ((long) readRawByte() & 0xFF)
        | (((long) readRawByte() & 0xFF) << 8)
        | (((long) readRawByte() & 0xFF) << 16)
        | (((long) readRawByte() & 0xFF) << 24)
        | (((long) readRawByte() & 0xFF) << 32)
        | (((long) readRawByte() & 0xFF) << 40)
        | (((long) readRawByte() & 0xFF) << 48)
        | (((long) readRawByte() & 0xFF) << 56);
  }

  public byte readRawByte() throws IOException {
    if (bufferPos == bufferSize) {
      refillBuffer(true);
    }
    return buffer[bufferPos++];
  }

  private byte[] readRawBytesSlowPath(int size) throws IOException {
    if (size < 0) throw InvalidProtocolBufferException.negativeSize();
    int currentAbsolute = totalBytesRetired + bufferPos;
    if (currentAbsolute + size > currentLimit) {
      skipRawBytes(currentLimit - currentAbsolute);
      throw InvalidProtocolBufferException.truncatedMessage();
    }
    if (size > sizeLimit) {
      throw new InvalidProtocolBufferException("Protocol message was too large.  May be malicious. "
          + "Use CodedInputStream.setSizeLimit() to increase the size limit.");
    }
    byte[] out = new byte[size];
    int copied = 0;
    int inBuf = remainingInBuffer();
    if (inBuf > 0) {
      int chunk = Math.min(inBuf, size);
      System.arraycopy(buffer, bufferPos, out, 0, chunk);
      bufferPos += chunk;
      copied = chunk;
    }
    while (copied < size) {
      if (input == null) throw InvalidProtocolBufferException.truncatedMessage();
      int n = input.read(out, copied, size - copied);
      if (n <= 0) throw InvalidProtocolBufferException.truncatedMessage();
      totalBytesRetired += n;
      copied += n;
    }
    return out;
  }

  public void skipRawBytes(int size) throws IOException {
    if (size < 0) throw InvalidProtocolBufferException.negativeSize();
    int currentAbsolute = totalBytesRetired + bufferPos;
    if (currentAbsolute + size > currentLimit) {
      skipRawBytes(currentLimit - currentAbsolute);
      throw InvalidProtocolBufferException.truncatedMessage();
    }
    int inBuf = remainingInBuffer();
    if (size <= inBuf) {
      bufferPos += size;
      return;
    }
    int remaining = size - inBuf;
    bufferPos = bufferSize;
    while (remaining > 0) {
      if (input == null) throw InvalidProtocolBufferException.truncatedMessage();
      long skipped = input.skip(remaining);
      if (skipped <= 0) {
        if (input.read() == -1) throw InvalidProtocolBufferException.truncatedMessage();
        skipped = 1;
      }
      totalBytesRetired += (int) skipped;
      remaining -= (int) skipped;
    }
  }

  // --- limits & end-of-message detection ---------------------------------

  /**
   * Restrict reads to {@code byteLimit} more bytes from the current position.
   * Returns the previous limit so the caller can {@link #popLimit(int)} after
   * the embedded message has been consumed.
   */
  public int pushLimit(int byteLimit) throws InvalidProtocolBufferException {
    if (byteLimit < 0) throw InvalidProtocolBufferException.negativeSize();
    int newAbsolute = totalBytesRetired + bufferPos + byteLimit;
    int oldLimit = currentLimit;
    if (newAbsolute > oldLimit) throw InvalidProtocolBufferException.truncatedMessage();
    currentLimit = newAbsolute;
    recomputeBufferSizeAfterLimit();
    return oldLimit;
  }

  public void popLimit(int oldLimit) {
    currentLimit = oldLimit;
    recomputeBufferSizeAfterLimit();
  }

  public int getBytesUntilLimit() {
    if (currentLimit == Integer.MAX_VALUE) return -1;
    int currentAbsolute = totalBytesRetired + bufferPos;
    return currentLimit - currentAbsolute;
  }

  public boolean isAtEnd() throws IOException {
    return bufferPos == bufferSize && !tryRefillBuffer(1);
  }

  private void recomputeBufferSizeAfterLimit() {
    bufferSize += bufferSizeAfterLimit;
    int bufferEndAbsolute = totalBytesRetired + bufferSize;
    if (bufferEndAbsolute > currentLimit) {
      bufferSizeAfterLimit = bufferEndAbsolute - currentLimit;
      bufferSize -= bufferSizeAfterLimit;
    } else {
      bufferSizeAfterLimit = 0;
    }
  }

  private int remainingInBuffer() {
    return bufferSize - bufferPos;
  }

  private void refillBuffer(boolean mustSucceed) throws IOException {
    if (!tryRefillBuffer(1) && mustSucceed) {
      throw InvalidProtocolBufferException.truncatedMessage();
    }
  }

  private boolean tryRefillBuffer(int n) throws IOException {
    if (bufferPos + n <= bufferSize) return true;
    if (totalBytesRetired + bufferPos + n > currentLimit) return false;
    if (input == null) return false;
    if (bufferPos > 0) {
      // Compact: keep the unread tail of the current buffer at offset 0.
      int unread = bufferSize - bufferPos;
      if (unread > 0) System.arraycopy(buffer, bufferPos, buffer, 0, unread);
      totalBytesRetired += bufferPos;
      bufferSize = unread;
      bufferPos = 0;
    }
    int read = input.read(buffer, bufferSize, buffer.length - bufferSize);
    if (read <= 0) return false;
    bufferSize += read;
    recomputeBufferSizeAfterLimit();
    return bufferSize >= n || tryRefillBuffer(n);
  }

  private static int decodeZigZag32(int n) {
    return (n >>> 1) ^ -(n & 1);
  }

  private static long decodeZigZag64(long n) {
    return (n >>> 1) ^ -(n & 1L);
  }
}
