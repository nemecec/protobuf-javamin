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
 * Thrown by {@link MessageLite.Builder#build()} when one or more {@code required}
 * fields haven't been set. We don't track the missing fields by name — the message
 * just identifies the offending message type. Callers that want detailed diagnostics
 * can inspect the builder state before {@code build()}.
 */
public class UninitializedMessageException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public UninitializedMessageException(String message) {
    super(message);
  }

  public UninitializedMessageException(MessageLite incompleteMessage) {
    super("Message missing required fields: " + incompleteMessage.getClass().getName());
  }

  public InvalidProtocolBufferException asInvalidProtocolBufferException() {
    return new InvalidProtocolBufferException(getMessage());
  }
}
