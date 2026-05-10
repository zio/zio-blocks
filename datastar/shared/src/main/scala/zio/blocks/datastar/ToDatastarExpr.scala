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
import zio.http.html.ToJs

@implicitNotFound(
  "No ToDatastarExpr instance found for type ${A}. Use js\"...\" for Datastar expressions or typed Signal/SignalUpdate values."
)
trait ToDatastarExpr[-A] {
  def toDatastarExpr(a: A): String
}

object ToDatastarExpr extends ToDatastarExprLowPriority {
  def apply[A](implicit ev: ToDatastarExpr[A]): ToDatastarExpr[A] = ev
}

private[datastar] trait ToDatastarExprLowPriority {
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
  implicit def notString[A]: NotString[A] = new NotString[A] {}
}

private[datastar] object NotString extends NotStringLowPriority {
  implicit val notStringString1: NotString[String] = new NotString[String] {}
  implicit val notStringString2: NotString[String] = new NotString[String] {}
}
