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
// End-to-end tests:
//   * Sample.proto in src/test/proto is run through OUR protobuf-javamin codegen
//     and roundtripped against itself (SampleRoundtripTest).
//   * The same shape, in src/crossCheck/proto, is also run through Google's
//     stock protoc Java codegen so the test classpath has both generated trees
//     side by side. CrossCheckTest exchanges serialized bytes between the two
//     to prove that what one produces, the other parses back to identical
//     field values — for every field type, including oneof, nested, repeated.
//
// This module isn't published; it exists purely to validate the codegen and
// runtime against a real .proto end-to-end on every build.

import com.google.protobuf.gradle.id

plugins {
  alias(libs.plugins.protobuf)
}

// SourceSet declared up front so subsequent blocks (dependencies, protobuf,
// task config) can reference it.
sourceSets {
  test {
    proto {
      srcDir("src/test/proto")
    }
  }
  // Sibling source set whose .proto sources are compiled by Google's standard
  // codegen. Test code imports these classes via the testImplementation hook
  // below to perform cross-encoder roundtrip checks.
  create("crossCheck") {
    proto {
      srcDir("src/crossCheck/proto")
    }
  }
}

dependencies {
  implementation(project(":runtime"))

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
  testImplementation(libs.assertj.core)

  // Reference encoder for byte-for-byte wire compatibility checks.
  testImplementation(libs.protobuf.java)

  // Make Google-generated Sample types available to test code, plus the
  // protobuf-java runtime they depend on at compile + runtime.
  testImplementation(sourceSets["crossCheck"].output)
  "crossCheckImplementation"(libs.protobuf.java)
}

protobuf {
  protoc {
    artifact = libs.protoc.get().toString()
  }
  plugins {
    id("javamin") {
      // Resolve the codegen uber-jar (`:all` classifier, `@jar` extension)
      // from a Maven repo — mavenLocal in this in-tree test. protoc downloads
      // it and invokes via `java -jar`. This is exactly the pattern downstream
      // consumers will use.
      artifact = "dev.nemecec.protobuf.javamin:protobuf-javamin-codegen:${project.version}:all@jar"
    }
  }
  generateProtoTasks {
    // The :test sourceSet's protos go through our javamin codegen only.
    ofSourceSet("test").configureEach {
      builtins.named("java") { /* configure-named registration */ }
      builtins.remove(builtins.named("java").get())
      plugins {
        id("javamin")
      }
      // The codegen artifact must be in mavenLocal before protobuf-gradle-plugin
      // resolves it.
      dependsOn(":codegen:publishToMavenLocal")
    }
    // The :crossCheck sourceSet's protos go through stock protoc's Java
    // codegen, producing classes that depend on com.google.protobuf at runtime.
    // No javamin plugin here.
    ofSourceSet("crossCheck").configureEach {
      // `builtins.java` is registered by default; just leave it on. It produces
      // full protobuf-java code (not lite), which has the richest test ergonomics.
    }
  }
}

repositories {
  mavenLocal()
}

// The protobuf plugin adds the .proto src dir to test resources too; bundle
// it once and ignore subsequent duplicates rather than fighting Gradle.
tasks.withType<ProcessResources>().configureEach {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
