package zio.blocks.schema.migration

import zio.json._
import zio.json.ast.Json
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.migration.MigrationAction._

object DynamicMigrationCodec {

  // --- DynamicOptic Codecs ---
  
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
        case "AtIndex" => obj.get("index").flatMap(_.as[Int].toOption).toRight("Missing index").map(DynamicOptic.Node.AtIndex(_))
        case "Elements" => Right(DynamicOptic.Node.Elements)
        case "Wrapped" => Right(DynamicOptic.Node.Wrapped)
        case _ => Left(s"Unknown node type: $typ")
      }
    } yield res
  }

  implicit val dynamicOpticEncoder: JsonEncoder[DynamicOptic] = JsonEncoder[Vector[DynamicOptic.Node]].contramap(_.nodes.toVector)
  implicit val dynamicOpticDecoder: JsonDecoder[DynamicOptic] = JsonDecoder[Vector[DynamicOptic.Node]].map(nodes => DynamicOptic(nodes))


  // --- SchemaExpr Codec ---
  implicit val schemaExprEncoder: JsonEncoder[SchemaExpr[Any, _]] = JsonEncoder[Json].contramap {
    case SchemaExpr.Constant(v) => Json.Obj("type" -> Json.Str("Constant"), "value" -> Json.Str(v.toString)) 
    case SchemaExpr.DefaultValue() => Json.Obj("type" -> Json.Str("DefaultValue"))
    case SchemaExpr.ToUpperCase() => Json.Obj("type" -> Json.Str("ToUpperCase"))
    case SchemaExpr.ToLowerCase() => Json.Obj("type" -> Json.Str("ToLowerCase"))
    case _ => Json.Obj("type" -> Json.Str("UnknownExpr"))
  }
  
  implicit val schemaExprDecoder: JsonDecoder[SchemaExpr[Any, _]] = JsonDecoder[Json].mapOrFail { json =>
    val typ = json.asObject.flatMap(_.get("type")).flatMap(_.asString).getOrElse("Unknown")
    typ match {
      case "DefaultValue" => Right(SchemaExpr.DefaultValue())
      case "ToUpperCase" => Right(SchemaExpr.ToUpperCase().asInstanceOf[SchemaExpr[Any, _]])
      case "ToLowerCase" => Right(SchemaExpr.ToLowerCase().asInstanceOf[SchemaExpr[Any, _]])
      case "Constant" => 
        val valStr = json.asObject.flatMap(_.get("value")).flatMap(_.asString).getOrElse("")
        Right(SchemaExpr.Constant(valStr).asInstanceOf[SchemaExpr[Any, _]])
      case _ => Left(s"Unsupported SchemaExpr type: $typ")
    }
  }

  // --- MigrationAction Codec ---
  implicit val migrationActionEncoder: JsonEncoder[MigrationAction] = JsonEncoder[Json].contramap { action =>
     val (tpe, data) = action match {
       case AddField(at, defVal) => 
         ("AddField", Json.Obj("at" -> at.toJsonAST.getOrElse(Json.Null), "default" -> schemaExprEncoder.toJsonAST(defVal).getOrElse(Json.Null)))
       case DropField(at, defRev) => 
         ("DropField", Json.Obj("at" -> at.toJsonAST.getOrElse(Json.Null), "defaultForReverse" -> schemaExprEncoder.toJsonAST(defRev).getOrElse(Json.Null)))
       case RenameField(at, newName) =>
         ("RenameField", Json.Obj("at" -> at.toJsonAST.getOrElse(Json.Null), "newName" -> Json.Str(newName)))
       case TransformValue(at, t) =>
         ("TransformValue", Json.Obj("at" -> at.toJsonAST.getOrElse(Json.Null), "transform" -> schemaExprEncoder.toJsonAST(t).getOrElse(Json.Null)))
       case Mandate(at, defVal) =>
         ("Mandate", Json.Obj("at" -> at.toJsonAST.getOrElse(Json.Null), "default" -> schemaExprEncoder.toJsonAST(defVal).getOrElse(Json.Null)))
       case Optionalize(at) =>
         ("Optionalize", Json.Obj("at" -> at.toJsonAST.getOrElse(Json.Null)))
       case ChangeType(at, conv) =>
         ("ChangeType", Json.Obj("at" -> at.toJsonAST.getOrElse(Json.Null), "converter" -> schemaExprEncoder.toJsonAST(conv).getOrElse(Json.Null)))
       case Join(at, paths, comb) =>
          ("Join", Json.Obj("at" -> at.toJsonAST.getOrElse(Json.Null), "sourcePaths" -> paths.toJsonAST.getOrElse(Json.Null), "combiner" -> schemaExprEncoder.toJsonAST(comb).getOrElse(Json.Null)))
       case Split(at, paths, split) =>
          ("Split", Json.Obj("at" -> at.toJsonAST.getOrElse(Json.Null), "targetPaths" -> paths.toJsonAST.getOrElse(Json.Null), "splitter" -> schemaExprEncoder.toJsonAST(split).getOrElse(Json.Null)))
       case _ => ("Unknown", Json.Obj())
     }
     Json.Obj("type" -> Json.Str(tpe), "data" -> data)
  }
  
  implicit val migrationActionDecoder: JsonDecoder[MigrationAction] = JsonDecoder[Json].mapOrFail { json =>
     for {
       obj <- json.asObject.toRight("Not an object")
       tpe <- obj.get("type").flatMap(_.asString).toRight("Missing type")
       data <- obj.get("data").flatMap(_.asObject).toRight("Missing data")
       
       res <- tpe match {
         case "AddField" => 
           for {
             at <- data.get("at").flatMap(_.as[DynamicOptic].toOption).toRight("Invalid at")
             default <- data.get("default").flatMap(_.as[SchemaExpr[Any, _]].toOption).toRight("Invalid default")
           } yield AddField(at, default)
           
         case "DropField" => 
           for {
             at <- data.get("at").flatMap(_.as[DynamicOptic].toOption).toRight("Invalid at")
             default <- data.get("defaultForReverse").flatMap(_.as[SchemaExpr[Any, _]].toOption).toRight("Invalid defaultForReverse")
           } yield DropField(at, default)
           
         case "RenameField" =>
            for {
              at <- data.get("at").flatMap(_.as[DynamicOptic].toOption).toRight("Invalid at")
              newName <- data.get("newName").flatMap(_.asString).toRight("Invalid newName")
            } yield RenameField(at, newName)
            
         case "TransformValue" =>
            for {
              at <- data.get("at").flatMap(_.as[DynamicOptic].toOption).toRight("Invalid at")
              tr <- data.get("transform").flatMap(_.as[SchemaExpr[Any, _]].toOption).toRight("Invalid transform")
            } yield TransformValue(at, tr)
            
         case "Mandate" =>
             for {
               at <- data.get("at").flatMap(_.as[DynamicOptic].toOption).toRight("Invalid at")
               defVal <- data.get("default").flatMap(_.as[SchemaExpr[Any, _]].toOption).toRight("Invalid default")
             } yield Mandate(at, defVal)
             
         case "Optionalize" =>
             data.get("at").flatMap(_.as[DynamicOptic].toOption).toRight("Invalid at").map(Optionalize(_))
             
         case "ChangeType" =>
             for {
               at <- data.get("at").flatMap(_.as[DynamicOptic].toOption).toRight("Invalid at")
               conv <- data.get("converter").flatMap(_.as[SchemaExpr[Any, _]].toOption).toRight("Invalid converter")
             } yield ChangeType(at, conv)
         
         case "Join" =>
             for {
                at <- data.get("at").flatMap(_.as[DynamicOptic].toOption).toRight("Invalid at")
                paths <- data.get("sourcePaths").flatMap(_.as[Vector[DynamicOptic]].toOption).toRight("Invalid sourcePaths")
                comb <- data.get("combiner").flatMap(_.as[SchemaExpr[Any, _]].toOption).toRight("Invalid combiner")
             } yield Join(at, paths, comb)
             
         case "Split" =>
             for {
                at <- data.get("at").flatMap(_.as[DynamicOptic].toOption).toRight("Invalid at")
                paths <- data.get("targetPaths").flatMap(_.as[Vector[DynamicOptic]].toOption).toRight("Invalid targetPaths")
                split <- data.get("splitter").flatMap(_.as[SchemaExpr[Any, _]].toOption).toRight("Invalid splitter")
             } yield Split(at, paths, split)

         case _ => Left(s"Unsupported or unknown action type: $tpe")
       }
     } yield res
  }

  implicit val dynamicMigrationEncoder: JsonEncoder[DynamicMigration] = JsonEncoder[Vector[MigrationAction]].contramap(_.actions)
  implicit val dynamicMigrationDecoder: JsonDecoder[DynamicMigration] = JsonDecoder[Vector[MigrationAction]].map(DynamicMigration(_))
  
}
