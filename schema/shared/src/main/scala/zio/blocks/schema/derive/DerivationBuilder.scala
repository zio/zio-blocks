package zio.blocks.schema.derive

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding}
import zio.blocks.typeid.TypeId
import zio.blocks.docs.Doc

/**
 * A `DerivationBuilder` is capable of deriving a type class instance for any
 * data type that has a schema. Though instances for all substructures can be
 * derived automatically, you can also specify instances for specific
 * substructures using the `withOverride` method.
 *
 * {{{
 * val personSchema = Schema.derive[Person]
 *
 * val personEq: Eq[Person] = personSchema
 *   .deriving[Eq]
 *   .instance(Person.age, Eq[Int])
 *   .modifier(Person.name, Modifier.rename("fullName"))
 *   .derive
 * }}}
 */
final case class DerivationBuilder[TC[_], A](
  schema: Schema[A],
  deriver: Deriver[TC],
  instanceOverrides: IndexedSeq[InstanceOverride],
  modifierOverrides: IndexedSeq[ModifierOverride]
) {

  /**
   * Overrides the type class instance at an exact path in the schema tree
   * identified by `optic`. This is the most precise override: it targets one
   * specific location.
   */
  def instance[B](optic: Optic[A, B], instance: => TC[B]): DerivationBuilder[TC, A] =
    copy(instanceOverrides = instanceOverrides :+ new InstanceOverrideByOptic(optic.toDynamic, Lazy(instance)))

  /**
   * Overrides the type class instance for every occurrence of the type
   * identified by `typeId`, regardless of where it appears in the schema tree.
   * This is the least precise override.
   */
  def instance[B](typeId: TypeId[B], instance: => TC[B]): DerivationBuilder[TC, A] =
    copy(instanceOverrides = instanceOverrides :+ new InstanceOverrideByType(typeId, Lazy(instance)))

  /**
   * Overrides the type class instance for a term (record field or variant case)
   * identified by `termName` inside a parent record or variant identified by
   * `typeId`. This is a medium-precision override between optic-based (exact
   * path) and type-based (all occurrences).
   *
   * The `typeId` refers to the ''parent'' record/variant type, not the field
   * type. The field type `B` is not statically checked against the actual term
   * type. If no term with the given name exists in the parent type, the
   * override is silently ignored.
   */
  def instance[P, B](typeId: TypeId[P], termName: String, instance: => TC[B]): DerivationBuilder[TC, A] =
    copy(instanceOverrides =
      instanceOverrides :+ new InstanceOverrideByTypeAndTermName(typeId, termName, Lazy(instance))
    )

  /**
   * Adds a reflect-level modifier for every occurrence of the type identified
   * by `typeId`.
   */
  def modifier[B](typeId: TypeId[B], modifier: Modifier.Reflect): DerivationBuilder[TC, A] =
    copy(modifierOverrides = modifierOverrides :+ new ModifierReflectOverrideByType(typeId, modifier))

  /**
   * Adds a modifier at an exact path in the schema tree identified by `optic`.
   * Accepts both [[Modifier.Reflect]] (applied to the node itself) and
   * [[Modifier.Term]] (applied to the terminal field or case at the end of the
   * optic path).
   */
  def modifier[B](optic: Optic[A, B], modifier: Modifier): DerivationBuilder[TC, A] = modifier match {
    case mr: Modifier.Reflect =>
      copy(modifierOverrides = modifierOverrides :+ new ModifierReflectOverrideByOptic(optic.toDynamic, mr))
    case mt: Modifier.Term =>
      val nodes = optic.toDynamic.nodes
      if (nodes.isEmpty) this
      else {
        val path = new DynamicOptic(nodes.init)
        nodes.last match {
          case DynamicOptic.Node.Field(name) =>
            copy(modifierOverrides = modifierOverrides :+ new ModifierTermOverrideByOptic(path, name, mt))
          case DynamicOptic.Node.Case(name) =>
            copy(modifierOverrides = modifierOverrides :+ new ModifierTermOverrideByOptic(path, name, mt))
          case _ => this
        }
      }
  }

  /**
   * Adds a term-level modifier for a field or variant case identified by
   * `termName` inside a parent type identified by `typeId`. The `typeId` refers
   * to the ''parent'' record/variant type that owns the term, not the term's
   * own type. If no term with the given name exists in the parent type, the
   * modifier is silently ignored.
   */
  def modifier[B](typeId: TypeId[B], termName: String, modifier: Modifier.Term): DerivationBuilder[TC, A] =
    copy(modifierOverrides = modifierOverrides :+ new ModifierTermOverrideByType(typeId, termName, modifier))

  lazy val derive: TC[A] = {
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
        .map(_.asInstanceOf[Lazy[TC[A0]]])

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
      else
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
            case None => term
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
            val defaultValue = storedDefaultValue.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
            val examples     = storedExamples.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
            val instance     = getCustomInstance[A0](path, typeId).getOrElse {
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
                  metadata,
                  doc,
                  prependCombinedModifiers(modifiers, path, typeId),
                  defaultValue,
                  examples
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
            val defaultValue = storedDefaultValue.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
            val examples     = storedExamples.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
            val instance     = getCustomInstance[A0](path, typeId).getOrElse {
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
                    else
                      case_
                        .copy(modifiers = caseModifiersToPrepend ++ case_.modifiers)
                        .asInstanceOf[Term[G, A0, ? <: A0]]
                  }
                }
              deriver
                .deriveVariant(
                  updatedCases,
                  typeId,
                  metadata,
                  doc,
                  prependCombinedModifiers(modifiers, path, typeId),
                  defaultValue,
                  examples
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
            val defaultValue = storedDefaultValue.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
            val examples     = storedExamples.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
            val instance     = getCustomInstance[C[A0]](path, typeId).getOrElse(
              deriver
                .deriveSequence(
                  element,
                  typeId,
                  metadata,
                  doc,
                  prependCombinedModifiers(modifiers, path, typeId),
                  defaultValue,
                  examples
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
            val defaultValue = storedDefaultValue.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
            val examples     = storedExamples.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
            val instance     = getCustomInstance[M[Key, Value]](path, typeId).getOrElse(
              deriver
                .deriveMap(
                  key,
                  value,
                  typeId,
                  metadata,
                  doc,
                  prependCombinedModifiers(modifiers, path, typeId),
                  defaultValue,
                  examples
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
                  metadata,
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
            val defaultValue = storedDefaultValue.flatMap(dv => primitiveType.fromDynamicValue(dv, Nil).toOption)
            val examples     = storedExamples.flatMap(dv => primitiveType.fromDynamicValue(dv, Nil).toOption)
            val instance     = getCustomInstance[A0](path, typeId).getOrElse(
              deriver
                .derivePrimitive(
                  primitiveType,
                  typeId,
                  metadata,
                  doc,
                  prependCombinedModifiers(modifiers, path, typeId),
                  defaultValue,
                  examples
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
            val defaultValue = storedDefaultValue.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
            val examples     = storedExamples.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
            val instance     = getCustomInstance[A0](path, typeId)
              .getOrElse(
                deriver.deriveWrapper(
                  wrapped,
                  typeId,
                  metadata,
                  doc,
                  prependCombinedModifiers(modifiers, path, typeId),
                  defaultValue,
                  examples
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
