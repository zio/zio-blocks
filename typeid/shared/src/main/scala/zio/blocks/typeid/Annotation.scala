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
 * nested annotation, a class literal or an enum value. Both macros record
 * constants, named arguments and nested annotations, and drop an argument
 * written as `Array(...)`. The Scala 3 macro also records the values passed to
 * a repeated parameter as one [[AnnotationArg.ArrayArg]] (Scala 2 records each
 * as a [[AnnotationArg.Const]]) and enum values, Java ones included, as
 * [[AnnotationArg.EnumValue]]; it records a `classOf[T]` argument as
 * `Const(null)` (a known bug), and Scala 2 drops it.
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
   * An array of arguments. The Scala 3 macro writes the values passed to a
   * repeated parameter this way, as in `@Tags("a", "b")` for
   * `class Tags(names: String*)`.
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
   * A nested annotation argument, as in `@Outer(new Inner("value"))`.
   */
  final case class Nested(annotation: Annotation) extends AnnotationArg

  /**
   * A class literal, `classOf[T]`. The macros do not produce it: Scala 3
   * records `classOf[T]` as `Const(null)` (a known bug) and Scala 2 drops it.
   */
  final case class ClassOf(tpe: TypeRepr) extends AnnotationArg

  /**
   * An enum value, such as `RetentionPolicy.RUNTIME` in
   * `@Retention(RetentionPolicy.RUNTIME)`. Only the Scala 3 macro produces it,
   * for Scala 3 and Java enums.
   */
  final case class EnumValue(enumType: TypeId[_], valueName: String) extends AnnotationArg
}
