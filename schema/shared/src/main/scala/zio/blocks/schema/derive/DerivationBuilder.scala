package zio.blocks.schema.derive

import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding}

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
  def instance[B](optic: Optic[A, B], instance: => TC[B]): DerivationBuilder[TC, A] =
    copy(instanceOverrides = instanceOverrides :+ new InstanceOverrideByOptic(optic.toDynamic, Lazy(instance)))

  def instance[B](typeName: TypeName[B], instance: => TC[B]): DerivationBuilder[TC, A] =
    copy(instanceOverrides = instanceOverrides :+ new InstanceOverrideByType(typeName, Lazy(instance)))

  def modifier[B](typeName: TypeName[B], modifier: Modifier.Reflect): DerivationBuilder[TC, A] =
    copy(modifierOverrides = modifierOverrides :+ new ModifierReflectOverrideByType(typeName, modifier))

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

  lazy val derive: TC[A] = {
    val allInstanceOverrides = instanceOverrides ++ deriver.instanceOverrides
    val allModifierOverrides = modifierOverrides ++ deriver.modifierOverrides
    val instanceByOpticMap   =
      allInstanceOverrides.collect { case InstanceOverrideByOptic(optic, instance) => (optic, instance) }.toMap
    val instanceByTypeMap =
      allInstanceOverrides.collect { case InstanceOverrideByType(typeName, instance) => (typeName, instance) }.toMap
    val modifierReflectByOpticMap =
      allModifierOverrides.foldLeft[Map[DynamicOptic, Vector[Modifier.Reflect]]](Map.empty) {
        case (acc, ModifierReflectOverrideByOptic(optic, modifier)) =>
          acc.updated(optic, acc.getOrElse(optic, Vector.empty).appended(modifier))
        case (acc, _) => acc
      }
    val modifierReflectByTypeMap =
      allModifierOverrides.foldLeft[Map[TypeName[?], Vector[Modifier.Reflect]]](Map.empty) {
        case (acc, ModifierReflectOverrideByType(typeName, modifier)) =>
          acc.updated(typeName, acc.getOrElse(typeName, Vector.empty).appended(modifier))
        case (acc, _) => acc
      }
    val modifierTermByOpticMap =
      allModifierOverrides.foldLeft[Map[DynamicOptic, Vector[(String, Modifier.Term)]]](Map.empty) {
        case (acc, ModifierTermOverrideByOptic(optic, termName, modifier)) =>
          acc.updated(optic, acc.getOrElse(optic, Vector.empty).appended((termName, modifier)))
        case (acc, _) => acc
      }
    val modifierTermByTypeMap =
      allModifierOverrides.foldLeft[Map[TypeName[?], Vector[(String, Modifier.Term)]]](Map.empty) {
        case (acc, ModifierTermOverrideByType(typeName, termName, modifier)) =>
          acc.updated(typeName, acc.getOrElse(typeName, Vector.empty).appended((termName, modifier)))
        case (acc, _) => acc
      }

    def prependCombinedModifiers[A0](modifiers: Seq[Modifier.Reflect], path: DynamicOptic, typeName: TypeName[A0]) =
      (modifierReflectByOpticMap.get(path), modifierReflectByTypeMap.get(typeName)) match {
        case (Some(modifiers1), Some(modifiers2)) => (modifiers1 ++ modifiers2) ++ modifiers
        case (Some(modifiers1), _)                => modifiers1 ++ modifiers
        case (_, Some(modifiers2))                => modifiers2 ++ modifiers
        case _                                    => modifiers
      }

    def combineModifiers[A0](path: DynamicOptic, typeName: TypeName[A0]) =
      (modifierTermByOpticMap.get(path), modifierTermByTypeMap.get(typeName)) match {
        case (Some(modifiers1), Some(modifiers2)) => modifiers1 ++ modifiers2
        case (Some(modifiers1), _)                => modifiers1
        case (_, Some(modifiers2))                => modifiers2
        case _                                    => Seq.empty
      }

    def getCustomInstance[A0](path: DynamicOptic, typeName: TypeName[A0]): Option[Lazy[TC[A0]]] =
      // first try to find an instance by optic (more precise)
      instanceByOpticMap
        .get(path)
        // then try to find an instance by type name (more general)
        .orElse(instanceByTypeMap.get(typeName.asInstanceOf[TypeName[Any]]))
        .map(_.asInstanceOf[Lazy[TC[A0]]])

    type F[T, A0] = Binding[T, A0]
    type G[T, A0] = BindingInstance[TC, T, A0]

    schema.reflect
      .transform[G](
        DynamicOptic.root,
        new ReflectTransformer[F, G] {
          override def transformRecord[A0](
            path: DynamicOptic,
            fields: IndexedSeq[Term[G, A0, ?]],
            typeName: TypeName[A0],
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
                typeName,
                new BindingInstance(
                  metadata,
                  Lazy.fail(new IllegalStateException("Temporary instance for fromDynamicValue conversion"))
                ),
                doc,
                modifiers
              )
            val defaultValue = storedDefaultValue.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
            val examples     = storedExamples.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
            val instance     = getCustomInstance[A0](path, typeName).getOrElse {
              val modifiersToPrepend = combineModifiers(path, typeName)
              val updatedFields      =
                if (modifiersToPrepend.isEmpty) fields
                else {
                  fields.map { field =>
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
                  typeName,
                  metadata,
                  doc,
                  prependCombinedModifiers(modifiers, path, typeName),
                  defaultValue,
                  examples
                )
            }
            new Reflect.Record(
              fields,
              typeName,
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
            typeName: TypeName[A0],
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
                typeName,
                new BindingInstance(
                  metadata,
                  Lazy.fail(new IllegalStateException("Temporary instance for fromDynamicValue conversion"))
                ),
                doc,
                modifiers
              )
            val defaultValue = storedDefaultValue.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
            val examples     = storedExamples.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
            val instance     = getCustomInstance[A0](path, typeName).getOrElse {
              val modifiersToAdd = combineModifiers(path, typeName)
              val updatedCases   =
                if (modifiersToAdd.isEmpty) cases
                else {
                  cases.map { case_ =>
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
                  typeName,
                  metadata,
                  doc,
                  prependCombinedModifiers(modifiers, path, typeName),
                  defaultValue,
                  examples
                )
            }
            new Reflect.Variant(
              cases,
              typeName,
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
            typeName: TypeName[C[A0]],
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
                typeName,
                new BindingInstance(
                  metadata,
                  Lazy.fail(new IllegalStateException("Temporary instance for fromDynamicValue conversion"))
                ),
                doc,
                modifiers
              )
            val defaultValue = storedDefaultValue.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
            val examples     = storedExamples.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
            val instance     = getCustomInstance[C[A0]](path, typeName).getOrElse(
              deriver
                .deriveSequence(
                  element,
                  typeName,
                  metadata,
                  doc,
                  prependCombinedModifiers(modifiers, path, typeName),
                  defaultValue,
                  examples
                )
            )
            new Reflect.Sequence(
              element,
              typeName,
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
            typeName: TypeName[M[Key, Value]],
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
                typeName,
                new BindingInstance(
                  metadata,
                  Lazy.fail(new IllegalStateException("Temporary instance for fromDynamicValue conversion"))
                ),
                doc,
                modifiers
              )
            val defaultValue = storedDefaultValue.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
            val examples     = storedExamples.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
            val instance     = getCustomInstance[M[Key, Value]](path, typeName).getOrElse(
              deriver
                .deriveMap(
                  key,
                  value,
                  typeName,
                  metadata,
                  doc,
                  prependCombinedModifiers(modifiers, path, typeName),
                  defaultValue,
                  examples
                )
            )
            new Reflect.Map(
              key,
              value,
              typeName,
              new BindingInstance(metadata, instance),
              doc,
              modifiers,
              storedDefaultValue,
              storedExamples
            )
          }

          override def transformDynamic(
            path: DynamicOptic,
            typeName: TypeName[DynamicValue],
            metadata: F[BindingType.Dynamic, DynamicValue],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect],
            storedDefaultValue: Option[DynamicValue],
            storedExamples: collection.immutable.Seq[DynamicValue]
          ): Lazy[Reflect.Dynamic[G]] = Lazy {
            val instance = getCustomInstance[DynamicValue](path, TypeName.dynamicValue)
              .getOrElse(
                deriver.deriveDynamic[G](
                  metadata,
                  doc,
                  prependCombinedModifiers(modifiers, path, typeName),
                  storedDefaultValue,
                  storedExamples
                )
              )
            new Reflect.Dynamic(
              new BindingInstance(metadata, instance),
              typeName,
              doc,
              modifiers,
              storedDefaultValue,
              storedExamples
            )
          }

          override def transformPrimitive[A0](
            path: DynamicOptic,
            primitiveType: PrimitiveType[A0],
            typeName: TypeName[A0],
            metadata: F[BindingType.Primitive, A0],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect],
            storedDefaultValue: Option[DynamicValue],
            storedExamples: collection.immutable.Seq[DynamicValue]
          ): Lazy[Reflect.Primitive[G, A0]] = Lazy {
            val defaultValue = storedDefaultValue.flatMap(dv => primitiveType.fromDynamicValue(dv, Nil).toOption)
            val examples     = storedExamples.flatMap(dv => primitiveType.fromDynamicValue(dv, Nil).toOption)
            val instance     = getCustomInstance[A0](path, typeName).getOrElse(
              deriver
                .derivePrimitive(
                  primitiveType,
                  typeName,
                  metadata,
                  doc,
                  prependCombinedModifiers(modifiers, path, typeName),
                  defaultValue,
                  examples
                )
            )
            new Reflect.Primitive(
              primitiveType,
              typeName,
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
            typeName: TypeName[A0],
            wrapperPrimitiveType: Option[PrimitiveType[A0]],
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
                typeName,
                wrapperPrimitiveType,
                new BindingInstance(
                  metadata,
                  Lazy.fail(new IllegalStateException("Temporary instance for fromDynamicValue conversion"))
                ),
                doc,
                modifiers
              )
            val defaultValue = storedDefaultValue.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
            val examples     = storedExamples.flatMap(dv => tempReflect.fromDynamicValue(dv).toOption)
            val instance     = getCustomInstance[A0](path, typeName)
              .getOrElse(
                deriver.deriveWrapper(
                  wrapped,
                  typeName,
                  wrapperPrimitiveType,
                  metadata,
                  doc,
                  prependCombinedModifiers(modifiers, path, typeName),
                  defaultValue,
                  examples
                )
              )
            new Reflect.Wrapper(
              wrapped,
              typeName,
              wrapperPrimitiveType,
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
