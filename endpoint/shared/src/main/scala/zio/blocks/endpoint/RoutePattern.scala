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

  /**
   * Ordered, purely phantom registry of [[PathVar]] markers contributed by
   * `pathCodec` - a pass-through of `pathCodec.PathVars` (see
   * [[PathCodec.PathVars]]). Like `PathCodec`'s own `Segment`/`Transform`
   * cases, this class-body declaration is a best-effort placeholder: the REAL,
   * precisely-computed value is carried by
   * [[RoutePattern.RoutePatternOps]]`.`/`'s own refined return type, which is
   * what every acceptance test asserts against.
   */
  type PathVars = pathCodec.PathVars

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
    copy(pathCodec = PathCodec.combineUnrefined(prefix, pathCodec))

  def render: String =
    s"${Method.render(method)} ${pathCodec.render}"
}

object RoutePattern {
  implicit val unitUnit: Tuples.Tuples.WithOut[Unit, Unit, Unit] = Tuples.Tuples.leftUnit[Unit]

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
      acc.copy(pathCodec =
        PathCodec.combineUnrefined(acc.pathCodec, PathCodec(SegmentCodec.literalValidated(segment)))(
          Tuples.Tuples.leftUnit[Unit]
        )
      )
    )

  def apply(method: Method, pathString: String): RoutePattern[Unit] =
    apply(method, Path(pathString))

  def fromMethod(method: Method): RoutePattern[Unit] =
    RoutePattern(method, PathCodec.empty)

  def any: RoutePattern[Path] { type PathVars = SegmentCodec.NoPathVars } =
    RoutePattern(Method.ANY, PathCodec.trailing)
      .asInstanceOf[RoutePattern[Path] { type PathVars = SegmentCodec.NoPathVars }]

  def any(method: Method): RoutePattern[Path] { type PathVars = SegmentCodec.NoPathVars } =
    RoutePattern(method, PathCodec.trailing)
      .asInstanceOf[RoutePattern[Path] { type PathVars = SegmentCodec.NoPathVars }]

  implicit final class MethodSyntax(private val method: Method) extends AnyVal {
    def /[A, PV](path: PathCodec[A] { type PathVars = PV }): RoutePattern[A] { type PathVars = PV } =
      RoutePattern(method, path).asInstanceOf[RoutePattern[A] { type PathVars = PV }]
  }

  /**
   * Carries the precise, ordered `PathVars` combine through `/` via
   * refinement-typed receiver capture (the same pattern
   * `PathCodec.PathCodecOps` uses, and the same pattern
   * `SegmentCodecPlatformSpecific`'s `~` extension uses on Scala 3/2) -
   * `RoutePattern[A]`'s own class-body `PathVars` (a plain pass-through of
   * `pathCodec.PathVars`) cannot be more precise on its own, since
   * dependent-type capture requires a refinement on the METHOD RECEIVER, not a
   * case-class-body declaration.
   */
  implicit final class RoutePatternOps[A, PV](private val self: RoutePattern[A] { type PathVars = PV }) extends AnyVal {
    def /[B, PV2, C, PVC](that: PathCodec[B] { type PathVars = PV2 })(implicit
      combiner: Tuples.Tuples.WithOut[A, B, C],
      _pathVarsCombiner: PathCodec.RoutePathVarsCombiner[PV, PV2, PVC]
    ): RoutePattern[C] { type PathVars = PVC } = {
      val _ = _pathVarsCombiner
      self
        .copy(pathCodec = PathCodec.combineUnrefined(self.pathCodec, that)(combiner))
        .asInstanceOf[RoutePattern[C] { type PathVars = PVC }]
    }
  }
}
