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
 * derivation still succeeds and the override has no effect on derived behavior.
 *
 * Classification: contract characterization of the lenient-override contract
 * (unknown terms ignored, known terms applied). The positive-control tests pin
 * that overrides for known terms take effect, so the unknown-term tests are
 * nonvacuous: they assert the derived behavior is identical to the baseline.
 */
object LenientDerivationSpec extends SchemaBaseSpec {

  trait Show[A] {
    def show(value: A): String
  }

  object Show {
    def apply[A](f: A => String): Show[A] = new Show[A] {
      def show(value: A): String = f(value)
    }
  }

  /**
   * Minimal observable deriver: renders records field by field, honoring
   * `Modifier.rename` on terms. Only primitives and records are reachable for
   * the fixtures below; the remaining shapes fail fast if ever reached.
   */
  object ShowDeriver extends Deriver[Show] {
    def derivePrimitive[A](
      primitiveType: PrimitiveType[A],
      typeId: TypeId[A],
      binding: Binding.Primitive[A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    ): Lazy[Show[A]] = Lazy(Show[A](value => String.valueOf(value)))

    def deriveRecord[F[_, _], A](
      fields: IndexedSeq[Term[F, A, ?]],
      typeId: TypeId[A],
      binding: Binding.Record[A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[A]] = {
      val recordFields  = fields.asInstanceOf[IndexedSeq[Term[Binding, A, ?]]]
      val recordBinding = binding
      val recordReflect = new Reflect.Record[Binding, A](recordFields, typeId, recordBinding, doc, modifiers)
      Lazy {
        new Show[A] {
          private lazy val fieldShows: IndexedSeq[Show[Any]] =
            fields.map(field => instance(field.value.metadata).asInstanceOf[Lazy[Show[Any]]].force)
          private def displayName(field: Term[F, A, ?]): String =
            field.modifiers.collectFirst { case Modifier.rename(name) => name }.getOrElse(field.name)
          def show(value: A): String = {
            val registers = Registers(recordReflect.usedRegisters)
            recordBinding.deconstructor.deconstruct(registers, RegisterOffset.Zero, value)
            val rendered = fields.indices.map { i =>
              val fieldValue = recordReflect.registers(i).get(registers, RegisterOffset.Zero)
              s"${displayName(fields(i))} = ${fieldShows(i).show(fieldValue)}"
            }
            s"${typeId.name}(${rendered.mkString(", ")})"
          }
        }
      }
    }

    def deriveVariant[F[_, _], A](
      cases: IndexedSeq[Term[F, A, ?]],
      typeId: TypeId[A],
      binding: Binding.Variant[A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[A]] =
      Lazy.fail(new UnsupportedOperationException("ShowDeriver does not support variants"))

    def deriveSequence[F[_, _], C[_], A](
      element: Reflect[F, A],
      typeId: TypeId[C[A]],
      binding: Binding.Seq[C, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[C[A]],
      examples: Seq[C[A]]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[C[A]]] =
      Lazy.fail(new UnsupportedOperationException("ShowDeriver does not support sequences"))

    def deriveMap[F[_, _], M[_, _], K, V](
      key: Reflect[F, K],
      value: Reflect[F, V],
      typeId: TypeId[M[K, V]],
      binding: Binding.Map[M, K, V],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[M[K, V]],
      examples: Seq[M[K, V]]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[M[K, V]]] =
      Lazy.fail(new UnsupportedOperationException("ShowDeriver does not support maps"))

    def deriveDynamic[F[_, _]](
      binding: Binding.Dynamic,
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[DynamicValue],
      examples: Seq[DynamicValue]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[DynamicValue]] =
      Lazy.fail(new UnsupportedOperationException("ShowDeriver does not support dynamic values"))

    def deriveWrapper[F[_, _], A, B](
      wrapped: Reflect[F, B],
      typeId: TypeId[A],
      binding: Binding.Wrapper[A, B],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[A]] =
      Lazy.fail(new UnsupportedOperationException("ShowDeriver does not support wrappers"))
  }

  case class ReportPerson(name: String, age: Int)
  object ReportPerson {
    implicit val schema: Schema[ReportPerson] = Schema.derived
  }

  private val parentId: TypeId[ReportPerson] = Schema[ReportPerson].reflect.typeId

  private val alice: ReportPerson = ReportPerson("Alice", 30)

  private val baseline: String = "ReportPerson(name = Alice, age = 30)"

  private val angledName: Show[String] = Show(name => s"<$name>")

  def spec: Spec[TestEnvironment, Any] = suite("LenientDerivationSpec")(
    test("derives ordinary per-field behavior without overrides") {
      val show = Schema[ReportPerson].deriving(ShowDeriver).derive
      assertTrue(show.show(alice) == baseline)
    },
    test("known instance term override takes effect on derived behavior") {
      val show = Schema[ReportPerson].deriving(ShowDeriver).instance(parentId, "name", angledName).derive
      assertTrue(show.show(alice) == "ReportPerson(name = <Alice>, age = 30)")
    },
    test("unknown instance term is ignored and ordinary behavior is intact") {
      val show = Schema[ReportPerson].deriving(ShowDeriver).instance(parentId, "naem", angledName).derive
      // No exception: derivation still succeeds, override ignored as before.
      assertTrue(show.show(alice) == baseline)
    },
    test("known modifier term override takes effect on derived behavior") {
      val show =
        Schema[ReportPerson].deriving(ShowDeriver).modifier(parentId, "name", Modifier.rename("n")).derive
      assertTrue(show.show(alice) == "ReportPerson(n = Alice, age = 30)")
    },
    test("unknown modifier term is ignored and ordinary behavior is intact") {
      val show =
        Schema[ReportPerson].deriving(ShowDeriver).modifier(parentId, "naem", Modifier.rename("n")).derive
      // No exception: derivation still succeeds, modifier ignored as before.
      assertTrue(show.show(alice) == baseline)
    }
  )
}
