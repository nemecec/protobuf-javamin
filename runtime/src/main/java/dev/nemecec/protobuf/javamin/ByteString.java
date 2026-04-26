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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Immutable sequence of bytes — the receiver type for {@code bytes} proto fields.
 *
 * <p>Mirrors {@code com.google.protobuf.ByteString} for the operations downstream
 * code typically uses: {@link #copyFrom(byte[])}, {@link #copyFromUtf8(String)},
 * {@link #toByteArray()}, {@link #size()}, {@link #isEmpty()},
 * {@link #toStringUtf8()}.
 *
 * <p>This implementation copies on input and on {@link #toByteArray()}. We never
 * expose the internal array, so the instance is genuinely immutable from the
 * outside.
 */
public final class ByteString {
  public static final ByteString EMPTY = new ByteString(new byte[0]);

  private final byte[] bytes;
  private int hash; // cached, 0 until computed

  private ByteString(byte[] bytes) {
    this.bytes = bytes;
  }

  public static ByteString copyFrom(byte[] source) {
    return copyFrom(source, 0, source.length);
  }

  public static ByteString copyFrom(byte[] source, int offset, int size) {
    if (size == 0) return EMPTY;
    byte[] copy = new byte[size];
    System.arraycopy(source, offset, copy, 0, size);
    return new ByteString(copy);
  }

  public static ByteString copyFromUtf8(String text) {
    if (text.isEmpty()) return EMPTY;
    return new ByteString(text.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Wrap an existing array without copying. Caller must not mutate the array
   * afterwards. Used by {@link CodedInputStream} on the read path where it
   * already allocated a fresh buffer for the field.
   */
  static ByteString wrap(byte[] bytes) {
    return bytes.length == 0 ? EMPTY : new ByteString(bytes);
  }

  public int size() {
    return bytes.length;
  }

  public boolean isEmpty() {
    return bytes.length == 0;
  }

  public byte byteAt(int index) {
    return bytes[index];
  }

  public byte[] toByteArray() {
    int len = bytes.length;
    if (len == 0) return new byte[0];
    byte[] copy = new byte[len];
    System.arraycopy(bytes, 0, copy, 0, len);
    return copy;
  }

  public String toStringUtf8() {
    return new String(bytes, StandardCharsets.UTF_8);
  }

  /** Internal accessor — package-private so {@link CodedOutputStream} can write
   *  bytes without copying. Callers outside this package use {@link #toByteArray()}. */
  byte[] internalArray() {
    return bytes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ByteString)) return false;
    return Arrays.equals(bytes, ((ByteString) o).bytes);
  }

  @Override
  public int hashCode() {
    int h = hash;
    if (h == 0 && bytes.length > 0) {
      h = Arrays.hashCode(bytes);
      if (h == 0) h = 1;
      hash = h;
    }
    return h;
  }

  @Override
  public String toString() {
    // Don't dump the raw bytes — they may be opaque, large, or sensitive.
    // The size is enough to disambiguate at a glance during diagnostics.
    return "ByteString[" + bytes.length + " bytes]";
  }
}
