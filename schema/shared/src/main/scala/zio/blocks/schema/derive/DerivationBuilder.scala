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
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding}
import zio.blocks.typeid.TypeId
import zio.blocks.docs.Doc

/**
 * Customizes one derivation of `TC[A]` from a schema: start it with
 * `Schema#deriving`, override the instance or add modifiers for parts of `A`
 * with `instance` and `modifier`, then read `derive`. The overrides apply to
 * this derivation only and leave the schema unchanged; to apply the same ones
 * to every derivation, add them to the deriver with `Deriver#withInstance` and
 * `Deriver#withModifier`. Each method returns a new builder.
 *
 * For a node, an instance override by optic wins over one by type. An override
 * by parent type and field name replaces that field's instance and wins over a
 * type override of the field's type, unless an optic override targets the same
 * field. For the same target, an override on the deriver wins over one given
 * here (see `docs/dev/issues.md`), and among those given here the last one
 * wins. A node whose instance is overridden is not derived, so modifiers added
 * for it, or for the fields of an overridden record, have no effect.
 *
 * {{{
 * import zio.blocks.schema.json._
 * import zio.blocks.typeid.TypeId
 *
 * case class Book(title: String, year: Int)
 * object Book {
 *   implicit val schema: Schema[Book] = Schema.derived
 * }
 *
 * val yearAsString: JsonCodec[Int] = new JsonCodec[Int] {
 *   def decodeValue(in: JsonReader): Int           = in.readString().toInt
 *   def encodeValue(x: Int, out: JsonWriter): Unit = out.writeVal(x.toString)
 * }
 *
 * val codec: JsonCodec[Book] = Book.schema
 *   .deriving(JsonCodecDeriver)
 *   .instance(TypeId.of[Int], yearAsString)
 *   .modifier(TypeId.of[Book], "title", Modifier.rename("name"))
 *   .derive
 *
 * val json = codec.encodeToString(Book("Zionomicon", 2021)) // {"name":"Zionomicon","year":"2021"}
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/type-class-derivation/ Type-Class Derivation]]
 */
