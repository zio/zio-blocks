/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.smithy

/**
 * A Smithy model: the contents of one Smithy IDL file. It holds the IDL
 * version, the namespace, the `use` statements, the metadata, the shape
 * definitions and the `apply` statements. Read IDL text with
 * [[SmithyModel.parse]], look shapes up with [[SmithyModel#findShape]], build a
 * model by hand from [[ShapeDefinition]]s, and write it back with
 * `prettyPrint`.
 *
 * The parser reads a subset of the Smithy IDL: it rejects several Smithy 2.0
 * constructs, such as mixins, member default values, inline operation input and
 * output, and text blocks (a known bug).
 *
 * {{{
 * val idl =
 *   """\$version: "2"
 *     |namespace com.example.weather
 *     |
 *     |structure Forecast {
 *     |    @required
 *     |    city: String
 *     |    celsius: Float
 *     |}
 *     |""".stripMargin
 *
 * val model = SmithyModel.parse(idl)
 * model.map(_.allShapeIds) // Right(List(com.example.weather#Forecast))
 *
 * model.toOption.flatMap(_.findShape("Forecast")).collect { case ShapeDefinition(_, s: StructureShape) =>
 *   s.members.map(_.name)
 * } // Some(List(city, celsius))
 * }}}
 *
 * @param version
 *   the Smithy IDL version, as written in the `\$version` statement (e.g.,
 *   "2.0"). The parser accepts any string here.
 * @param namespace
 *   the namespace for all shapes in this model (e.g., "com.example.api")
 * @param useStatements
 *   list of ShapeIds imported via use statements. The parser records them but
 *   does not use them to resolve names.
 * @param metadata
 *   map of metadata key-value pairs for the model. Being a `Map`, it does not
 *   keep the source order; `prettyPrint` writes the entries sorted by key.
 * @param shapes
 *   list of shape definitions in the model, in source order. The parser keeps
 *   duplicate names.
 * @param applyStatements
 *   list of apply statements that attach traits to shapes
 * @see
 *   [[https://zioblocks.com/reference/smithy/ Smithy]]
 */
final case class SmithyModel(
  version: String,
  namespace: String,
  useStatements: List[ShapeId],
  metadata: Map[String, NodeValue],
  shapes: List[ShapeDefinition],
  applyStatements: List[ApplyStatement] = Nil
) {

  /**
   * Serializes this model as Smithy IDL text with default 4-space indentation.
   * Parsing the text back gives the same shapes, except that shape references
   * in `smithy.api` or in the model's own namespace come back with an empty
   * namespace, and an apply statement with several traits comes back as one
   * statement per trait.
   *
   * @return
   *   the Smithy IDL text
   */
  def prettyPrint: String = SmithyPrinter.print(this)

  /**
   * Serializes this model as Smithy IDL text with configurable indentation.
   *
   * @param indent
   *   the number of spaces to use for indentation within shapes
   * @return
   *   the Smithy IDL text
   */
  def prettyPrint(indent: Int): String = SmithyPrinter.print(this, indent)

  /**
   * Finds a shape by name within this model.
   *
   * Returns the first ShapeDefinition with the given name, or None if not
   * found. Only the name is compared, not a namespace.
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

/** Reads Smithy IDL text into a [[SmithyModel]]. */
object SmithyModel {

  /**
   * Parses a Smithy IDL string into a `SmithyModel`.
   *
   * The input must start with a `\$version` statement and have a `namespace`
   * statement. Shape references written without a namespace (`city: String`)
   * get a [[ShapeId]] with an empty namespace. Trait names written without a
   * namespace always get `smithy.api`, even when a `use` statement imports them
   * from another namespace (a known bug). Several Smithy 2.0 constructs fail to
   * parse (a known bug, see [[SmithyModel]]).
   *
   * @param input
   *   the complete Smithy IDL text to parse
   * @return
   *   `Right(SmithyModel)` on success, or `Left(SmithyError)` describing the
   *   parse failure with location information
   */
  def parse(input: String): Either[SmithyError, SmithyModel] = SmithyParser.parse(input)
}

/**
 * Represents a named shape within a Smithy model.
 *
 * A ShapeDefinition pairs a shape name with its Shape definition. The parser
 * gives both the same name; `prettyPrint` writes the definition's name, not the
 * shape's.
 *
 * @param name
 *   the shape name (e.g., "User", "Order")
 * @param shape
 *   the Shape definition for this named shape
 */
final case class ShapeDefinition(name: String, shape: Shape)

/**
 * Represents an apply statement that attaches traits to an existing shape.
 *
 * In Smithy IDL, apply statements allow traits to be added to shapes defined
 * elsewhere, e.g., `apply MyShape @tag("foo")`.
 *
 * @param target
 *   the shape to apply traits to, as written after `apply`
 * @param traits
 *   the traits to apply
 */
final case class ApplyStatement(
  target: String,
  traits: List[TraitApplication]
)
