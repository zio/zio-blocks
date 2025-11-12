package zio.blocks.schema.derive

import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, BindingType}

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
 *   .modifier(Person.name, Modifier.config("rename", "fullName"))
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

  def modifier[B](optic: Optic[A, B], modifier: Modifier.Reflect): DerivationBuilder[TC, A] =
    copy(modifierOverrides = modifierOverrides :+ new ModifierReflectOverrideByOptic(optic.toDynamic, modifier))

  def modifier[B](typeName: TypeName[B], modifier: Modifier.Reflect): DerivationBuilder[TC, A] =
    copy(modifierOverrides = modifierOverrides :+ new ModifierReflectOverrideByType(typeName, modifier))

  def modifier[B](typeName: TypeName[B], termName: String, modifier: Modifier.Term): DerivationBuilder[TC, A] =
    copy(modifierOverrides = modifierOverrides :+ new ModifierTermOverride(typeName, termName, modifier))

  lazy val derive: TC[A] = {
    val allInstanceOverrides = deriver.instanceOverrides ++ instanceOverrides
    val allModifierOverrides = deriver.modifierOverrides ++ modifierOverrides

    val instanceByOpticMap =
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
    val modifierTermByTypeMap =
      allModifierOverrides.foldLeft[Map[TypeName[?], Vector[(String, Modifier.Term)]]](Map.empty) {
        case (acc, ModifierTermOverride(typeName, termName, modifier)) =>
          acc.updated(typeName, acc.getOrElse(typeName, Vector.empty).appended((termName, modifier)))
        case (acc, _) => acc
      }

    def appendModifiers[A0](modifiers: Seq[Modifier.Reflect], path: DynamicOptic, typeName: TypeName[A0]) =
      (modifierReflectByOpticMap.get(path), modifierReflectByTypeMap.get(typeName)) match {
        case (Some(modifiers1), Some(modifiers2)) => modifiers ++ (modifiers1 ++ modifiers2)
        case (Some(modifiers1), _)                => modifiers ++ modifiers1
        case (_, Some(modifiers2))                => modifiers ++ modifiers2
        case _                                    => modifiers
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
            modifiers: Seq[Modifier.Reflect]
          ): Lazy[Reflect.Record[G, A0]] = Lazy {
            val instance = getCustomInstance[A0](path, typeName).getOrElse {
              val modifiersToAdd = modifierTermByTypeMap.getOrElse(typeName, Vector.empty)
              val updatedFields  =
                if (modifiersToAdd.isEmpty) fields
                else {
                  fields.map { field =>
                    val fieldModifiersToAdd = modifiersToAdd.collect {
                      case (name, modifier) if name == field.name => modifier
                    }
                    if (fieldModifiersToAdd.isEmpty) field
                    else field.copy(modifiers = field.modifiers ++ fieldModifiersToAdd).asInstanceOf[Term[G, A0, ?]]
                  }
                }
              deriver.deriveRecord(updatedFields, typeName, metadata, doc, appendModifiers(modifiers, path, typeName))
            }
            new Reflect.Record(fields, typeName, new BindingInstance(metadata, instance), doc, modifiers)
          }

          override def transformVariant[A0](
            path: DynamicOptic,
            cases: IndexedSeq[Term[G, A0, ? <: A0]],
            typeName: TypeName[A0],
            metadata: F[BindingType.Variant, A0],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect]
          ): Lazy[Reflect.Variant[G, A0]] = Lazy {
            val instance = getCustomInstance[A0](path, typeName).getOrElse {
              val modifiersToAdd = modifierTermByTypeMap.getOrElse(typeName, Vector.empty)
              val updatedCases   =
                if (modifiersToAdd.isEmpty) cases
                else {
                  cases.map { case_ =>
                    val caseModifiersToAdd = modifiersToAdd.collect {
                      case (name, modifier) if name == case_.name => modifier
                    }
                    if (caseModifiersToAdd.isEmpty) case_
                    else
                      case_.copy(modifiers = case_.modifiers ++ caseModifiersToAdd).asInstanceOf[Term[G, A0, ? <: A0]]
                  }
                }
              deriver.deriveVariant(updatedCases, typeName, metadata, doc, appendModifiers(modifiers, path, typeName))
            }
            new Reflect.Variant(cases, typeName, new BindingInstance(metadata, instance), doc, modifiers)
          }

          override def transformSequence[A0, C[_]](
            path: DynamicOptic,
            element: Reflect[G, A0],
            typeName: TypeName[C[A0]],
            metadata: F[BindingType.Seq[C], C[A0]],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect]
          ): Lazy[Reflect.Sequence[G, A0, C]] = Lazy {
            val instance = getCustomInstance[C[A0]](path, typeName).getOrElse(
              deriver.deriveSequence(element, typeName, metadata, doc, appendModifiers(modifiers, path, typeName))
            )
            new Reflect.Sequence(element, typeName, new BindingInstance(metadata, instance), doc, modifiers)
          }

          override def transformMap[Key, Value, M[_, _]](
            path: DynamicOptic,
            key: Reflect[G, Key],
            value: Reflect[G, Value],
            typeName: TypeName[M[Key, Value]],
            metadata: F[BindingType.Map[M], M[Key, Value]],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect]
          ): Lazy[Reflect.Map[G, Key, Value, M]] = Lazy {
            val instance = getCustomInstance[M[Key, Value]](path, typeName).getOrElse(
              deriver.deriveMap(key, value, typeName, metadata, doc, appendModifiers(modifiers, path, typeName))
            )
            new Reflect.Map(key, value, typeName, new BindingInstance(metadata, instance), doc, modifiers)
          }

          override def transformDynamic(
            path: DynamicOptic,
            typeName: TypeName[DynamicValue],
            metadata: F[BindingType.Dynamic, DynamicValue],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect]
          ): Lazy[Reflect.Dynamic[G]] = Lazy {
            val instance = getCustomInstance[DynamicValue](path, TypeName.dynamicValue)
              .getOrElse(deriver.deriveDynamic[G](metadata, doc, appendModifiers(modifiers, path, typeName)))
            new Reflect.Dynamic(new BindingInstance(metadata, instance), typeName, doc, modifiers)
          }

          override def transformPrimitive[A0](
            path: DynamicOptic,
            primitiveType: PrimitiveType[A0],
            typeName: TypeName[A0],
            metadata: F[BindingType.Primitive, A0],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect]
          ): Lazy[Reflect.Primitive[G, A0]] = Lazy {
            val instance = getCustomInstance[A0](path, typeName).getOrElse(
              deriver
                .derivePrimitive(primitiveType, typeName, metadata, doc, appendModifiers(modifiers, path, typeName))
            )
            new Reflect.Primitive(primitiveType, typeName, new BindingInstance(metadata, instance), doc, modifiers)
          }

          override def transformWrapper[A0, B](
            path: DynamicOptic,
            wrapped: Reflect[G, B],
            typeName: TypeName[A0],
            primitiveType: Option[PrimitiveType[A0]],
            metadata: F[BindingType.Wrapper[A0, B], A0],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect]
          ): Lazy[Reflect.Wrapper[G, A0, B]] = Lazy {
            val instance = getCustomInstance[A0](path, typeName)
              .getOrElse(
                deriver.deriveWrapper(
                  wrapped,
                  typeName,
                  primitiveType,
                  metadata,
                  doc,
                  appendModifiers(modifiers, path, typeName)
                )
              )
            new Reflect.Wrapper(
              wrapped,
              typeName,
              primitiveType,
              new BindingInstance(metadata, instance),
              doc,
              modifiers
            )
          }
        }
      )
      .flatMap(_.metadata.instance)
      .force
  }
}
