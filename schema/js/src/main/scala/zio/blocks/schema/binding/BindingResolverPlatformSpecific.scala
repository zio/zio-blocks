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

package zio.blocks.schema.binding

import zio.blocks.schema.DynamicValue
import zio.blocks.typeid.TypeId

/**
 * Adds `reflection` to the [[BindingResolver]] companion; its implementation
 * differs between the JVM and Scala.js.
 */
private[binding] trait BindingResolverPlatformSpecific {

  /**
   * A resolver that derives [[Binding.Record]]s for case classes at run time,
   * with Java reflection. On the JVM, `resolveRecord` builds the binding from
   * the class's public constructor and its fields, and caches the result, found
   * or not, per normalized `TypeId`; every other `resolve` method returns
   * `None`. On Scala.js there is no reflection, so every method returns `None`.
   * Combine it with [[BindingResolver.defaults]] for primitives and
   * collections.
   *
   * The constructor used is the first that `getConstructors` returns, and the
   * fields are the first of `getDeclaredFields`, as many as the constructor has
   * parameters. On Scala 3, when a case class extends a trait that has a `val`,
   * that `val`'s field comes first, so the binding reads the wrong fields.
   */
  val reflection: BindingResolver = NoOpReflection

  private object NoOpReflection extends BindingResolver {

    def resolveRecord[A](implicit typeId: TypeId[A]): Option[Binding.Record[A]] = None

    def resolveVariant[A](implicit typeId: TypeId[A]): Option[Binding.Variant[A]] = None

    def resolvePrimitive[A](implicit typeId: TypeId[A]): Option[Binding.Primitive[A]] = None

    def resolveWrapper[A](implicit typeId: TypeId[A]): Option[Binding.Wrapper[A, _]] = None

    def resolveDynamic(implicit typeId: TypeId[DynamicValue]): Option[Binding.Dynamic] = None

    def resolveSeq[X](implicit typeId: TypeId[X], u: UnapplySeq[X]): Option[Binding.Seq[u.C, u.A]] = None

    def resolveSeqFor[C[_], A](typeId: TypeId[C[A]]): Option[Binding.Seq[C, A]] = None

    def resolveMap[X](implicit typeId: TypeId[X], u: UnapplyMap[X]): Option[Binding.Map[u.M, u.K, u.V]] = None

    def resolveMapFor[M[_, _], K, V](typeId: TypeId[M[K, V]]): Option[Binding.Map[M, K, V]] = None

    override def toString: String = "BindingResolver.Reflection (no-op on JS)"
  }
}
