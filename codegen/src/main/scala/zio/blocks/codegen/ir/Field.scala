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

package zio.blocks.codegen.ir

/**
 * A field in the codegen IR: a constructor parameter of a [[CaseClass]] or
 * [[AbstractClass]], or a parameter of a parameterized [[EnumCase]]. The
 * emitter writes it as `name: Type = default`, with each annotation on its own
 * line above it (on the same line inside a Scala 3 enum case), and wraps the
 * name in backquotes when it is a keyword or not a plain identifier.
 *
 * {{{
 * import zio.blocks.codegen.ir._
 * import zio.blocks.codegen.emit.{EmitterConfig, ScalaEmitter}
 *
 * // Simple field
 * val nameField = Field("name", TypeRef.String)
 *
 * // Field with default value
 * val ageField = Field("age", TypeRef.Int, Some("0"))
 *
 * // Field with annotations and documentation (the emitter drops the doc)
 * val emailField = Field(
 *   "email",
 *   TypeRef.String,
 *   annotations = List(Annotation("required")),
 *   doc = Some("User email address")
 * )
 *
 * // Field with complex type
 * val itemsField = Field("items", TypeRef.list(TypeRef.String))
 *
 * ScalaEmitter.emitField(emailField, EmitterConfig.default)
 * // @required
 * // email: String
 * }}}
 *
 * @param name
 *   The name of the field
 * @param typeRef
 *   The type of the field
 * @param defaultValue
 *   Optional default value, as Scala source text written after ` = ` (defaults
 *   to None)
 * @param annotations
 *   List of annotations on the field (defaults to empty list)
 * @param doc
 *   Optional documentation of the field (defaults to None). The emitter never
 *   writes it (a known bug).
 *
 * @see
 *   [[https://zioblocks.com/reference/codegen/field/ Field]]
 */
final case class Field(
  name: String,
  typeRef: TypeRef,
  defaultValue: Option[String] = None,
  annotations: List[Annotation] = Nil,
  doc: Option[String] = None
)
