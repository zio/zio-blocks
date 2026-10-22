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

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.typeid.TypeId
import zio.blocks.docs.Doc

/**
 * A `Deriver[TC]` builds instances of the type class `TC` from a schema, such
 * as `JsonCodecDeriver` for `JsonCodec`. Implement it to derive your own type
 * class for any type that has a `Schema`, or configure an existing one, and
 * pass it to `Schema#derive` or `Schema#deriving`.
 *
 * It has one method for each kind of schema node: primitive, record, variant,
 * sequence, map, dynamic value and wrapper. `DerivationBuilder#derive` walks
 * the schema's `Reflect` tree and calls the method for each node, passing the
 * node's children with their instances, which a method reads with `instance`. A
 * node overridden by optic or by type is not derived: its method is not called
 * for it. The nodes below it, and a field or case overridden by parent type and
 * name, are still derived; the field's result is then replaced by the override.
 * A child behind a `Reflect.Deferred` node, such as a field with a default
 * value or a recursive field, is derived only when the deriver first reads that
 * child, which can be after `derive` returns.
 *
 * Every method returns a [[zio.blocks.schema.Lazy Lazy]]. A recursive type,
 * such as `case class Tree(value: Int, children: List[Tree])`, reaches the
 * deriver again inside its own children, so force a child's instance on first
 * use, for example in a `lazy val` of the instance you return, and not while
 * deriving: forcing it inside `deriveRecord` derives the same type again
 * without end and overflows the stack.
 *
 * `withInstance` and `withModifier` return a deriver that applies the same
 * overrides to every derivation it runs.
 *
 * {{{
 * import zio.blocks.schema.json.{JsonCodec, JsonCodecDeriver}
 *
 * case class Book(title: String, publishedIn: Int)
 * object Book {
 *   implicit val schema: Schema[Book] = Schema.derived
 * }
 *
 * val codec: JsonCodec[Book] = Book.schema.derive(JsonCodecDeriver.withFieldNameMapper(NameMapper.SnakeCase))
 * val json                   = codec.encodeToString(Book("Zionomicon", 2021)) // {"title":"Zionomicon","published_in":2021}
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/type-class-derivation/ Type-Class Derivation]]
 */
trait Deriver[TC[_]] { self =>

  /**
   * The [[zio.blocks.schema.derive.HasInstance HasInstance]] that reads
   * instances of `TC` out of node metadata of type `F`.
   */
  type HasInstance[F[_, _]] = zio.blocks.schema.derive.HasInstance[F, TC]

  /**
   * Returns the binding stored in the metadata `fa` of a child node, such as
   * `field.value.metadata` in `deriveRecord`. The binding constructs and
   * deconstructs the child's values.
   */
  final def binding[F[_, _], T, A](fa: F[T, A])(implicit F: HasBinding[F]): Binding[T, A] = F.binding(fa)

  /**
   * Returns the instance stored in the metadata `fa` of a child node, such as
   * `field.value.metadata` in `deriveRecord`. It is the overriding instance
   * when the child is overridden. Force it on first use, not while deriving, so
   * that recursive types work.
   */
  final def instance[F[_, _], T, A](fa: F[T, A])(implicit D: HasInstance[F]): Lazy[TC[A]] = D.instance(fa)

  /**
   * Returns the instance for a primitive type, such as `Int`, `String` or
   * `java.time.Instant`. `primitiveType` says which one and carries its
   * validation.
   *
   * In every method, `modifiers` are the node's own modifiers with those of
   * matching modifier overrides in front, and `defaultValue` and `examples` are
   * the ones stored in the schema; a stored value that does not convert to the
   * node's type is left out.
   */
  def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding.Primitive[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[TC[A]]

  /**
   * Returns the instance for a record, such as a case class. Each of `fields`
   * is a [[zio.blocks.schema.Term Term]] with the field's name, its modifiers
   * (those of matching modifier overrides first) and its `Reflect`, whose
   * `metadata` holds the field's binding and instance. `binding` constructs and
   * deconstructs `A`.
   */
  def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Record[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]]

