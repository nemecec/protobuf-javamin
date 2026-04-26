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
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves fully-qualified proto type names (with leading dot, e.g.
 * {@code .pkg.OuterProto.NestedProto}) to fully-qualified Java class names.
 *
 * <p>Populated up-front by walking every file in the {@code CodeGeneratorRequest}
 * — including imports — so cross-file references resolve regardless of which
 * file is currently being emitted.
 */
final class TypeRegistry {

  private final Map<String, String> javaName = new HashMap<>();
  // Reverse-lookup from proto type name → message descriptor. Needed so the
  // codegen can recognise synthetic {@code map_entry = true} messages (which
  // protoc generates for every map<K, V> field) and steer them onto the map
  // emission path instead of treating them as plain user-defined messages.
  private final Map<String, DescriptorProto> messages = new HashMap<>();

  static TypeRegistry build(Iterable<FileDescriptorProto> files) {
    TypeRegistry r = new TypeRegistry();
    for (FileDescriptorProto file : files) {
      String protoPkg = file.getPackage();
      String javaPkg = javaPackage(file);

      for (DescriptorProto m : file.getMessageTypeList()) {
        r.collectMessage(protoPkg, javaPkg, "", m);
      }
      for (EnumDescriptorProto e : file.getEnumTypeList()) {
        r.javaName.put(prefix(protoPkg, e.getName()), javaPkg + "." + e.getName());
      }
    }
    return r;
  }

  private void collectMessage(String protoPkg, String javaPkg, String javaOuter, DescriptorProto m) {
    String protoName = prefix(protoPkg, m.getName());
    String javaName = javaPkg + "." + (javaOuter.isEmpty() ? m.getName() : javaOuter + "." + m.getName());
    this.javaName.put(protoName, javaName);
    this.messages.put(protoName, m);

    String childOuter = javaOuter.isEmpty() ? m.getName() : javaOuter + "." + m.getName();
    String childProto = protoName.substring(1); // strip leading dot for further prefixing
    for (DescriptorProto nested : m.getNestedTypeList()) {
      collectMessage(childProto, javaPkg, childOuter, nested);
    }
    for (EnumDescriptorProto nestedEnum : m.getEnumTypeList()) {
      this.javaName.put("." + childProto + "." + nestedEnum.getName(),
          javaPkg + "." + childOuter + "." + nestedEnum.getName());
    }
  }

  /** Java FQN for {@code .pkg.Foo.Bar} ⇒ {@code java.pkg.Foo.Bar}. */
  String javaName(String protoTypeName) {
    String name = javaName.get(protoTypeName);
    if (name == null) {
      throw new IllegalStateException("Unknown proto type: " + protoTypeName);
    }
    return name;
  }

  /** Message descriptor for a fully-qualified proto type name, or {@code null}
   *  if the name refers to an enum (or anything else not registered as a
   *  message). Used to detect synthetic map_entry messages from a field's
   *  type-name reference. */
  DescriptorProto message(String protoTypeName) {
    return messages.get(protoTypeName);
  }

  /** Proto package + simple name → fully-qualified proto name with leading dot. */
  private static String prefix(String protoPkg, String simple) {
    return protoPkg.isEmpty() ? "." + simple : "." + protoPkg + "." + simple;
  }

  /** Per protoc convention: prefer {@code option java_package}, otherwise the
   *  proto {@code package} declaration. */
  static String javaPackage(FileDescriptorProto f) {
    if (f.hasOptions() && f.getOptions().hasJavaPackage()
        && !f.getOptions().getJavaPackage().isEmpty()) {
      return f.getOptions().getJavaPackage();
    }
    return f.getPackage();
  }
}
