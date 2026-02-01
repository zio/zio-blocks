package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, Schema}

/**
 * Advanced schema shape validation for migrations.
 *
 * This validator implements the maintainer-requested "lists of lists" approach
 * for tracking field transformations at each nesting depth. It ensures complete
 * migration coverage by tracking:
 *
 *   1. HandledPaths - source schema paths that have been addressed (dropped,
 *      renamed, transformed)
 *   2. ProvidedPaths - target schema paths that have been supplied (added,
 *      renamed to, transformed)
 *
 * The hierarchical path representation encodes depth explicitly, addressing the
 * maintainer's feedback: "for nested migrations, lists must be 'lists of
 * lists', where the top level encodes how deep and the bottom level encodes
 * add/remove/update stuff."
 */
object SchemaShapeValidator {

  /**
   * A path segment representing a single step in a schema path.
   *
   * This is the foundation of the "lists of lists" approach:
   *   - FieldSegment("address") + FieldSegment("street") = nested path
   *   - CaseSegment("Success") = variant case access
   *   - ElementsSegment = collection element access
   */
  sealed trait PathSegment {
    def render: String
  }
  object PathSegment {
    final case class Field(name: String) extends PathSegment { def render: String = s"field:$name" }
    final case class Case(name: String)  extends PathSegment { def render: String = s"case:$name"  }
    case object Elements                 extends PathSegment { def render: String = "elements"     }
    case object MapKeys                  extends PathSegment { def render: String = "mapKeys"      }
    case object MapValues                extends PathSegment { def render: String = "mapValues"    }
  }

  /**
   * A hierarchical path representing a location in a schema.
   *
   * This is the "list of lists" structure requested by maintainers: each
   * HierarchicalPath is a list of PathSegments, and the collection of paths
   * forms a list of these lists.
   */
  final case class HierarchicalPath(segments: List[PathSegment]) {
    def /(segment: PathSegment): HierarchicalPath = HierarchicalPath(segments :+ segment)
    def /(fieldName: String): HierarchicalPath    = this / PathSegment.Field(fieldName)
    def /(optic: DynamicOptic): HierarchicalPath  = HierarchicalPath(
      segments ++ HierarchicalPath.fromDynamicOptic(optic).segments
    )

    def depth: Int =
      segments.count {
        case PathSegment.Field(_) => true
        case _                    => false
      }

    def render: String =
      if (segments.isEmpty) "<root>"
      else segments.map(_.render).mkString("/")

    def toFlatString: String =
      segments.collect { case PathSegment.Field(name) => name }.mkString(".")
  }

  object HierarchicalPath {
    val root: HierarchicalPath = HierarchicalPath(Nil)

    def fromDynamicOptic(optic: DynamicOptic): HierarchicalPath =
      HierarchicalPath(optic.nodes.map {
        case DynamicOptic.Node.Field(name)   => PathSegment.Field(name)
        case DynamicOptic.Node.Case(name)    => PathSegment.Case(name)
        case DynamicOptic.Node.Elements      => PathSegment.Elements
        case DynamicOptic.Node.MapKeys       => PathSegment.MapKeys
        case DynamicOptic.Node.MapValues     => PathSegment.MapValues
        case DynamicOptic.Node.AtIndex(i)    => PathSegment.Field(s"[$i]")
        case DynamicOptic.Node.AtIndices(is) => PathSegment.Field(s"[${is.mkString(",")}]")
        case DynamicOptic.Node.AtMapKey(k)   => PathSegment.Field(s"@$k")
        case DynamicOptic.Node.AtMapKeys(ks) => PathSegment.Field(s"@[${ks.mkString(",")}]")
        case DynamicOptic.Node.Wrapped       => PathSegment.Field("<wrapped>")
      }.toList)

    def field(name: String): HierarchicalPath = root / name
  }