  /**
   * Returns the instance for a variant, such as a sealed trait or an enum. Each
   * of `cases` is a [[zio.blocks.schema.Term Term]] for one case, as in
   * `deriveRecord`. `binding` tells which case a value is and downcasts it to
   * that case.
   */
  def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Variant[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]]

  /**
   * Returns the instance for a sequence, such as a `List`, a `Vector` or an
   * `Array`, of `element` values. `binding` builds and iterates the collection.
   */
  def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding.Seq[C, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[C[A]]]

  /**
   * Returns the instance for a map from `key` values to `value` values.
   * `binding` builds and iterates the map.
   */
  def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding.Map[M, K, V],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[M[K, V]]]

  /**
   * Returns the instance for [[zio.blocks.schema.DynamicValue DynamicValue]],
   * the node of a schema part that accepts any value.
   */
  def deriveDynamic[F[_, _]](
    binding: Binding.Dynamic,
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[DynamicValue]]

  /**
   * Returns the instance for a type `A` that wraps a `B`, such as a newtype or
   * the result of `Schema#transform`. `wrapped` is the node of `B`, and
   * `binding` converts between `A` and `B`.
   */
  def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding.Wrapper[A, B],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]]

  /**
   * The instance overrides added with `withInstance`, oldest first; empty for a
   * deriver without any. `DerivationBuilder#derive` applies them after its own,
   * so for the same target they win over `DerivationBuilder#instance`.
   */
  def instanceOverrides: IndexedSeq[InstanceOverride] = Chunk.empty

  /**
   * The modifier overrides added with `withModifier`, oldest first; empty for a
   * deriver without any. `DerivationBuilder#derive` passes their modifiers to
   * the deriver after those of `DerivationBuilder#modifier`.
   */
  def modifierOverrides: IndexedSeq[ModifierOverride] = Chunk.empty

  /**
   * Returns a deriver that uses `instance` for every occurrence of the type
   * `A`, at any depth, in each derivation it runs, instead of deriving one.
   * Occurrences match by equality with the implicit `typeId`, so an override
   * for `Option[Int]` leaves `Option[String]` derived as usual. The override is
   * evaluated when the deriver first forces it, not here: during `derive` for
   * `JsonCodecDeriver`, possibly later for a deriver that forces children on
   * first use. It may be evaluated more than once, and again on each derivation
   * that uses this deriver (see `docs/dev/issues.md`).
   *
   * An override by optic or by field name wins over this one for the nodes it
   * targets. For the same type, a later `withInstance` wins over an earlier
   * one, and this override wins over one given to `DerivationBuilder#instance`
   * for the same derivation, which is then ignored without a warning (see
   * `docs/dev/issues.md`).
   *
   * Derivation never looks for an implicit `TC[A]` in scope.
   * `Schema#jsonCodec`, `Schema#getInstance` and the `toJsonString` and
   * `fromJson` syntax use the format's default deriver, so they ignore this
   * override: derive the instance with `Schema#derive` or `Schema#deriving` and
   * call it directly. The result is a plain `Deriver[TC]`, so set format
   * options, such as `JsonCodecDeriver#withFieldNameMapper`, before this call.
   *
   * {{{
   * import java.time.LocalDate
   * import java.time.format.DateTimeFormatter
   * import zio.blocks.schema.json._
   *
   * case class Book(title: String, published: LocalDate)
   * object Book {
   *   implicit val schema: Schema[Book] = Schema.derived
   * }
   *
   * val dayFirst = DateTimeFormatter.ofPattern("dd/MM/yyyy")
   * val dayFirstDate: JsonCodec[LocalDate] = new JsonCodec[LocalDate] {
   *   def decodeValue(in: JsonReader): LocalDate           = LocalDate.parse(in.readString(), dayFirst)
   *   def encodeValue(x: LocalDate, out: JsonWriter): Unit = out.writeVal(dayFirst.format(x))
   * }
   *
   * val codec = Book.schema.derive(JsonCodecDeriver.withInstance[LocalDate](dayFirstDate))
   * val json  = codec.encodeToString(Book("Zionomicon", LocalDate.of(2021, 12, 1)))
   * // {"title":"Zionomicon","published":"01/12/2021"}
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/schema/codec/ Codec]]
   */
  final def withInstance[A](instance: => TC[A])(implicit typeId: TypeId[A]): Deriver[TC] =
    new DeriverWithOverrides[TC](
      self,
      Chunk(new InstanceOverrideByType[TC, A](typeId, Lazy(instance))),
      Chunk.empty
    )

  /**
   * Returns a deriver that uses `instance` for the field or case named
   * `termName` of the record or variant whose type ID is `typeId`, wherever
   * that type appears, instead of deriving one. `typeId` identifies the parent
   * type, not the field's type.
   *
   * The compiler does not check `B` against the field's type: a mismatch fails
   * with a `ClassCastException` when the instance is used. A name that `A` does
   * not have is ignored. This override wins over a type override of the field's
   * type, and an optic override of the same field wins over it. For the same
   * parent and name, it wins over one given to `DerivationBuilder#instance` for
   * the same derivation.
   */
  final def withInstance[A, B](typeId: TypeId[A], termName: String, instance: => TC[B]): Deriver[TC] =
    new DeriverWithOverrides[TC](
      self,
      Chunk(new InstanceOverrideByTypeAndTermName[TC, A, B](typeId, termName, Lazy(instance))),
      Chunk.empty
    )

  /**
   * Returns a deriver that adds the reflect-level `modifier` to every node
   * whose type ID is `typeId` in each derivation it runs, without changing the
   * schema. Which modifiers take effect depends on the deriver: for example,
   * `JsonCodecDeriver` reads `Modifier.fieldNaming` on a record.
   *
   * The deriver receives it in front of the node's own modifiers, behind
   * modifiers added by optic and those added for the same type with
   * `DerivationBuilder#modifier`. On a node overridden by optic or by type the
   * modifier has no effect, since the node is not derived. A field overridden
   * by parent type and name is still derived with it, so a modifier that fails
   * derivation, such as an unknown `Modifier.fieldNaming`, still throws,
   * although the output uses the override.
   */
  final def withModifier[A](typeId: TypeId[A], modifier: Modifier.Reflect): Deriver[TC] =
    new DeriverWithOverrides[TC](
      self,
      Chunk.empty,
      Chunk(new ModifierReflectOverrideByType[A](typeId, modifier))
    )

  /**
   * Returns a deriver that adds the term-level `modifier`, such as
   * `Modifier.rename`, to the field or case named `termName` of the record or
   * variant whose type ID is `typeId`, wherever that type appears, without
   * changing the schema. A name that `A` does not have is ignored.
   *
   * The deriver receives it in front of the term's own modifiers, such as a
   * `@Modifier.rename` annotation, and behind those added with
   * `DerivationBuilder#modifier` for the same term. `JsonCodecDeriver` uses the
   * first `rename` it finds, so this one wins over the annotation and loses to
   * the builder's.
   *
   * {{{
   * import zio.blocks.schema.json.JsonCodecDeriver
   * import zio.blocks.typeid.TypeId
   *
   * case class Book(title: String, year: Int)
   * object Book {
   *   implicit val schema: Schema[Book] = Schema.derived
   * }
   *
   * val deriver = JsonCodecDeriver.withModifier(TypeId.of[Book], "year", Modifier.rename("published"))
   * val json    = Book.schema.derive(deriver).encodeToString(Book("Zionomicon", 2021))
   * // {"title":"Zionomicon","published":2021}
   * }}}
   */
  final def withModifier[A](typeId: TypeId[A], termName: String, modifier: Modifier.Term): Deriver[TC] =
    new DeriverWithOverrides[TC](
      self,
      Chunk.empty,
      Chunk(new ModifierTermOverrideByType[A](typeId, termName, modifier))
    )
}
