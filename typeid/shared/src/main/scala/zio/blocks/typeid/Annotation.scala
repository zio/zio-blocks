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

package zio.blocks.typeid

/**
 * Represents a Scala/Java annotation on a type, method, or other declaration.
 *
 * Annotations carry metadata that can be inspected at compile-time or runtime.
 * Examples:
 *   - `@deprecated("use newMethod", "1.0")`
 *   - `@tailrec`
 *   - `@JsonProperty("field_name")`
 *
 * @param typeId
 *   The TypeId of the annotation class
 * @param args
 *   The arguments passed to the annotation constructor
 */
final case class Annotation(
  typeId: TypeId[_],
  args: List[AnnotationArg] = Nil
) {

  /**
   * Returns the simple name of the annotation class.
   */
  def name: String = typeId.name

  /**
   * Returns the fully qualified name of the annotation class.
   */
  def fullName: String = typeId.fullName
}

/**
 * Represents an argument to an annotation.
 *
 * Annotation arguments can be:
 *   - Constant values (strings, numbers, booleans, etc.)
 *   - Arrays of values
 *   - Named arguments
 *   - Nested annotations
 *   - Class references
 *   - Enum values
 */
sealed trait AnnotationArg

object AnnotationArg {

  /**
   * A constant value argument.
   *
   * @param value
   *   The constant value (String, Int, Long, Double, Boolean, etc.)
   */
  final case class Const(value: Any) extends AnnotationArg

  /**
   * An array of annotation arguments.
   *
   * Used for annotation parameters that accept arrays:
   * `@Ann(values = Array("a", "b", "c"))`
   */
  final case class ArrayArg(values: List[AnnotationArg]) extends AnnotationArg

  /**
   * A named annotation argument.
   *
   * Used when arguments are specified by name:
   * `@deprecated(message = "use newMethod", since = "1.0")`
   */
  final case class Named(name: String, value: AnnotationArg) extends AnnotationArg

  /**
   * A nested annotation argument.
   *
   * Used when an annotation contains another annotation:
   * `@Outer(@Inner("value"))`
   */
  final case class Nested(annotation: Annotation) extends AnnotationArg

  /**
   * A class literal reference.
   *
   * Used for classOf references: `@Ann(classOf[String])`
   */
  final case class ClassOf(tpe: TypeRepr) extends AnnotationArg

  /**
   * An enum value reference.
   *
   * Used for enum constant references: `@Retention(RetentionPolicy.RUNTIME)`
   */
  final case class EnumValue(enumType: TypeId[_], valueName: String) extends AnnotationArg
}
