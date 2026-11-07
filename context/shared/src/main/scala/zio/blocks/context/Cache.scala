/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
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

package zio.blocks.context

import zio.blocks.typeid.TypeId

/**
 * The per-context lookup cache: a `ConcurrentHashMap` on the JVM, a
 * `mutable.HashMap` on Scala.js.
 */
private[context] trait Cache {

  /** Returns the cached value for `key`, or `null`. */
  def get(key: TypeId.Erased): Any

  /** Stores `value` for `key`, replacing any cached value. */
  def put(key: TypeId.Erased, value: Any): Unit

  /**
   * Stores `value` for `key` unless a value is already cached; returns the
   * value already cached, or `null` if there was none.
   */
  def putIfAbsent(key: TypeId.Erased, value: Any): Any
}
