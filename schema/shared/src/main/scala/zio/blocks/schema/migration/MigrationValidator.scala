package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * MigrationValidator provides logic to validate migrations against schemas.
 *
 * It uses a simplified representation of schema structure that can be derived
 * both at runtime (from Schema) and at compile-time (from macros).
 */
object MigrationValidator {

  sealed trait SchemaStructure
  object SchemaStructure {
    case class Record(fields: scala.collection.immutable.Map[String, SchemaStructure]) extends SchemaStructure
    case class Variant(cases: scala.collection.immutable.Map[String, SchemaStructure]) extends SchemaStructure
    case class Primitive(name: String)                                                 extends SchemaStructure
    case class Sequence(element: SchemaStructure)                                      extends SchemaStructure
    case class MapStructure(key: SchemaStructure, value: SchemaStructure)              extends SchemaStructure
    case object AnyValue                                                               extends SchemaStructure
  }

  /**
   * Derive SchemaStructure from a runtime Schema.
   */
  def fromSchema[A](schema: Schema[A]): SchemaStructure =
    fromReflect(schema.reflect)

  private def fromReflect[F[_, _], A](reflect: Reflect[F, A]): SchemaStructure =
    reflect match {
      case r: Reflect.Record[F, a] =>
        SchemaStructure.Record(r.fields.map(f => f.name -> fromReflect(f.value)).toMap)
      case v: Reflect.Variant[F, a] =>
        SchemaStructure.Variant(v.cases.map(c => c.name -> fromReflect(c.value)).toMap)
      case s: Reflect.Sequence[F, e, c] =>
        SchemaStructure.Sequence(fromReflect(s.element))
      case m: Reflect.Map[F, k, v, mm] =>
        SchemaStructure.MapStructure(fromReflect(m.key), fromReflect(m.value))
      case p: Reflect.Primitive[F, a] =>
        SchemaStructure.Primitive(p.primitiveType.toString)
      case _ => SchemaStructure.AnyValue
    }

  /**
   * Validate a migration against a starting structure and check if it results
   * in target structure.
   */
  def validate(
    start: SchemaStructure,
    target: SchemaStructure,
    actions: Seq[MigrationAction]
  ): Either[String, Unit] =
    actions
      .foldLeft[Either[String, SchemaStructure]](Right(start)) { (acc, action) =>
        acc.flatMap(current => applyAction(current, action))
      }
      .flatMap { result =>
        if (isCompatible(result, target)) Right(())
        else {
          val error = s"Migration resulted in incompatible structure.\nResult: $result\nExpected: $target"
          System.err.println(s"[VALIDATOR ERROR] $error")
          Left(error)
        }
      }

