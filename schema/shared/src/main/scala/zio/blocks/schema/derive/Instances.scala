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

package zio.blocks.schema.derive

import zio.blocks.schema._

/**
 * An automatically derived instance of `TC[A]` together with an optional custom
 * one that replaces it. Nothing in ZIO Blocks creates it: a `DerivationBuilder`
 * keeps its custom instances as [[InstanceOverride]]s.
 */
final case class Instances[TC[_], A](automatic: Lazy[TC[A]], custom: Option[Lazy[TC[A]]]) {

  /** Returns `custom` when it is set, or else `automatic`. */
  def derivation: Lazy[TC[A]] = custom.getOrElse(automatic)
}

/** The companion of [[Instances]]; it has no members. */
object Instances {}
