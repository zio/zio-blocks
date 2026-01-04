package zio.blocks.schema.derive

import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, BindingType}
import zio.blocks.typeid.TypeId

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
  implicit val bindingHasInstance: zio.blocks.schema.derive.HasInstance[Binding, TC] =
    new zio.blocks.schema.derive.HasInstance[Binding, TC] {
      def instance[T, A](fa: Binding[T, A]): Lazy[TC[A]] =
        Lazy.fail(new RuntimeException("Binding HasInstance should not be called"))
    }

  implicit val bindingHasBinding: zio.blocks.schema.binding.HasBinding[Binding] =
    new zio.blocks.schema.binding.HasBinding[Binding] {
      def binding[T, A](fa: Binding[T, A]): Binding[T, A]                                          = fa
      def updateBinding[T, A](fa: Binding[T, A], f: Binding[T, A] => Binding[T, A]): Binding[T, A] = f(fa)
    }

  def instance[B](optic: Optic[A, B], instance: => TC[B]): DerivationBuilder[TC, A] =
    copy(instanceOverrides = instanceOverrides :+ new InstanceOverrideByOptic(optic.toDynamic, Lazy(instance)))

  def instance[B](typeId: TypeId[B], instance: => TC[B]): DerivationBuilder[TC, A] =
    copy(instanceOverrides = instanceOverrides :+ new InstanceOverrideByType(typeId, Lazy(instance)))

  def modifier[B](typeId: TypeId[B], modifier: Modifier.Reflect): DerivationBuilder[TC, A] =
    copy(modifierOverrides = modifierOverrides :+ new ModifierReflectOverrideByType(typeId, modifier))

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
      allInstanceOverrides.collect { case InstanceOverrideByType(typeId, instance) => (typeId, instance) }.toMap
    val modifierReflectByOpticMap =
      allModifierOverrides.foldLeft[Map[DynamicOptic, Vector[Modifier.Reflect]]](Map.empty) {
        case (acc, ModifierReflectOverrideByOptic(optic, modifier)) =>
          acc.updated(optic, acc.getOrElse(optic, Vector.empty).appended(modifier))
        case (acc, _) => acc
      }
    val modifierReflectByTypeMap =
      allModifierOverrides.foldLeft[Map[TypeId[?], Vector[Modifier.Reflect]]](Map.empty) {
        case (acc, ModifierReflectOverrideByType(typeId, modifier)) =>
          acc.updated(typeId, acc.getOrElse(typeId, Vector.empty).appended(modifier))
        case (acc, _) => acc
      }
    val modifierTermByOpticMap =
      allModifierOverrides.foldLeft[Map[DynamicOptic, Vector[(String, Modifier.Term)]]](Map.empty) {
        case (acc, ModifierTermOverrideByOptic(optic, termName, modifier)) =>
          acc.updated(optic, acc.getOrElse(optic, Vector.empty).appended((termName, modifier)))
        case (acc, _) => acc
      }
    val modifierTermByTypeMap =
      allModifierOverrides.foldLeft[Map[TypeId[?], Vector[(String, Modifier.Term)]]](Map.empty) {
        case (acc, ModifierTermOverrideByType(typeId, termName, modifier)) =>
          acc.updated(typeId, acc.getOrElse(typeId, Vector.empty).appended((termName, modifier)))
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
      // first try to find an instance by optic (more precise)
      instanceByOpticMap
        .get(path)
        // then try to find an instance by type name (more general)
        .orElse(instanceByTypeMap.get(typeId.asInstanceOf[TypeId[Any]]))
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
            typeId: TypeId[A0],
            metadata: F[BindingType.Record, A0],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect]
          ): Lazy[Reflect.Record[G, A0]] = Lazy {
            val instance = getCustomInstance[A0](path, typeId).getOrElse[Lazy[TC[A0]]] {
              val modifiersToPrepend = combineModifiers(path, typeId)
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
              val derivationResult: Lazy[TC[A0]] = deriver
                .deriveRecord(
                  updatedFields.asInstanceOf[IndexedSeq[Term[F, A0, ?]]],
                  typeId,
                  metadata,
                  doc,
                  prependCombinedModifiers(modifiers, path, typeId)
                )(bindingHasBinding, bindingHasInstance)
              derivationResult
            }
            new Reflect.Record(fields, typeId, new BindingInstance(metadata, instance), doc, modifiers)
          }

          override def transformVariant[A0](
            path: DynamicOptic,
            cases: IndexedSeq[Term[G, A0, ? <: A0]],
            typeId: TypeId[A0],
            metadata: F[BindingType.Variant, A0],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect]
          ): Lazy[Reflect.Variant[G, A0]] = Lazy {
            val instance = getCustomInstance[A0](path, typeId).getOrElse {
              val modifiersToAdd = combineModifiers(path, typeId)
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
              val derivationResult: Lazy[TC[A0]] = deriver
                .deriveVariant(
                  updatedCases.asInstanceOf[IndexedSeq[Term[F, A0, ?]]],
                  typeId,
                  metadata,
                  doc,
                  prependCombinedModifiers(modifiers, path, typeId)
                )(bindingHasBinding, bindingHasInstance)
              derivationResult
            }
            new Reflect.Variant(cases, typeId, new BindingInstance(metadata, instance), doc, modifiers)
          }

          override def transformSequence[A0, C[_]](
            path: DynamicOptic,
            element: Reflect[G, A0],
            typeId: TypeId[C[A0]],
            metadata: F[BindingType.Seq[C], C[A0]],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect]
          ): Lazy[Reflect.Sequence[G, A0, C]] = Lazy {
            val instance = getCustomInstance[C[A0]](path, typeId).getOrElse(
              deriver
                .deriveSequence(
                  element.asInstanceOf[Reflect[F, A0]],
                  typeId,
                  metadata,
                  doc,
                  prependCombinedModifiers(modifiers, path, typeId)
                )(bindingHasBinding, bindingHasInstance)
            )
            new Reflect.Sequence(element, typeId, new BindingInstance(metadata, instance), doc, modifiers)
          }

          override def transformMap[Key, Value, M[_, _]](
            path: DynamicOptic,
            key: Reflect[G, Key],
            value: Reflect[G, Value],
            typeId: TypeId[M[Key, Value]],
            metadata: F[BindingType.Map[M], M[Key, Value]],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect]
          ): Lazy[Reflect.Map[G, Key, Value, M]] = Lazy {
            val instance = getCustomInstance[M[Key, Value]](path, typeId).getOrElse(
              deriver
                .deriveMap(
                  key.asInstanceOf[Reflect[F, Key]],
                  value.asInstanceOf[Reflect[F, Value]],
                  typeId,
                  metadata,
                  doc,
                  prependCombinedModifiers(modifiers, path, typeId)
                )(bindingHasBinding, bindingHasInstance)
            )
            new Reflect.Map(key, value, typeId, new BindingInstance(metadata, instance), doc, modifiers)
          }

          override def transformDynamic(
            path: DynamicOptic,
            typeId: TypeId[DynamicValue],
            metadata: F[BindingType.Dynamic, DynamicValue],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect]
          ): Lazy[Reflect.Dynamic[G]] = Lazy {
            val dynamicTypeId =
              zio.blocks.typeid.TypeId.nominal[DynamicValue]("DynamicValue", zio.blocks.typeid.Owner.zioBlocksSchema)
            val instance = getCustomInstance[DynamicValue](path, dynamicTypeId)
              .getOrElse(
                deriver.deriveDynamic[F](metadata, doc, prependCombinedModifiers(modifiers, path, typeId))(
                  bindingHasBinding,
                  bindingHasInstance
                )
              )
            new Reflect.Dynamic(new BindingInstance(metadata, instance), typeId, doc, modifiers)
          }

          override def transformPrimitive[A0](
            path: DynamicOptic,
            primitiveType: PrimitiveType[A0],
            typeId: TypeId[A0],
            metadata: F[BindingType.Primitive, A0],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect]
          ): Lazy[Reflect.Primitive[G, A0]] = Lazy {
            val instance = getCustomInstance[A0](path, typeId).getOrElse(
              deriver
                .derivePrimitive(
                  primitiveType,
                  typeId,
                  metadata,
                  doc,
                  prependCombinedModifiers(modifiers, path, typeId)
                )(bindingHasBinding, bindingHasInstance)
            )
            new Reflect.Primitive(primitiveType, typeId, new BindingInstance(metadata, instance), doc, modifiers)
          }

          override def transformWrapper[A0, B](
            path: DynamicOptic,
            wrapped: Reflect[G, B],
            typeId: TypeId[A0],
            wrapperPrimitiveType: Option[PrimitiveType[A0]],
            metadata: F[BindingType.Wrapper[A0, B], A0],
            doc: Doc,
            modifiers: Seq[Modifier.Reflect]
          ): Lazy[Reflect.Wrapper[G, A0, B]] = Lazy {
            val instance = getCustomInstance[A0](path, typeId).getOrElse {
              val derivationResult: Lazy[TC[A0]] = deriver.deriveWrapper(
                wrapped.asInstanceOf[Reflect[F, B]],
                typeId,
                wrapperPrimitiveType,
                metadata,
                doc,
                prependCombinedModifiers(modifiers, path, typeId)
              )(bindingHasBinding, bindingHasInstance)
              derivationResult
            }
            new Reflect.Wrapper(
              wrapped,
              typeId,
              wrapperPrimitiveType,
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
