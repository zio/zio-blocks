package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * Validates that migration actions will correctly transform source schema to target schema.
 *
 * The validator simulates migration actions on a structural representation of the source
 * schema and verifies the result matches the target schema structure.
 */
object MigrationValidator {

  /**
   * A structural representation of a schema for validation purposes.
   */
  sealed trait SchemaStructure {
    def fieldNames: Set[String]
  }

  object SchemaStructure {

    /**
     * A record structure with named fields.
     */
    final case class Record(
      name: String,
      fields: Map[String, SchemaStructure],
      isOptional: Map[String, Boolean]
    ) extends SchemaStructure {
      def fieldNames: Set[String] = fields.keySet
    }

    /**
     * A variant (sum type) structure with named cases.
     */
    final case class Variant(
      name: String,
      cases: Map[String, SchemaStructure]
    ) extends SchemaStructure {
      def fieldNames: Set[String] = cases.keySet
    }

    /**
     * A sequence structure.
     */
    final case class Sequence(element: SchemaStructure) extends SchemaStructure {
      def fieldNames: Set[String] = Set.empty
    }

    /**
     * A map structure.
     */
    final case class MapType(key: SchemaStructure, value: SchemaStructure) extends SchemaStructure {
      def fieldNames: Set[String] = Set.empty
    }

    /**
     * A primitive type.
     */
    final case class Primitive(typeName: String) extends SchemaStructure {
      def fieldNames: Set[String] = Set.empty
    }

    /**
     * An optional wrapper.
     */
    final case class Optional(inner: SchemaStructure) extends SchemaStructure {
      def fieldNames: Set[String] = Set.empty
    }

    /**
     * An unknown/dynamic structure.
     */
    case object Dynamic extends SchemaStructure {
      def fieldNames: Set[String] = Set.empty
    }
  }

  /**
   * Validation result.
   */
  sealed trait ValidationResult {
    def isValid: Boolean
    def errors: List[String]

    def ++(other: ValidationResult): ValidationResult = (this, other) match {
      case (Valid, Valid)           => Valid
      case (Valid, e: Invalid)      => e
      case (e: Invalid, Valid)      => e
      case (Invalid(e1), Invalid(e2)) => Invalid(e1 ++ e2)
    }
  }

  case object Valid extends ValidationResult {
    val isValid: Boolean     = true
    val errors: List[String] = Nil
  }

  final case class Invalid(errors: List[String]) extends ValidationResult {
    val isValid: Boolean = false
  }

  object Invalid {
    def apply(error: String): Invalid = Invalid(List(error))
  }

  /**
   * Extract the structural representation from a schema.
   */
  def extractStructure[A](schema: Schema[A]): SchemaStructure = {
    extractFromReflect(schema.reflect)
  }

  private def extractFromReflect[F[_, _], A](reflect: Reflect[F, A]): SchemaStructure = {
    // Check for Option types first (Options are encoded as Variants with Some/None)
    if (reflect.isOption) {
      reflect.optionInnerType match {
        case Some(inner) => SchemaStructure.Optional(extractFromReflect(inner))
        case None        => SchemaStructure.Optional(SchemaStructure.Dynamic)
      }
    } else {
      // Treat wrappers as transparent for validation purposes.
      reflect.asWrapperUnknown match {
        case Some(unknown) =>
          extractFromReflect(unknown.wrapper.wrapped.asInstanceOf[Reflect[F, Any]])
            .asInstanceOf[SchemaStructure]

        case None =>
          reflect match {
            case r: Reflect.Record[F, A @unchecked] =>
              val fields = r.fields.map { term =>
                term.name -> extractFromReflect(term.value)
              }.toMap
              val isOptional = r.fields.map { term =>
                term.name -> term.value.isOption
              }.toMap
              SchemaStructure.Record(r.typeId.name, fields, isOptional)

            case v: Reflect.Variant[F, A @unchecked] =>
              val cases = v.cases.map { term =>
                term.name -> extractFromReflect(term.value)
              }.toMap
              SchemaStructure.Variant(v.typeId.name, cases)

            case s: Reflect.Sequence[F @unchecked, _, _] =>
              SchemaStructure.Sequence(extractFromReflect(s.element))

            case m: Reflect.Map[F @unchecked, _, _, _] =>
              SchemaStructure.MapType(
                extractFromReflect(m.key),
                extractFromReflect(m.value)
              )

            case p: Reflect.Primitive[F, A @unchecked] =>
              val typeName = p.primitiveType.getClass.getSimpleName.stripSuffix("$")
              SchemaStructure.Primitive(typeName)

            case _: Reflect.Dynamic[F @unchecked] =>
              SchemaStructure.Dynamic

            case _ =>
              SchemaStructure.Dynamic
          }
      }
    }
  }

