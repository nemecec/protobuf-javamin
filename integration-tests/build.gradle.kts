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
// End-to-end tests: feed a sample .proto file through the codegen, compile the
// generated Java against :runtime, and roundtrip messages through it.
//
// We invoke our codegen as an external process (the protoc-plugin protocol)
// using protoc. The Gradle protobuf plugin handles the orchestration.
// Wire-format compatibility against Google's protobuf-java is pinned in
// :runtime's CodedOutputStreamCompatTest / CodedInputStreamCompatTest.
//
// This module is not published — it exists purely to validate the codegen
// against a real .proto end-to-end on every build.

import com.google.protobuf.gradle.id

plugins {
  alias(libs.plugins.protobuf)
}

dependencies {
  implementation(project(":runtime"))

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
  testImplementation(libs.assertj.core)

  // Reference encoder for byte-for-byte wire compatibility checks.
  testImplementation(libs.protobuf.java)
}

protobuf {
  protoc {
    artifact = libs.protoc.get().toString()
  }
  plugins {
    id("javamin") {
      // Reuse the application-style launcher script the codegen module produces.
      // It's an executable shell script that puts our plugin's jar on the classpath
      // and runs Main; protoc invokes it as a child process.
      path = "${rootProject.projectDir}/codegen/build/install/codegen/bin/codegen"
    }
  }
  generateProtoTasks {
    all().configureEach {
      // Drop the built-in Java codegen and replace with our javamin plugin.
      builtins.named("java") { /* configure-named registration */ }
      builtins.remove(builtins.named("java").get())
      plugins {
        id("javamin")
      }
      dependsOn(":codegen:installDist")
    }
  }
}

sourceSets {
  test {
    proto {
      srcDir("src/test/proto")
    }
  }
}

// The protobuf plugin adds the .proto src dir to the test resources too; bundle
// it once and ignore subsequent duplicates rather than fighting Gradle about it.
tasks.withType<ProcessResources>().configureEach {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
