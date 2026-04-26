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
plugins {
  java
}

allprojects {
  group = "dev.nemecec.protobuf.javamin"
  version = "1.0.0-SNAPSHOT"

  repositories {
    mavenCentral()
  }
}

subprojects {
  apply(plugin = "java")

  configure<JavaPluginExtension> {
    toolchain {
      languageVersion.set(JavaLanguageVersion.of(8))
    }
    // Sources + javadoc jars are added automatically by the vanniktech publish
    // plugin on published subprojects; we don't need (and must not duplicate)
    // them here.
  }

  tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
  }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
  }

  // Don't fail the build on missing-doc warnings — our javadoc is informal.
  tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
  }

  // Sign only when in-memory keys are provided (CI). Local builds skip signing.
  tasks.withType<Sign>().configureEach {
    enabled = project.findProperty("signingInMemoryKey") != null
  }

}
