package zio.blocks.schema.migration.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema.json._
import zio.blocks.schema.migration._
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaError}

object MigrationJsonCodec {

  // =================================================================================
  // 1. DynamicOptic Codec
  // =================================================================================

  implicit val nodeEncoder: JsonEncoder[DynamicOptic.Node] = JsonEncoder.instance {
    case DynamicOptic.Node.Field(name) => new Json.String(s"field:$name")
    case DynamicOptic.Node.Case(name)  => new Json.String(s"case:$name")
    case DynamicOptic.Node.Elements    => new Json.String("each")
    case _                             => new Json.String("unknown")
  }

  implicit val nodeDecoder: JsonDecoder[DynamicOptic.Node] = JsonDecoder.instance {
    case str: Json.String =>
      str.value.split(":", 2) match {
        case Array("field", name) => Right(DynamicOptic.Node.Field(name))
        case Array("case", name)  => Right(DynamicOptic.Node.Case(name))
        case Array("each")        => Right(DynamicOptic.Node.Elements)
        case _                    => Right(DynamicOptic.Node.Field(str.value))
      }
    case _ => Left(SchemaError("Expected String for Optic Node"))
  }

  implicit val opticEncoder: JsonEncoder[DynamicOptic] =
    JsonEncoder[Vector[DynamicOptic.Node]].contramap(_.nodes.toVector)

  implicit val opticDecoder: JsonDecoder[DynamicOptic] =
    JsonDecoder[Vector[DynamicOptic.Node]].map(nodes => DynamicOptic(nodes))

  // =================================================================================
  // 2. Helpers (Utility Functions)
  // =================================================================================

  private def jsonObj(fields: (String, Json)*): Json =
    new Json.Object(Chunk.fromIterable(fields))

  private def findField(chunk: Chunk[(String, Json)], key: String): Option[Json] =
    chunk.find(_._1 == key).map(_._2)

  private def getString(json: Json): Option[String] = json match {
    case s: Json.String => Some(s.value)
    case _              => None
  }

  private def getObject(json: Json): Option[Chunk[(String, Json)]] = json match {
    case o: Json.Object => Some(o.value)
    case _              => None
  }

  private def jsonToDynamicValue(json: Json): DynamicValue = json match {
    case s: Json.String => DynamicValue.Primitive(PrimitiveValue.String(s.value))
    // [FIX] Extract underlying java.math.BigDecimal for PrimitiveValue
    case n: Json.Number  => DynamicValue.Primitive(PrimitiveValue.BigDecimal(n.value.bigDecimal))
    case b: Json.Boolean => DynamicValue.Primitive(PrimitiveValue.Boolean(b.value))
    case _               => DynamicValue.Primitive(PrimitiveValue.Unit)
  }

  private def dynamicValueToJson(dv: DynamicValue): Json = dv match {
    case DynamicValue.Primitive(PrimitiveValue.String(s))  => new Json.String(s)
    case DynamicValue.Primitive(PrimitiveValue.Int(i))     => new Json.Number(BigDecimal(i))
    case DynamicValue.Primitive(PrimitiveValue.Long(l))    => new Json.Number(BigDecimal(l))
    case DynamicValue.Primitive(PrimitiveValue.Double(d))  => new Json.Number(BigDecimal(d))
    case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) => Json.Boolean(b)

    // [FIX] 'bd' is already scala.math.BigDecimal, so passing it directly without BigDecimal(bd) wrapper
    case DynamicValue.Primitive(PrimitiveValue.BigDecimal(bd)) => new Json.Number(bd)

