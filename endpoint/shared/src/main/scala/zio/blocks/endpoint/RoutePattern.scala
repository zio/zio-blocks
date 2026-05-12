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

package zio.blocks.endpoint

import zio.blocks.docs.Doc
import zio.blocks.combinators.Tuples
import zio.http.{Method, Path}

/**
 * HTTP method paired with a typed path pattern. The primary constructor is
 * `Method.GET / "users" / PathCodec.int("id")`. Use `alternatives` to expand
 * `orElse` branches, `RoutePattern.any(Method)` or `RoutePattern.any` for
 * catch-all trailing routes, and `nest` to prepend a path prefix.
 */
final case class RoutePattern[A](
  method: Method,
  pathCodec: PathCodec[A],
  doc: Doc = Doc.empty
) {
  def /[B, C](that: PathCodec[B])(implicit combiner: Tuples.Tuples.WithOut[A, B, C]): RoutePattern[C] =
    if (that == PathCodec.empty) this.asInstanceOf[RoutePattern[C]]
    else if (pathCodec == PathCodec.empty) copy(pathCodec = that.asInstanceOf[PathCodec[C]])
    else copy(pathCodec = pathCodec ++ that)

  def alternatives: List[RoutePattern[A]] =
    pathCodec.alternatives.flatMap { codec =>
      method match {
        case Method.ANY         => Method.standardMethods.toList.map(single => copy(method = single, pathCodec = codec))
        case Method.Methods(ms) => ms.toList.map(single => copy(method = single, pathCodec = codec))
        case _                  => List(copy(pathCodec = codec))
      }
    }

  def decode(actual: Method, path: Path): Either[String, A] =
    if (!method.matches(actual) && !(method == Method.GET && actual == Method.HEAD))
      Left(s"Expected HTTP method ${Method.render(method)} but found ${Method.render(actual)}")
    else pathCodec.decode(path)

  def encode(value: A): Either[String, (Method, Path)] =
    format(value).map(method -> _)

  def format(value: A): Either[String, Path] =
    pathCodec.format(value)

  def matches(actual: Method, path: Path): Boolean =
    decode(actual, path).isRight

  def nest(prefix: PathCodec[Unit]): RoutePattern[A] =
    copy(pathCodec = prefix / pathCodec)

  def render: String =
    s"${Method.render(method)} ${pathCodec.render}"
}

object RoutePattern {
  val CONNECT: RoutePattern[Unit] = fromMethod(Method.CONNECT)
  val DELETE: RoutePattern[Unit]  = fromMethod(Method.DELETE)
  val GET: RoutePattern[Unit]     = fromMethod(Method.GET)
  val HEAD: RoutePattern[Unit]    = fromMethod(Method.HEAD)
  val OPTIONS: RoutePattern[Unit] = fromMethod(Method.OPTIONS)
  val PATCH: RoutePattern[Unit]   = fromMethod(Method.PATCH)
  val POST: RoutePattern[Unit]    = fromMethod(Method.POST)
  val PUT: RoutePattern[Unit]     = fromMethod(Method.PUT)
  val TRACE: RoutePattern[Unit]   = fromMethod(Method.TRACE)

  def apply(method: Method): RoutePattern[Unit] =
    RoutePattern(method, PathCodec.empty)

  def apply(method: Method, path: Path): RoutePattern[Unit] =
    path.segments.foldLeft(fromMethod(method))((acc, segment) =>
      acc./[Unit, Unit](PathCodec(SegmentCodec.literalValidated(segment)))(Tuples.Tuples.leftUnit[Unit])
    )

  def apply(method: Method, pathString: String): RoutePattern[Unit] =
    apply(method, Path(pathString))

  def fromMethod(method: Method): RoutePattern[Unit] =
    RoutePattern(method, PathCodec.empty)

  def any: RoutePattern[Path] =
    RoutePattern(Method.ANY, PathCodec.trailing)

  def any(method: Method): RoutePattern[Path] =
    RoutePattern(method, PathCodec.trailing)

  implicit final class MethodSyntax(private val method: Method) extends AnyVal {
    def /[A](path: PathCodec[A]): RoutePattern[A] =
      RoutePattern(method, path)
  }
}