  /**
   * Validate that applying actions to source schema produces target schema structure.
   */
  def validate[A, B](
    sourceSchema: Schema[A],
    targetSchema: Schema[B],
    actions: Vector[MigrationAction]
  ): ValidationResult = {
    val source          = extractStructure(sourceSchema)
    val target          = extractStructure(targetSchema)
    val simulatedResult = simulateActions(source, actions)

    simulatedResult match {
      case Left(error) =>
        Invalid(error)
      case Right(result) =>
        compareStructures(result, target, DynamicOptic.root)
    }
  }

  /**
   * Simulate applying migration actions to a schema structure.
   */
  private def simulateActions(
    structure: SchemaStructure,
    actions: Vector[MigrationAction]
  ): Either[String, SchemaStructure] = {
    actions.foldLeft[Either[String, SchemaStructure]](Right(structure)) { (current, action) =>
      current.flatMap(s => simulateAction(s, action))
    }
  }

  /**
   * Simulate a single migration action.
   */
  private def simulateAction(
    structure: SchemaStructure,
    action: MigrationAction
  ): Either[String, SchemaStructure] = {
    action match {
      case MigrationAction.AddField(parentPath, fieldName, _) =>
        modifyAtPath(structure, parentPath) {
          case r: SchemaStructure.Record =>
            if (r.fields.contains(fieldName)) {
              Left(s"Cannot add field '$fieldName': already exists")
            } else {
              Right(r.copy(
                fields = r.fields + (fieldName -> SchemaStructure.Dynamic),
                isOptional = r.isOptional + (fieldName -> false)
              ))
            }
          case other =>
            Left(s"Cannot add field to non-record structure: ${describeStructure(other)}")
        }

      case MigrationAction.DropField(parentPath, fieldName, _) =>
        modifyAtPath(structure, parentPath) {
          case r: SchemaStructure.Record =>
            if (!r.fields.contains(fieldName)) {
              Left(s"Cannot drop field '$fieldName': does not exist")
            } else {
              Right(r.copy(
                fields = r.fields - fieldName,
                isOptional = r.isOptional - fieldName
              ))
            }
          case other =>
            Left(s"Cannot drop field from non-record structure: ${describeStructure(other)}")
        }

      case MigrationAction.RenameField(parentPath, fromName, toName) =>
        modifyAtPath(structure, parentPath) {
          case r: SchemaStructure.Record =>
            if (!r.fields.contains(fromName)) {
              Left(s"Cannot rename field '$fromName': does not exist")
            } else if (r.fields.contains(toName)) {
              Left(s"Cannot rename field to '$toName': already exists")
            } else {
              val fieldValue = r.fields(fromName)
              val isOpt      = r.isOptional.getOrElse(fromName, false)
              Right(r.copy(
                fields = (r.fields - fromName) + (toName -> fieldValue),
                isOptional = (r.isOptional - fromName) + (toName -> isOpt)
              ))
            }
          case other =>
            Left(s"Cannot rename field in non-record structure: ${describeStructure(other)}")
        }

      case MigrationAction.Mandate(path, _) =>
        modifyAtPath(structure, path.dropLastField._1) {
          case r: SchemaStructure.Record =>
            val fieldName = path.lastFieldName.getOrElse("")
            if (!r.fields.contains(fieldName)) {
              Left(s"Cannot mandate field '$fieldName': does not exist")
            } else {
              r.fields(fieldName) match {
                case SchemaStructure.Optional(inner) =>
                  Right(r.copy(
                    fields = r.fields + (fieldName -> inner),
                    isOptional = r.isOptional + (fieldName -> false)
                  ))
                case _ =>
                  Right(r.copy(isOptional = r.isOptional + (fieldName -> false)))
              }
            }
          case other =>
            Left(s"Cannot mandate field in non-record structure: ${describeStructure(other)}")
        }

      case MigrationAction.Optionalize(path) =>
        modifyAtPath(structure, path.dropLastField._1) {
          case r: SchemaStructure.Record =>
            val fieldName = path.lastFieldName.getOrElse("")
            if (!r.fields.contains(fieldName)) {
              Left(s"Cannot optionalize field '$fieldName': does not exist")
            } else {
              val wrapped = SchemaStructure.Optional(r.fields(fieldName))
              Right(r.copy(
                fields = r.fields + (fieldName -> wrapped),
                isOptional = r.isOptional + (fieldName -> true)
              ))
            }
          case other =>
            Left(s"Cannot optionalize field in non-record structure: ${describeStructure(other)}")
        }

      case MigrationAction.RenameCase(path, fromName, toName) =>
        modifyAtPath(structure, path) {
          case v: SchemaStructure.Variant =>
            if (!v.cases.contains(fromName)) {
              Left(s"Cannot rename case '$fromName': does not exist")
            } else if (v.cases.contains(toName)) {
              Left(s"Cannot rename case to '$toName': already exists")
            } else {
              val caseValue = v.cases(fromName)
              Right(v.copy(cases = (v.cases - fromName) + (toName -> caseValue)))
            }
          case other =>
            Left(s"Cannot rename case in non-variant structure: ${describeStructure(other)}")
        }

      // These actions don't change structure, just values
      case _: MigrationAction.TransformValue    => Right(structure)
      case _: MigrationAction.ChangeType        => Right(structure)
      case _: MigrationAction.TransformCase     => Right(structure)

      case MigrationAction.Join(targetPath, sourcePaths, _, _) =>
        // Join removes source fields and adds a target field
        val afterDrops = sourcePaths.foldLeft[Either[String, SchemaStructure]](Right(structure)) {
          (current, sourcePath) =>
            current.flatMap { s =>
              val (parentPath, fieldNameOpt) = sourcePath.dropLastField
              fieldNameOpt match {
                case Some(fieldName) =>
                  modifyAtPath(s, parentPath) {
                    case r: SchemaStructure.Record =>
                      if (!r.fields.contains(fieldName)) {
                        Left(s"Join source field '$fieldName' does not exist")
                      } else {
                        Right(r.copy(
                          fields = r.fields - fieldName,
                          isOptional = r.isOptional - fieldName
                        ))
                      }
                    case other =>
                      Left(s"Cannot drop join source from non-record: ${describeStructure(other)}")
                  }
                case None =>
                  Left("Join source path must end in a field")
              }
            }
        }
        // Add the target field
        afterDrops.flatMap { s =>
          val (parentPath, fieldNameOpt) = targetPath.dropLastField
          fieldNameOpt match {
            case Some(fieldName) =>
              modifyAtPath(s, parentPath) {
                case r: SchemaStructure.Record =>
                  Right(r.copy(
                    fields = r.fields + (fieldName -> SchemaStructure.Dynamic),
                    isOptional = r.isOptional + (fieldName -> false)
                  ))
                case other =>
                  Left(s"Cannot add join target to non-record: ${describeStructure(other)}")
              }
            case None =>
              Left("Join target path must end in a field")
          }
        }

      case MigrationAction.Split(sourcePath, targetPaths, _, _) =>
        // Split removes source field and adds target fields
        val (sourceParentPath, sourceFieldNameOpt) = sourcePath.dropLastField
        val afterDrop = sourceFieldNameOpt match {
          case Some(fieldName) =>
            modifyAtPath(structure, sourceParentPath) {
              case r: SchemaStructure.Record =>
                if (!r.fields.contains(fieldName)) {
                  Left(s"Split source field '$fieldName' does not exist")
                } else {
                  Right(r.copy(
                    fields = r.fields - fieldName,
                    isOptional = r.isOptional - fieldName
                  ))
                }
              case other =>
                Left(s"Cannot drop split source from non-record: ${describeStructure(other)}")
            }
          case None =>
            Left("Split source path must end in a field")
        }
        // Add all target fields
        afterDrop.flatMap { s =>
          targetPaths.foldLeft[Either[String, SchemaStructure]](Right(s)) { (current, targetPath) =>
            current.flatMap { struct =>
              val (parentPath, fieldNameOpt) = targetPath.dropLastField
              fieldNameOpt match {
                case Some(fieldName) =>
                  modifyAtPath(struct, parentPath) {
                    case r: SchemaStructure.Record =>
                      Right(r.copy(
                        fields = r.fields + (fieldName -> SchemaStructure.Dynamic),
                        isOptional = r.isOptional + (fieldName -> false)
                      ))
                    case other =>
                      Left(s"Cannot add split target to non-record: ${describeStructure(other)}")
                  }
                case None =>
                  Left("Split target path must end in a field")
              }
            }
          }
        }
      case _: MigrationAction.TransformElements => Right(structure)
      case _: MigrationAction.TransformKeys     => Right(structure)
      case _: MigrationAction.TransformValues   => Right(structure)
      case MigrationAction.Identity             => Right(structure)
    }
  }

