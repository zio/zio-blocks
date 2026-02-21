package zio.blocks.schema.derive

import zio.blocks.schema._
import zio.blocks.typeid.TypeId

/**
 * An override that supplies a custom type class instance during derivation,
 * bypassing the auto-derived one.
 *
 * Resolution priority (highest to lowest):
 *   1. [[InstanceOverrideByOptic]] — matches an exact path in the schema tree
 *   2. [[InstanceOverrideByTypeAndTermName]] — matches a term (field or case)
 *      name inside a specific parent record/variant
 *   3. [[InstanceOverrideByType]] — matches every occurrence of a type
 */
sealed trait InstanceOverride

/**
 * Overrides the type class instance for the substructure at an exact path in
 * the schema tree.
 *
 * This is the most precise override: it targets one specific location
 * identified by a [[zio.blocks.schema.DynamicOptic DynamicOptic]]. Created via
 * `DerivationBuilder.instance(optic, instance)`.
 */
case class InstanceOverrideByOptic[TC[_], A](optic: DynamicOptic, instance: Lazy[TC[A]]) extends InstanceOverride

/**
 * Overrides the type class instance for every occurrence of a type, regardless
 * of where it appears in the schema tree.
 *
 * This is the least precise override. Created via
 * `DerivationBuilder.instance(typeId, instance)`.
 */
case class InstanceOverrideByType[TC[_], A](typeId: TypeId[A], instance: Lazy[TC[A]]) extends InstanceOverride

/**
 * Overrides the type class instance for a term (record field or variant case)
 * with a specific name inside a parent record or variant identified by its
 * [[zio.blocks.typeid.TypeId TypeId]]. Applies at every matching (parentTypeId,
 * termName) pair in the schema tree.
 *
 * This provides medium precision between [[InstanceOverrideByOptic]] and
 * [[InstanceOverrideByType]]. Created via
 * `DerivationBuilder.instance(typeId, termName, instance)`.
 */
case class InstanceOverrideByTypeAndTermName[TC[_], P, A](typeId: TypeId[P], termName: String, instance: Lazy[TC[A]])
    extends InstanceOverride
