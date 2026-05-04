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
import zio.blocks.schema.binding._
import zio.blocks.typeid.TypeId
import zio.test._

import java.lang.ref.WeakReference

/**
 * Regression test: a `DerivationBuilder.derive` call must not leave its
 * anonymous `ReflectTransformer` (with the captured override maps) pinned in
 * any per-`Reflect.Deferred` `ThreadLocal` cache after the call returns.
 *
 * Before the cache was scoped via `Reflect.withTransformCache`, every
 * derivation against a recursive schema permanently retained the transformer on
 * each `Deferred` of the schema's `Reflect` tree, accumulating across the
 * lifetime of long-lived worker threads (e.g. ZIO's `ZScheduler`).
 */
object DerivationCacheLeakSpec extends SchemaBaseSpec {

  case class Simple(name: String, value: Int)
  object Simple {
    implicit val schema: Schema[Simple] = Schema.derived
  }

  case class Tree(left: Option[Tree], right: Option[Tree])
  object Tree {
    implicit val schema: Schema[Tree] = Schema.derived
  }

  // A throwaway type class with no captured state of its own — the deriver under test only
  // needs to walk every `Reflect.Deferred` once.
  trait Marker[A] {
    def name: String
  }

  private def freshDeriver(): Deriver[Marker] =
    new Deriver[Marker] {

      private def named[A](n: String): Lazy[Marker[A]] =
        Lazy(new Marker[A] { val name: String = n })

      override def derivePrimitive[A](
        primitiveType: PrimitiveType[A],
        typeId: TypeId[A],
        binding: Binding.Primitive[A],
        doc: zio.blocks.docs.Doc,
        modifiers: Seq[Modifier.Reflect],
        defaultValue: Option[A],
        examples: Seq[A]
      ): Lazy[Marker[A]] = named(s"primitive:${typeId.name}")

      override def deriveRecord[F[_, _], A](
        fields: IndexedSeq[Term[F, A, ?]],
        typeId: TypeId[A],
        binding: Binding.Record[A],
        doc: zio.blocks.docs.Doc,
        modifiers: Seq[Modifier.Reflect],
        defaultValue: Option[A],
        examples: Seq[A]
      )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Marker[A]] = named(s"record:${typeId.name}")

      override def deriveVariant[F[_, _], A](
        cases: IndexedSeq[Term[F, A, ?]],
        typeId: TypeId[A],
        binding: Binding.Variant[A],
        doc: zio.blocks.docs.Doc,
        modifiers: Seq[Modifier.Reflect],
        defaultValue: Option[A],
        examples: Seq[A]
      )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Marker[A]] = named(s"variant:${typeId.name}")

      override def deriveSequence[F[_, _], C[_], A](
        element: Reflect[F, A],
        typeId: TypeId[C[A]],
        binding: Binding.Seq[C, A],
        doc: zio.blocks.docs.Doc,
        modifiers: Seq[Modifier.Reflect],
        defaultValue: Option[C[A]],
        examples: Seq[C[A]]
      )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Marker[C[A]]] = named(s"seq:${typeId.name}")

      override def deriveMap[F[_, _], M[_, _], K, V](
        key: Reflect[F, K],
        value: Reflect[F, V],
        typeId: TypeId[M[K, V]],
        binding: Binding.Map[M, K, V],
        doc: zio.blocks.docs.Doc,
        modifiers: Seq[Modifier.Reflect],
        defaultValue: Option[M[K, V]],
        examples: Seq[M[K, V]]
      )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Marker[M[K, V]]] = named(s"map:${typeId.name}")

      override def deriveDynamic[F[_, _]](
        binding: Binding.Dynamic,
        doc: zio.blocks.docs.Doc,
        modifiers: Seq[Modifier.Reflect],
        defaultValue: Option[DynamicValue],
        examples: Seq[DynamicValue]
      )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Marker[DynamicValue]] = named("dynamic")

      override def deriveWrapper[F[_, _], A, B](
        wrapped: Reflect[F, B],
        typeId: TypeId[A],
        binding: Binding.Wrapper[A, B],
        doc: zio.blocks.docs.Doc,
        modifiers: Seq[Modifier.Reflect],
        defaultValue: Option[A],
        examples: Seq[A]
      )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Marker[A]] = named(s"wrapper:${typeId.name}")
    }

  private def gcUntilCleared(ref: WeakReference[?], maxRounds: Int = 50): Boolean = {
    var rounds = 0
    while (ref.get != null && rounds < maxRounds) {
      System.gc()
      System.runFinalization()
      Thread.sleep(20)
      rounds += 1
    }
    ref.get == null
  }

  // Run derivation in its own frame so that the only references that survive are the
  // returned `Marker` and the `WeakReference` to the builder.
  private def deriveAndWeakRef[A](
    schema: Schema[A]
  ): (Marker[A], WeakReference[DerivationBuilder[Marker, A]]) = {
    val builder = schema.deriving[Marker](freshDeriver())
    val marker  = builder.derive
    (marker, new WeakReference[DerivationBuilder[Marker, A]](builder))
  }

  def spec: Spec[TestEnvironment, Any] = suite("DerivationCacheLeakSpec")(
    test("transformCache is empty after derive on a simple schema") {
      val (marker, _) = deriveAndWeakRef(Simple.schema)
      assertTrue(marker.name == "record:Simple") &&
      assertTrue(Reflect.transformCache.get.isEmpty)
    },
    test("transformCache is empty after derive on a recursive schema") {
      val (marker, _) = deriveAndWeakRef(Tree.schema)
      assertTrue(marker.name == "record:Tree") &&
      assertTrue(Reflect.transformCache.get.isEmpty)
    },
    test("DerivationBuilder.derive does not pin its ReflectTransformer after returning (simple)") {
      val (marker, ref) = deriveAndWeakRef(Simple.schema)
      assertTrue(marker.name == "record:Simple") &&
      assertTrue(gcUntilCleared(ref))
    },
    test("DerivationBuilder.derive does not pin its ReflectTransformer after returning (recursive)") {
      val (marker, ref) = deriveAndWeakRef(Tree.schema)
      assertTrue(marker.name == "record:Tree") &&
      assertTrue(gcUntilCleared(ref))
    }
  )
}