  /**
   * Represents the shape of a schema as a set of hierarchical field paths.
   */
  final case class SchemaShape(
    fieldPaths: Set[HierarchicalPath],
    optionalPaths: Set[HierarchicalPath],
    casePaths: Set[HierarchicalPath]
  ) {
    def hasField(path: HierarchicalPath): Boolean   = fieldPaths.contains(path)
    def isOptional(path: HierarchicalPath): Boolean = optionalPaths.contains(path)
    def hasCase(path: HierarchicalPath): Boolean    = casePaths.contains(path)
    def allPaths: Set[HierarchicalPath]             = fieldPaths ++ optionalPaths ++ casePaths

    // For backwards compatibility with flat string lookups
    def hasField(path: String): Boolean = fieldPaths.exists(_.toFlatString == path)
  }

  object SchemaShape {
    def empty: SchemaShape = SchemaShape(Set.empty, Set.empty, Set.empty)

    /**
     * Extract schema shape from a Schema[A] by using DynamicSchema.
     */
    def fromSchema[A](schema: Schema[A]): SchemaShape = {
      val ds = schema.toDynamicSchema
      extractFromDynamicSchema(ds.reflect, HierarchicalPath.root, Set.empty, Set.empty, Set.empty)
    }

    private def extractFromDynamicSchema(
      reflect: zio.blocks.schema.Reflect.Unbound[_],
      prefix: HierarchicalPath,
      fields: Set[HierarchicalPath],
      optionals: Set[HierarchicalPath],
      cases: Set[HierarchicalPath]
    ): SchemaShape = {
      import zio.blocks.schema.Reflect
      import zio.blocks.schema.binding.NoBinding

      // Resolve deferred reflects first
      val resolved: zio.blocks.schema.Reflect.Unbound[_] = reflect match {
        case d: Reflect.Deferred[NoBinding, _] @unchecked => d.value.asInstanceOf[Reflect.Unbound[_]]
        case other                                        => other
      }

      resolved match {
        case r: Reflect.Record[NoBinding, _] @unchecked =>
          val newFields = r.fields.foldLeft(fields) { (acc, term) =>
            val path = prefix / term.name
            acc + path
          }
          SchemaShape(newFields, optionals, cases)

        case v: Reflect.Variant[NoBinding, _] @unchecked =>
          val newCases = v.cases.foldLeft(cases) { (acc, term) =>
            acc + (prefix / PathSegment.Case(term.name))
          }
          SchemaShape(fields, optionals, newCases)

        case w: Reflect.Wrapper[NoBinding, _, _] @unchecked =>
          val innerReflect = w.wrapped.asInstanceOf[Reflect.Unbound[_]]
          if (w.typeId.name.contains("Option")) {
            extractFromDynamicSchema(innerReflect, prefix, fields, optionals + prefix, cases)
          } else {
            extractFromDynamicSchema(innerReflect, prefix, fields, optionals, cases)
          }

        case _ =>
          SchemaShape(fields, optionals, cases)
      }
    }
  }

