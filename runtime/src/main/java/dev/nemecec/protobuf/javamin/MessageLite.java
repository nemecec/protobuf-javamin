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
import java.io.OutputStream;

/**
 * Marker interface implemented by every generated message class. Mirrors the
 * minimal slice of {@code com.google.protobuf.MessageLite} that downstream
 * proto-using code typically depends on.
 *
 * <p>Generated classes provide the actual serialization logic inline — there is
 * no schema cache, no descriptor table, no {@code dynamicMethod} dispatch. The
 * methods declared here are the entire contract.
 */
public interface MessageLite {

  /** Number of bytes the wire-format encoding of this message will produce. */
  int getSerializedSize();

  /**
   * Whether all {@code required} fields have been set. Generated code returns
   * {@code true} unconditionally for messages with no required fields, which
   * the JIT can fold away.
   */
  boolean isInitialized();

  /** Encode the message to {@code output}. The caller is responsible for the framing
   *  (raw, delimited, embedded) — {@code writeTo} only emits the fields. */
  void writeTo(CodedOutputStream output) throws IOException;

  default void writeTo(OutputStream output) throws IOException {
    CodedOutputStream cos = CodedOutputStream.newInstance(output);
    writeTo(cos);
    cos.flush();
  }

  /** Encode with a varint length prefix, then the body. The standard framing
   *  for streaming many proto messages over a single transport (file, socket). */
  default void writeDelimitedTo(OutputStream output) throws IOException {
    CodedOutputStream cos = CodedOutputStream.newInstance(output);
    cos.writeUInt32NoTag(getSerializedSize());
    writeTo(cos);
    cos.flush();
  }

  default byte[] toByteArray() {
    try {
      byte[] result = new byte[getSerializedSize()];
      CodedOutputStream cos = CodedOutputStream.newInstance(result);
      writeTo(cos);
      if (cos.spaceLeft() != 0) {
        throw new RuntimeException(
            "Serialized size mismatch (had " + cos.spaceLeft() + " bytes left).");
      }
      return result;
    } catch (IOException e) {
      throw new RuntimeException("Serializing to byte[] threw IOException (this should not happen).", e);
    }
  }

  /**
   * The mutable companion of a message. Generated builders implement this with
   * concrete setters/getters; the interface gives the runtime enough to
   * implement {@code parseFrom} / {@code mergeDelimitedFrom} generically.
   */
  interface Builder<M extends MessageLite, B extends Builder<M, B>> {
    M build();
    M buildPartial();
    B mergeFrom(CodedInputStream input) throws IOException;

    default B mergeFrom(byte[] data) throws InvalidProtocolBufferException {
      try {
        CodedInputStream input = CodedInputStream.newInstance(data);
        mergeFrom(input);
        return self();
      } catch (InvalidProtocolBufferException e) {
        throw e;
      } catch (IOException e) {
        throw new InvalidProtocolBufferException("Reading from byte[] threw IOException.", e);
      }
    }

    default B mergeFrom(InputStream input) throws IOException {
      mergeFrom(CodedInputStream.newInstance(input));
      return self();
    }

    @SuppressWarnings("unchecked")
    default B self() { return (B) this; }
  }
}
