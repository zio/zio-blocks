package zio.blocks.smithy

/**
 * Represents a top-level Smithy model.
 *
 * A SmithyModel contains all the shapes, metadata, and configuration for a
 * Smithy API definition. It includes the model version, target namespace, use
 * statements, metadata key-value pairs, and a list of shape definitions.
 *
 * @param version
 *   the Smithy specification version (e.g., "2.0")
 * @param namespace
 *   the namespace for all shapes in this model (e.g., "com.example.api")
 * @param useStatements
 *   list of ShapeIds imported via use statements
 * @param metadata
 *   map of metadata key-value pairs for the model
 * @param shapes
 *   list of shape definitions in the model
 */
final case class SmithyModel(
  version: String,
  namespace: String,
  useStatements: List[ShapeId],
  metadata: Map[String, NodeValue],
  shapes: List[ShapeDefinition]
) {

  /**
   * Finds a shape by name within this model.
   *
   * Returns the first ShapeDefinition with the given name, or None if not
   * found.
   *
   * @param name
   *   the shape name to search for
   * @return
   *   Some(ShapeDefinition) if found, None otherwise
   */
  def findShape(name: String): Option[ShapeDefinition] =
    shapes.find(_.name == name)

  /**
   * Returns the list of all ShapeIds in this model.
   *
   * Each ShapeId is constructed from the model's namespace and the shape's
   * name. The order matches the order of shapes in the shapes list.
   *
   * @return
   *   list of ShapeIds for all shapes in this model
   */
  def allShapeIds: List[ShapeId] =
    shapes.map(shapeDef => ShapeId(namespace, shapeDef.name))
}

/**
 * Represents a named shape within a Smithy model.
 *
 * A ShapeDefinition pairs a shape name with its Shape definition, allowing
 * shapes to be organized and referenced within a model.
 *
 * @param name
 *   the shape name (e.g., "User", "Order")
 * @param shape
 *   the Shape definition for this named shape
 */
final case class ShapeDefinition(name: String, shape: Shape)
