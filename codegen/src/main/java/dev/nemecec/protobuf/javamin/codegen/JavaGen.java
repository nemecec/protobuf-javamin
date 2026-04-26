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
package dev.nemecec.protobuf.javamin.codegen;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;

/**
 * Walks the protoc-supplied descriptors and emits one Java source file per
 * top-level proto message and per top-level proto enum.
 *
 * <p>Nested messages and nested enums become inner classes of their parent —
 * the same shape Google's javalite produces with {@code java_multiple_files = true}.
 *
 * <p>Each generated message is self-contained: read/write/computeSize logic
 * lives directly in the class. There is no shared schema cache, descriptor
 * table, or {@code dynamicMethod} dispatcher, which is the whole point of this
 * codegen — see the per-class metaspace numbers in the project README.
 */
final class JavaGen {

  CodeGeneratorResponse generate(CodeGeneratorRequest request) {
    TypeRegistry registry = TypeRegistry.build(request.getProtoFileList());
    CodeGeneratorResponse.Builder response = CodeGeneratorResponse.newBuilder();

    // The plugin should advertise "this version of the spec uses optional".
    // (Not strictly needed for proto2-only codegen, but keeps protoc happy
    // when proto3 files mark fields with the explicit `optional` keyword.)
    response.setSupportedFeatures(
        CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL.getNumber());

    for (String fileName : request.getFileToGenerateList()) {
      FileDescriptorProto file = findFile(request, fileName);
      String javaPkg = TypeRegistry.javaPackage(file);
      String javaPath = javaPkg.replace('.', '/');

      for (DescriptorProto message : file.getMessageTypeList()) {
        String src = new MessageWriter(registry, javaPkg).write(message);
        response.addFile(CodeGeneratorResponse.File.newBuilder()
            .setName(javaPath + "/" + message.getName() + ".java")
            .setContent(src));
      }
      for (EnumDescriptorProto e : file.getEnumTypeList()) {
        String src = new EnumWriter(javaPkg).write(e);
        response.addFile(CodeGeneratorResponse.File.newBuilder()
            .setName(javaPath + "/" + e.getName() + ".java")
            .setContent(src));
      }
    }
    return response.build();
  }

  private static FileDescriptorProto findFile(CodeGeneratorRequest req, String fileName) {
    for (FileDescriptorProto f : req.getProtoFileList()) {
      if (fileName.equals(f.getName())) return f;
    }
    throw new IllegalStateException("Requested file not in proto_file: " + fileName);
  }

  /** Field-type categorization used by the message writer to pick read/write/size methods. */
  enum FieldKind {
    SCALAR_INT32, SCALAR_INT64, SCALAR_UINT32, SCALAR_UINT64,
    SCALAR_SINT32, SCALAR_SINT64, SCALAR_FIXED32, SCALAR_FIXED64,
    SCALAR_SFIXED32, SCALAR_SFIXED64, SCALAR_FLOAT, SCALAR_DOUBLE,
    SCALAR_BOOL, SCALAR_STRING, SCALAR_BYTES,
    ENUM, MESSAGE;

    static FieldKind from(FieldDescriptorProto.Type t) {
      switch (t) {
        case TYPE_INT32: return SCALAR_INT32;
        case TYPE_INT64: return SCALAR_INT64;
        case TYPE_UINT32: return SCALAR_UINT32;
        case TYPE_UINT64: return SCALAR_UINT64;
        case TYPE_SINT32: return SCALAR_SINT32;
        case TYPE_SINT64: return SCALAR_SINT64;
        case TYPE_FIXED32: return SCALAR_FIXED32;
        case TYPE_FIXED64: return SCALAR_FIXED64;
        case TYPE_SFIXED32: return SCALAR_SFIXED32;
        case TYPE_SFIXED64: return SCALAR_SFIXED64;
        case TYPE_FLOAT: return SCALAR_FLOAT;
        case TYPE_DOUBLE: return SCALAR_DOUBLE;
        case TYPE_BOOL: return SCALAR_BOOL;
        case TYPE_STRING: return SCALAR_STRING;
        case TYPE_BYTES: return SCALAR_BYTES;
        case TYPE_ENUM: return ENUM;
        case TYPE_MESSAGE: return MESSAGE;
        default: throw new IllegalArgumentException("Unsupported field type: " + t);
      }
    }
  }
}
