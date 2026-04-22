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
import zio.blocks.schema.binding.Binding
import zio.blocks.schema.{DynamicOptic, Optic, PrimitiveType, Reflect, Schema, SchemaExpr, SchemaRepr}

/**
 * Immutable builder for typed [[Migration]] values. Selector-taking methods
 * append one [[MigrationAction]] at a time; [[build]] and [[buildPartial]]
 * materialise the final [[Migration]].
 */
final case class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Chunk[MigrationAction]
) extends MigrationBuilderVersionSpecific[A, B] {

  /** Renames a root-level variant case from `from` to `to`. */
  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    append(new MigrationAction.RenameCase(DynamicOptic.root.caseOf(from), from, to))

  /**
   * Builds the final [[Migration]]. A future release promotes this to a macro
   * that rejects incomplete migrations; until then it has the same runtime
   * behaviour as [[buildPartial]].
   */
  def build: Migration[A, B] =
    new Migration(sourceSchema, targetSchema, new DynamicMigration(actions))

  /**
   * Builds the final [[Migration]] without completeness validation. Stays
   * permissive even once [[build]] is strengthened to a compile-time check.
   */
  def buildPartial: Migration[A, B] =
    new Migration(sourceSchema, targetSchema, new DynamicMigration(actions))

  /**
   * Nests a builder scoped to case `CaseA` of the source variant, producing a
   * [[MigrationAction.TransformCase]] that runs only when the variant selects
   * `CaseA`.
   */
  def transformCase[CaseA <: A, CaseB <: B](
    caseMigration: MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB]
  )(implicit caseASchema: Schema[CaseA], caseBSchema: Schema[CaseB]): MigrationBuilder[A, B] = {
    val nested =
      caseMigration(new MigrationBuilder(caseASchema, caseBSchema, Chunk.empty))

    append(
      new MigrationAction.TransformCase(
        DynamicOptic.root.caseOf(MigrationBuilderSupport.caseName[CaseA]),
        nested.actions
      )
    )
  }

  private[migration] def append(action: MigrationAction): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
}

/**
 * Public helper object that the Scala 2 and Scala 3 [[MigrationBuilder]] macros
 * emit direct calls to at the call site. The methods are intentionally not
 * `private[migration]` because macro-expanded user code (in whatever package
 * the caller lives in — `schema-examples/...`, user applications, etc.) must be
 * able to resolve them by name. Users should not call these directly; they are
 * the erased target of the selector-based builder API on [[MigrationBuilder]].
 */
object MigrationBuilderSupport {

  def addField[A, B](
    builder: MigrationBuilder[A, B],
    targetOptic: Any,
    default: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val targetPath              = toDynamic(targetOptic, "addField")
    val (recordPath, fieldName) = parentAndField(targetPath, "addField")

    builder.append(new MigrationAction.AddField(recordPath, fieldName, default))
  }

  def dropField[A, B](
    builder: MigrationBuilder[A, B],
    sourceOptic: Any,
    defaultForReverse: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val sourcePath              = toDynamic(sourceOptic, "dropField")
    val (recordPath, fieldName) = parentAndField(sourcePath, "dropField")

    builder.append(new MigrationAction.DropField(recordPath, fieldName, defaultForReverse))
  }

  def renameField[A, B](
    builder: MigrationBuilder[A, B],
    sourceOptic: Any,
    targetOptic: Any
  ): MigrationBuilder[A, B] = {
    val sourcePath      = toDynamic(sourceOptic, "renameField")
    val targetPath      = toDynamic(targetOptic, "renameField")
    val sourceParent    = parentPath(sourcePath, "renameField")
    val targetParent    = parentPath(targetPath, "renameField")
    val targetFieldName = fieldName(targetPath, "renameField")

    if (sourceParent != targetParent)
      fail("renameField requires source and target selectors to share the same parent path")

    builder.append(new MigrationAction.Rename(sourcePath, targetFieldName))
  }

