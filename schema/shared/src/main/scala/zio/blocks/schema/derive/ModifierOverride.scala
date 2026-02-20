package zio.blocks.schema.derive

import zio.blocks.schema._
import zio.blocks.typeid.TypeId

/**
 * An override that attaches a [[zio.blocks.schema.Modifier Modifier]] to a
 * schema node during derivation.
 *
 * Unlike [[InstanceOverride]], modifier overrides are additive: when multiple
 * overrides match the same node, their modifiers are combined (optic-matched
 * modifiers are prepended before type-matched ones).
 */
sealed trait ModifierOverride

/**
 * Attaches a [[zio.blocks.schema.Modifier.Reflect Modifier.Reflect]] to the
 * schema node at an exact path in the schema tree.
 *
 * Created via `DerivationBuilder.modifier(optic, modifier)` when the modifier
 * is a `Modifier.Reflect`.
 */
case class ModifierReflectOverrideByOptic(optic: DynamicOptic, modifier: Modifier.Reflect) extends ModifierOverride

/**
 * Attaches a [[zio.blocks.schema.Modifier.Reflect Modifier.Reflect]] to every
 * schema node whose type matches the given [[zio.blocks.typeid.TypeId TypeId]].
 *
 * Created via `DerivationBuilder.modifier(typeId, modifier)`.
 */
case class ModifierReflectOverrideByType[A](typeId: TypeId[A], modifier: Modifier.Reflect) extends ModifierOverride

/**
 * Attaches a [[zio.blocks.schema.Modifier.Term Modifier.Term]] to a specific
 * term (field or case) within every schema node whose type matches the given
 * [[zio.blocks.typeid.TypeId TypeId]]. The `termName` identifies which field or
 * case within the matched node receives the modifier.
 */
case class ModifierTermOverrideByType[A](typeId: TypeId[A], termName: String, modifier: Modifier.Term)
    extends ModifierOverride

/**
 * Attaches a [[zio.blocks.schema.Modifier.Term Modifier.Term]] to a specific
 * term (field or case) within the schema node at an exact path. The `termName`
 * identifies which field or case within the matched node receives the modifier.
 *
 * Created via `DerivationBuilder.modifier(optic, modifier)` when the modifier
 * is a `Modifier.Term`. The optic's last node is extracted as the term name,
 * and the remaining prefix becomes the path.
 */
case class ModifierTermOverrideByOptic[A](optic: DynamicOptic, termName: String, modifier: Modifier.Term)
    extends ModifierOverride
