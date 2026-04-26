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

/**
 * Optional base class for generated messages. Provides the {@code parseFrom*}
 * static-factory pattern via a small handful of helper methods generated code
 * can delegate to. None of these methods cache schemas, build descriptors, or
 * do any reflection — they are zero-state passes through {@link CodedInputStream}.
 *
 * <p>Generated code looks roughly like:
 * <pre>
 * public final class FooProto extends AbstractMessageLite&lt;FooProto, FooProto.Builder&gt; {
 *   public static FooProto parseFrom(byte[] data) throws InvalidProtocolBufferException {
 *     return AbstractMessageLite.parseFrom(newBuilder(), data);
 *   }
 *   ...
 * }
 * </pre>
 */
public abstract class AbstractMessageLite<M extends MessageLite, B extends MessageLite.Builder<M, B>>
    implements MessageLite {

  /**
   * Parse a length-delimited message from a stream. The stream is left positioned
   * just past the message bytes, so a sequence of {@code parseDelimitedFrom} calls
   * walks a stream of messages cleanly. Returns {@code null} if the stream is at
   * EOF before any bytes are read.
   *
   * <p>Reads exactly the body bytes into a transient buffer rather than wrapping
   * the stream in a {@link CodedInputStream}: a CIS buffers 4 KB ahead, so any
   * bytes belonging to the following message would be stranded in its private
   * buffer when we discard it.
   */
  public static <M extends MessageLite, B extends MessageLite.Builder<M, B>> M parseDelimitedFrom(
      B builder, InputStream input) throws IOException {
    int firstByte = input.read();
    if (firstByte == -1) return null;
    int size = readRawVarint32WithFirstByte(firstByte, input);
    if (size < 0) throw InvalidProtocolBufferException.negativeSize();
    byte[] body = new byte[size];
    int read = 0;
    while (read < size) {
      int n = input.read(body, read, size - read);
      if (n <= 0) throw InvalidProtocolBufferException.truncatedMessage();
      read += n;
    }
    builder.mergeFrom(body);
    return finish(builder);
  }

  public static <M extends MessageLite, B extends MessageLite.Builder<M, B>> M parseFrom(
      B builder, byte[] data) throws InvalidProtocolBufferException {
    builder.mergeFrom(data);
    return finish(builder);
  }

  public static <M extends MessageLite, B extends MessageLite.Builder<M, B>> M parseFrom(
      B builder, InputStream input) throws IOException {
    builder.mergeFrom(input);
    return finish(builder);
  }

  private static <M extends MessageLite, B extends MessageLite.Builder<M, B>> M finish(B builder)
      throws InvalidProtocolBufferException {
    M built = builder.buildPartial();
    if (!built.isInitialized()) {
      throw new UninitializedMessageException(built).asInvalidProtocolBufferException();
    }
    return built;
  }

  /** The varint-prefix read used by {@link #parseDelimitedFrom(MessageLite.Builder, InputStream)},
   *  given the already-read first byte. We can't lean on {@link CodedInputStream} here because
   *  it would buffer ahead and capture bytes belonging to the message body. */
  private static int readRawVarint32WithFirstByte(int firstByte, InputStream input) throws IOException {
    if ((firstByte & 0x80) == 0) return firstByte;
    int result = firstByte & 0x7F;
    int shift = 7;
    while (shift < 32) {
      int b = input.read();
      if (b == -1) throw InvalidProtocolBufferException.truncatedMessage();
      result |= (b & 0x7F) << shift;
      if ((b & 0x80) == 0) return result;
      shift += 7;
    }
    // Drain any remaining continuation bytes (varint32 may sign-extend up to 10 bytes when
    // produced from a negative int64 source).
    while (shift < 64) {
      int b = input.read();
      if (b == -1) throw InvalidProtocolBufferException.truncatedMessage();
      if ((b & 0x80) == 0) return result;
      shift += 7;
    }
    throw InvalidProtocolBufferException.malformedVarint();
  }
}
