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

package zio.blocks.typeid

import zio.blocks.chunk.Chunk

/**
 * The members of [[TypeId]] whose behaviour depends on the platform: on the JVM
 * they use reflection, and on Scala.js they find no class and construct
 * nothing.
 */
trait TypeIdPlatformSpecific { self: TypeId[_] =>

  /**
   * Returns the runtime class of this type on the JVM, or `None` when there is
   * none. A primitive gives its primitive class (`int` for `Int`), an applied
   * type its erased class (`List` for `List[Int]`), and an object the class of
   * its single instance. Aliases, opaque types, `Any`, `Nothing`, and classes
   * that cannot be loaded give `None`. On Scala.js it returns `None`.
   */
  def clazz: Option[Class[_]]

  /**
   * Creates a value of this type from `args`, on the JVM: some standard
   * collections, `Option`, `Some`, `None`, `Either`, `Tuple2`, `Tuple3` and
   * several `java.time` types directly, any other class by reflection with the
   * first public constructor that fits `args`. Returns `Left` with a message
   * when that fails. On Scala.js it returns `Left` for every type. See
   * [[TypeId#construct]].
   */
  def construct(args: Chunk[AnyRef]): Either[String, Any]
}