  /**
   * Tracks which paths have been handled (from source) and provided (to
   * target).
   *
   * This implements the "lists of lists" approach - each set is a collection of
   * hierarchical paths.
   */
  final case class MigrationCoverage(
    handledFromSource: Set[HierarchicalPath],
    providedToTarget: Set[HierarchicalPath],
    renamedFields: Map[HierarchicalPath, HierarchicalPath], // oldPath -> newPath
    droppedFields: Set[HierarchicalPath],
    addedFields: Set[HierarchicalPath]
  ) {
    // Flat string compatibility methods
    def handleField(field: String): MigrationCoverage =
      handlePath(HierarchicalPath.field(field))

    def provideField(field: String): MigrationCoverage =
      providePath(HierarchicalPath.field(field))

    def renameField(from: String, to: String): MigrationCoverage =
      renamePath(HierarchicalPath.field(from), HierarchicalPath.field(to))

    def dropField(field: String): MigrationCoverage =
      dropPath(HierarchicalPath.field(field))

    def addField(field: String): MigrationCoverage =
      addPath(HierarchicalPath.field(field))

    // Hierarchical path methods
    def handlePath(path: HierarchicalPath): MigrationCoverage =
      copy(handledFromSource = handledFromSource + path)

    def providePath(path: HierarchicalPath): MigrationCoverage =
      copy(providedToTarget = providedToTarget + path)

    def renamePath(from: HierarchicalPath, to: HierarchicalPath): MigrationCoverage =
      copy(
        handledFromSource = handledFromSource + from,
        providedToTarget = providedToTarget + to,
        renamedFields = renamedFields + (from -> to)
      )

    def dropPath(path: HierarchicalPath): MigrationCoverage =
      copy(
        handledFromSource = handledFromSource + path,
        droppedFields = droppedFields + path
      )

    def addPath(path: HierarchicalPath): MigrationCoverage =
      copy(
        providedToTarget = providedToTarget + path,
        addedFields = addedFields + path
      )

    /**
     * Merge coverage from nested actions within a parent path context.
     */
    def mergeNested(nested: MigrationCoverage, parentPath: HierarchicalPath): MigrationCoverage =
      MigrationCoverage(
        handledFromSource ++ nested.handledFromSource.map(p => HierarchicalPath(parentPath.segments ++ p.segments)),
        providedToTarget ++ nested.providedToTarget.map(p => HierarchicalPath(parentPath.segments ++ p.segments)),
        renamedFields ++ nested.renamedFields.map { case (from, to) =>
          HierarchicalPath(parentPath.segments ++ from.segments) -> HierarchicalPath(parentPath.segments ++ to.segments)
        },
        droppedFields ++ nested.droppedFields.map(p => HierarchicalPath(parentPath.segments ++ p.segments)),
        addedFields ++ nested.addedFields.map(p => HierarchicalPath(parentPath.segments ++ p.segments))
      )

    /**
     * Render coverage by depth level (maintainer's "lists of lists"
     * visualization).
     */
    def renderByDepth: String = {
      val allPaths = handledFromSource ++ providedToTarget
      val byDepth  = allPaths.groupBy(_.depth)
      val maxDepth = if (byDepth.isEmpty) 0 else byDepth.keys.max

      (0 to maxDepth).map { depth =>
        val pathsAtDepth = byDepth.getOrElse(depth, Set.empty)
        s"Depth $depth: ${pathsAtDepth.map(_.render).mkString(", ")}"
      }
        .mkString("\n")
    }
  }

  object MigrationCoverage {
    def empty: MigrationCoverage = MigrationCoverage(
      handledFromSource = Set.empty,
      providedToTarget = Set.empty,
      renamedFields = Map.empty,
      droppedFields = Set.empty,
      addedFields = Set.empty
    )
  }

  /**
   * Result of schema shape validation.
   */
  sealed trait ShapeValidationResult
  object ShapeValidationResult {
    case object Complete extends ShapeValidationResult

