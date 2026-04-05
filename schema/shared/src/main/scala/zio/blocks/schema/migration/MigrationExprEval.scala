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

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.Binding.bindingHasBinding

private[migration] object MigrationExprEval {

  private implicit val binding: HasBinding[Binding] = bindingHasBinding

  def eval(
    expr: MigrationExpr,
    root: DynamicValue,
    sourceSchema: Schema[?],
    targetSchema: Schema[?],
    errorPath: DynamicOptic
  ): Either[SchemaError, DynamicValue] =
    expr match {
      case MigrationExpr.Literal(value) =>
        Right(value)

      case MigrationExpr.RootPath(relative) =>
        root.get(relative).one.left.map(_ => SchemaError.message("Path resolution failed", errorPath))

      case MigrationExpr.ConcatStrings(left, right) =>
        for {
          lv <- eval(left, root, sourceSchema, targetSchema, errorPath)
          rv <- eval(right, root, sourceSchema, targetSchema, errorPath)
          ls <- asString(lv, errorPath)
          rs <- asString(rv, errorPath)
        } yield new DynamicValue.Primitive(new PrimitiveValue.String(ls + rs))

      case MigrationExpr.IntPlus(left, right) =>
        for {
          lv <- eval(left, root, sourceSchema, targetSchema, errorPath)
          rv <- eval(right, root, sourceSchema, targetSchema, errorPath)
          li <- asInt(lv, errorPath)
          ri <- asInt(rv, errorPath)
        } yield new DynamicValue.Primitive(new PrimitiveValue.Int(li + ri))

      case MigrationExpr.SchemaRootDefault(MigrationSchemaSlot.Source) =>
        defaultDynamic(sourceSchema, errorPath)

      case MigrationExpr.SchemaRootDefault(MigrationSchemaSlot.Target) =>
        defaultDynamic(targetSchema, errorPath)

      case MigrationExpr.FieldDefault(name, MigrationSchemaSlot.Source) =>
        fieldDefaultDynamic(sourceSchema, name, errorPath)

      case MigrationExpr.FieldDefault(name, MigrationSchemaSlot.Target) =>
        fieldDefaultDynamic(targetSchema, name, errorPath)

      case _: MigrationExpr.CoercePrimitive =>
        Left(SchemaError.message("CoercePrimitive must be evaluated with an input value", errorPath))
    }

  def evalUnary(
    value: DynamicValue,
    target: MigrationPrimitiveTarget,
    errorPath: DynamicOptic
  ): Either[SchemaError, DynamicValue] = {
    import MigrationPrimitiveTarget._
    target match {
      case Int =>
        asInt(value, errorPath).map(i => new DynamicValue.Primitive(new PrimitiveValue.Int(i)))
      case Long =>
        asLong(value, errorPath).map(l => new DynamicValue.Primitive(new PrimitiveValue.Long(l)))
      case Double =>
        asDouble(value, errorPath).map(d => new DynamicValue.Primitive(new PrimitiveValue.Double(d)))
      case String =>
        Right(new DynamicValue.Primitive(new PrimitiveValue.String(value.toString)))
      case Boolean =>
        asBoolean(value, errorPath).map(b => new DynamicValue.Primitive(new PrimitiveValue.Boolean(b)))
    }
  }

  private def defaultDynamic(schema: Schema[?], path: DynamicOptic): Either[SchemaError, DynamicValue] =
    schema.getDefaultValue match {
      case Some(v) =>
        val anySchema = schema.asInstanceOf[Schema[Any]]
        Right(anySchema.toDynamicValue(v.asInstanceOf[Any]))
      case None => Left(SchemaError.message("No default value on schema", path))
    }

  private def fieldDefaultDynamic(
    schema: Schema[?],
    name: String,
    path: DynamicOptic
  ): Either[SchemaError, DynamicValue] =
    schema.reflect match {
      case r: Reflect.Record[Binding, ?] =>
        r.fieldByName(name) match {
          case Some(term) =>
            term.value.getDefaultValue match {
              case Some(v) =>
                val fieldSchema = new Schema[Any](term.value.asInstanceOf[Reflect.Bound[Any]])
                Right(fieldSchema.toDynamicValue(v.asInstanceOf[Any]))
              case None => Left(SchemaError.message(s"No default for field '$name'", path))
            }
          case None => Left(SchemaError.message(s"Unknown field '$name'", path))
        }
      case _ => Left(SchemaError.message("Schema is not a record", path))
    }

  private def asString(value: DynamicValue, path: DynamicOptic): Either[SchemaError, String] =
    value match {
      case DynamicValue.Primitive(p: PrimitiveValue.String) => Right(p.value)
      case other                                            => Left(SchemaError.message(s"Expected string, got ${other.valueType}", path))
    }

  private def asInt(value: DynamicValue, path: DynamicOptic): Either[SchemaError, Int] =
    value match {
      case DynamicValue.Primitive(p: PrimitiveValue.Int)  => Right(p.value)
      case DynamicValue.Primitive(p: PrimitiveValue.Long) =>
        if (p.value.isValidInt) Right(p.value.toInt)
        else Left(SchemaError.message("Long does not fit in Int", path))
      case other => Left(SchemaError.message(s"Expected int, got ${other.valueType}", path))
    }

  private def asLong(value: DynamicValue, path: DynamicOptic): Either[SchemaError, Long] =
    value match {
      case DynamicValue.Primitive(p: PrimitiveValue.Long)   => Right(p.value)
      case DynamicValue.Primitive(p: PrimitiveValue.Int)    => Right(p.value.toLong)
      case DynamicValue.Primitive(p: PrimitiveValue.Double) => Right(p.value.toLong)
      case other                                            => Left(SchemaError.message(s"Expected numeric, got ${other.valueType}", path))
    }

  private def asDouble(value: DynamicValue, path: DynamicOptic): Either[SchemaError, Double] =
    value match {
      case DynamicValue.Primitive(p: PrimitiveValue.Double) => Right(p.value)
      case DynamicValue.Primitive(p: PrimitiveValue.Float)  => Right(p.value.toDouble)
      case DynamicValue.Primitive(p: PrimitiveValue.Int)    => Right(p.value.toDouble)
      case DynamicValue.Primitive(p: PrimitiveValue.Long)   => Right(p.value.toDouble)
      case other                                            => Left(SchemaError.message(s"Expected number, got ${other.valueType}", path))
    }

  private def asBoolean(value: DynamicValue, path: DynamicOptic): Either[SchemaError, Boolean] =
    value match {
      case DynamicValue.Primitive(p: PrimitiveValue.Boolean) => Right(p.value)
      case other                                             => Left(SchemaError.message(s"Expected boolean, got ${other.valueType}", path))
    }
}
