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
// Lean device-side runtime. Zero runtime dependencies.
// Generated proto classes reference only types from this module.

plugins {
  alias(libs.plugins.maven.publish)
}

dependencies {
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
  testImplementation(libs.assertj.core)

  // Used in tests to verify wire-format compatibility against Google's encoder.
  testImplementation(libs.protobuf.java)
}

mavenPublishing {
  publishToMavenCentral(automaticRelease = true)
  signAllPublications()

  coordinates(group.toString(), "protobuf-javamin-runtime", version.toString())

  pom {
    name.set("protobuf-javamin runtime")
    description.set("Lean proto2 wire-format runtime for tightly memory-constrained JVMs.")
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

// Gradle 9 requires the implicit metadata→javadoc dependency to be declared.
afterEvaluate {
  tasks.named("generateMetadataFileForMavenPublication") {
    dependsOn(tasks.named("plainJavadocJar"))
  }
}
