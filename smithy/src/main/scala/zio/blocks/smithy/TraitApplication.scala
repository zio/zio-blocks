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
 * Represents a trait applied to a shape in Smithy.
 *
 * A TraitApplication is the pairing of a trait ID (which identifies the trait)
 * with an optional value (which provides configuration for the trait).
 *
 * @param id
 *   the ShapeId of the trait (e.g., "smithy.api#required")
 * @param value
 *   the optional NodeValue configuration for this trait
 */
final case class TraitApplication(id: ShapeId, value: Option[NodeValue])

object TraitApplication {

  /**
   * Creates the `@required` trait, which marks a shape as required.
   *
   * This is a convenience factory for the smithy.api#required trait, which
   * takes no configuration value.
   *
   * @return
   *   a TraitApplication for the @required trait
   */
  def required: TraitApplication =
    TraitApplication(ShapeId("smithy.api", "required"), None)

  /**
   * Creates the `@documentation` trait with the given documentation text.
   *
   * This is a convenience factory for the smithy.api#documentation trait.
   *
   * @param text
   *   the documentation string
   * @return
   *   a TraitApplication for the @documentation trait
   */
  def documentation(text: String): TraitApplication =
    TraitApplication(
      ShapeId("smithy.api", "documentation"),
      Some(NodeValue.String(text))
    )

  /**
   * Creates the `@http` trait with the given HTTP method and URI.
   *
   * This is a convenience factory for the smithy.api#http trait, which
   * configures HTTP bindings for a shape (typically an operation).
   *
   * @param method
   *   the HTTP method (e.g., "GET", "POST", "PUT", "DELETE")
   * @param uri
   *   the URI template (e.g., "/users/{id}")
   * @return
   *   a TraitApplication for the @http trait
   */
  def http(method: String, uri: String): TraitApplication =
    TraitApplication(
      ShapeId("smithy.api", "http"),
      Some(
        NodeValue.Object(
          List(
            "method" -> NodeValue.String(method),
            "uri"    -> NodeValue.String(uri)
          )
        )
      )
    )

  /**
   * Creates the `@error` trait with the given error classification.
   *
   * This is a convenience factory for the smithy.api#error trait, which marks a
   * shape as an error and specifies its classification.
   *
   * @param value
   *   the error classification (typically "client" or "server")
   * @return
   *   a TraitApplication for the @error trait
   */
  def error(value: String): TraitApplication =
    TraitApplication(
      ShapeId("smithy.api", "error"),
      Some(NodeValue.String(value))
    )
}
