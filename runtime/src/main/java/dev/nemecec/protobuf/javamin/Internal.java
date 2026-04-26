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
package dev.nemecec.protobuf.javamin;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Helpers referenced by generated code only. Public so the codegen can emit
 * direct method calls without going through reflection.
 */
public final class Internal {
  private Internal() {}

  /** Empty default for {@code repeated} fields. Generated classes initialize
   *  their list field to this so an unset field still has a non-null List for
   *  {@code getXList()}. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <T> List<T> emptyList() {
    return (List<T>) EMPTY_LIST;
  }

  /** Empty default for {@code map<K, V>} fields. Same role as {@link #emptyList()}
   *  but on the map side — keeps {@code getXMap()} non-null when nothing was put. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <K, V> Map<K, V> emptyMap() {
    return (Map<K, V>) EMPTY_MAP;
  }

  private static final List<Object> EMPTY_LIST = Collections.emptyList();
  private static final Map<Object, Object> EMPTY_MAP = Collections.emptyMap();
}