  private def applyAction(structure: SchemaStructure, action: MigrationAction): Either[String, SchemaStructure] =
    action match {
      case MigrationAction.Rename(at, newName) =>
        def renameInRecord(s: SchemaStructure): Either[String, SchemaStructure] = s match {
          case SchemaStructure.Record(fields) =>
            // Last node of 'at' should be the field we are renaming
            at.nodes.lastOption match {
              case Some(DynamicOptic.Node.Field(oldName)) =>
                fields.get(oldName) match {
                  case Some(fieldStructure) =>
                    Right(SchemaStructure.Record((fields - oldName) + (newName -> fieldStructure)))
                  case None => Left(s"Field '$oldName' not found for rename")
                }
              case _ => Left("Rename at must end with a Field node")
            }
          case _ => Left("Rename can only be applied to Record fields")
        }

        if (at.nodes.length == 1) renameInRecord(structure)
        else modifyAtPath(structure, DynamicOptic(at.nodes.dropRight(1)))(renameInRecord)

      case MigrationAction.DropField(at, _) =>
        def dropInRecord(s: SchemaStructure): Either[String, SchemaStructure] = s match {
          case SchemaStructure.Record(fields) =>
            at.nodes.lastOption match {
              case Some(DynamicOptic.Node.Field(fieldName)) =>
                Right(SchemaStructure.Record(fields - fieldName))
              case _ => Left("DropField at must end with a Field node")
            }
          case _ => Left("DropField can only be applied to Record fields")
        }

        if (at.nodes.length == 1) dropInRecord(structure)
        else modifyAtPath(structure, DynamicOptic(at.nodes.dropRight(1)))(dropInRecord)

      case MigrationAction.AddField(at, defaultValue) =>
        def addInRecord(s: SchemaStructure): Either[String, SchemaStructure] = s match {
          case SchemaStructure.Record(fields) =>
            at.nodes.lastOption match {
              case Some(DynamicOptic.Node.Field(fieldName)) =>
                // Using AnyValue for simplicity; could be refined by inspecting defaultValue
                Right(SchemaStructure.Record(fields + (fieldName -> SchemaStructure.AnyValue)))
              case _ => Left("AddField at must end with a Field node")
            }
          case _ => Left("AddField can only be applied to Record fields")
        }

        if (at.nodes.length == 1) addInRecord(structure)
        else modifyAtPath(structure, DynamicOptic(at.nodes.dropRight(1)))(addInRecord)

      case MigrationAction.RenameCase(at, oldName, newName) =>
        def renameInVariant(s: SchemaStructure): Either[String, SchemaStructure] = s match {
          case SchemaStructure.Variant(cases) =>
            cases.get(oldName) match {
              case Some(caseStructure) =>
                Right(SchemaStructure.Variant((cases - oldName) + (newName -> caseStructure)))
              case None => Left(s"Case '$oldName' not found for rename")
            }
          case _ => Left("RenameCase can only be applied to Variant structures")
        }

        if (at.nodes.isEmpty) renameInVariant(structure)
        else modifyAtPath(structure, at)(renameInVariant)

      case MigrationAction.RemoveCase(at, caseName) =>
        def removeInVariant(s: SchemaStructure): Either[String, SchemaStructure] = s match {
          case SchemaStructure.Variant(cases) =>
            Right(SchemaStructure.Variant(cases - caseName))
          case _ => Left("RemoveCase can only be applied to Variant structures")
        }

        if (at.nodes.isEmpty) removeInVariant(structure)
        else modifyAtPath(structure, at)(removeInVariant)

      case MigrationAction.TransformValue(at, _) =>
        modifyAtPath(structure, at)(s =>
          if (isPrimitive(s)) Right(SchemaStructure.Primitive("Unknown")) // Primitives transform to other primitives
          else Left(s"TransformValue can only be applied to primitives for this ticket, found: $s")
        )

      case MigrationAction.ChangeType(at, _) =>
        modifyAtPath(structure, at)(s =>
          if (isPrimitive(s)) Right(SchemaStructure.Primitive("Unknown"))
          else Left(s"ChangeType can only be applied to primitives for this ticket, found: $s")
        )

      case MigrationAction.Join(at, sources, _) =>
        // Join takes multiple sources and creates a new field
        // For this ticket, sources and target must be primitives
        def joinInRecord(s: SchemaStructure): Either[String, SchemaStructure] = s match {
          case SchemaStructure.Record(fields) =>
            at.nodes.lastOption match {
              case Some(DynamicOptic.Node.Field(fieldName)) =>
                Right(SchemaStructure.Record(fields + (fieldName -> SchemaStructure.Primitive("Unknown"))))
              case _ => Left("Join at must end with a Field node")
            }
          case _ => Left("Join can only be applied to Record fields")
        }

        if (at.nodes.length == 1) joinInRecord(structure)
        else modifyAtPath(structure, DynamicOptic(at.nodes.dropRight(1)))(joinInRecord)

      case MigrationAction.Split(at, targets, _) =>
        // Split takes one source and creates multiple target fields
        modifyAtPath(structure, at)(s =>
          if (isPrimitive(s)) Right(s) // The source field stays as a primitive (or could be dropped)
          else Left(s"Split source must be a primitive for this ticket, found: $s")
        ).flatMap { updatedStructure =>
          // Now add the target fields
          targets.foldLeft[Either[String, SchemaStructure]](Right(updatedStructure)) { (acc, targetPath) =>
            acc.flatMap { s =>
              def addTarget(inner: SchemaStructure): Either[String, SchemaStructure] = inner match {
                case SchemaStructure.Record(fields) =>
                  targetPath.nodes.lastOption match {
                    case Some(DynamicOptic.Node.Field(fieldName)) =>
                      Right(SchemaStructure.Record(fields + (fieldName -> SchemaStructure.Primitive("Unknown"))))
                    case _ => Left("Split target at must end with a Field node")
                  }
                case _ => Left("Split target can only be added to Record fields")
              }
              if (targetPath.nodes.length == 1) addTarget(s)
              else modifyAtPath(s, DynamicOptic(targetPath.nodes.dropRight(1)))(addTarget)
            }
          }
        }

      case MigrationAction.TransformElements(at, _) =>
        modifyAtPath(structure, at) {
          case SchemaStructure.Sequence(element) =>
            if (isPrimitive(element)) Right(SchemaStructure.Sequence(element))
            else Left("TransformElements can only be applied to primitive elements for this ticket")
          case _ => Left("TransformElements can only be applied to Sequence structures")
        }

      case MigrationAction.TransformKeys(at, _) =>
        modifyAtPath(structure, at) {
          case SchemaStructure.MapStructure(key, value) =>
            if (isPrimitive(key)) Right(SchemaStructure.MapStructure(key, value))
            else Left("TransformKeys can only be applied to primitive keys for this ticket")
          case _ => Left("TransformKeys can only be applied to Map structures")
        }

      case MigrationAction.TransformValues(at, _) =>
        modifyAtPath(structure, at) {
          case SchemaStructure.MapStructure(key, value) =>
            if (isPrimitive(value)) Right(SchemaStructure.MapStructure(key, value))
            else Left("TransformValues can only be applied to primitive values for this ticket")
          case _ => Left("TransformValues can only be applied to Map structures")
        }

      case MigrationAction.Optionalize(at, _) =>
        def optionalizeInRecord(s: SchemaStructure): Either[String, SchemaStructure] = s match {
          case SchemaStructure.Record(fields) =>
            at.nodes.lastOption match {
              case Some(DynamicOptic.Node.Field(fieldName)) =>
                fields.get(fieldName) match {
                  case Some(fieldStructure) =>
                    // Transform structure to Option representation (Variant with None and Some)
                    val optionStructure = SchemaStructure.Variant(
                      Map(
                        "None" -> SchemaStructure.Record(Map.empty),
                        "Some" -> SchemaStructure.Record(Map("value" -> fieldStructure))
                      )
                    )
                    Right(SchemaStructure.Record(fields + (fieldName -> optionStructure)))
                  case None => Left(s"Field '$fieldName' not found for optionalize")
                }
              case _ => Left("Optionalize at must end with a Field node")
            }
          case _ => Left("Optionalize can only be applied to Record fields")
        }

        if (at.nodes.length == 1) optionalizeInRecord(structure)
        else modifyAtPath(structure, DynamicOptic(at.nodes.dropRight(1)))(optionalizeInRecord)

      case _ => Right(structure)
    }

