/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.derive

import zio.blocks.schema._
import zio.blocks.typeid.TypeId

/**
 * An override that supplies a type class instance during derivation instead of
 * deriving one. `DerivationBuilder#instance` and `Deriver#withInstance` create
 * them; `DerivationBuilder#instanceOverrides` and `Deriver#instanceOverrides`
 * hold them.
 *
 * Resolution priority (highest to lowest):
 *   1. [[InstanceOverrideByOptic]]: matches an exact path in the schema tree
 *   2. [[InstanceOverrideByTypeAndTermName]]: matches a term (field or case)
 *      name inside a specific parent record or variant
 *   3. [[InstanceOverrideByType]]: matches every occurrence of a type
 *
 * For the same target, an override from the deriver wins over one from the
 * `DerivationBuilder`, and a later one wins over an earlier one at the same
 * level.
 */
sealed trait InstanceOverride

/**
 * Overrides the type class instance for the node at an exact path in the schema
 * tree.
 *
 * This is the most precise override: it targets the location that a
 * [[zio.blocks.schema.DynamicOptic DynamicOptic]] identifies. Created via
 * `DerivationBuilder#instance(optic, instance)`.
 */
case class InstanceOverrideByOptic[TC[_], A](optic: DynamicOptic, instance: Lazy[TC[A]]) extends InstanceOverride

/**
 * Overrides the type class instance for every occurrence of a type, regardless
 * of where it appears in the schema tree. Occurrences match by equality with
 * `typeId`.
 *
 * This is the least precise override. Created via
 * `DerivationBuilder#instance(typeId, instance)` and
 * `Deriver#withInstance(instance)`.
 */
case class InstanceOverrideByType[TC[_], A](typeId: TypeId[A], instance: Lazy[TC[A]]) extends InstanceOverride

/**
 * Overrides the type class instance for a term (record field or variant case)
 * with a specific name inside a parent record or variant identified by its
 * [[zio.blocks.typeid.TypeId TypeId]]. Applies at every matching (parentTypeId,
 * termName) pair in the schema tree; a name that the parent does not have is
 * ignored.
 *
 * This provides medium precision between [[InstanceOverrideByOptic]] and
 * [[InstanceOverrideByType]]. Created via
 * `DerivationBuilder#instance(typeId, termName, instance)` and
 * `Deriver#withInstance(typeId, termName, instance)`.
 */
case class InstanceOverrideByTypeAndTermName[TC[_], P, A](typeId: TypeId[P], termName: String, instance: Lazy[TC[A]])
    extends InstanceOverride