  /**
   * Modify a structure at a given path.
   */
  private def modifyAtPath(
    structure: SchemaStructure,
    path: DynamicOptic
  )(
    modify: SchemaStructure => Either[String, SchemaStructure]
  ): Either[String, SchemaStructure] = {
    if (path.nodes.isEmpty) {
      modify(structure)
    } else {
      val nodes = path.nodes.toList
      modifyAtPathRecursive(structure, nodes, modify)
    }
  }

  private def modifyAtPathRecursive(
    structure: SchemaStructure,
    path: List[DynamicOptic.Node],
    modify: SchemaStructure => Either[String, SchemaStructure]
  ): Either[String, SchemaStructure] = {
    path match {
      case Nil =>
        modify(structure)

      case DynamicOptic.Node.Field(name) :: rest =>
        structure match {
          case r: SchemaStructure.Record =>
            r.fields.get(name) match {
              case Some(fieldStructure) =>
                modifyAtPathRecursive(fieldStructure, rest, modify).map { newField =>
                  r.copy(fields = r.fields + (name -> newField))
                }
              case None =>
                Left(s"Field '$name' not found in record")
            }
          case other =>
            Left(s"Cannot navigate field '$name' in non-record: ${describeStructure(other)}")
        }

      case DynamicOptic.Node.Case(name) :: rest =>
        structure match {
          case v: SchemaStructure.Variant =>
            v.cases.get(name) match {
              case Some(caseStructure) =>
                modifyAtPathRecursive(caseStructure, rest, modify).map { newCase =>
                  v.copy(cases = v.cases + (name -> newCase))
                }
              case None =>
                Left(s"Case '$name' not found in variant")
            }
          case other =>
            Left(s"Cannot navigate case '$name' in non-variant: ${describeStructure(other)}")
        }

      case (_: DynamicOptic.Node.AtIndex) :: rest =>
        structure match {
          case s: SchemaStructure.Sequence =>
            modifyAtPathRecursive(s.element, rest, modify).map { newElement =>
              s.copy(element = newElement)
            }
          case other =>
            Left(s"Cannot navigate index in non-sequence: ${describeStructure(other)}")
        }

      case (_: DynamicOptic.Node.AtIndices) :: rest =>
        structure match {
          case s: SchemaStructure.Sequence =>
            modifyAtPathRecursive(s.element, rest, modify).map { newElement =>
              s.copy(element = newElement)
            }
          case other =>
            Left(s"Cannot navigate indices in non-sequence: ${describeStructure(other)}")
        }

      case (_: DynamicOptic.Node.AtMapKey) :: rest =>
        structure match {
          case m: SchemaStructure.MapType =>
            modifyAtPathRecursive(m.value, rest, modify).map { newValue =>
              m.copy(value = newValue)
            }
          case other =>
            Left(s"Cannot navigate map value in non-map: ${describeStructure(other)}")
        }

      case (_: DynamicOptic.Node.AtMapKeys) :: rest =>
        structure match {
          case m: SchemaStructure.MapType =>
            modifyAtPathRecursive(m.value, rest, modify).map { newValue =>
              m.copy(value = newValue)
            }
          case other =>
            Left(s"Cannot navigate map values in non-map: ${describeStructure(other)}")
        }

      case DynamicOptic.Node.Elements :: rest =>
        structure match {
          case s: SchemaStructure.Sequence =>
            modifyAtPathRecursive(s.element, rest, modify).map { newElement =>
              s.copy(element = newElement)
            }
          case other =>
            Left(s"Cannot navigate elements in non-sequence: ${describeStructure(other)}")
        }

      case DynamicOptic.Node.Wrapped :: rest =>
        // Wrappers are treated as transparent for validation purposes
        modifyAtPathRecursive(structure, rest, modify)

      case DynamicOptic.Node.MapKeys :: rest =>
        structure match {
          case m: SchemaStructure.MapType =>
            modifyAtPathRecursive(m.key, rest, modify).map { newKey =>
              m.copy(key = newKey)
            }
          case other =>
            Left(s"Cannot navigate map keys in non-map: ${describeStructure(other)}")
        }

      case DynamicOptic.Node.MapValues :: rest =>
        structure match {
          case m: SchemaStructure.MapType =>
            modifyAtPathRecursive(m.value, rest, modify).map { newValue =>
              m.copy(value = newValue)
            }
          case other =>
            Left(s"Cannot navigate map values in non-map: ${describeStructure(other)}")
        }
    }
  }

