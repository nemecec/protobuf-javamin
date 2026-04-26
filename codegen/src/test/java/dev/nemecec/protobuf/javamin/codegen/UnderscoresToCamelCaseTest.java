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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Pins {@link MessageWriter#underscoresToCamelCase(String, boolean)} to the
 * same naming rule that stock {@code protoc} applies for Java codegen — see
 * {@code UnderscoresToCamelCaseImpl} in {@code google/protobuf/descriptor.cc}.
 *
 * <p>The promise of this codegen is "drop-in for protobuf-javalite at the API
 * level". That breaks the moment two codegens disagree on a method name for
 * the same .proto. The non-obvious cases are digits forcing capitalization on
 * the next letter (so {@code packed_fixed32s} / {@code packedFixed32s} both
 * map to {@code PackedFixed32S} on the Java side) and underscores being
 * dropped while triggering capitalization. This test pins both.
 */
class UnderscoresToCamelCaseTest {

  @ParameterizedTest(name = "[{index}] cap={1} \"{0}\" -> \"{2}\"")
  @CsvSource({
      // Plain ASCII names — exercise both initial-cap settings.
      "id,                false, id",
      "id,                true,  Id",
      "name,              false, name",
      "createdAtMillis,   false, createdAtMillis",
      "createdAtMillis,   true,  CreatedAtMillis",

      // Underscores: dropped, next letter capitalized (per protoc).
      "foo_bar,           false, fooBar",
      "foo_bar,           true,  FooBar",
      "a_b_c,             false, aBC",
      "trailing_,         false, trailing",
      "_leading,          false, Leading",
      "__double,          false, Double",

      // Digits: kept verbatim, force capitalize on the next letter.
      "fixed32s,          false, fixed32S",
      "fixed32s,          true,  Fixed32S",
      "packedFixed32s,    false, packedFixed32S",
      "packedFixed32s,    true,  PackedFixed32S",
      "field42,           true,  Field42",
      "v1value,           false, v1Value",

      // Already-capital letters preserved (except the first one when capNext=false).
      "FooBar,            false, fooBar",
      "FooBar,            true,  FooBar",
      "URLPath,           false, uRLPath",
      "URLPath,           true,  URLPath",

      // Empty input is the identity.
      "'',                false, ''",
      "'',                true,  ''",
  })
  @DisplayName("matches protoc's UnderscoresToCamelCase rule")
  void matchesProtoc(String input, boolean capNext, String expected) {
    assertThat(MessageWriter.underscoresToCamelCase(input, capNext)).isEqualTo(expected);
  }
}