  def transformField[A, B](
    builder: MigrationBuilder[A, B],
    sourceOptic: Any,
    targetOptic: Any,
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val path = samePath(sourceOptic, targetOptic, "transformField")

    builder.append(new MigrationAction.TransformValue(path, transform))
  }

  def changeFieldType[A, B](
    builder: MigrationBuilder[A, B],
    sourceOptic: Any,
    targetOptic: Any,
    converter: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val path = samePath(sourceOptic, targetOptic, "changeFieldType")

    builder.append(new MigrationAction.ChangeType(path, converter))
  }

  def mandateField[A, B](
    builder: MigrationBuilder[A, B],
    sourceOptic: Any,
    targetOptic: Any,
    default: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val path = samePath(sourceOptic, targetOptic, "mandateField")

    builder.append(new MigrationAction.Mandate(path, default))
  }

  def optionalizeField[A, B](
    builder: MigrationBuilder[A, B],
    sourceOptic: Any,
    targetOptic: Any
  ): MigrationBuilder[A, B] = {
    val path       = samePath(sourceOptic, targetOptic, "optionalizeField")
    val sourceRepr = schemaReprAt(builder.sourceSchema, path, "optionalizeField")

    builder.append(new MigrationAction.Optionalize(path, sourceRepr))
  }

  def transformElements[A, B](
    builder: MigrationBuilder[A, B],
    at: Any,
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] =
    builder.append(new MigrationAction.TransformElements(toDynamic(at, "transformElements"), transform))

  def transformKeys[A, B](
    builder: MigrationBuilder[A, B],
    at: Any,
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] =
    builder.append(new MigrationAction.TransformKeys(toDynamic(at, "transformKeys"), transform))

  def transformValues[A, B](
    builder: MigrationBuilder[A, B],
    at: Any,
    transform: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] =
    builder.append(new MigrationAction.TransformValues(toDynamic(at, "transformValues"), transform))

  def join[A, B](
    builder: MigrationBuilder[A, B],
    targetOptic: Any,
    sourceOptics: Seq[Any],
    combiner: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val targetPath  = toDynamic(targetOptic, "join")
    val sourcePaths = Chunk.from(sourceOptics.map(toDynamic(_, "join")))

    builder.append(new MigrationAction.Join(targetPath, sourcePaths, combiner))
  }

  def split[A, B](
    builder: MigrationBuilder[A, B],
    sourceOptic: Any,
    targetOptics: Seq[Any],
    splitter: SchemaExpr[_, _]
  ): MigrationBuilder[A, B] = {
    val sourcePath  = toDynamic(sourceOptic, "split")
    val targetPaths = Chunk.from(targetOptics.map(toDynamic(_, "split")))

    builder.append(new MigrationAction.Split(sourcePath, targetPaths, splitter))
  }

  def caseName[A](implicit schema: Schema[A]): String =
    schema.reflect.typeId.name

  private def samePath(sourceOptic: Any, targetOptic: Any, method: String): DynamicOptic = {
    val sourcePath = toDynamic(sourceOptic, method)
    val targetPath = toDynamic(targetOptic, method)

    if (sourcePath == targetPath) sourcePath
    else fail(s"$method requires matching source and target selectors")
  }

  private def toDynamic(opticLike: Any, method: String): DynamicOptic =
    opticLike match {
      case optic: Optic[_, _] => optic.asInstanceOf[Optic[Any, Any]].toDynamic
      case _                  => fail(s"$method requires a selector supported by CompanionOptics")
    }

  private def parentAndField(path: DynamicOptic, method: String): (DynamicOptic, String) =
    (parentPath(path, method), fieldName(path, method))

  private def parentPath(path: DynamicOptic, method: String): DynamicOptic = {
    val nodes = path.nodes
    if (nodes.isEmpty) fail(s"$method requires a field selector")
    else
      nodes.last match {
        case _: DynamicOptic.Node.Field => new DynamicOptic(nodes.init)
        case _                          => fail(s"$method requires a field selector")
      }
  }

