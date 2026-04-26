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
//
// Distributed in two flavours:
//   * thin jar (no classifier) — the codegen classes only, with protobuf-java
//     as a Maven dependency. For uncommon use cases like embedding the codegen.
//   * shadow uber-jar (`:all` classifier) — codegen + bundled protobuf-java +
//     `Main-Class` manifest. This is what consumers actually use: protoc resolves
//     it from Maven via `artifact = "<g>:<a>:<v>:all@jar"` and runs it as
//     `java -jar`. No separate install step on the consumer side.

plugins {
  alias(libs.plugins.shadow)
  alias(libs.plugins.maven.publish)
}

// Used in the manifest of the shadow uber-jar so `java -jar` finds the entry
// point, and referenced by tests if they want to run the codegen in-process.
val mainClassFqn = "dev.nemecec.protobuf.javamin.codegen.Main"

dependencies {
  // Used at codegen time to parse CodeGeneratorRequest from stdin and emit
  // CodeGeneratorResponse to stdout. Bundled into the published uber-jar
  // (Shadow); never reaches the consumer's runtime classpath.
  implementation(libs.protobuf.java)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
  testImplementation(libs.assertj.core)
}

// Shadow defaults to classifier "all" — keep it. We just need the Main-Class
// manifest entry so `java -jar` works.
tasks.shadowJar {
  manifest {
    attributes["Main-Class"] = mainClassFqn
  }
  // protobuf-java has META-INF/services entries; merge them so Shadow doesn't
  // overwrite one with another.
  mergeServiceFiles()
}

mavenPublishing {
  publishToMavenCentral(automaticRelease = true)
  signAllPublications()

  coordinates(group.toString(), "protobuf-javamin-codegen", version.toString())

  pom {
    name.set("protobuf-javamin codegen")
    description.set("protoc plugin (executable uber-jar) that emits Java source for protobuf-javamin's lean runtime.")
    url.set("https://github.com/nemecec/protobuf-javamin")
    licenses {
      license {
        name.set("Apache License, Version 2.0")
        url.set("https://www.apache.org/licenses/LICENSE-2.0")
      }
    }
    developers {
      developer {
        id.set("nemecec")
        name.set("Neeme Praks")
      }
    }
    scm {
      url.set("https://github.com/nemecec/protobuf-javamin")
      connection.set("scm:git:git://github.com/nemecec/protobuf-javamin.git")
      developerConnection.set("scm:git:ssh://git@github.com/nemecec/protobuf-javamin.git")
    }
  }
}

// vanniktech-maven-publish ≥ 0.31 auto-attaches the Shadow `:all` jar to the
// same publication when Shadow is on the classpath, so we don't add it manually
// here. Just declare the implicit metadata→javadoc dependency that Gradle 9
// requires.
afterEvaluate {
  tasks.named("generateMetadataFileForMavenPublication") {
    dependsOn(tasks.named("plainJavadocJar"))
  }
}