  private def isPrimitive(s: SchemaStructure): Boolean = s match {
    case SchemaStructure.Primitive(_) => true
    case SchemaStructure.AnyValue     => true // Wildcard
    case _                            => false
  }

  private def isCompatible(actual: SchemaStructure, expected: SchemaStructure): Boolean =
    (actual, expected) match {
      case (a, b) if a == b                                                     => true
      case (SchemaStructure.AnyValue, _)                                        => true
      case (_, SchemaStructure.AnyValue)                                        => true
      case (SchemaStructure.Primitive("Unknown"), SchemaStructure.Primitive(_)) => true
      case (SchemaStructure.Primitive(_), SchemaStructure.Primitive("Unknown")) => true
      case (SchemaStructure.Record(af), SchemaStructure.Record(ef))             =>
        ef.forall { case (name, estructure) =>
          af.get(name).exists(astructure => isCompatible(astructure, estructure))
        }
      case _ => false
    }

  private def modifyAtPath(
    structure: SchemaStructure,
    path: DynamicOptic
  )(f: SchemaStructure => Either[String, SchemaStructure]): Either[String, SchemaStructure] =
    if (path.nodes.isEmpty) f(structure)
    else {
      val node = path.nodes.head
      val tail = DynamicOptic(path.nodes.tail)
      node match {
        case DynamicOptic.Node.Field(name) =>
          structure match {
            case SchemaStructure.Record(fields) =>
              fields.get(name) match {
                case Some(inner) =>
                  modifyAtPath(inner, tail)(f).map(updated => SchemaStructure.Record(fields + (name -> updated)))
                case None =>
                  // Create missing records if needed (similar to how DynamicOptic.set now works)
                  modifyAtPath(SchemaStructure.Record(scala.collection.immutable.Map.empty), tail)(f).map(updated =>
                    SchemaStructure.Record(fields + (name -> updated))
                  )
              }
            case _ => Left(s"Expected record at $name")
          }
        case _ => Left(s"Optic node $node not yet supported in Validator simulation")
      }
    }
}
