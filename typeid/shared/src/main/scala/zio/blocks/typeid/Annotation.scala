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
 * An annotation on a type definition, as [[TypeId#annotations]] lists it, such
 * as `@deprecated("use Point", "1.0")` on a class. The macros record the
 * arguments they can read and leave out the others. Both skip annotations from
 * `scala.annotation.internal` and `scala.annotation.unchecked` and `@nowarn`;
 * Scala 2 also skips `@specialized`, and Scala 3 `@targetName` and
 * `@experimental`.
 *
 * @param typeId
 *   the id of the annotation class
 * @param args
 *   the arguments passed to the annotation's constructor
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
 * An argument of an [[Annotation]]: a constant, an array, a named argument, a
 * nested annotation, a class literal or an enum value. The Scala 2 macro
 * records constants, arrays, named arguments and nested annotations; the Scala
 * 3 macro also records class literals and Scala 3 enum values.
 */
sealed trait AnnotationArg

/**
 * The cases of [[AnnotationArg]]: `Const`, `ArrayArg`, `Named`, `Nested`,
 * `ClassOf` and `EnumValue`.
 */
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
   * A named argument, as in
   * `@deprecated(message = "use Point", since = "1.0")`. On Scala 2, the macro
   * also records the arguments of a Scala annotation this way when they are
   * written by position, as in `@deprecated("use Point", "1.0")`; Scala 3
   * records those as positional [[Const]] values.
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