final case class DerivationBuilder[TC[_], A](
  schema: Schema[A],
  deriver: Deriver[TC],
  instanceOverrides: IndexedSeq[InstanceOverride],
  modifierOverrides: IndexedSeq[ModifierOverride]
) {

  /**
   * Uses `instance` for the part of `A` that `optic` focuses on, instead of
   * deriving one. Only that path matches, so other occurrences of the same type
   * are derived as usual; a path through `each` matches every element of that
   * sequence. `derive` evaluates `instance`, not this call, and may evaluate it
   * more than once.
   *
   * With `JsonCodecDeriver`, an override inside a record type that is recursive
   * or has a field with a default value applies to every occurrence of that
   * type, or to none (see `docs/dev/issues.md`).
   *
   * {{{
   * import zio.blocks.schema.json._
   *
   * case class Book(title: String, author: String)
   * object Book extends CompanionOptics[Book] {
   *   implicit val schema: Schema[Book] = Schema.derived
   *   val title: Lens[Book, String]     = $(_.title)
   * }
   *
   * val upperCase: JsonCodec[String] = new JsonCodec[String] {
   *   def decodeValue(in: JsonReader): String           = in.readString()
   *   def encodeValue(x: String, out: JsonWriter): Unit = out.writeVal(x.toUpperCase)
   * }
   *
   * val codec = Book.schema.deriving(JsonCodecDeriver).instance(Book.title, upperCase).derive
   * val json  = codec.encodeToString(Book("Zionomicon", "John De Goes"))
   * // {"title":"ZIONOMICON","author":"John De Goes"}
   * }}}
   */
  def instance[B](optic: Optic[A, B], instance: => TC[B]): DerivationBuilder[TC, A] =
    copy(instanceOverrides = instanceOverrides.appended(new InstanceOverrideByOptic(optic.toDynamic, Lazy(instance))))

  /**
   * Uses `instance` for every occurrence of the type whose type ID is `typeId`,
   * at any depth, instead of deriving one. Occurrences match by equality with
   * `typeId`, so an override for `Option[Int]` leaves `Option[String]` derived
   * as usual.
   *
   * An override by optic or by field name wins over this one for the nodes it
   * targets, and a later override for the same type replaces this one. When the
   * deriver has a `Deriver#withInstance` override for the same type, that one
   * wins and this one is ignored without a warning (see `docs/dev/issues.md`).
   */
  def instance[B](typeId: TypeId[B], instance: => TC[B]): DerivationBuilder[TC, A] =
    copy(instanceOverrides = instanceOverrides.appended(new InstanceOverrideByType(typeId, Lazy(instance))))

  /**
   * Uses `instance` for the field or case named `termName` of the record or
   * variant whose type ID is `typeId`, wherever that type appears in `A`,
   * instead of deriving one. `typeId` identifies the parent type, not the
   * field's type.
   *
   * The compiler does not check `B` against the field's type: a mismatch fails
   * with a `ClassCastException` when the instance is used. A name that the
   * parent type does not have is ignored. This override wins over a type
   * override of the field's type, an optic override of the same field wins over
   * it, and a `Deriver#withInstance` override for the same parent and name wins
   * over it.
   */
  def instance[P, B](typeId: TypeId[P], termName: String, instance: => TC[B]): DerivationBuilder[TC, A] =
    copy(instanceOverrides =
      instanceOverrides.appended(new InstanceOverrideByTypeAndTermName(typeId, termName, Lazy(instance)))
    )

  /**
   * Adds the reflect-level `modifier` to every node whose type ID is `typeId`.
   * Which modifiers take effect depends on the deriver: for example,
   * `JsonCodecDeriver` reads `Modifier.fieldNaming` on a record. The deriver
   * receives it in front of the node's own modifiers and behind modifiers added
   * by optic.
   */
  def modifier[B](typeId: TypeId[B], modifier: Modifier.Reflect): DerivationBuilder[TC, A] =
    copy(modifierOverrides = modifierOverrides.appended(new ModifierReflectOverrideByType(typeId, modifier)))

  /**
   * Adds `modifier` at the path of `optic`. A [[Modifier.Reflect]] goes to the
   * node at that path. A [[Modifier.Term]], such as `Modifier.rename`, goes to
   * the field or case that the optic's last step names; when the last step is
   * neither, for example `each`, the modifier is ignored and this builder is
   * returned unchanged.
   *
   * Modifiers add up instead of replacing each other. The deriver receives
   * those added by optic first, in the order they were added, then those added
   * by type, then the schema's own, and `JsonCodecDeriver` uses the first
   * `rename` it finds. With `JsonCodecDeriver`, a modifier inside a record type
   * that is recursive or has a field with a default value can apply to every
   * occurrence of that type, or to none (see `docs/dev/issues.md`).
   *
   * {{{
   * import zio.blocks.schema.json.JsonCodecDeriver
   *
   * case class Book(title: String, year: Int)
   * object Book extends CompanionOptics[Book] {
   *   implicit val schema: Schema[Book] = Schema.derived
   *   val title: Lens[Book, String]     = $(_.title)
   * }
   *
   * val codec = Book.schema.deriving(JsonCodecDeriver).modifier(Book.title, Modifier.rename("name")).derive
   * val json  = codec.encodeToString(Book("Zionomicon", 2021)) // {"name":"Zionomicon","year":2021}
   * }}}
   */
  def modifier[B](optic: Optic[A, B], modifier: Modifier): DerivationBuilder[TC, A] = modifier match {
    case mr: Modifier.Reflect =>
      copy(modifierOverrides = modifierOverrides.appended(new ModifierReflectOverrideByOptic(optic.toDynamic, mr)))
    case mt: Modifier.Term =>
      val nodes = optic.toDynamic.nodes
      if (nodes.isEmpty) this
      else {
        nodes.last match {
          case f: DynamicOptic.Node.Field =>
            copy(modifierOverrides =
              modifierOverrides.appended(new ModifierTermOverrideByOptic(new DynamicOptic(nodes.init), f.name, mt))
            )
          case c: DynamicOptic.Node.Case =>
            copy(modifierOverrides =
              modifierOverrides.appended(new ModifierTermOverrideByOptic(new DynamicOptic(nodes.init), c.name, mt))
            )
          case _ => this
        }
      }
  }

  /**
   * Adds the term-level `modifier`, such as `Modifier.rename`, to the field or
   * case named `termName` of the record or variant whose type ID is `typeId`,
   * wherever that type appears in `A`. `typeId` identifies the parent type, not
   * the term's own type, and a name that the parent type does not have is
   * ignored. The deriver receives it behind modifiers added by optic and in
   * front of those added with `Deriver#withModifier` and the term's own, such
   * as a `@Modifier.rename` annotation.
   */
  def modifier[B](typeId: TypeId[B], termName: String, modifier: Modifier.Term): DerivationBuilder[TC, A] =
    copy(modifierOverrides = modifierOverrides.appended(new ModifierTermOverrideByType(typeId, termName, modifier)))

  /**
   * Derives the instance of `TC[A]` with the overrides added so far. It is a
   * `lazy val`: the first read derives the instance, and later reads of this
   * builder return the same one. `Schema#derive` starts a new builder on every
   * call, so it derives again each time. An error that the deriver throws, such
   * as `JsonCodecDeriver` rejecting an unknown `Modifier.fieldNaming` strategy,
   * is thrown from this read.
   *
   * {{{
   * import zio.blocks.schema.json.JsonCodecDeriver
   *
   * case class Book(title: String, year: Int)
   * object Book {
   *   implicit val schema: Schema[Book] = Schema.derived
   * }
   *
   * val builder = Book.schema.deriving(JsonCodecDeriver)
   * val same    = builder.derive eq builder.derive                         // true
   * val json    = builder.derive.encodeToString(Book("Zionomicon", 2021)) // {"title":"Zionomicon","year":2021}
   * }}}
   */
  lazy val derive: TC[A] = Reflect.withTransformCache {
    val allInstanceOverrides = instanceOverrides ++ deriver.instanceOverrides
    val allModifierOverrides = modifierOverrides ++ deriver.modifierOverrides
    val instanceByOpticMap   =
      allInstanceOverrides.collect { case InstanceOverrideByOptic(optic, instance) => (optic, instance) }.toMap
    val instanceByTypeMap =
      allInstanceOverrides.collect { case InstanceOverrideByType(typeId, instance) => (typeId, instance) }.toMap
    val instanceByTypeAndTermNameMap =
      allInstanceOverrides.collect { case InstanceOverrideByTypeAndTermName(typeId, termName, instance) =>
        ((typeId, termName), instance)
      }.toMap
    val modifierReflectByOpticMap =
      allModifierOverrides.foldLeft[Map[DynamicOptic, Chunk[Modifier.Reflect]]](Map.empty) {
        case (acc, ModifierReflectOverrideByOptic(optic, modifier)) =>
          acc.updated(optic, acc.getOrElse(optic, Chunk.empty).appended(modifier))
        case (acc, _) => acc
      }
    val modifierReflectByTypeMap =
      allModifierOverrides.foldLeft[Map[TypeId[?], Chunk[Modifier.Reflect]]](Map.empty) {
        case (acc, ModifierReflectOverrideByType(typeId, modifier)) =>
          acc.updated(typeId, acc.getOrElse(typeId, Chunk.empty).appended(modifier))
        case (acc, _) => acc
      }
    val modifierTermByOpticMap =
      allModifierOverrides.foldLeft[Map[DynamicOptic, Chunk[(String, Modifier.Term)]]](Map.empty) {
        case (acc, ModifierTermOverrideByOptic(optic, termName, modifier)) =>
          acc.updated(optic, acc.getOrElse(optic, Chunk.empty).appended((termName, modifier)))
        case (acc, _) => acc
      }
    val modifierTermByTypeMap =
      allModifierOverrides.foldLeft[Map[TypeId[?], Chunk[(String, Modifier.Term)]]](Map.empty) {
        case (acc, ModifierTermOverrideByType(typeId, termName, modifier)) =>
          acc.updated(typeId, acc.getOrElse(typeId, Chunk.empty).appended((termName, modifier)))
        case (acc, _) => acc
      }

    def prependCombinedModifiers[A0](modifiers: Seq[Modifier.Reflect], path: DynamicOptic, typeId: TypeId[A0]) =
      (modifierReflectByOpticMap.get(path), modifierReflectByTypeMap.get(typeId)) match {
        case (Some(modifiers1), Some(modifiers2)) => (modifiers1 ++ modifiers2) ++ modifiers
        case (Some(modifiers1), _)                => modifiers1 ++ modifiers
        case (_, Some(modifiers2))                => modifiers2 ++ modifiers
        case _                                    => modifiers
      }

    def combineModifiers[A0](path: DynamicOptic, typeId: TypeId[A0]) =
      (modifierTermByOpticMap.get(path), modifierTermByTypeMap.get(typeId)) match {
        case (Some(modifiers1), Some(modifiers2)) => modifiers1 ++ modifiers2
        case (Some(modifiers1), _)                => modifiers1
        case (_, Some(modifiers2))                => modifiers2
        case _                                    => Seq.empty
      }

    def getCustomInstance[A0](path: DynamicOptic, typeId: TypeId[A0]): Option[Lazy[TC[A0]]] =
      instanceByOpticMap
        .get(path)
        .orElse(instanceByTypeMap.get(typeId.asInstanceOf[TypeId[Any]]))
        .asInstanceOf[Option[Lazy[TC[A0]]]]

    type F[T, A0] = Binding[T, A0]
    type G[T, A0] = BindingInstance[TC, T, A0]

    def replaceFieldInstance[A0](reflect: Reflect[G, A0], newInstance: Lazy[TC[A0]]): Reflect[G, A0] = {
      def swap[T, B](bi: BindingInstance[TC, T, B]): BindingInstance[TC, T, B] =
        new BindingInstance(bi.binding, newInstance.asInstanceOf[Lazy[TC[B]]])

      (reflect match {
        case p: Reflect.Primitive[G, _]              => p.copy(primitiveBinding = swap(p.primitiveBinding))
        case r: Reflect.Record[G, _]                 => r.copy(recordBinding = swap(r.recordBinding))
        case v: Reflect.Variant[G, _]                => v.copy(variantBinding = swap(v.variantBinding))
        case s: Reflect.Sequence[G, _, _] @unchecked => s.copy(seqBinding = swap(s.seqBinding))
        case m: Reflect.Map[G, _, _, _] @unchecked   => m.copy(mapBinding = swap(m.mapBinding))
        case d: Reflect.Dynamic[G] @unchecked        => d.copy(dynamicBinding = swap(d.dynamicBinding))
        case w: Reflect.Wrapper[G, _, _]             => w.copy(wrapperBinding = swap(w.wrapperBinding))
        case d: Reflect.Deferred[G, A0]              => d.copy(_value = () => replaceFieldInstance(d._value(), newInstance))
      }).asInstanceOf[Reflect[G, A0]]
    }

    def applyTypeAndTermNameOverrides[A0](
      typeId: TypeId[A0],
      path: DynamicOptic,
      terms: IndexedSeq[Term[G, A0, ?]],
      pathBuilder: (DynamicOptic, String) => DynamicOptic
    ): IndexedSeq[Term[G, A0, ?]] =
      if (instanceByTypeAndTermNameMap.isEmpty) terms
      else {
        terms.map { term =>
          instanceByTypeAndTermNameMap.get((typeId.asInstanceOf[TypeId[Any]], term.name)) match {
            case Some(overrideInstance) =>
              val fieldPath = pathBuilder(path, term.name)
              if (instanceByOpticMap.contains(fieldPath)) term
              else {
                val newValue = replaceFieldInstance(
                  term.value.asInstanceOf[Reflect[G, Any]],
                  overrideInstance.asInstanceOf[Lazy[TC[Any]]]
                )
                term.copy(value = newValue.asInstanceOf[Reflect[G, term.Focus]]).asInstanceOf[Term[G, A0, ?]]
              }
            case _ => term
          }
        }
      }

    schema.reflect
      .transform[G](
        DynamicOptic.root,
        new ReflectTransformer[F, G] {
          override def transformRecord[A0](
            path: DynamicOptic,
            fields: IndexedSeq[Term[G, A0, ?]],
            typeId: TypeId[A0],
            metadata: F[BindingType.Record, A0],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect],
            storedDefaultValue: Option[DynamicValue],
            storedExamples: collection.immutable.Seq[DynamicValue]
          ): Lazy[Reflect.Record[G, A0]] = Lazy {
            implicit val hasBindingG: HasBinding[G] = BindingInstance.hasBinding[TC]
            val tempReflect                         =
              new Reflect.Record[G, A0](
                fields,
                typeId,
                new BindingInstance(
                  metadata,
                  Lazy.fail(new IllegalStateException("Temporary instance for fromDynamicValue conversion"))
                ),
                doc,
                modifiers
              )
            val instance = getCustomInstance[A0](path, typeId).getOrElse {
              val fieldsWithInstanceOverrides =
                applyTypeAndTermNameOverrides(typeId, path, fields, (p, name) => p.field(name))
              val modifiersToPrepend = combineModifiers(path, typeId)
              val updatedFields      =
                if (modifiersToPrepend.isEmpty) fieldsWithInstanceOverrides
                else {
                  fieldsWithInstanceOverrides.map { field =>
                    val fieldModifiersToPrepend = modifiersToPrepend.collect {
                      case (name, modifier) if name == field.name => modifier
                    }
                    if (fieldModifiersToPrepend.isEmpty) field
                    else field.copy(modifiers = fieldModifiersToPrepend ++ field.modifiers).asInstanceOf[Term[G, A0, ?]]
                  }
                }
              deriver
                .deriveRecord(
                  updatedFields,
                  typeId,
                  Binding.bindingHasBinding.record(metadata),
                  doc,
                  prependCombinedModifiers(modifiers, path, typeId),
                  storedDefaultValue.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption),
                  storedExamples.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
                )
            }
            new Reflect.Record(
              fields,
              typeId,
              new BindingInstance(metadata, instance),
              doc,
              modifiers,
              storedDefaultValue,
              storedExamples
            )
          }

          override def transformVariant[A0](
            path: DynamicOptic,
            cases: IndexedSeq[Term[G, A0, ? <: A0]],
            typeId: TypeId[A0],
            metadata: F[BindingType.Variant, A0],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect],
            storedDefaultValue: Option[DynamicValue],
            storedExamples: collection.immutable.Seq[DynamicValue]
          ): Lazy[Reflect.Variant[G, A0]] = Lazy {
            implicit val hasBindingG: HasBinding[G] = BindingInstance.hasBinding[TC]
            val tempReflect                         =
              new Reflect.Variant[G, A0](
                cases,
                typeId,
                new BindingInstance(
                  metadata,
                  Lazy.fail(new IllegalStateException("Temporary instance for fromDynamicValue conversion"))
                ),
                doc,
                modifiers
              )
            val instance = getCustomInstance[A0](path, typeId).getOrElse {
              val casesWithInstanceOverrides =
                applyTypeAndTermNameOverrides(typeId, path, cases, (p, name) => p.caseOf(name))
                  .asInstanceOf[IndexedSeq[Term[G, A0, ? <: A0]]]
              val modifiersToAdd = combineModifiers(path, typeId)
              val updatedCases   =
                if (modifiersToAdd.isEmpty) casesWithInstanceOverrides
                else {
                  casesWithInstanceOverrides.map { case_ =>
                    val caseModifiersToPrepend = modifiersToAdd.collect {
                      case (name, modifier) if name == case_.name => modifier
                    }
                    if (caseModifiersToPrepend.isEmpty) case_
                    else {
                      case_
                        .copy(modifiers = caseModifiersToPrepend ++ case_.modifiers)
                        .asInstanceOf[Term[G, A0, ? <: A0]]
                    }
                  }
                }
              deriver
                .deriveVariant(
                  updatedCases,
                  typeId,
                  Binding.bindingHasBinding.variant(metadata),
                  doc,
                  prependCombinedModifiers(modifiers, path, typeId),
                  storedDefaultValue.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption),
                  storedExamples.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
                )
            }
            new Reflect.Variant(
              cases,
              typeId,
              new BindingInstance(metadata, instance),
              doc,
              modifiers,
              storedDefaultValue,
              storedExamples
            )
          }

          override def transformSequence[A0, C[_]](
            path: DynamicOptic,
            element: Reflect[G, A0],
            typeId: TypeId[C[A0]],
            metadata: F[BindingType.Seq[C], C[A0]],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect],
            storedDefaultValue: Option[DynamicValue],
            storedExamples: collection.immutable.Seq[DynamicValue]
          ): Lazy[Reflect.Sequence[G, A0, C]] = Lazy {
            implicit val hasBindingG: HasBinding[G] = BindingInstance.hasBinding[TC]
            val tempReflect                         =
              new Reflect.Sequence[G, A0, C](
                element,
                typeId,
                new BindingInstance(
                  metadata,
                  Lazy.fail(new IllegalStateException("Temporary instance for fromDynamicValue conversion"))
                ),
                doc,
                modifiers
              )
            val instance = getCustomInstance[C[A0]](path, typeId).getOrElse(
              deriver
                .deriveSequence(
                  element,
                  typeId,
                  Binding.bindingHasBinding.seq(metadata),
                  doc,
                  prependCombinedModifiers(modifiers, path, typeId),
                  storedDefaultValue.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption),
                  storedExamples.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
                )
            )
            new Reflect.Sequence(
              element,
              typeId,
              new BindingInstance(metadata, instance),
              doc,
              modifiers,
              storedDefaultValue,
              storedExamples
            )
          }

          override def transformMap[Key, Value, M[_, _]](
            path: DynamicOptic,
            key: Reflect[G, Key],
            value: Reflect[G, Value],
            typeId: TypeId[M[Key, Value]],
            metadata: F[BindingType.Map[M], M[Key, Value]],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect],
            storedDefaultValue: Option[DynamicValue],
            storedExamples: collection.immutable.Seq[DynamicValue]
          ): Lazy[Reflect.Map[G, Key, Value, M]] = Lazy {
            implicit val hasBindingG: HasBinding[G] = BindingInstance.hasBinding[TC]
            val tempReflect                         =
              new Reflect.Map[G, Key, Value, M](
                key,
                value,
                typeId,
                new BindingInstance(
                  metadata,
                  Lazy.fail(new IllegalStateException("Temporary instance for fromDynamicValue conversion"))
                ),
                doc,
                modifiers
              )
            val instance = getCustomInstance[M[Key, Value]](path, typeId).getOrElse(
              deriver
                .deriveMap(
                  key,
                  value,
                  typeId,
                  Binding.bindingHasBinding.map(metadata),
                  doc,
                  prependCombinedModifiers(modifiers, path, typeId),
                  storedDefaultValue.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption),
                  storedExamples.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
                )
            )
            new Reflect.Map(
              key,
              value,
              typeId,
              new BindingInstance(metadata, instance),
              doc,
              modifiers,
              storedDefaultValue,
              storedExamples
            )
          }

          override def transformDynamic(
            path: DynamicOptic,
            typeId: TypeId[DynamicValue],
            metadata: F[BindingType.Dynamic, DynamicValue],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect],
            storedDefaultValue: Option[DynamicValue],
            storedExamples: collection.immutable.Seq[DynamicValue]
          ): Lazy[Reflect.Dynamic[G]] = Lazy {
            val instance = getCustomInstance[DynamicValue](path, TypeId.of[DynamicValue])
              .getOrElse(
                deriver.deriveDynamic[G](
                  Binding.bindingHasBinding.dynamic(metadata),
                  doc,
                  prependCombinedModifiers(modifiers, path, typeId),
                  storedDefaultValue,
                  storedExamples
                )
              )
            new Reflect.Dynamic(
              new BindingInstance(metadata, instance),
              typeId,
              doc,
              modifiers,
              storedDefaultValue,
              storedExamples
            )
          }

          override def transformPrimitive[A0](
            path: DynamicOptic,
            primitiveType: PrimitiveType[A0],
            typeId: TypeId[A0],
            metadata: F[BindingType.Primitive, A0],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect],
            storedDefaultValue: Option[DynamicValue],
            storedExamples: collection.immutable.Seq[DynamicValue]
          ): Lazy[Reflect.Primitive[G, A0]] = Lazy {
            val instance = getCustomInstance[A0](path, typeId).getOrElse(
              deriver
                .derivePrimitive(
                  primitiveType,
                  typeId,
                  Binding.bindingHasBinding.primitive(metadata),
                  doc,
                  prependCombinedModifiers(modifiers, path, typeId),
                  storedDefaultValue.flatMap(dv => primitiveType.fromDynamicValue(dv, Nil).toOption),
                  storedExamples.flatMap(dv => primitiveType.fromDynamicValue(dv, Nil).toOption)
                )
            )
            new Reflect.Primitive(
              primitiveType,
              typeId,
              new BindingInstance(metadata, instance),
              doc,
              modifiers,
              storedDefaultValue,
              storedExamples
            )
          }

          override def transformWrapper[A0, B](
            path: DynamicOptic,
            wrapped: Reflect[G, B],
            typeId: TypeId[A0],
            metadata: F[BindingType.Wrapper[A0, B], A0],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect],
            storedDefaultValue: Option[DynamicValue],
            storedExamples: collection.immutable.Seq[DynamicValue]
          ): Lazy[Reflect.Wrapper[G, A0, B]] = Lazy {
            implicit val hasBindingG: HasBinding[G] = BindingInstance.hasBinding[TC]
            val tempReflect                         =
              new Reflect.Wrapper[G, A0, B](
                wrapped,
                typeId,
                new BindingInstance(
                  metadata,
                  Lazy.fail(new IllegalStateException("Temporary instance for fromDynamicValue conversion"))
                ),
                doc,
                modifiers
              )
            val instance = getCustomInstance[A0](path, typeId)
              .getOrElse(
                deriver.deriveWrapper(
                  wrapped,
                  typeId,
                  Binding.bindingHasBinding.wrapper(metadata),
                  doc,
                  prependCombinedModifiers(modifiers, path, typeId),
                  storedDefaultValue.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption),
                  storedExamples.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
                )
              )
            new Reflect.Wrapper(
              wrapped,
              typeId,
              new BindingInstance(metadata, instance),
              doc,
              modifiers,
              storedDefaultValue,
              storedExamples
            )
          }
        }
      )
      .flatMap(_.metadata.instance)
      .force
  }
}
