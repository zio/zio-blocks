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

package zio.http.datastar

import scala.annotation.{implicitAmbiguous, implicitNotFound}
import zio.blocks.html._

@implicitNotFound(
  "No ToDatastarExpr instance found for type ${A}. Use js\"...\" for Datastar expressions or typed Signal/SignalUpdate values."
)
/**
 * Turns a value into the text of a Datastar expression, for the `:=` methods of
 * the attribute builders. An instance exists for every type with a `ToJs`
 * instance except `String`: a `js"..."` expression, a [[Signal]] (rendered as
 * its reference), a [[SignalUpdate]] (rendered as an object), a
 * [[DatastarRef]], and plain values such as `Int`, `Boolean` or `List[Int]`. A
 * `String` does not compile, because a raw string in an expression position is
 * usually a mistake; write `js"..."` instead.
 *
 * {{{
 * // no-check: Scala 3.10 users can't summon ToDatastarExpr (a known bug)
 * import zio.blocks.html._
 *
 * val count = Signal[Int]("count")
 *
 * ToDatastarExpr[Signal[Int]].toDatastarExpr(count) // "\$count"
 * ToDatastarExpr[Int].toDatastarExpr(5)             // "5"
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/datastar/attributes/ Datastar Attributes]]
 */
trait ToDatastarExpr[-A] {

  /** Returns the expression text for `a`. */
  def toDatastarExpr(a: A): String
}

/** Summons and derives [[ToDatastarExpr]] instances. */
object ToDatastarExpr extends ToDatastarExprLowPriority {

  /** Returns the instance in scope for `A`. */
  def apply[A](implicit ev: ToDatastarExpr[A]): ToDatastarExpr[A] = ev
}

private[datastar] trait ToDatastarExprLowPriority {

  /**
   * Derives an instance from a `ToJs` instance, for every type except `String`.
   */
  implicit def fromToJs[A](implicit toJs: ToJs[A], notString: NotString[A]): ToDatastarExpr[A] =
    new ToDatastarExpr[A] {
      def toDatastarExpr(a: A): String = toJs.toJs(a)
    }
}

@implicitAmbiguous(
  "Raw String values are not allowed in Datastar expression positions. Use js\"...\" for Datastar expressions or typed Signal/SignalUpdate values."
)
private[datastar] sealed trait NotString[A]

private[datastar] trait NotStringLowPriority {

  /** Evidence for every type; `NotString` makes it ambiguous for `String`. */
  implicit def notString[A]: NotString[A] = new NotString[A] {}
}

private[datastar] object NotString extends NotStringLowPriority {
  implicit val notStringString1: NotString[String] = new NotString[String] {}
  implicit val notStringString2: NotString[String] = new NotString[String] {}
}
