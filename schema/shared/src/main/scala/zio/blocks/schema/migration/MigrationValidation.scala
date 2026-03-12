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

import zio.blocks.schema.{DynamicOptic, Schema}

object MigrationValidation {

  final case class ValidationError(message: String) extends RuntimeException(message)

  def validate[A, B](
    sourceSchema: Schema[A],
    targetSchema: Schema[B],
    actions: Vector[MigrationAction]
  ): Vector[String] = {
    val sourceFields = extractFieldNames(sourceSchema)
    val targetFields = extractFieldNames(targetSchema)

    if (sourceFields.isEmpty && targetFields.isEmpty) return Vector.empty

    val errors = Vector.newBuilder[String]

    val addedFields   = actions.collect { case a: MigrationAction.AddField => rootFieldName(a.at) }.flatten.toSet
    val droppedFields = actions.collect { case a: MigrationAction.DropField => rootFieldName(a.at) }.flatten.toSet
    val renamedFrom   = actions.collect { case a: MigrationAction.Rename => rootFieldName(a.at) }.flatten.toSet
    val renamedTo     = actions.collect { case a: MigrationAction.Rename => a.to }.toSet

    val carriedOver = sourceFields.intersect(targetFields) -- droppedFields -- renamedFrom

    if (targetFields.nonEmpty) {
      val invalidAdds = addedFields -- targetFields
      invalidAdds.foreach { f =>
        errors += s"AddField '$f' does not correspond to a field in the target schema"
      }
    }

    if (sourceFields.nonEmpty) {
      val invalidDrops = droppedFields -- sourceFields
      invalidDrops.foreach { f =>
        errors += s"DropField '$f' does not exist in the source schema"
      }
    }

    if (sourceFields.nonEmpty) {
      val invalidRenames = renamedFrom -- sourceFields
      invalidRenames.foreach { f =>
        errors += s"Rename source '$f' does not exist in the source schema"
      }
    }

    if (targetFields.nonEmpty && sourceFields.nonEmpty) {
      val accountedFor = carriedOver ++ addedFields ++ renamedTo
      val unaccounted  = targetFields -- accountedFor
      if (unaccounted.nonEmpty) {
        errors += s"Target fields not accounted for by migration: ${unaccounted.toVector.sorted.mkString(", ")}"
      }
    }

    if (sourceFields.nonEmpty && targetFields.nonEmpty) {
      val handledSource = carriedOver ++ droppedFields ++ renamedFrom
      val unhandled     = sourceFields -- handledSource
      if (unhandled.nonEmpty) {
        errors += s"Source fields not handled by migration: ${unhandled.toVector.sorted.mkString(", ")}"
      }
    }

    errors.result()
  }

  private def extractFieldNames[A](schema: Schema[A]): Set[String] =
    schema.reflect.asRecord match {
      case Some(record) => record.fields.map(_.name).toSet
      case None         => Set.empty
    }

  private def rootFieldName(optic: DynamicOptic): Option[String] =
    optic.nodes.headOption match {
      case Some(DynamicOptic.Node.Field(name)) => Some(name)
      case _                                   => None
    }
}
