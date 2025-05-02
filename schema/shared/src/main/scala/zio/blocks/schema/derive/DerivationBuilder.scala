package zio.blocks.schema.derive

import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, BindingType, HasBinding}
import scala.collection.immutable.{Map => ScalaMap}

/**
 * A {{DerivationBuilder}} is capable of deriving a type class instance for any
 * data type that has a schema. Though instances for all substructures can be
 * derived automatically, you can also specify instances for specific
 * substructures using the `withOverride` method.
 *
 * ```scala
 * val personSchema = Schema.derive[Person]
 *
 * val personEq: Eq[Person] = personSchema
 *   .deriving[Eq]
 *   .instance(Person.age, Eq[Int])
 *   .modifier(Person.name, Term.transient)
 *   .derive
 * ```
 */
final case class DerivationBuilder[TC[_], A](
  schema: Schema[A],
  deriver: Deriver[TC],
  instanceOverrides: IndexedSeq[InstanceOverride[TC, ?]],
  modifierOverrides: IndexedSeq[ModifierOverride]
) {
  def instance[B](optic: Optic.Bound[A, B], instance: => TC[B]): DerivationBuilder[TC, A] = {
    val override_ = InstanceOverride(optic.toDynamic, Lazy(instance))
    copy(instanceOverrides = instanceOverrides :+ override_)
  }

  def modifier[B](optic: Optic.Bound[A, B], modifier: Modifier): DerivationBuilder[TC, A] = {
    val override_ = ModifierOverride(optic.toDynamic, modifier)
    copy(modifierOverrides = modifierOverrides :+ override_)
  }

  def derive: Lazy[TC[A]] = {
    val instanceMap = instanceOverrides.map(override_ => override_.optic -> override_.instance).toMap
    val modifierMap = modifierOverrides.foldLeft[ScalaMap[DynamicOptic, Vector[Modifier]]](ScalaMap.empty) {
      case (acc, override_) =>
        acc + (override_.optic -> acc.getOrElse(override_.optic, Vector.empty).appended(override_.modifier))
    }

    def addModifiers[A](reflect: Reflect.Bound[A], optic: DynamicOptic): Reflect.Bound[A] = {
      val extra = modifierMap.getOrElse(optic, Vector.empty)

      reflect match {
        case record: Reflect.Record.Bound[A] =>
          record.modifiers(extra.collect { case m: Modifier.Record => m })

        case sequence: Reflect.Sequence.Bound[a, b] @unchecked =>
          sequence.modifiers(extra.collect { case m: Modifier.Seq => m })

        case map: Reflect.Map.Bound[a, b, c] @unchecked =>
          map.modifiers(extra.collect { case m: Modifier.Map => m })

        case dynamic: Reflect.Dynamic.Bound @unchecked =>
          dynamic.modifiers(extra.collect { case m: Modifier.Dynamic => m.asInstanceOf[dynamic.ModifierType] })

        case primitive: Reflect.Primitive.Bound[a] =>
          primitive.modifiers(extra.collect { case m: Modifier.Primitive => m })

        case _ => reflect
      }
    }

    type F[T, A] = Binding[T, A]
    type G[T, A] = BindingInstance[TC, T, A]

    schema.reflect
      .transform[G](new ReflectTransformer[F, G] {
        override def transformRecord[A](
          fields: Seq[Term[G, A, ?]],
          typeName: TypeName[A],
          metadata: F[BindingType.Record, A],
          doc: Doc,
          modifiers: Seq[Modifier.Record]
        ): Lazy[Reflect.Record[G, A]] =
          Lazy(
            Reflect.Record(
              fields,
              typeName,
              BindingInstance(metadata, deriver.deriveRecord(fields, typeName, doc, modifiers)),
              doc,
              modifiers
            )
          )

        override def transformVariant[A](
          cases: Seq[Term[G, A, ? <: A]],
          typeName: TypeName[A],
          metadata: F[BindingType.Variant, A],
          doc: Doc,
          modifiers: Seq[Modifier.Variant]
        ): Lazy[Reflect.Variant[G, A]] =
          Lazy(
            Reflect.Variant(
              cases,
              typeName,
              BindingInstance(metadata, deriver.deriveVariant(cases, typeName, doc, modifiers)),
              doc,
              modifiers
            )
          )

        override def transformSequence[A, C[_]](
          element: Reflect[G, A],
          typeName: TypeName[C[A]],
          metadata: F[BindingType.Seq[C], C[A]],
          doc: Doc,
          modifiers: Seq[Modifier.Seq]
        ): Lazy[Reflect.Sequence[G, A, C]] =
          Lazy(
            Reflect.Sequence(
              element,
              BindingInstance(metadata, deriver.deriveSequence(element, typeName, doc, modifiers)),
              typeName,
              doc,
              modifiers
            )
          )

        override def transformMap[Key, Value, M[_, _]](
          key: Reflect[G, Key],
          value: Reflect[G, Value],
          typeName: TypeName[M[Key, Value]],
          metadata: F[BindingType.Map[M], M[Key, Value]],
          doc: Doc,
          modifiers: Seq[Modifier.Map]
        ): Lazy[Reflect.Map[G, Key, Value, M]] =
          Lazy(
            Reflect.Map(
              key,
              value,
              BindingInstance(metadata, deriver.deriveMap(key, value, typeName, doc, modifiers)),
              typeName,
              doc,
              modifiers
            )
          )

        override def transformDynamic(
          metadata: F[BindingType.Dynamic, DynamicValue],
          doc: Doc,
          modifiers: Seq[Modifier.Dynamic]
        ): Lazy[Reflect.Dynamic[G]] =
          Lazy(Reflect.Dynamic(BindingInstance(metadata, deriver.deriveDynamic[G](doc, modifiers)), doc, modifiers))

        override def transformPrimitive[A](
          primitiveType: PrimitiveType[A],
          typeName: TypeName[A],
          metadata: F[BindingType.Primitive, A],
          doc: Doc,
          modifiers: Seq[Modifier.Primitive]
        ): Lazy[Reflect.Primitive[G, A]] =
          Lazy(
            Reflect.Primitive(
              primitiveType,
              BindingInstance(metadata, deriver.derivePrimitive(primitiveType, typeName, doc, modifiers)),
              typeName,
              doc,
              modifiers
            )
          )

        override def transformDeferred[A](
          value: () => Reflect[G, A]
        ): Lazy[Reflect.Deferred[G, A]] =
          Lazy(Reflect.Deferred(value))
      })
      .flatMap(_.metadata.instance)
  }
}
