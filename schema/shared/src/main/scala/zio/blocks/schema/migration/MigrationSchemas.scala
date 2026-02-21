package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

/**
 * Hand-written `Schema` instances for migration types, enabling serialization
 * and self-description of migrations.
 *
 * `MigrationMetadata` and `DynamicMigration` are fully serializable.
 * `MigrationAction` is serialized structurally — action type, paths, and lossy
 * flag are preserved. `SchemaExpr` fields within actions are serialized as
 * their DynamicValue representation when possible (Literal values) or as opaque
 * markers when not (closures).
 */
object MigrationSchemas {

  // ── MigrationMetadata ──────────────────────────────────────────────

  implicit lazy val migrationMetadataSchema: Schema[MigrationMetadata] =
    new Schema(
      reflect = new Reflect.Record[Binding, MigrationMetadata](
        fields = Vector(
          Schema[Option[String]].reflect.asTerm[MigrationMetadata]("id"),
          Schema[Option[String]].reflect.asTerm[MigrationMetadata]("description"),
          Schema[Option[Long]].reflect.asTerm[MigrationMetadata]("timestamp"),
          Schema[Option[String]].reflect.asTerm[MigrationMetadata]("createdBy"),
          Schema[Option[String]].reflect.asTerm[MigrationMetadata]("fingerprint")
        ),
        typeId = TypeId.of[MigrationMetadata],
        recordBinding = new Binding.Record(
          constructor = new Constructor[MigrationMetadata] {
            def usedRegisters: RegisterOffset = RegisterOffset(objects = 5)

            def construct(in: Registers, offset: RegisterOffset): MigrationMetadata =
              MigrationMetadata(
                id = in.getObject(offset + RegisterOffset(objects = 0)).asInstanceOf[Option[String]],
                description = in.getObject(offset + RegisterOffset(objects = 1)).asInstanceOf[Option[String]],
                timestamp = in.getObject(offset + RegisterOffset(objects = 2)).asInstanceOf[Option[Long]],
                createdBy = in.getObject(offset + RegisterOffset(objects = 3)).asInstanceOf[Option[String]],
                fingerprint = in.getObject(offset + RegisterOffset(objects = 4)).asInstanceOf[Option[String]]
              )
          },
          deconstructor = new Deconstructor[MigrationMetadata] {
            def usedRegisters: RegisterOffset = RegisterOffset(objects = 5)

            def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationMetadata): Unit = {
              out.setObject(offset + RegisterOffset(objects = 0), in.id)
              out.setObject(offset + RegisterOffset(objects = 1), in.description)
              out.setObject(offset + RegisterOffset(objects = 2), in.timestamp)
              out.setObject(offset + RegisterOffset(objects = 3), in.createdBy)
              out.setObject(offset + RegisterOffset(objects = 4), in.fingerprint)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

  // ── MigrationError ─────────────────────────────────────────────────

  /**
   * Schema for MigrationError — captures message and path; omits cause/action
   * for simplicity.
   */
  implicit lazy val migrationErrorSchema: Schema[MigrationError] = {
    implicit val dynamicOpticSchema: Schema[DynamicOptic] = DynamicOptic.schema
    new Schema(
      reflect = new Reflect.Record[Binding, MigrationError](
        fields = Vector(
          Schema[String].reflect.asTerm[MigrationError]("message"),
          dynamicOpticSchema.reflect.asTerm[MigrationError]("path"),
          Schema[Option[Int]].reflect.asTerm[MigrationError]("actionIndex"),
          Schema[Option[String]].reflect.asTerm[MigrationError]("actualShape"),
          Schema[Option[String]].reflect.asTerm[MigrationError]("expectedShape"),
          Schema[Option[String]].reflect.asTerm[MigrationError]("inputSlice")
        ),
        typeId = TypeId.of[MigrationError],
        recordBinding = new Binding.Record(
          constructor = new Constructor[MigrationError] {
            def usedRegisters: RegisterOffset = RegisterOffset(objects = 6)

            def construct(in: Registers, offset: RegisterOffset): MigrationError =
              MigrationError(
                message = in.getObject(offset + RegisterOffset(objects = 0)).asInstanceOf[String],
                path = in.getObject(offset + RegisterOffset(objects = 1)).asInstanceOf[DynamicOptic],
                actionIndex = in.getObject(offset + RegisterOffset(objects = 2)).asInstanceOf[Option[Int]],
                actualShape = in.getObject(offset + RegisterOffset(objects = 4)).asInstanceOf[Option[String]],
                expectedShape = in.getObject(offset + RegisterOffset(objects = 5)).asInstanceOf[Option[String]],
                inputSlice = in.getObject(offset + RegisterOffset(objects = 3)).asInstanceOf[Option[String]]
              )
          },
          deconstructor = new Deconstructor[MigrationError] {
            def usedRegisters: RegisterOffset = RegisterOffset(objects = 6)

            def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationError): Unit = {
              out.setObject(offset + RegisterOffset(objects = 0), in.message)
              out.setObject(offset + RegisterOffset(objects = 1), in.path)
              out.setObject(offset + RegisterOffset(objects = 2), in.actionIndex)
              out.setObject(offset + RegisterOffset(objects = 3), in.inputSlice)
              out.setObject(offset + RegisterOffset(objects = 4), in.actualShape)
              out.setObject(offset + RegisterOffset(objects = 5), in.expectedShape)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  // ── MigrationAction ────────────────────────────────────────────────

  /**
   * Schema for `MigrationAction` using DynamicValue transform.
   *
   * Actions are serialized as DynamicValue.Record with:
   *   - "type": String (action class name)
   *   - "at": DynamicValue (the DynamicOptic path)
   *   - "lossy": Boolean
   *   - action-specific fields (newName for Rename, caseName for RenameCase,
   *     etc.)
   *
   * `SchemaExpr` fields are serialized as opaque DynamicValue.Null markers
   * since SchemaExpr contains closures that cannot be serialized generically.
   * For Literal expressions, the contained DynamicValue is preserved.
   */
  implicit lazy val migrationActionSchema: Schema[MigrationAction] = {
    implicit val dynamicOpticSchema: Schema[DynamicOptic] = DynamicOptic.schema
    val opticSchema                                       = dynamicOpticSchema

    Schema[DynamicValue].transform(
      // fromDynamic: DynamicValue → MigrationAction
      dv =>
        dv match {
          case DynamicValue.Record(fields) =>
            val fieldMap = fields.iterator.map(kv => (kv._1, kv._2)).toMap
            val typeName = fieldMap
              .get("type")
              .flatMap {
                case DynamicValue.Primitive(PrimitiveValue.String(s)) => Some(s)
                case _                                                => None
              }
              .getOrElse(throw SchemaError.missingField(Nil, "type"))

            def readOptic(name: String): DynamicOptic =
              fieldMap
                .get(name)
                .flatMap(opticSchema.fromDynamicValue(_).toOption)
                .getOrElse(throw SchemaError.missingField(Nil, name))

            def readString(name: String): String =
              fieldMap
                .get(name)
                .flatMap {
                  case DynamicValue.Primitive(PrimitiveValue.String(s)) => Some(s)
                  case _                                                => None
                }
                .getOrElse(throw SchemaError.missingField(Nil, name))

            typeName match {
              case "Rename" =>
                MigrationAction.Rename(readOptic("at"), readString("newName"))
              case "DropField" =>
                MigrationAction.DropField(readOptic("at"), reverseDefault = None)
              case "AddField" =>
                // AddField requires a SchemaExpr — use a placeholder Literal with DynamicValue
                val defaultDv = fieldMap.getOrElse("defaultValue", DynamicValue.Null)
                val expr      = SchemaExpr.Literal[Any, Any](
                  defaultDv,
                  Schema[DynamicValue].asInstanceOf[Schema[Any]]
                )
                MigrationAction.AddField(readOptic("at"), expr)
              case "Optionalize" =>
                MigrationAction.Optionalize(readOptic("at"))
              case "Mandate" =>
                val defaultDv = fieldMap.getOrElse("defaultValue", DynamicValue.Null)
                val expr      = SchemaExpr.Literal[Any, Any](
                  defaultDv,
                  Schema[DynamicValue].asInstanceOf[Schema[Any]]
                )
                MigrationAction.Mandate(readOptic("at"), expr)
              case "RenameCase" =>
                MigrationAction.RenameCase(readOptic("at"), readString("fromName"), readString("toName"))
              case "TransformValue" | "ChangeType" | "TransformElements" | "TransformKeys" | "TransformValues" |
                  "Join" | "Split" | "TransformCase" =>
                // These actions contain SchemaExpr closures; deserialization is limited
                throw SchemaError.conversionFailed(
                  Nil,
                  s"Cannot deserialize $typeName action: contains SchemaExpr closures"
                )
              case other =>
                throw SchemaError.conversionFailed(Nil, s"Unknown MigrationAction type: $other")
            }
          case _ =>
            throw SchemaError.expectationMismatch(Nil, "Expected a Record for MigrationAction")
        },
      // toDynamic: MigrationAction → DynamicValue
      { action =>
        import zio.blocks.chunk.Chunk

        def opticDv(optic: DynamicOptic): DynamicValue =
          opticSchema.toDynamicValue(optic)

        def strDv(s: String): DynamicValue =
          DynamicValue.Primitive(PrimitiveValue.String(s))

        def boolDv(b: Boolean): DynamicValue =
          DynamicValue.Primitive(PrimitiveValue.Boolean(b))

        action match {
          case MigrationAction.Rename(at, newName) =>
            DynamicValue.Record(
              Chunk(
                ("type", strDv("Rename")),
                ("at", opticDv(at)),
                ("newName", strDv(newName)),
                ("lossy", boolDv(false))
              )
            )
          case MigrationAction.DropField(at, _) =>
            DynamicValue.Record(
              Chunk(
                ("type", strDv("DropField")),
                ("at", opticDv(at)),
                ("lossy", boolDv(action.lossy))
              )
            )
          case MigrationAction.AddField(at, defaultExpr) =>
            val defaultDv = defaultExpr match {
              case lit: SchemaExpr.Literal[_, _] =>
                lit.schema.asInstanceOf[Schema[Any]].toDynamicValue(lit.value)
              case _ => DynamicValue.Null
            }
            DynamicValue.Record(
              Chunk(
                ("type", strDv("AddField")),
                ("at", opticDv(at)),
                ("defaultValue", defaultDv),
                ("lossy", boolDv(false))
              )
            )
          case MigrationAction.Optionalize(at) =>
            DynamicValue.Record(
              Chunk(
                ("type", strDv("Optionalize")),
                ("at", opticDv(at)),
                ("lossy", boolDv(false))
              )
            )
          case MigrationAction.Mandate(at, defaultExpr) =>
            val defaultDv = defaultExpr match {
              case lit: SchemaExpr.Literal[_, _] =>
                lit.schema.asInstanceOf[Schema[Any]].toDynamicValue(lit.value)
              case _ => DynamicValue.Null
            }
            DynamicValue.Record(
              Chunk(
                ("type", strDv("Mandate")),
                ("at", opticDv(at)),
                ("defaultValue", defaultDv),
                ("lossy", boolDv(false))
              )
            )
          case MigrationAction.RenameCase(at, fromName, toName) =>
            DynamicValue.Record(
              Chunk(
                ("type", strDv("RenameCase")),
                ("at", opticDv(at)),
                ("fromName", strDv(fromName)),
                ("toName", strDv(toName)),
                ("lossy", boolDv(false))
              )
            )
          case MigrationAction.TransformValue(at, _, _) =>
            DynamicValue.Record(
              Chunk(
                ("type", strDv("TransformValue")),
                ("at", opticDv(at)),
                ("lossy", boolDv(action.lossy))
              )
            )
          case MigrationAction.ChangeType(at, _, _) =>
            DynamicValue.Record(
              Chunk(
                ("type", strDv("ChangeType")),
                ("at", opticDv(at)),
                ("lossy", boolDv(action.lossy))
              )
            )
          case MigrationAction.TransformElements(at, _, _) =>
            DynamicValue.Record(
              Chunk(
                ("type", strDv("TransformElements")),
                ("at", opticDv(at)),
                ("lossy", boolDv(action.lossy))
              )
            )
          case MigrationAction.TransformKeys(at, _, _) =>
            DynamicValue.Record(
              Chunk(
                ("type", strDv("TransformKeys")),
                ("at", opticDv(at)),
                ("lossy", boolDv(action.lossy))
              )
            )
          case MigrationAction.TransformValues(at, _, _) =>
            DynamicValue.Record(
              Chunk(
                ("type", strDv("TransformValues")),
                ("at", opticDv(at)),
                ("lossy", boolDv(action.lossy))
              )
            )
          case MigrationAction.TransformCase(at, caseName, _) =>
            DynamicValue.Record(
              Chunk(
                ("type", strDv("TransformCase")),
                ("at", opticDv(at)),
                ("caseName", strDv(caseName)),
                ("lossy", boolDv(action.lossy))
              )
            )
          case MigrationAction.Join(at, sourcePaths, _, _, _) =>
            DynamicValue.Record(
              Chunk(
                ("type", strDv("Join")),
                ("at", opticDv(at)),
                (
                  "sourcePaths",
                  DynamicValue.Sequence(
                    zio.blocks.chunk.Chunk.fromIterable(sourcePaths.map(opticDv))
                  )
                ),
                ("lossy", boolDv(action.lossy))
              )
            )
          case MigrationAction.Split(at, targetPaths, _, _, _) =>
            DynamicValue.Record(
              Chunk(
                ("type", strDv("Split")),
                ("at", opticDv(at)),
                (
                  "targetPaths",
                  DynamicValue.Sequence(
                    zio.blocks.chunk.Chunk.fromIterable(targetPaths.map(opticDv))
                  )
                ),
                ("lossy", boolDv(action.lossy))
              )
            )
        }
      }
    )
  }

  // ── DynamicMigration ───────────────────────────────────────────────

  /** Schema for DynamicMigration using DynamicValue transform. */
  implicit lazy val dynamicMigrationSchema: Schema[DynamicMigration] = {
    val actionSchema = migrationActionSchema
    val metaSchema   = migrationMetadataSchema

    Schema[DynamicValue].transform(
      // fromDynamic
      dv =>
        dv match {
          case DynamicValue.Record(fields) =>
            val fieldMap = fields.iterator.map(kv => (kv._1, kv._2)).toMap
            val actions  = fieldMap.get("actions") match {
              case Some(DynamicValue.Sequence(elems)) =>
                elems.iterator.map { elem =>
                  actionSchema.fromDynamicValue(elem).fold(e => throw e, identity)
                }.toVector
              case None =>
                throw SchemaError.missingField(Nil, "actions")
              case Some(other) =>
                throw SchemaError.expectationMismatch(
                  Nil,
                  s"Expected Sequence for 'actions', got ${other.valueType}"
                )
            }
            val metadata = fieldMap
              .get("metadata")
              .flatMap(metaSchema.fromDynamicValue(_).toOption)
              .getOrElse(MigrationMetadata.empty)
            DynamicMigration(actions, metadata)
          case _ =>
            throw SchemaError.expectationMismatch(Nil, "Expected a Record for DynamicMigration")
        },
      // toDynamic
      { dm =>
        import zio.blocks.chunk.Chunk
        DynamicValue.Record(
          Chunk(
            (
              "actions",
              DynamicValue.Sequence(
                Chunk.fromIterable(dm.actions.map(actionSchema.toDynamicValue))
              )
            ),
            ("metadata", metaSchema.toDynamicValue(dm.metadata))
          )
        )
      }
    )
  }
}
