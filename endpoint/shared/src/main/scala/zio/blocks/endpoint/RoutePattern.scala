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
 * An HTTP method paired with a [[PathCodec]]: the part of an [[Endpoint]] that
 * says which requests it answers. `A` is the value the path captures.
 *
 * Build one with `Method.GET / "notes" / PathCodec.int("id")` after `import
 * RoutePattern._`, which supplies the `/` syntax. `RoutePattern.any` and
 * `RoutePattern.any(method)` give catch-all routes, `nest` adds a path prefix,
 * and `alternatives` expands the pattern for a [[RouteTree]]. A pattern for
 * `GET` also accepts `HEAD` requests.
 *
 * {{{
 * import zio.blocks.endpoint.RoutePattern._
 * import zio.http.{Method, Path}
 *
 * val getNote = Method.GET / "notes" / PathCodec.int("id")
 *
 * getNote.render                                  // GET /notes/{id}
 * getNote.decode(Method.GET, Path("/notes/42"))   // Right(42)
 * getNote.decode(Method.POST, Path("/notes/42"))  // Left(Expected HTTP method GET but found POST)
 * getNote.encode(7)                               // Right((GET,/notes/7))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/endpoint/route-pattern/ RoutePattern]]
 */
final case class RoutePattern[A](
  method: Method,
  pathCodec: PathCodec[A],
  doc: Doc = Doc.empty
) {

  /**
   * Expands the pattern into one pattern per method and path, the form a
   * [[RouteTree]] stores: each `orElse` branch of the path gives its own
   * pattern, a method set built with `#|` gives one pattern per method, and
   * `Method.ANY` gives one per standard method (all nine). Duplicates are
   * removed.
   *
   * A pattern whose path is empty (the root, `/`) gives an empty list, so
   * [[RouteTree.add]] stores nothing for it: a known bug. A pattern with a
   * trailing capture, such as `RoutePattern.any`, still matches `/`.
   *
   * {{{
   * import zio.blocks.endpoint.RoutePattern._
   * import zio.http.Method
   *
   * val notes = (Method.GET #| Method.POST) / PathCodec("notes").orElse(PathCodec("memos"))
   *
   * notes.alternatives.map(_.render).sorted // List(GET /memos, GET /notes, POST /memos, POST /notes)
   * }}}
   */
  def alternatives: List[RoutePattern[A]] =
    (pathCodec.alternatives.flatMap { codec =>
      method match {
        case Method.ANY         => Method.standardMethods.toList.map(single => copy(method = single, pathCodec = codec))
        case Method.Methods(ms) => ms.toList.map(single => copy(method = single, pathCodec = codec))
        case _                  => List(copy(pathCodec = codec))
      }
    }).distinct

  /**
   * Decodes the value the path captures, after checking the method. The method
   * matches when [[zio.http.Method#matches]] says so (so `Method.ANY` on either
   * side matches), and a `GET` pattern also accepts `HEAD`; a pattern for a
   * method set such as `GET #| POST` does not. A wrong method gives
   * `Left(Expected HTTP method GET but found POST)`; a wrong path gives the
   * [[PathCodec#decode]] error with `Route <render>: ` in front.
   */
  def decode(actual: Method, path: Path): Either[String, A] =
    if (!method.matches(actual) && !(method == Method.GET && actual == Method.HEAD))
      Left(s"Expected HTTP method ${Method.render(method)} but found ${Method.render(actual)}")
    else pathCodec.decode(path).left.map(error => s"Route $render: $error")

  /**
   * Returns this pattern's method with the path that [[format]] writes for
   * `value`.
   */
  def encode(value: A): Either[String, (Method, Path)] =
    format(value).map(method -> _)

  /**
   * Writes `value` into the path (see [[PathCodec#format]]).
   */
  def format(value: A): Either[String, Path] =
    pathCodec.format(value)

  /**
   * Tests whether a request with method `actual` and path `path` matches, like
   * `decode(actual, path).isRight` but without building the decoded value (see
   * [[PathCodec#matches]]).
   */
  def matches(actual: Method, path: Path): Boolean =
    (method.matches(actual) || (method == Method.GET && actual == Method.HEAD)) &&
      PathCodecRuntime.matchesCodec(pathCodec, path.segments)

  /**
   * Puts the literal-only path `prefix` in front of this pattern's path.
   *
   * {{{
   * import zio.blocks.endpoint.RoutePattern._
   * import zio.http.Method
   *
   * (Method.GET / "notes").nest(PathCodec("api") / "v1").render // GET /api/v1/notes
   * }}}
   */
  def nest(prefix: PathCodec[Unit]): RoutePattern[A] =
    copy(pathCodec = PathCodec.combineUnrefined(prefix, pathCodec))

  /**
   * Renders the method and path template, as in `GET /notes/{id}`. `Method.ANY`
   * renders as `*`, and a method set as its methods joined by `#|`, such as
   * `GET#|POST`.
   */
  def render: String =
    s"${Method.render(method)} ${pathCodec.render}"
}

/**
 * Constructors for route patterns, one pattern per standard method with an
 * empty path, and the `/` syntax: `import RoutePattern._` to write
 * `Method.GET / "notes"`.
 */
object RoutePattern {

  /**
   * The `Tuples` instance that joins two `Unit` values into `Unit`, for routes
   * of literal segments only.
   */
  implicit val unitUnit: Tuples.Tuples.WithOut[Unit, Unit, Unit] = Tuples.Tuples.leftUnit[Unit]

  /**
   * `CONNECT` with an empty path.
   */
  val CONNECT: RoutePattern[Unit] = fromMethod(Method.CONNECT)

  /**
   * `DELETE` with an empty path.
   */
  val DELETE: RoutePattern[Unit] = fromMethod(Method.DELETE)

  /**
   * `GET` with an empty path; extend it with `/`, as in `RoutePattern.GET /
   * "notes"`.
   */
  val GET: RoutePattern[Unit] = fromMethod(Method.GET)

  /**
   * `HEAD` with an empty path.
   */
  val HEAD: RoutePattern[Unit] = fromMethod(Method.HEAD)

  /**
   * `OPTIONS` with an empty path.
   */
  val OPTIONS: RoutePattern[Unit] = fromMethod(Method.OPTIONS)

  /**
   * `PATCH` with an empty path.
   */
  val PATCH: RoutePattern[Unit] = fromMethod(Method.PATCH)

  /**
   * `POST` with an empty path.
   */
  val POST: RoutePattern[Unit] = fromMethod(Method.POST)

  /**
   * `PUT` with an empty path.
   */
  val PUT: RoutePattern[Unit] = fromMethod(Method.PUT)

  /**
   * `TRACE` with an empty path.
   */
  val TRACE: RoutePattern[Unit] = fromMethod(Method.TRACE)

  /**
   * Builds a pattern for `method` with an empty path; same as `fromMethod`.
   */
  def apply(method: Method): RoutePattern[Unit] =
    RoutePattern(method, PathCodec.empty)

  /**
   * Builds a literal-only route with one literal per segment of `path`. The
   * segments are not checked, as with `apply(method, pathString)`.
   */
  def apply(method: Method, path: Path): RoutePattern[Unit] =
    path.segments.foldLeft(fromMethod(method))((acc, segment) =>
      acc.copy(pathCodec =
        PathCodec.combineUnrefined(acc.pathCodec, PathCodec(SegmentCodec.literalValidated(segment)))(
          Tuples.Tuples.leftUnit[Unit]
        )
      )
    )

  /**
   * Builds a literal-only route from a path string, split at `/` with
   * `Path.apply`, which does not percent-decode. As with
   * `PathCodec.apply(String)`, the segments are not checked: `"notes/a b"` is
   * accepted as the literal `a b`, a known bug.
   */
  def apply(method: Method, pathString: String): RoutePattern[Unit] =
    apply(method, Path(pathString))

  /**
   * Builds a pattern for `method` with an empty path.
   */
  def fromMethod(method: Method): RoutePattern[Unit] =
    RoutePattern(method, PathCodec.empty)

  /**
   * Matches every method and every path, capturing the whole path as a
   * [[zio.http.Path]]. It renders as `* /...`.
   */
  def any: RoutePattern[Path] =
    RoutePattern(Method.ANY, PathCodec.trailing)

  /**
   * Matches `method` with every path, capturing the whole path as a
   * [[zio.http.Path]].
   */
  def any(method: Method): RoutePattern[Path] =
    RoutePattern(method, PathCodec.trailing)

  /**
   * Adds `/` to [[zio.http.Method]], to start a pattern.
   */
  implicit final class MethodSyntax(private val method: Method) extends AnyVal {

    /**
     * Pairs the method with `path`; a string converts to a literal path, as in
     * `Method.GET / "notes"`.
     */
    def /[A](path: PathCodec[A]): RoutePattern[A] =
      RoutePattern(method, path)
  }

  /**
   * Adds `/` to a pattern, to extend its path.
   */
  implicit final class RoutePatternOps[A](private val self: RoutePattern[A]) extends AnyVal {

    /**
     * Appends `that` to the pattern's path, joining the captured values as
     * [[PathCodec#++]] does.
     */
    def /[B, C](that: PathCodec[B])(implicit
      combiner: Tuples.Tuples.WithOut[A, B, C]
    ): RoutePattern[C] =
      self.copy(pathCodec = PathCodec.combineUnrefined(self.pathCodec, that)(combiner))
  }
}
