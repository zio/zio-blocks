package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic

/**
 * Defines how two JSON values should be merged.
 */
sealed trait MergeStrategy {

  /**
   * Merges two JSON values at the given path.
   *
   * @param path
   *   The current path in the JSON structure
   * @param left
   *   The left (base) JSON value
   * @param right
   *   The right (overlay) JSON value
   * @return
   *   The merged JSON value
   */
  def merge(path: DynamicOptic, left: Json, right: Json): Json
}

object MergeStrategy {

  private def mergeObjects(
    path: DynamicOptic,
    leftFields: Vector[(Predef.String, Json)],
    rightFields: Vector[(Predef.String, Json)],
    recursive: Boolean,
    mergeFn: (DynamicOptic, Json, Json) => Json
  ): Json.Object = {
    val leftMap: Map[Predef.String, Json]           = leftFields.toMap
    val rightMap: Map[Predef.String, Json]          = rightFields.toMap
    val allKeys: Vector[Predef.String]              = (leftFields.map(_._1) ++ rightFields.map(_._1)).distinct
    val mergedFields: Vector[(Predef.String, Json)] = allKeys.map { key =>
      val leftOpt  = leftMap.get(key)
      val rightOpt = rightMap.get(key)
      (leftOpt, rightOpt) match {
        case (Some(lv), Some(rv)) =>
          if (recursive) (key, mergeFn(path.field(key), lv, rv))
          else (key, rv)
        case (Some(lv), None) => (key, lv)
        case (None, Some(rv)) => (key, rv)
        case (None, None)     => throw new IllegalStateException("Key not found in either map")
      }
    }
    Json.Object(mergedFields)
  }

  /**
   * Auto merge strategy that recursively merges objects and replaces other
   * values.
   *
   *   - Objects are merged recursively (fields from both are combined)
   *   - Arrays from right replace arrays from left
   *   - Primitives from right replace primitives from left
   *   - If types differ, right replaces left
   */
  case object Auto extends MergeStrategy {
    def merge(path: DynamicOptic, left: Json, right: Json): Json = (left, right) match {
      case (Json.Object(leftFields), Json.Object(rightFields)) =>
        mergeObjects(path, leftFields, rightFields, recursive = true, merge)
      case (_, _) => right
    }
  }

  /**
   * Deep merge strategy that recursively merges both objects and arrays.
   *
   *   - Objects are merged recursively
   *   - Arrays are merged by concatenating elements
   *   - Primitives from right replace primitives from left
   */
  case object Deep extends MergeStrategy {
    def merge(path: DynamicOptic, left: Json, right: Json): Json = (left, right) match {
      case (Json.Object(leftFields), Json.Object(rightFields)) =>
        mergeObjects(path, leftFields, rightFields, recursive = true, merge)
      case (Json.Array(leftElems), Json.Array(rightElems)) =>
        Json.Array(leftElems ++ rightElems)
      case (_, _) => right
    }
  }

  /**
   * Shallow merge strategy that only merges top-level object fields.
   *
   *   - Top-level object fields from right override left
   *   - No recursive merging of nested objects
   *   - All other values from right replace left
   */
  case object Shallow extends MergeStrategy {
    def merge(path: DynamicOptic, left: Json, right: Json): Json = (left, right) match {
      case (Json.Object(leftFields), Json.Object(rightFields)) =>
        mergeObjects(path, leftFields, rightFields, recursive = false, merge)
      case (_, _) => right
    }
  }

  /**
   * Concat merge strategy that concatenates arrays and merges objects.
   *
   *   - Objects are merged recursively
   *   - Arrays are concatenated (left ++ right)
   *   - Primitives from right replace primitives from left
   */
  case object Concat extends MergeStrategy {
    def merge(path: DynamicOptic, left: Json, right: Json): Json = Deep.merge(path, left, right)
  }

  /**
   * Replace merge strategy that always uses the right value.
   */
  case object Replace extends MergeStrategy {
    def merge(path: DynamicOptic, left: Json, right: Json): Json = right
  }

  /**
   * Custom merge strategy using a user-provided function.
   *
   * @param f
   *   A function that takes the path, left value, and right value, and returns
   *   the merged value
   */
  final case class Custom(f: (DynamicOptic, Json, Json) => Json) extends MergeStrategy {
    def merge(path: DynamicOptic, left: Json, right: Json): Json = f(path, left, right)
  }
}
