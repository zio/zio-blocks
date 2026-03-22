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

package zio.blocks.scope

/**
 * Scala 2 version-specific InStack support.
 *
 * In the new Scope design, InStack is no longer needed as the HList-based scope
 * is replaced with a two-parameter Scope[ParentTag, Tag].
 *
 * This trait is kept empty for binary compatibility.
 */
private[scope] trait InStackVersionSpecific
