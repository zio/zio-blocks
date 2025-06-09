package zio.blocks.schema.derive

import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding}
import scala.collection.immutable.{Map => ScalaMap}

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
 *   .modifier(Person.name, Term.transient)
 *   .derive
 * }}}
 */
final case class DerivationBuilder[TC[_], A](
  schema: Schema[A],
  deriver: Deriver[TC],
  instanceOverrides: IndexedSeq[InstanceOverride[TC, ?]],
  modifierOverrides: IndexedSeq[ModifierOverride]
) {
  def instance[B](optic: Optic[A, B], instance: => TC[B]): DerivationBuilder[TC, A] = {
    val override_ = InstanceOverride(optic.toDynamic, Lazy(instance))
    copy(instanceOverrides = instanceOverrides :+ override_)
  }

  def modifier[B](optic: Optic[A, B], modifier: Modifier): DerivationBuilder[TC, A] = {
    val override_ = ModifierOverride(optic.toDynamic, modifier)
    copy(modifierOverrides = modifierOverrides :+ override_)
  }

  lazy val derive: TC[A] = {
    val instanceMap = instanceOverrides.map(override_ => override_.optic -> override_.instance).toMap
    val modifierMap = modifierOverrides.foldLeft[ScalaMap[DynamicOptic, Vector[Modifier]]](ScalaMap.empty) {
      case (acc, override_) =>
        acc + (override_.optic -> acc.getOrElse(override_.optic, Vector.empty).appended(override_.modifier))
    }

    def extraModifiers(reflect: Reflect.Type, optic: DynamicOptic): Vector[reflect.ModifierType] = {
      val extra = modifierMap.getOrElse(optic, Vector.empty)

      (reflect match {
        case Reflect.Type.Record =>
          extra.collect { case m: Modifier.Record => m }

        case _: Reflect.Type.Sequence[_] =>
          extra.collect { case m: Modifier.Seq => m }

        case _: Reflect.Type.Map[_] =>
          extra.collect { case m: Modifier.Map => m }

        case Reflect.Type.Dynamic =>
          extra.collect { case m: Modifier.Dynamic => m }

        case Reflect.Type.Primitive =>
          extra.collect { case m: Modifier.Primitive => m }

        case _ => Vector.empty
      }).asInstanceOf[Vector[reflect.ModifierType]]
    }

    def getCustomInstance[A](path: DynamicOptic): Option[Lazy[TC[A]]] =
      instanceMap.get(path).map(_.asInstanceOf[Lazy[TC[A]]])

    type F[T, A] = Binding[T, A]
    type G[T, A] = BindingInstance[TC, T, A]

    schema.reflect
      .transform[G](
        DynamicOptic.root,
        new ReflectTransformer[F, G] {
          override def transformRecord[A](
            path: DynamicOptic,
            fields: IndexedSeq[Term[G, A, ?]],
            typeName: TypeName[A],
            metadata: F[BindingType.Record, A],
            doc: Doc,
            modifiers: Seq[Modifier.Record]
          ): Lazy[Reflect.Record[G, A]] = {
            val instance = getCustomInstance[A](path).getOrElse(
              deriver.deriveRecord(fields, typeName, doc, modifiers ++ extraModifiers(Reflect.Type.Record, path))
            )
            Lazy(
              Reflect.Record(
                fields,
                typeName,
                BindingInstance(metadata, instance),
                doc,
                modifiers
              )
            )
          }

          override def transformVariant[A](
            path: DynamicOptic,
            cases: IndexedSeq[Term[G, A, ? <: A]],
            typeName: TypeName[A],
            metadata: F[BindingType.Variant, A],
            doc: Doc,
            modifiers: Seq[Modifier.Variant]
          ): Lazy[Reflect.Variant[G, A]] = {
            val instance = getCustomInstance[A](path).getOrElse(
              deriver.deriveVariant(cases, typeName, doc, modifiers ++ extraModifiers(Reflect.Type.Variant, path))
            )
            Lazy(
              Reflect.Variant(
                cases,
                typeName,
                BindingInstance(metadata, instance),
                doc,
                modifiers
              )
            )
          }

          override def transformSequence[A, C[_]](
            path: DynamicOptic,
            element: Reflect[G, A],
            typeName: TypeName[C[A]],
            metadata: F[BindingType.Seq[C], C[A]],
            doc: Doc,
            modifiers: Seq[Modifier.Seq]
          ): Lazy[Reflect.Sequence[G, A, C]] = {
            val instance = getCustomInstance[C[A]](path).getOrElse(
              deriver.deriveSequence(element, typeName, doc, modifiers ++ extraModifiers(Reflect.Type.Sequence(), path))
            )
            Lazy(
              Reflect.Sequence(
                element,
                BindingInstance(metadata, instance),
                typeName,
                doc,
                modifiers
              )
            )
          }

          override def transformMap[Key, Value, M[_, _]](
            path: DynamicOptic,
            key: Reflect[G, Key],
            value: Reflect[G, Value],
            typeName: TypeName[M[Key, Value]],
            metadata: F[BindingType.Map[M], M[Key, Value]],
            doc: Doc,
            modifiers: Seq[Modifier.Map]
          ): Lazy[Reflect.Map[G, Key, Value, M]] = {
            val instance = getCustomInstance[M[Key, Value]](path).getOrElse(
              deriver.deriveMap(key, value, typeName, doc, modifiers ++ extraModifiers(Reflect.Type.Map(), path))
            )
            Lazy(
              Reflect.Map(
                key,
                value,
                BindingInstance(metadata, instance),
                typeName,
                doc,
                modifiers
              )
            )
          }

          override def transformDynamic(
            path: DynamicOptic,
            metadata: F[BindingType.Dynamic, DynamicValue],
            doc: Doc,
            modifiers: Seq[Modifier.Dynamic]
          ): Lazy[Reflect.Dynamic[G]] = {
            val instance = getCustomInstance[DynamicValue](path)
              .getOrElse(deriver.deriveDynamic[G](doc, modifiers ++ extraModifiers(Reflect.Type.Dynamic, path)))
            Lazy(
              Reflect.Dynamic(
                BindingInstance(metadata, instance),
                doc,
                modifiers
              )
            )
          }

          override def transformPrimitive[A](
            path: DynamicOptic,
            primitiveType: PrimitiveType[A],
            typeName: TypeName[A],
            metadata: F[BindingType.Primitive, A],
            doc: Doc,
            modifiers: Seq[Modifier.Primitive]
          ): Lazy[Reflect.Primitive[G, A]] = {
            val instance = getCustomInstance[A](path).getOrElse(
              deriver.derivePrimitive(
                primitiveType,
                typeName,
                doc,
                modifiers ++ extraModifiers(Reflect.Type.Primitive, path)
              )
            )
            Lazy(
              Reflect.Primitive(
                primitiveType,
                BindingInstance(metadata, instance),
                typeName,
                doc,
                modifiers
              )
            )
          }
        }
      )
      .flatMap(_.metadata.instance)
      .force
  }
}
