package zio.blocks.schema.derive

import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, HasBinding}

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
    val modifierMap = modifierOverrides.foldLeft[Map[DynamicOptic, Vector[Modifier]]](Map.empty) {
      case (acc, override_) =>
        acc.updated(
          override_.optic,
          acc.get(override_.optic).map(_ :+ override_.modifier).getOrElse(Vector(override_.modifier))
        )
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

    def coerceInstance[A](instance: Lazy[TC[?]]): Lazy[TC[A]] = instance.asInstanceOf[Lazy[TC[A]]]

    type BI[T, A] = BindingInstance[TC, T, A]

    schema.reflect
      .transform[BI](new ReflectTransformer[Binding, BI] {
        def apply[T, A](binding: Binding[T, A]): Lazy[BI[T, A]] =
          ???
      })
      .flatMap(_.metadata.instance)
  }
}
