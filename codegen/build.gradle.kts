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
  alias(libs.plugins.maven.publish)
}

dependencies {
  // We use the official protobuf-java only to parse CodeGeneratorRequest at build
  // time. The generated code we emit references only :runtime, never com.google.protobuf.
  implementation(libs.protobuf.java)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
  testImplementation(libs.assertj.core)
}

application {
  mainClass.set("dev.nemecec.protobuf.javamin.codegen.Main")
}

mavenPublishing {
  publishToMavenCentral(automaticRelease = true)
  signAllPublications()

  coordinates(group.toString(), "codegen", version.toString())

  pom {
    name.set("protobuf-javamin codegen")
    description.set("protoc plugin that emits Java source for protobuf-javamin's lean runtime.")
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