    case _ => Json.Null
  }

  // =================================================================================
  // 3. SchemaExpr Codec
  // =================================================================================

  implicit val exprEncoder: JsonEncoder[SchemaExpr[_]] = JsonEncoder.instance {
    case SchemaExpr.Identity() =>
      jsonObj("type" -> new Json.String("identity"))

    case SchemaExpr.Constant(v) =>
      jsonObj("type" -> new Json.String("constant"), "value" -> dynamicValueToJson(v))

    case SchemaExpr.DefaultValue(v) =>
      jsonObj("type" -> new Json.String("default"), "value" -> dynamicValueToJson(v))

    case SchemaExpr.Converted(operand, op) =>
      val opStr = op match {
        case SchemaExpr.ConversionOp.ToString => "ToString"
        case SchemaExpr.ConversionOp.ToInt    => "ToInt"
      }
      jsonObj(
        "type"    -> new Json.String("converted"),
        "operand" -> exprEncoder.encode(operand),
        "op"      -> new Json.String(opStr)
      )
  }

  implicit val exprDecoder: JsonDecoder[SchemaExpr[_]] = JsonDecoder.instance { json =>
    json match {
      case obj: Json.Object =>
        val fields = obj.value
        val tpeOpt = findField(fields, "type").flatMap(getString)

        tpeOpt match {
          case Some("identity") => Right(SchemaExpr.Identity())

          case Some("constant") =>
            val valJson = findField(fields, "value").getOrElse(Json.Null)
            Right(SchemaExpr.Constant(jsonToDynamicValue(valJson)))

          case Some("default") =>
            val valJson = findField(fields, "value").getOrElse(Json.Null)
            Right(SchemaExpr.DefaultValue(jsonToDynamicValue(valJson)))

          case Some("converted") =>
            for {
              opJson  <- findField(fields, "operand").toRight(SchemaError("Missing operand"))
              operand <- exprDecoder.decode(opJson)
              opStr   <- findField(fields, "op").flatMap(getString).toRight(SchemaError("Missing op"))
              op      <- opStr match {
                      case "ToString" => Right(SchemaExpr.ConversionOp.ToString)
                      case "ToInt"    => Right(SchemaExpr.ConversionOp.ToInt)
                      case _          => Left(SchemaError(s"Unknown ConversionOp: $opStr"))
                    }
            } yield SchemaExpr.Converted(operand, op)

          case Some(_) => Right(SchemaExpr.Identity())
          case None    => Left(SchemaError("Missing 'type' field"))
        }
      case _ => Left(SchemaError("Expected Object for SchemaExpr"))
    }
  }

  // =================================================================================
  // 4. MigrationAction Codec
  // =================================================================================

  implicit val actionEncoder: JsonEncoder[MigrationAction] = JsonEncoder.instance { action =>
    val (op, details) = action match {
      case MigrationAction.AddField(at, default) =>
        "AddField" -> jsonObj(
          "at"      -> opticEncoder.encode(at),
          "default" -> exprEncoder.encode(default)
        )

      case MigrationAction.DropField(at, df) =>
        "DropField" -> jsonObj("at" -> opticEncoder.encode(at), "defaultForReverse" -> exprEncoder.encode(df))

      case MigrationAction.Rename(at, to) =>
        "Rename" -> jsonObj("at" -> opticEncoder.encode(at), "to" -> new Json.String(to))

      case MigrationAction.TransformValue(at, transform) =>
        "TransformValue" -> jsonObj("at" -> opticEncoder.encode(at), "transform" -> exprEncoder.encode(transform))

      case MigrationAction.Mandate(at, d) =>
        "Mandate" -> jsonObj("at" -> opticEncoder.encode(at), "default" -> exprEncoder.encode(d))

      case MigrationAction.Optionalize(at) =>
        "Optionalize" -> jsonObj("at" -> opticEncoder.encode(at))

      case MigrationAction.RenameCase(at, from, to) =>
        "RenameCase" -> jsonObj(
          "at"   -> opticEncoder.encode(at),
          "from" -> new Json.String(from),
          "to"   -> new Json.String(to)
        )

      case MigrationAction.TransformElements(at, t) =>
        "TransformElements" -> jsonObj("at" -> opticEncoder.encode(at), "transform" -> exprEncoder.encode(t))

      case MigrationAction.TransformKeys(at, t) =>
        "TransformKeys" -> jsonObj("at" -> opticEncoder.encode(at), "transform" -> exprEncoder.encode(t))

      case MigrationAction.TransformValues(at, t) =>
        "TransformValues" -> jsonObj("at" -> opticEncoder.encode(at), "transform" -> exprEncoder.encode(t))

      case MigrationAction.TransformCase(at, acts) =>
        "TransformCase" -> jsonObj(
          "at"      -> opticEncoder.encode(at),
          "actions" -> JsonEncoder[Vector[MigrationAction]].encode(acts)
        )

      case MigrationAction.Join(at, sources, combiner) =>
        "Join" -> jsonObj(
          "at"          -> opticEncoder.encode(at),
          "sourcePaths" -> JsonEncoder[Vector[DynamicOptic]].encode(sources),
          "combiner"    -> exprEncoder.encode(combiner)
        )

      case MigrationAction.Split(at, targets, splitter) =>
        "Split" -> jsonObj(
          "at"          -> opticEncoder.encode(at),
          "targetPaths" -> JsonEncoder[Vector[DynamicOptic]].encode(targets),
          "splitter"    -> exprEncoder.encode(splitter)
        )

      case MigrationAction.ChangeType(at, converter) =>
        "ChangeType" -> jsonObj("at" -> opticEncoder.encode(at), "converter" -> exprEncoder.encode(converter))
    }

    jsonObj(
      "op"      -> new Json.String(op),
      "details" -> details
    )
  }

  implicit val actionDecoder: JsonDecoder[MigrationAction] = JsonDecoder.instance { json =>
    json match {
      case obj: Json.Object =>
        val fields = obj.value

        for {
          opJson <- findField(fields, "op").toRight(SchemaError("Missing 'op'"))
          op     <- getString(opJson).toRight(SchemaError("op must be string"))

          detJson  <- findField(fields, "details").toRight(SchemaError("Missing 'details'"))
          detChunk <- getObject(detJson).toRight(SchemaError("details must be object"))

          atJson <- findField(detChunk, "at").toRight(SchemaError("Missing 'at'"))
          at     <- opticDecoder.decode(atJson)

          action <- op match {
                      case "AddField" =>
                        findField(detChunk, "default")
                          .map(exprDecoder.decode)
                          .getOrElse(Right(SchemaExpr.Identity()))
                          .map(d => MigrationAction.AddField(at, d))

                      case "DropField" =>
                        findField(detChunk, "defaultForReverse")
                          .map(exprDecoder.decode)
                          .getOrElse(Right(SchemaExpr.Identity()))
                          .map(df => MigrationAction.DropField(at, df))

                      case "Rename" =>
                        findField(detChunk, "to")
                          .flatMap(getString)
                          .toRight(SchemaError("Missing 'to'"))
                          .map(to => MigrationAction.Rename(at, to))

                      case "TransformValue" =>
                        findField(detChunk, "transform")
                          .map(exprDecoder.decode)
                          .getOrElse(Right(SchemaExpr.Identity()))
                          .map(t => MigrationAction.TransformValue(at, t))

                      case "Mandate" =>
                        findField(detChunk, "default")
                          .map(exprDecoder.decode)
                          .getOrElse(Right(SchemaExpr.Identity()))
                          .map(d => MigrationAction.Mandate(at, d))

                      case "Optionalize" => Right(MigrationAction.Optionalize(at))

                      case "RenameCase" =>
                        for {
                          from <- findField(detChunk, "from").flatMap(getString).toRight(SchemaError("Missing 'from'"))
                          to   <- findField(detChunk, "to").flatMap(getString).toRight(SchemaError("Missing 'to'"))
                        } yield MigrationAction.RenameCase(at, from, to)

                      case "TransformElements" =>
                        findField(detChunk, "transform")
                          .map(exprDecoder.decode)
                          .getOrElse(Right(SchemaExpr.Identity()))
                          .map(t => MigrationAction.TransformElements(at, t))

                      case "TransformKeys" =>
                        findField(detChunk, "transform")
                          .map(exprDecoder.decode)
                          .getOrElse(Right(SchemaExpr.Identity()))
                          .map(t => MigrationAction.TransformKeys(at, t))

                      case "TransformValues" =>
                        findField(detChunk, "transform")
                          .map(exprDecoder.decode)
                          .getOrElse(Right(SchemaExpr.Identity()))
                          .map(t => MigrationAction.TransformValues(at, t))

                      case "ChangeType" =>
                        findField(detChunk, "converter")
                          .map(exprDecoder.decode)
                          .getOrElse(Right(SchemaExpr.Identity()))
                          .map(c => MigrationAction.ChangeType(at, c))

                      case "Join" =>
                        for {
                          srcsJson <- findField(detChunk, "sourcePaths").toRight(SchemaError("Missing sourcePaths"))
                          srcs     <- JsonDecoder[Vector[DynamicOptic]].decode(srcsJson)
                          combJson <- findField(detChunk, "combiner").toRight(SchemaError("Missing combiner"))
                          comb     <- exprDecoder.decode(combJson)
                        } yield MigrationAction.Join(at, srcs, comb)

                      case "Split" =>
                        for {
                          tgtsJson  <- findField(detChunk, "targetPaths").toRight(SchemaError("Missing targetPaths"))
                          tgts      <- JsonDecoder[Vector[DynamicOptic]].decode(tgtsJson)
                          splitJson <- findField(detChunk, "splitter").toRight(SchemaError("Missing splitter"))
                          splitter  <- exprDecoder.decode(splitJson)
                        } yield MigrationAction.Split(at, tgts, splitter)

                      case "TransformCase" =>
                        for {
                          actsJson <- findField(detChunk, "actions").toRight(SchemaError("Missing actions"))
                          acts     <- JsonDecoder[Vector[MigrationAction]].decode(actsJson)
                        } yield MigrationAction.TransformCase(at, acts)

                      case _ =>
                        Left(SchemaError(s"Unsupported Migration Action: $op"))
                    }
        } yield action

      case _ => Left(SchemaError("Expected Object"))
    }
  }

  // =================================================================================
  // 5. DynamicMigration Codec
  // =================================================================================

  implicit val migrationEncoder: JsonEncoder[DynamicMigration] =
    JsonEncoder[Vector[MigrationAction]].contramap(_.actions)

  implicit val migrationDecoder: JsonDecoder[DynamicMigration] =
    JsonDecoder[Vector[MigrationAction]].map(actions => DynamicMigration(actions))

  // [FIX] Removed 'extends AnyVal' to prevent Scaladoc crash in Scala 3.7.4
  implicit class MigrationJsonOps(val migration: DynamicMigration) {
    def toJson: String = migrationEncoder.encode(migration).toString
  }
}
