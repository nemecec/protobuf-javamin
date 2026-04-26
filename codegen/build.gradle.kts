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
// protoc plugin. Reads CodeGeneratorRequest from stdin, emits Java source.
// Build-time only — does not ship to the runtime classpath.

plugins {
  application
}

dependencies {
  // We use the official protobuf-java only to parse CodeGeneratorRequest at build
  // time. The generated code we emit references only :runtime, never com.google.protobuf.
  implementation("com.google.protobuf:protobuf-java:4.34.1")

  testImplementation(platform("org.junit:junit-bom:5.10.2"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testImplementation("org.assertj:assertj-core:3.25.3")
}

application {
  mainClass.set("dev.nemecec.protobuf.javamin.codegen.Main")
}