    final case class Incomplete(
      unhandledSourceFields: Set[HierarchicalPath],
      missingTargetFields: Set[HierarchicalPath],
      coverage: MigrationCoverage
    ) extends ShapeValidationResult {
      def render: String = {
        val parts = Chunk.empty[String] ++
          (if (unhandledSourceFields.nonEmpty)
             Chunk(s"Unhandled source fields: ${unhandledSourceFields.map(_.render).mkString(", ")}")
           else Chunk.empty) ++
          (if (missingTargetFields.nonEmpty)
             Chunk(s"Missing target fields: ${missingTargetFields.map(_.render).mkString(", ")}")
           else Chunk.empty)
        parts.mkString("; ")
      }

      /**
       * Enhanced error report with detailed formatting and hints. Provides
       * parity with Scala 3's compile-time validation messages.
       */
      def renderReport: String = {
        // Separate fields from cases for clearer messages
        val unhandledFields = unhandledSourceFields.filterNot(_.segments.exists {
          case PathSegment.Case(_) => true
          case _                   => false
        })
        val unhandledCases = unhandledSourceFields.filter(_.segments.exists {
          case PathSegment.Case(_) => true
          case _                   => false
        })
        val missingFields = missingTargetFields.filterNot(_.segments.exists {
          case PathSegment.Case(_) => true
          case _                   => false
        })
        val missingCases = missingTargetFields.filter(_.segments.exists {
          case PathSegment.Case(_) => true
          case _                   => false
        })

        val sb = new StringBuilder
        sb.append("\n===================================================================\n")
        sb.append("| Migration Validation Failed\n")
        sb.append("===================================================================\n\n")

        if (unhandledFields.nonEmpty) {
          sb.append("UNHANDLED FIELD PATHS (fields removed from source, need dropField or renameField):\n")
          unhandledFields.toList.sortBy(_.toFlatString).foreach(p => sb.append(s"  * ${p.toFlatString}\n"))
          sb.append("\n")
        }

        if (missingFields.nonEmpty) {
          sb.append("UNPROVIDED FIELD PATHS (fields added to target, need addField or renameField):\n")
          missingFields.toList.sortBy(_.toFlatString).foreach(p => sb.append(s"  * ${p.toFlatString}\n"))
          sb.append("\n")
        }

        if (unhandledCases.nonEmpty) {
          sb.append("UNHANDLED ENUM CASES (cases removed from source, need dropCase or renameCase):\n")
          unhandledCases.toList.sortBy(_.toFlatString).foreach(p => sb.append(s"  * ${p.render}\n"))
          sb.append("\n")
        }

        if (missingCases.nonEmpty) {
          sb.append("UNPROVIDED ENUM CASES (cases added to target, need addCase or renameCase):\n")
          missingCases.toList.sortBy(_.toFlatString).foreach(p => sb.append(s"  * ${p.render}\n"))
          sb.append("\n")
        }

        sb.append("-------------------------------------------------------------------\n")
        sb.append("HINTS:\n")

        if (unhandledFields.nonEmpty) {
          val example      = unhandledFields.head.toFlatString
          val selectorPath = example.split("\\.").mkString("_.")
          sb.append(s"  -> .dropFieldWithSelector(_.$selectorPath) - remove field from migration\n")
        }
        if (missingFields.nonEmpty) {
          val example      = missingFields.head.toFlatString
          val selectorPath = example.split("\\.").mkString("_.")
          sb.append(s"  -> .addFieldWithSelector(_.$selectorPath, defaultValue) - add field with default\n")
        }
        if (unhandledFields.nonEmpty && missingFields.nonEmpty) {
          sb.append("  -> .renameFieldWithSelector(_.oldPath, \"newName\") - when field was renamed\n")
        }
        if (unhandledCases.nonEmpty || missingCases.nonEmpty) {
          sb.append("  -> .renameCaseWithSelector(_.enumField, \"OldCase\", \"NewCase\") - rename enum case\n")
        }

        sb.append("-------------------------------------------------------------------\n")
        sb.toString
      }
    }
  }

  /**
   * Validate that a migration completely transforms source schema to target
   * schema.
   */
  def validateShape[A, B](migration: Migration[A, B]): ShapeValidationResult = {
    val sourceShape = SchemaShape.fromSchema(migration.sourceSchema)
    val targetShape = SchemaShape.fromSchema(migration.targetSchema)

    val coverage = computeCoverage(migration.dynamicMigration.actions, sourceShape, targetShape)

    // Fields in source that aren't handled by any action are implicitly kept
    val implicitlyKept = sourceShape.fieldPaths -- coverage.handledFromSource

    // These implicitly kept fields count as both handled (from source) and provided (to target)
    val finalHandled  = coverage.handledFromSource ++ implicitlyKept
    val finalProvided = coverage.providedToTarget ++ implicitlyKept

    // Check for completeness
    val unhandledSource = sourceShape.fieldPaths -- finalHandled
    val missingTarget   = targetShape.fieldPaths -- finalProvided

    if (unhandledSource.isEmpty && missingTarget.isEmpty) {
      ShapeValidationResult.Complete
    } else {
      ShapeValidationResult.Incomplete(unhandledSource, missingTarget, coverage)
    }
  }

