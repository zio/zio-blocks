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
import zio.blocks.docs.Doc
import zio.test._

/**
 * Term-level derivation overrides for unknown terms are silently ignored:
 * derivation still succeeds and the override has no effect.
 */
object LenientDerivationSpec extends SchemaBaseSpec {

  trait Marker[A]
  object Marker {
    def apply[A]: Marker[A] = new Marker[A] {}

    implicit val deriver: Deriver[Marker] = new Deriver[Marker] {
      def derivePrimitive[A](
        primitiveType: PrimitiveType[A],
        typeId: TypeId[A],
        binding: Binding.Primitive[A],
        doc: Doc,
        modifiers: Seq[Modifier.Reflect],
        defaultValue: Option[A],
        examples: Seq[A]
      ): Lazy[Marker[A]] = Lazy(Marker[A])

      def deriveRecord[F[_, _], A](
        fields: IndexedSeq[Term[F, A, ?]],
        typeId: TypeId[A],
        binding: Binding.Record[A],
        doc: Doc,
        modifiers: Seq[Modifier.Reflect],
        defaultValue: Option[A],
        examples: Seq[A]
      )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Marker[A]] = Lazy(Marker[A])

      def deriveVariant[F[_, _], A](
        cases: IndexedSeq[Term[F, A, ?]],
        typeId: TypeId[A],
        binding: Binding.Variant[A],
        doc: Doc,
        modifiers: Seq[Modifier.Reflect],
        defaultValue: Option[A],
        examples: Seq[A]
      )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Marker[A]] = Lazy(Marker[A])

      def deriveSequence[F[_, _], C[_], A](
        element: Reflect[F, A],
        typeId: TypeId[C[A]],
        binding: Binding.Seq[C, A],
        doc: Doc,
        modifiers: Seq[Modifier.Reflect],
        defaultValue: Option[C[A]],
        examples: Seq[C[A]]
      )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Marker[C[A]]] = Lazy(Marker[C[A]])

      def deriveMap[F[_, _], M[_, _], K, V](
        key: Reflect[F, K],
        value: Reflect[F, V],
        typeId: TypeId[M[K, V]],
        binding: Binding.Map[M, K, V],
        doc: Doc,
        modifiers: Seq[Modifier.Reflect],
        defaultValue: Option[M[K, V]],
        examples: Seq[M[K, V]]
      )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Marker[M[K, V]]] = Lazy(Marker[M[K, V]])

      def deriveDynamic[F[_, _]](
        binding: Binding.Dynamic,
        doc: Doc,
        modifiers: Seq[Modifier.Reflect],
        defaultValue: Option[DynamicValue],
        examples: Seq[DynamicValue]
      )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Marker[DynamicValue]] = Lazy(Marker[DynamicValue])

      def deriveWrapper[F[_, _], A, B](
        wrapped: Reflect[F, B],
        typeId: TypeId[A],
        binding: Binding.Wrapper[A, B],
        doc: Doc,
        modifiers: Seq[Modifier.Reflect],
        defaultValue: Option[A],
        examples: Seq[A]
      )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Marker[A]] = Lazy(Marker[A])
    }
  }

  case class ReportPerson(name: String, age: Int)
  object ReportPerson {
    implicit val schema: Schema[ReportPerson] = Schema.derived
  }

  private val parentId: TypeId[ReportPerson] = Schema[ReportPerson].reflect.typeId

  def spec: Spec[TestEnvironment, Any] = suite("LenientDerivationSpec")(
    test("unknown instance term is ignored and derivation succeeds") {
      val builder = Schema[ReportPerson]
        .deriving(Marker.deriver)
        .instance(parentId, "naem", Marker[String])
      // No exception: derivation still succeeds, override ignored as before.
      assertTrue(builder.derive != null)
    },
    test("unknown modifier term is ignored and derivation succeeds") {
      val builder = Schema[ReportPerson]
        .deriving(Marker.deriver)
        .modifier(parentId, "naem", Modifier.rename("n"))
      // No exception: derivation still succeeds, modifier ignored as before.
      assertTrue(builder.derive != null)
    }
  )
}
