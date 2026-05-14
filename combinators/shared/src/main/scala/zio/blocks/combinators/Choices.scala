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

package zio.blocks.combinators

/**
 * Direct branch construction / elimination over `|`.
 *
 * Scala 3 keeps native union types; `left`, `right`, and `separate` require
 * compile-time `Unions.WithOut` evidence that enforces disjointness.
 *
 * Scala 2 reinterprets `|` as `Either[L, R]`; the same method names work
 * without implicit evidence, but no compile-time disjointness check is
 * performed.
 *
 * Use `Choices.separate` to normalize elements back to `Either[L, R]` on both
 * platforms.
 */
object Choices extends ChoicesPlatformSpecific
