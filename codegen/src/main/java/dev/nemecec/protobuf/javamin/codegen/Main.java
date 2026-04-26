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

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import java.io.IOException;

/**
 * protoc plugin entrypoint. Reads a {@link CodeGeneratorRequest} from stdin,
 * drives {@link JavaGen}, and writes the resulting {@link CodeGeneratorResponse}
 * to stdout. This is the only class invoked by protoc.
 *
 * <p>The Gradle protobuf plugin discovers this via a generated wrapper script
 * (see {@code application} plugin configuration in {@code codegen/build.gradle.kts}).
 * In tests we can drive it directly by piping a serialized request into stdin.
 *
 * <p>This program runs at build time only — its dependency on
 * {@code com.google.protobuf:protobuf-java} (used to parse the request) does
 * not flow into the generated code or the device-side runtime.
 */
public final class Main {

  public static void main(String[] args) throws IOException {
    CodeGeneratorRequest request = CodeGeneratorRequest.parseFrom(System.in);
    CodeGeneratorResponse response;
    try {
      response = new JavaGen().generate(request);
    } catch (RuntimeException e) {
      // Surfacing errors via the response.error field is what protoc expects.
      // Throwing would print a stack trace but provide a less helpful build failure.
      response = CodeGeneratorResponse.newBuilder()
          .setError(e.getClass().getSimpleName() + ": " + e.getMessage())
          .build();
    }
    response.writeTo(System.out);
    System.out.flush();
  }

  private Main() {}
}
