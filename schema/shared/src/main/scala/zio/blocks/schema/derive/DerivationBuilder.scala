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

  def derive: TC[A] = {
    val instanceMap = instanceOverrides.map(override_ => override_.optic -> override_.instance).toMap
    val modifierMap = modifierOverrides.map(override_ => override_.optic -> override_.modifier).toMap

    def coerceInstance[A](instance: Lazy[TC[?]]): Lazy[TC[A]] = instance.asInstanceOf[Lazy[TC[A]]]

    def loop[A](path: DynamicOptic, reflect: Reflect.Bound[A]): Lazy[TC[A]] =
      if (instanceMap.contains(path)) {
        coerceInstance[A](instanceMap(path))
      } else
        reflect match {
          case prim @ Reflect.Primitive(primitiveType, primitiveBinding, typeName, doc, modifiers) =>
            deriver.derivePrimitive(prim)

          case rec @ Reflect.Record(fields, typeName, recordBinding, doc, modifiers) =>
            ???

          case variant @ Reflect.Variant(cases, typeName, variantBinding, doc, modifiers) =>
            ???

          case seq @ Reflect.Sequence(element, seqBinding, typeName, doc, modifiers) =>
            ???

          case map @ Reflect.Map(key, value, mapBinding, typeName, doc, modifiers) =>
            ???

          case dyn @ Reflect.Dynamic(dynamicBinding, doc, modifiers) =>
            ???

          case deferred @ Reflect.Deferred(_value) =>
            loop(path, deferred.value)
        }

    loop(DynamicOptic.root, schema.reflect).force
  }
}