  /**
   * Compare two structures for compatibility.
   */
  private def compareStructures(
    actual: SchemaStructure,
    expected: SchemaStructure,
    path: DynamicOptic
  ): ValidationResult = {
    (actual, expected) match {
      case (SchemaStructure.Dynamic, _) =>
        // Dynamic matches anything
        Valid

      case (_, SchemaStructure.Dynamic) =>
        // Dynamic matches anything
        Valid

      case (a: SchemaStructure.Record, e: SchemaStructure.Record) =>
        val missingFields = e.fields.keySet -- a.fields.keySet
        val extraFields   = a.fields.keySet -- e.fields.keySet

        var result: ValidationResult = Valid

        if (missingFields.nonEmpty) {
          result = result ++ Invalid(s"At ${path.toString}: Missing fields: ${missingFields.mkString(", ")}")
        }

        if (extraFields.nonEmpty) {
          result = result ++ Invalid(s"At ${path.toString}: Unexpected fields: ${extraFields.mkString(", ")}")
        }

        // Compare common fields
        val commonFields = a.fields.keySet.intersect(e.fields.keySet)
        commonFields.foreach { fieldName =>
          val fieldPath = path.field(fieldName)
          val actualField   = a.fields(fieldName)
          val expectedField = e.fields(fieldName)

          // When either side is Dynamic, structural comparison is intentionally permissive.
          // In those cases, optionality still needs to match to avoid invalid Mandate/Optionalize migrations.
          if (actualField == SchemaStructure.Dynamic || expectedField == SchemaStructure.Dynamic) {
            val actualOpt   = a.isOptional.getOrElse(fieldName, false)
            val expectedOpt = e.isOptional.getOrElse(fieldName, false)
            if (actualOpt != expectedOpt) {
              val expectedLabel = if (expectedOpt) "optional" else "mandatory"
              val actualLabel   = if (actualOpt) "optional" else "mandatory"
              result = result ++ Invalid(s"At ${fieldPath.toString}: Optionality mismatch: expected $expectedLabel, got $actualLabel")
            }
          }

          result = result ++ compareStructures(actualField, expectedField, fieldPath)
        }

        result

      case (a: SchemaStructure.Variant, e: SchemaStructure.Variant) =>
        val missingCases = e.cases.keySet -- a.cases.keySet
        val extraCases   = a.cases.keySet -- e.cases.keySet

        var result: ValidationResult = Valid

        if (missingCases.nonEmpty) {
          result = result ++ Invalid(s"At ${path.toString}: Missing cases: ${missingCases.mkString(", ")}")
        }

        if (extraCases.nonEmpty) {
          result = result ++ Invalid(s"At ${path.toString}: Unexpected cases: ${extraCases.mkString(", ")}")
        }

        // Compare common cases
        val commonCases = a.cases.keySet.intersect(e.cases.keySet)
        commonCases.foreach { caseName =>
          val casePath = path.caseOf(caseName)
          result = result ++ compareStructures(a.cases(caseName), e.cases(caseName), casePath)
        }

        result

      case (a: SchemaStructure.Sequence, e: SchemaStructure.Sequence) =>
        compareStructures(a.element, e.element, new DynamicOptic(path.nodes :+ DynamicOptic.Node.Elements))

      case (a: SchemaStructure.MapType, e: SchemaStructure.MapType) =>
        compareStructures(a.key, e.key, new DynamicOptic(path.nodes :+ DynamicOptic.Node.MapKeys)) ++
          compareStructures(a.value, e.value, new DynamicOptic(path.nodes :+ DynamicOptic.Node.MapValues))

      case (SchemaStructure.Optional(a), SchemaStructure.Optional(e)) =>
        compareStructures(a, e, path)

      case (a: SchemaStructure.Primitive, e: SchemaStructure.Primitive) =>
        if (a.typeName == e.typeName) Valid
        else Invalid(s"At ${path.toString}: Type mismatch: expected ${e.typeName}, got ${a.typeName}")

      case (a, e) =>
        Invalid(s"At ${path.toString}: Structure mismatch: expected ${describeStructure(e)}, got ${describeStructure(a)}")
    }
  }

  private def describeStructure(s: SchemaStructure): String = s match {
    case r: SchemaStructure.Record   => s"Record(${r.name})"
    case v: SchemaStructure.Variant  => s"Variant(${v.name})"
    case _: SchemaStructure.Sequence => "Sequence"
    case _: SchemaStructure.MapType  => "Map"
    case p: SchemaStructure.Primitive => p.typeName
    case _: SchemaStructure.Optional => "Optional"
    case SchemaStructure.Dynamic     => "Dynamic"
  }

  /**
   * Extension methods for DynamicOptic to help with validation.
   */
  implicit class DynamicOpticOps(private val optic: DynamicOptic) extends AnyVal {
    def dropLastField: (DynamicOptic, Option[String]) = {
      if (optic.nodes.isEmpty) {
        (optic, None)
      } else {
        optic.nodes.last match {
          case DynamicOptic.Node.Field(name) =>
            (new DynamicOptic(optic.nodes.dropRight(1)), Some(name))
          case _ =>
            (optic, None)
        }
      }
    }

    def lastFieldName: Option[String] = {
      if (optic.nodes.isEmpty) None
      else optic.nodes.last match {
        case DynamicOptic.Node.Field(name) => Some(name)
        case _                             => None
      }
    }
  }
}
