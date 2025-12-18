package zio.blocks.schema.migration

import zio.json._
import zio.json.ast.Json
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.DynamicValue

object DynamicMigrationCodec {

  // DynamicOptic Codec
  implicit val dynamicOpticEncoder: JsonEncoder[DynamicOptic] = JsonEncoder.string.contramap(_.toString)
  implicit val dynamicOpticDecoder: JsonDecoder[DynamicOptic] = JsonDecoder.string.map { str =>
     // Parsing logic would be needed here to fully support string-to-optic
     // For now, we stub this or assume a simple structure if we wanted true roundtrip
     // A better approach for robust serialization is to serialize the nodes structure
     DynamicOptic.root // STUB: Proper parsing of logical paths is complex and requires a parser
  }
  
  // To enable real roundtrip, we should serialize the internal structure (Nodes)
  // Let's create a better codec for DynamicOptic nodes:
  
  implicit val nodeEncoder: JsonEncoder[DynamicOptic.Node] = JsonEncoder[Json].contramap {
    case DynamicOptic.Node.Field(name) => Json.Obj("type" -> Json.Str("Field"), "name" -> Json.Str(name))
    case DynamicOptic.Node.Case(name) => Json.Obj("type" -> Json.Str("Case"), "name" -> Json.Str(name))
    case DynamicOptic.Node.AtIndex(idx) => Json.Obj("type" -> Json.Str("AtIndex"), "index" -> Json.Num(idx))
    case DynamicOptic.Node.Elements => Json.Obj("type" -> Json.Str("Elements"))
    case DynamicOptic.Node.Wrapped => Json.Obj("type" -> Json.Str("Wrapped"))
    case _ => Json.Obj("type" -> Json.Str("Unknown"))
  }
  
  implicit val nodeDecoder: JsonDecoder[DynamicOptic.Node] = JsonDecoder[Json].mapOrFail { json =>
    for {
      obj <- json.asObject.toRight("Not an object")
      typ <- obj.get("type").flatMap(_.asString).toRight("Missing type")
      res <- typ match {
        case "Field" => obj.get("name").flatMap(_.asString).toRight("Missing name").map(DynamicOptic.Node.Field(_))
        case "Case" => obj.get("name").flatMap(_.asString).toRight("Missing name").map(DynamicOptic.Node.Case(_))
        case "AtIndex" => obj.get("index").flatMap(_.asNumber).flatMap(_.toInt).toRight("Missing index").map(DynamicOptic.Node.AtIndex(_))
        case "Elements" => Right(DynamicOptic.Node.Elements)
        case "Wrapped" => Right(DynamicOptic.Node.Wrapped)
        case _ => Left(s"Unknown node type: $typ")
      }
    } yield res
  }

  implicit val properDynamicOpticEncoder: JsonEncoder[DynamicOptic] = JsonEncoder[Vector[DynamicOptic.Node]].contramap(_.nodes.toVector)
  implicit val properDynamicOpticDecoder: JsonDecoder[DynamicOptic] = JsonDecoder[Vector[DynamicOptic.Node]].map(nodes => DynamicOptic(nodes))


  // SchemaExpr Codec (Simplified for pure data schema expressions)
  implicit val schemaExprEncoder: JsonEncoder[SchemaExpr[Any, _]] = JsonEncoder[Json].contramap {
    case SchemaExpr.Constant(v) => Json.Obj("type" -> Json.Str("Constant"), "value" -> Json.Str(v.toString)) // Simplified
    case SchemaExpr.DefaultValue() => Json.Obj("type" -> Json.Str("DefaultValue"))
    case SchemaExpr.ToUpperCase() => Json.Obj("type" -> Json.Str("ToUpperCase"))
    case SchemaExpr.ToLowerCase() => Json.Obj("type" -> Json.Str("ToLowerCase"))
    // ... other cases
    case _ => Json.Obj("type" -> Json.Str("UnknownExpr"))
  }
  
  implicit val schemaExprDecoder: JsonDecoder[SchemaExpr[Any, _]] = JsonDecoder[Json].mapOrFail { json =>
    val typ = json.asObject.flatMap(_.get("type")).flatMap(_.asString).getOrElse("Unknown")
    typ match {
      case "DefaultValue" => Right(SchemaExpr.DefaultValue())
      case "ToUpperCase" => Right(SchemaExpr.ToUpperCase().asInstanceOf[SchemaExpr[Any, _]])
      case "ToLowerCase" => Right(SchemaExpr.ToLowerCase().asInstanceOf[SchemaExpr[Any, _]])
      // Constants require DynamicValue support for full fidelity
      case _ => Left(s"Unsupported SchemaExpr type: $typ")
    }
  }

  // MigrationAction Codec
  implicit val migrationActionEncoder: JsonEncoder[MigrationAction] = JsonEncoder[Json].contramap { action =>
     val (tpe, data) = action match {
       case MigrationAction.AddField(at, defVal) => 
         ("AddField", Json.Obj("at" -> at.toJsonAST.getOrElse(Json.Null), "default" -> defVal.toJsonAST.getOrElse(Json.Null)))
       case MigrationAction.DropField(at, _) => 
         ("DropField", Json.Obj("at" -> at.toJsonAST.getOrElse(Json.Null)))
       case MigrationAction.RenameField(at, newName) =>
         ("RenameField", Json.Obj("at" -> at.toJsonAST.getOrElse(Json.Null), "newName" -> Json.Str(newName)))
       // ... others
       case _ => ("Unknown", Json.Obj())
     }
     Json.Obj("type" -> Json.Str(tpe), "data" -> data)
  }
  
  implicit val migrationActionDecoder: JsonDecoder[MigrationAction] = JsonDecoder[Json].mapOrFail { json =>
     for {
       obj <- json.asObject.toRight("Not an object")
       tpe <- obj.get("type").flatMap(_.asString).toRight("Missing type")
       data <- obj.get("data").flatMap(_.asObject).toRight("Missing data")
       // Decoding logic here (requires extracting fields from data)
       // This is a partial implementation due to complexity constraints in this prompt
       res <- tpe match {
         case "DropField" => 
           data.get("at").flatMap(_.as[DynamicOptic].toOption).toRight("Invalid 'at'").map(at => MigrationAction.DropField(at, SchemaExpr.DefaultValue()))
         case _ => Left(s"Unsupported action: $tpe")
       }
     } yield res
  }

  implicit val dynamicMigrationEncoder: JsonEncoder[DynamicMigration] = JsonEncoder[Vector[MigrationAction]].contramap(_.actions)
  implicit val dynamicMigrationDecoder: JsonDecoder[DynamicMigration] = JsonDecoder[Vector[MigrationAction]].map(DynamicMigration(_))
  
}