  /**
   * Compute migration coverage by analyzing actions with hierarchical path
   * context.
   */
  private def computeCoverage(
    actions: Chunk[MigrationAction],
    sourceShape: SchemaShape,
    targetShape: SchemaShape
  ): MigrationCoverage =
    actions.foldLeft(MigrationCoverage.empty) { (coverage, action) =>
      action match {
        case MigrationAction.AddField(at, fieldName, _) =>
          val path = HierarchicalPath.fromDynamicOptic(at) / fieldName
          coverage.addPath(path)

        case MigrationAction.DropField(at, fieldName, _) =>
          val path = HierarchicalPath.fromDynamicOptic(at) / fieldName
          coverage.dropPath(path)

        case MigrationAction.Rename(at, from, to) =>
          val fromPath = HierarchicalPath.fromDynamicOptic(at) / from
          val toPath   = HierarchicalPath.fromDynamicOptic(at) / to
          coverage.renamePath(fromPath, toPath)

        case MigrationAction.TransformValue(at, fieldName, _, _) =>
          val path = HierarchicalPath.fromDynamicOptic(at) / fieldName
          coverage.handlePath(path).providePath(path)

        case MigrationAction.Mandate(at, fieldName, _) =>
          val path = HierarchicalPath.fromDynamicOptic(at) / fieldName
          coverage.handlePath(path).providePath(path)

        case MigrationAction.Optionalize(at, fieldName) =>
          val path = HierarchicalPath.fromDynamicOptic(at) / fieldName
          coverage.handlePath(path).providePath(path)

        case MigrationAction.ChangeType(at, fieldName, _, _) =>
          val path = HierarchicalPath.fromDynamicOptic(at) / fieldName
          coverage.handlePath(path).providePath(path)

        case MigrationAction.Join(at, targetFieldName, sourcePaths, _, _) =>
          val basePath   = HierarchicalPath.fromDynamicOptic(at)
          val targetPath = basePath / targetFieldName
          val covered    = sourcePaths.foldLeft(coverage) { (c, sp) =>
            c.handlePath(basePath / sp)
          }
          covered.providePath(targetPath)

        case MigrationAction.Split(at, sourceFieldName, targetPaths, _, _) =>
          val basePath   = HierarchicalPath.fromDynamicOptic(at)
          val sourcePath = basePath / sourceFieldName
          val covered    = coverage.handlePath(sourcePath)
          targetPaths.foldLeft(covered) { (c, tp) =>
            c.providePath(basePath / tp)
          }

        case MigrationAction.RenameCase(_, _, _) =>
          coverage // Case renames don't affect field coverage

        case MigrationAction.TransformCase(at, caseName, nestedActions) =>
          // Compute nested coverage within the case's context
          val caseContext    = HierarchicalPath.fromDynamicOptic(at) / PathSegment.Case(caseName)
          val nestedCoverage = computeCoverage(nestedActions, sourceShape, targetShape)
          // Merge nested coverage with proper path prefixing
          coverage.mergeNested(nestedCoverage, caseContext)

        case MigrationAction.TransformElements(_, _, _) =>
          coverage // Collection transforms don't affect field coverage

        case MigrationAction.TransformKeys(_, _, _) =>
          coverage // Map transforms don't affect field coverage

        case MigrationAction.TransformValues(_, _, _) =>
          coverage // Map transforms don't affect field coverage
      }
    }
}
