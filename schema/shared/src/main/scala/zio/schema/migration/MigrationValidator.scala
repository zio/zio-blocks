package zio.schema.migration

import zio.blocks.schema._
// import zio.blocks.schema.binding.Binding removed

object MigrationValidator {

  def validate(source: Schema[_], target: Schema[_], migration: DynamicMigration): Either[String, Unit] = {
    // 1. Snapshot structure of Source and Target as Maps of Path -> String (Type summary or simplified)
    val sourcePaths = extractPaths(source.reflect)
    val targetPaths = extractPaths(target.reflect)

    // 2. Simulate actions on SourcePaths
    migration.actions.foldLeft[Either[String, Map[Vector[String], String]]](Right(sourcePaths)) { (acc, action) =>
      acc.flatMap(paths => simulateAction(paths, action))
    } match {
      case Left(error)        => Left(error)
      case Right(resultPaths) =>
        // 3. Compare Result vs Target
        // We compare key sets (structural equality) and simplified value types if possible
        val missing = targetPaths.keySet -- resultPaths.keySet
        val extra   = resultPaths.keySet -- targetPaths.keySet

        if (missing.isEmpty && extra.isEmpty) Right(())
        else {
          val errorMsg = new StringBuilder("Migration result does not match target schema.\n")
          if (missing.nonEmpty) errorMsg.append(s"Missing fields: ${missing.map(_.mkString(".")).mkString(", ")}\n")
          if (extra.nonEmpty) errorMsg.append(s"Extra fields: ${extra.map(_.mkString(".")).mkString(", ")}\n")
          Left(errorMsg.toString())
        }
    }
  }

  // Type alias for simplified structure: Path (Vector[String]) -> Type Description (String)
  type PathMap = Map[Vector[String], String]

  private def extractPaths(reflect: Reflect.Bound[_]): PathMap = {
    def go(r: Reflect.Bound[_], currentPath: Vector[String]): PathMap = r match {
      case r: Reflect.Record[zio.blocks.schema.binding.Binding @unchecked, _] =>
        r.fields.zipWithIndex.foldLeft(Map.empty[Vector[String], String]) { case (acc, (field, _)) =>
          acc ++ go(field.value.asInstanceOf[Reflect.Bound[_]], currentPath :+ field.name)
        }
      case r: Reflect.Primitive[zio.blocks.schema.binding.Binding @unchecked, _] =>
        Map(currentPath -> r.typeName.name) // Leaf
      case r: Reflect.Sequence[zio.blocks.schema.binding.Binding @unchecked, _, _] =>
        Map(currentPath -> "Seq")
      case _ => Map(currentPath -> "Unknown")
    }
    go(reflect, Vector.empty)
  }

  private def simulateAction(paths: PathMap, action: MigrationAction): Either[String, PathMap] = action match {
    case MigrationAction.AddField(at, _) =>
      val path = at.nodes.collect { case zio.blocks.schema.DynamicOptic.Node.Field(n) => n }.toVector
      if (paths.contains(path)) Left(s"Field already exists: ${path.mkString(".")}")
      else Right(paths + (path -> "Any")) // We don't verify type of default yet

    case MigrationAction.DropField(at, _) =>
      val path = at.nodes.collect { case zio.blocks.schema.DynamicOptic.Node.Field(n) => n }.toVector
      if (paths.contains(path)) Right(paths - path)
      else Left(s"Cannot drop missing field: ${path.mkString(".")}")

    case MigrationAction.Rename(at, to) =>
      val oldPath = at.nodes.collect { case zio.blocks.schema.DynamicOptic.Node.Field(n) => n }.toVector
      if (paths.contains(oldPath)) {
        val newPath  = oldPath.init :+ to
        val typeInfo = paths(oldPath)
        if (paths.contains(newPath)) Left(s"Rename target already exists: ${newPath.mkString(".")}")
        else Right(paths - oldPath + (newPath -> typeInfo))
      } else Left(s"Cannot rename missing field: ${oldPath.mkString(".")}")

    // Other actions (ChangeType, Optionalize) update the Value (Type String), not the Key Set (mostly)
    // For Optionalize, we might want to track Option wrapper?
    // For this audit, Structural/Path existence is key.

    // Join: Removes source paths, adds target path
    case MigrationAction.Join(at, sources, _, _) =>
      val targetPath        = at.nodes.collect { case zio.blocks.schema.DynamicOptic.Node.Field(n) => n }.toVector
      val sourcePathsVector = sources.map(_.nodes.collect { case zio.blocks.schema.DynamicOptic.Node.Field(n) =>
        n
      }.toVector)

      val (found, missing) = sourcePathsVector.partition(paths.contains)
      if (missing.nonEmpty) Left(s"Join sources missing: ${missing.map(_.mkString(".")).mkString(", ")}")
      else {
        val withoutSources = paths -- found
        Right(withoutSources + (targetPath -> "Derived"))
      }

    // Split: Removes source path, adds target paths
    case MigrationAction.Split(at, targets, _, _) =>
      val sourcePath        = at.nodes.collect { case zio.blocks.schema.DynamicOptic.Node.Field(n) => n }.toVector
      val targetPathsVector = targets.map(_.nodes.collect { case zio.blocks.schema.DynamicOptic.Node.Field(n) =>
        n
      }.toVector)

      if (!paths.contains(sourcePath)) Left(s"Split source missing: ${sourcePath.mkString(".")}")
      else {
        val withoutSource = paths - sourcePath
        Right(withoutSource ++ targetPathsVector.map(_ -> "Derived"))
      }

    case _ => Right(paths) // Changes types/values but not structure
  }
}
