package zio.blocks.schema.json

/**
 * A cursor for navigating and manipulating JSON structures.
 * 
 * Provides a functional interface for traversing JSON with history tracking.
 */
final case class JsonCursor(
  focus: Json,
  path: JsonPath,
  root: Json
) {
  
  /**
   * Navigate to a field in the current focus (must be an object).
   */
  def downField(field: String): Option[JsonCursor] = focus match {
    case Json.Obj(fields) =>
      fields.get(field).map { value =>
        JsonCursor(value, path / field, root)
      }
    case _ => None
  }
  
  /**
   * Navigate to an index in the current focus (must be an array).
   */
  def downIndex(index: Int): Option[JsonCursor] = focus match {
    case Json.Arr(elements) =>
      if (index >= 0 && index < elements.length) {
        Some(JsonCursor(elements(index), path(index), root))
      } else None
    case _ => None
  }
  
  /**
   * Replace the value at the current focus.
   */
  def replace(value: Json): JsonCursor = {
    val newRoot = path.update(root, value).getOrElse(root)
    JsonCursor(value, path, newRoot)
  }
  
  /**
   * Delete the value at the current focus.
   */
  def delete: Option[JsonCursor] = {
    path.delete(root).map { newRoot =>
      JsonCursor(Json.Null, path, newRoot)
    }
  }
  
  /**
   * Get the updated root after all modifications.
   */
  def top: Json = root
  
  /**
   * Navigate using a relative path from current focus.
   */
  def navigate(relativePath: JsonPath): Option[JsonCursor] = {
    relativePath.navigate(focus).map { newFocus =>
      // Combine paths
      val newPath = combinePaths(path, relativePath)
      JsonCursor(newFocus, newPath, root)
    }
  }
  
  private def combinePaths(base: JsonPath, relative: JsonPath): JsonPath = relative match {
    case JsonPath.Root => base
    case JsonPath.Field(parent, name) => 
      JsonPath.Field(combinePaths(base, parent), name)
    case JsonPath.Index(parent, idx) => 
      JsonPath.Index(combinePaths(base, parent), idx)
  }
}

object JsonCursor {
  
  /**
   * Create a cursor at the root of a JSON value.
   */
  def apply(json: Json): JsonCursor = 
    JsonCursor(json, JsonPath.root, json)
}
