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

/**
 * Wire-format constants and tag-encoding helpers, mirroring proto2 wire layout
 * exactly (https://protobuf.dev/programming-guides/encoding/).
 *
 * <p>All values are byte-for-byte compatible with what {@code protoc}-generated
 * code emits, so messages encoded by this runtime decode in the official Java,
 * C++, Python, etc. runtimes and vice versa.
 */
public final class WireFormat {
  public static final int WIRETYPE_VARINT = 0;
  public static final int WIRETYPE_FIXED64 = 1;
  public static final int WIRETYPE_LENGTH_DELIMITED = 2;
  public static final int WIRETYPE_START_GROUP = 3;
  public static final int WIRETYPE_END_GROUP = 4;
  public static final int WIRETYPE_FIXED32 = 5;

  static final int TAG_TYPE_BITS = 3;
  static final int TAG_TYPE_MASK = (1 << TAG_TYPE_BITS) - 1;

  private WireFormat() {
  }

  public static int makeTag(int fieldNumber, int wireType) {
    return (fieldNumber << TAG_TYPE_BITS) | wireType;
  }

  public static int getTagFieldNumber(int tag) {
    return tag >>> TAG_TYPE_BITS;
  }

  public static int getTagWireType(int tag) {
    return tag & TAG_TYPE_MASK;
  }
}
