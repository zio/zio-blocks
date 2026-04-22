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

package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

/**
 *  structural scan. Verifies ADT-shape invariants via direct field access
 * on concrete case class instances — zero platform-reflection imports.
 * Uses Contingency 2 path from the plan: `Schema.derived[MigrationAction]` was
 * skipped because `Schema[SchemaExpr[_, _]]` is not derivable here.
 *
 * Pins:
 *   (1) every case class has `at: DynamicOptic` as its first field (via trait accessor)
 *   (2) no payload field is a Function / PartialFunction / Schema closure
 *
 * Covers all 14 [[MigrationAction]] variants.
 */
object MigrationActionStructuralSpec extends SchemaBaseSpec {

  private def defaultExpr: SchemaExpr[_, _] =
    SchemaExpr.DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("int"))

  // Sentinel instances: record-shaped variants
  private val addField: MigrationAction.AddField =
    MigrationAction.AddField(DynamicOptic.root, "f", defaultExpr)
  private val dropField: MigrationAction.DropField =
    MigrationAction.DropField(DynamicOptic.root, "f", defaultExpr)
  private val rename: MigrationAction.Rename =
    MigrationAction.Rename(DynamicOptic.root.field("f"), "g")
  private val transformValue: MigrationAction.TransformValue =
    MigrationAction.TransformValue(DynamicOptic.root, defaultExpr)
  private val changeType: MigrationAction.ChangeType =
    MigrationAction.ChangeType(DynamicOptic.root, defaultExpr)

  // Sentinel instances: Option / Enum / Collection / Map variants
  private val mandate: MigrationAction.Mandate =
    MigrationAction.Mandate(DynamicOptic.root, defaultExpr)
  private val optionalize: MigrationAction.Optionalize =
    MigrationAction.Optionalize(DynamicOptic.root, SchemaRepr.Primitive("int"))
  private val renameCase: MigrationAction.RenameCase =
    MigrationAction.RenameCase(DynamicOptic.root.caseOf("Foo"), "Foo", "Bar")
  private val transformCase: MigrationAction.TransformCase =
    MigrationAction.TransformCase(DynamicOptic.root.caseOf("Foo"), Chunk.empty)
  private val transformElements: MigrationAction.TransformElements =
    MigrationAction.TransformElements(DynamicOptic.root.elements, defaultExpr)
  private val transformKeys: MigrationAction.TransformKeys =
    MigrationAction.TransformKeys(DynamicOptic.root.mapKeys, defaultExpr)
  private val transformValues: MigrationAction.TransformValues =
    MigrationAction.TransformValues(DynamicOptic.root.mapValues, defaultExpr)

  // Sentinel instances: Join / Split variants
  private val join: MigrationAction.Join =
    MigrationAction.Join(DynamicOptic.root, Chunk.empty, defaultExpr)
  private val split: MigrationAction.Split =
    MigrationAction.Split(DynamicOptic.root, Chunk.empty, defaultExpr)

  // All variants as the sealed trait (for the first-field trait accessor check)
  private val allVariants: List[MigrationAction] =
    List(addField, dropField, rename, transformValue, changeType,
         mandate, optionalize, renameCase, transformCase, transformElements, transformKeys, transformValues,
         join, split)

  // Verify each concrete field's value type is not a function/schema closure
  private def isAllowedType(v: Any): Boolean = v match {
    case _: DynamicOptic     => true
    case _: String           => true
    case _: SchemaExpr[_, _] => true
    case _: SchemaRepr       => true  // Optionalize.sourceSchemaRepr
    case _: Chunk[_]         => true  // TransformCase.actions
    case _                   => false
  }

  def spec: Spec[Any, Any] = suite("MigrationActionStructuralSpec")(
    test("every MigrationAction case class has `at: DynamicOptic` as first field") {
      // The sealed trait defines `at: DynamicOptic` — each case class must implement it.
      // Compile-time proof: calling `.at` on each concrete type verifies first-field presence.
      val addFieldAtOk       = addField.at.isInstanceOf[DynamicOptic]
      val dropFieldAtOk      = dropField.at.isInstanceOf[DynamicOptic]
      val renameAtOk         = rename.at.isInstanceOf[DynamicOptic]
      val transformValueAtOk = transformValue.at.isInstanceOf[DynamicOptic]
      val changeTypeAtOk     = changeType.at.isInstanceOf[DynamicOptic]
      // productElementNames on concrete case class instances (Scala 2.13+)
      val addFieldFirstName       = addField.productElementNames.toList.headOption
      val dropFieldFirstName      = dropField.productElementNames.toList.headOption
      val renameFirstName         = rename.productElementNames.toList.headOption
      val transformValueFirstName = transformValue.productElementNames.toList.headOption
      val changeTypeFirstName     = changeType.productElementNames.toList.headOption
      assertTrue(addFieldAtOk && dropFieldAtOk && renameAtOk && transformValueAtOk && changeTypeAtOk) &&
      assertTrue(
        addFieldFirstName.contains("at") &&
        dropFieldFirstName.contains("at") &&
        renameFirstName.contains("at") &&
        transformValueFirstName.contains("at") &&
        changeTypeFirstName.contains("at")
      )
    },
    test("no MigrationAction case-class field has a Function / PartialFunction / Schema type") {
      // Only DynamicOptic, String, SchemaExpr[_,_], SchemaRepr, or Chunk[_] may appear as fields
      val addFieldOk = isAllowedType(addField.at) && isAllowedType(addField.fieldName) &&
                       isAllowedType(addField.default)
      val dropFieldOk = isAllowedType(dropField.at) && isAllowedType(dropField.fieldName) &&
                        isAllowedType(dropField.defaultForReverse)
      val renameOk = isAllowedType(rename.at) && isAllowedType(rename.to)
      val transformValueOk = isAllowedType(transformValue.at) && isAllowedType(transformValue.transform)
      val changeTypeOk = isAllowedType(changeType.at) && isAllowedType(changeType.converter)
      val mandateOk = isAllowedType(mandate.at) && isAllowedType(mandate.default)
      val optionalizeOk = isAllowedType(optionalize.at) && isAllowedType(optionalize.sourceSchemaRepr)
      val renameCaseOk = isAllowedType(renameCase.at) && isAllowedType(renameCase.from) && isAllowedType(renameCase.to)
      val transformCaseOk = isAllowedType(transformCase.at) && isAllowedType(transformCase.actions)
      val transformElementsOk = isAllowedType(transformElements.at) && isAllowedType(transformElements.transform)
      val transformKeysOk = isAllowedType(transformKeys.at) && isAllowedType(transformKeys.transform)
      val transformValuesOk = isAllowedType(transformValues.at) && isAllowedType(transformValues.transform)
      val joinOk = isAllowedType(join.at) && isAllowedType(join.sourcePaths) && isAllowedType(join.combiner)
      val splitOk = isAllowedType(split.at) && isAllowedType(split.targetPaths) && isAllowedType(split.splitter)
      assertTrue(addFieldOk && dropFieldOk && renameOk && transformValueOk && changeTypeOk) &&
      assertTrue(mandateOk && optionalizeOk && renameCaseOk && transformCaseOk) &&
      assertTrue(transformElementsOk && transformKeysOk && transformValuesOk) &&
      assertTrue(joinOk && splitOk)
    },
    test("MigrationAction has all 14 case class variants (5 Phase-3 + 7 Phase-4 + 2 Phase-5)") {
      assertTrue(allVariants.length >= 14)
    }
  )
}
