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

/**
 * Thrown when wire-format input fails to parse: malformed varint, truncated
 * length-delimited field, tag with wire type that doesn't match the schema, etc.
 *
 * <p>Extends {@link IOException} for source-compat with code that catches
 * {@code IOException} from a {@code parseFrom(InputStream)} call site.
 */
public class InvalidProtocolBufferException extends IOException {
  private static final long serialVersionUID = 1L;

  public InvalidProtocolBufferException(String description) {
    super(description);
  }

  public InvalidProtocolBufferException(String description, Throwable cause) {
    super(description, cause);
  }

  static InvalidProtocolBufferException truncatedMessage() {
    return new InvalidProtocolBufferException(
        "While parsing a protocol message, the input ended unexpectedly in the middle of a field. "
            + "This could mean either that the input has been truncated or that an embedded message "
            + "misreported its own length.");
  }

  static InvalidProtocolBufferException negativeSize() {
    return new InvalidProtocolBufferException(
        "CodedInputStream encountered an embedded string or message which claimed to have negative size.");
  }

  static InvalidProtocolBufferException malformedVarint() {
    return new InvalidProtocolBufferException("CodedInputStream encountered a malformed varint.");
  }

  static InvalidProtocolBufferException invalidTag() {
    return new InvalidProtocolBufferException("Protocol message contained an invalid tag (zero).");
  }

  static InvalidProtocolBufferException invalidWireType() {
    return new InvalidProtocolBufferException("Protocol message tag had invalid wire type.");
  }

  static InvalidProtocolBufferException invalidUtf8() {
    return new InvalidProtocolBufferException("Protocol message had invalid UTF-8.");
  }
}
