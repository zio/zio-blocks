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

package zio.blocks.data.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicSchemaExpr, DynamicValue, Schema}
import zio.blocks.schema.migration.{DynamicMigration, Migration, MigrationAction}

/**
 * Schema-based derivation for automatic Migration[A, B] from structural diff of Record schemas.
 *
 * Compares Reflect.Record fields by name and position to produce AddField, DropField,
 * RenameField, etc. Only handles simple Record (case class) schemas.
 */
object SchemaDerivation {

  /**
   * Derives a Migration[A, B] by computing the structural diff between source and target record schemas.
   */
  def derive[A, B](implicit sa: Schema[A], sb: Schema[B]): Migration[A, B] = {
    val raOpt = sa.reflect.asRecord
    val rbOpt = sb.reflect.asRecord

    (raOpt, rbOpt) match {
      case (Some(ra), Some(rb)) =>
        val actions = computeRecordDiff(ra, rb)
        val dyn     = new DynamicMigration(actions)
        new Migration(sa, sb, dyn)
      case _ =>
        // Non-record: identity migration (no actions)
        new Migration(sa, sb, DynamicMigration.empty)
    }
  }

  private def computeRecordDiff[A, B](
    ra: zio.blocks.schema.Reflect.Record.Bound[A],
    rb: zio.blocks.schema.Reflect.Record.Bound[B]
  ): Chunk[MigrationAction] = {
    val fieldsA = ra.fields.map(f => f.name -> f).toMap
    val fieldsB = rb.fields.map(f => f.name -> f).toMap

    val namesA = ra.fields.map(_.name).toIndexedSeq
    val namesB = rb.fields.map(_.name).toIndexedSeq

    var actions = Chunk.empty[MigrationAction]

    // Detect renames by position (simple heuristic: same index, different name, and names crossed)
    val minLen = math.min(namesA.length, namesB.length)
    var i = 0
    while (i < minLen) {
      val na = namesA(i)
      val nb = namesB(i)
      if (na != nb && !fieldsB.contains(na) && !fieldsA.contains(nb)) {
        // likely rename
        val at = DynamicOptic.root.field(na)
        actions = actions :+ MigrationAction.RenameField(at, nb)
      }
      i += 1
    }

    // Added fields (in B not in A, and not already handled as rename)
    val added = namesB.filterNot(n => fieldsA.contains(n) || actions.exists {
      case r: MigrationAction.RenameField => r.to == n
      case _                              => false
    })
    added.foreach { name =>
      val at      = DynamicOptic.root.field(name)
      val default = DynamicSchemaExpr.Literal(DynamicValue.Null, null.asInstanceOf[Schema[_]]) // sensible null default (schema unused in derivation)
      actions = actions :+ MigrationAction.AddField(at, default)
    }

    // Dropped fields (in A not in B, not renamed away)
    val dropped = namesA.filterNot(n => fieldsB.contains(n) || actions.exists {
      case r: MigrationAction.RenameField => r.from.contains(n)
      case _                              => false
    })
    dropped.foreach { name =>
      val at      = DynamicOptic.root.field(name)
      val default = DynamicSchemaExpr.Literal(DynamicValue.Null, null.asInstanceOf[Schema[_]])
      actions = actions :+ MigrationAction.DropField(at, default)
    }

    // For same-name fields: detect optionality or type changes (simplified: skip complex type diff for Record-only)
    // (optionality detection would inspect Wrapper for Option, omitted per scope limit)

    actions
  }
}