  private def fieldName(path: DynamicOptic, method: String): String =
    path.nodes.lastOption match {
      case Some(field: DynamicOptic.Node.Field) => field.name
      case _                                    => fail(s"$method requires a field selector")
    }

  private def schemaReprAt[A](schema: Schema[A], path: DynamicOptic, method: String): SchemaRepr =
    schema.get(path) match {
      case Some(reflect) => schemaReprOf(reflect)
      case None          => fail(s"$method could not resolve schema metadata for selector ${path.toScalaString}")
    }

  private def schemaReprOf(reflect: Reflect[Binding, _]): SchemaRepr =
    reflect.asPrimitive match {
      case Some(primitive) => primitiveSchemaRepr(primitive.primitiveType)
      case None            =>
        if (reflect.isOption)
          SchemaRepr.Optional(schemaReprOf(reflect.optionInnerType.get))
        else
          reflect.asSequenceUnknown match {
            case Some(sequence) =>
              SchemaRepr.Sequence(schemaReprOf(sequence.sequence.element))
            case None =>
              reflect.asMapUnknown match {
                case Some(map) =>
                  SchemaRepr.Map(schemaReprOf(map.map.key), schemaReprOf(map.map.value))
                case None =>
                  reflect.asRecord match {
                    case Some(record) =>
                      SchemaRepr.Record(
                        record.fields.map(field => (field.name, schemaReprOf(field.value))).toIndexedSeq
                      )
                    case None =>
                      reflect.asVariant match {
                        case Some(variant) =>
                          SchemaRepr.Variant(
                            variant.cases.map(case_ => (case_.name, schemaReprOf(case_.value))).toIndexedSeq
                          )
                        case None =>
                          SchemaRepr.Nominal(reflect.typeId.name)
                      }
                  }
              }
          }
    }

  private def primitiveSchemaRepr(primitiveType: PrimitiveType[_]): SchemaRepr =
    SchemaRepr.Primitive(primitiveName(primitiveType))

  private def primitiveName(primitiveType: PrimitiveType[_]): String =
    primitiveType match {
      case _: PrimitiveType.Unit.type      => "unit"
      case _: PrimitiveType.Boolean        => "boolean"
      case _: PrimitiveType.Byte           => "byte"
      case _: PrimitiveType.Short          => "short"
      case _: PrimitiveType.Int            => "int"
      case _: PrimitiveType.Long           => "long"
      case _: PrimitiveType.Float          => "float"
      case _: PrimitiveType.Double         => "double"
      case _: PrimitiveType.Char           => "char"
      case _: PrimitiveType.String         => "string"
      case _: PrimitiveType.BigInt         => "bigint"
      case _: PrimitiveType.BigDecimal     => "bigdecimal"
      case _: PrimitiveType.DayOfWeek      => "dayofweek"
      case _: PrimitiveType.Duration       => "duration"
      case _: PrimitiveType.Instant        => "instant"
      case _: PrimitiveType.LocalDate      => "localdate"
      case _: PrimitiveType.LocalDateTime  => "localdatetime"
      case _: PrimitiveType.LocalTime      => "localtime"
      case _: PrimitiveType.Month          => "month"
      case _: PrimitiveType.MonthDay       => "monthday"
      case _: PrimitiveType.OffsetDateTime => "offsetdatetime"
      case _: PrimitiveType.OffsetTime     => "offsettime"
      case _: PrimitiveType.Period         => "period"
      case _: PrimitiveType.Year           => "year"
      case _: PrimitiveType.YearMonth      => "yearmonth"
      case _: PrimitiveType.ZoneId         => "zoneid"
      case _: PrimitiveType.ZoneOffset     => "zoneoffset"
      case _: PrimitiveType.ZonedDateTime  => "zoneddatetime"
      case _: PrimitiveType.UUID           => "uuid"
      case _: PrimitiveType.Currency       => "currency"
    }

  private def fail(message: String): Nothing =
    throw new IllegalArgumentException(message)
}
