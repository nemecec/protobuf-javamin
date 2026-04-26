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
//   * The same shape in src/crossCheck/proto is run through Google's stock
//     protoc Java codegen so the test classpath has both generated trees side
//     by side. CrossEncoderRoundtripTest exchanges serialized bytes between
//     the two to prove that what one produces, the other parses back to
//     identical field values — for every field type, including nested,
//     repeated, and repeated enums.
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
      builtins.removeIf { it.name == "java" }
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
  // The codegen uber-jar is published to mavenLocal by the dependsOn above so
  // protobuf-gradle-plugin can resolve it via Maven coordinates. Scope the
  // mavenLocal lookup to our group only — we don't want every other dependency
  // resolution attempt to detour through ~/.m2.
  mavenLocal {
    content {
      includeGroup("dev.nemecec.protobuf.javamin")
    }
  }
}

// The protobuf plugin adds the .proto src dir to test resources too; bundle
// it once and ignore subsequent duplicates rather than fighting Gradle.
tasks.withType<ProcessResources>().configureEach {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// --- Google-twin test generation ---------------------------------------------
//
// Take the existing roundtrip tests in src/test/java and generate parallel
// versions that import the stock-protoc-generated Sample / Defaulted classes
// (the crossCheck source set) plus stock's runtime types (com.google.protobuf
// .ByteString, .CodedOutputStream, etc.). Both versions then compile and run
// against their respective codegens — the Google twin failing to compile
// surfaces an API surface drift, and a runtime failure surfaces a behavioural
// drift. Tests that probe javamin-specific behaviour (toString format,
// unknown-enum handling) or that intentionally use both codegens (cross-
// encoder methods) are wrapped in `// JAVAMIN-ONLY-BEGIN / END` markers and
// dropped from the twin.

val googleTwinTestSourceDir = layout.buildDirectory.dir("generated/sources/google-twin-tests/java")

val twinSources = listOf(
    "SampleRoundtripTest",
    "MapRoundtripTest",
    "DefaultedRoundtripTest"
)

val generateGoogleTwinTests by tasks.registering {
  group = "verification"
  description = "Sed-rewrites javamin tests into stock-protoc-generated counterparts."

  val srcDir = file("src/test/java/dev/nemecec/protobuf/javamin/integration")
  val outDir = googleTwinTestSourceDir
  inputs.files(twinSources.map { srcDir.resolve("$it.java") })
  outputs.dir(outDir)

  doLast {
    val out = outDir.get().asFile.resolve("dev/nemecec/protobuf/javamin/integration")
    out.mkdirs()
    for (name in twinSources) {
      val src = srcDir.resolve("$name.java").readText()
      val twinName = name.replace("Test", "GoogleTest")

      // Strip JAVAMIN-ONLY-BEGIN ... JAVAMIN-ONLY-END blocks (multi-line).
      val stripped = src.replace(
          Regex("""(?ms)^[ \t]*//\s*JAVAMIN-ONLY-BEGIN.*?//\s*JAVAMIN-ONLY-END[ \t]*\n?"""),
          ""
      )

      // Rewrite imports + class name. We match only on lines beginning with
      // `import ` so the file's own `package` declaration and any incidental
      // textual occurrences (string literals, comments) are left alone. The
      // runtime-type rule has a `(?!integration)` lookahead so it doesn't
      // cascade onto the result of the generated-package rule (both sides of
      // which still live under `dev.nemecec.protobuf.javamin.integration.*`).
      val rewritten = stripped
          .replace(
              Regex("""^import dev\.nemecec\.protobuf\.javamin\.integration\.gen\.""",
                  RegexOption.MULTILINE),
              "import dev.nemecec.protobuf.javamin.integration.crosscheck.gen."
          )
          .replace(
              Regex("""^import dev\.nemecec\.protobuf\.javamin\.(?!integration)""",
                  RegexOption.MULTILINE),
              "import com.google.protobuf."
          )
          .replace("class $name", "class $twinName")

      out.resolve("$twinName.java").writeText(rewritten)
    }
  }
}

sourceSets.test {
  java.srcDir(googleTwinTestSourceDir)
}

tasks.named("compileTestJava") {
  dependsOn(generateGoogleTwinTests)
}
