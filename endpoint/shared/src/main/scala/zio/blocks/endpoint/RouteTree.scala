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

import scala.collection.immutable.ListMap

import zio.blocks.chunk.Chunk
import zio.http.{Method, Path}

/**
 * A routing table that maps a request's method and path to a value of type `A`,
 * such as a handler. It is a tree keyed by method, then by one path segment per
 * level.
 *
 * At each level a literal segment is tried first; when it leads to no route,
 * the capturing segments are tried in this order: int, long, UUID, bool,
 * string, segments built with `~`, trailing. So `/notes/42` goes to a
 * `{id: Int}` route before a `{slug: String}` one, and a string capture at a
 * level wins over a `~` segment beside it. Only the shape of each segment is
 * checked: a `transformOrFail` decoder does not run, so the route found can
 * still fail to decode the path. The trailing slash of the request path is
 * ignored.
 *
 * A `HEAD` request with no `HEAD` route uses the `GET` routes. A trailing route
 * such as `/assets/...` also matches its prefix `/assets` when there is no
 * exact route for it. Adding a route that has the same method and segment
 * shapes as an existing one replaces its value, and so does `merge` (the
 * right-hand tree wins).
 *
 * {{{
 * import zio.blocks.endpoint.RoutePattern._
 * import zio.http.{Method, Path}
 *
 * val routes = RouteTree
 *   .empty[String]
 *   .add(Method.GET / "notes" / "latest", "latest note")
 *   .add(Method.GET / "notes" / PathCodec.int("id"), "note by id")
 *   .add(Method.GET / "notes" / PathCodec.string("slug"), "note by slug")
 *
 * routes.get(Method.GET, Path("/notes/latest")) // Some(latest note)
 * routes.get(Method.GET, Path("/notes/42"))     // Some(note by id)
 * routes.get(Method.GET, Path("/notes/hello"))  // Some(note by slug)
 * routes.get(Method.HEAD, Path("/notes/42"))    // Some(note by id)
 * routes.get(Method.POST, Path("/notes/42"))    // None
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/endpoint/route-tree/ RouteTree]]
 */
final case class RouteTree[A](
  roots: Map[Method, SegmentSubtree[A]]
) { self =>

  /**
   * Adds `value` under every method and path that `pattern.alternatives` gives
   * (so `Method.ANY` adds it under all nine standard methods). A route with the
   * same method and segment shapes as an existing one replaces it; the capture
   * names do not count, so `/notes/{id}` and `/notes/{n}` (both `Int`) are the
   * same route.
   *
   * A pattern with an empty path adds nothing, so a route for `/` cannot be
   * stored: a known bug (see [[RoutePattern.alternatives]]). Throws
   * `IllegalStateException` for an `orElse` branch of several segments.
   */
  def add[A1 >: A](pattern: RoutePattern[_], value: A1): RouteTree[A1] =
    pattern.alternatives.foldLeft(this.asInstanceOf[RouteTree[A1]]) { (tree, alternative) =>
      tree.addSingle(alternative, value)
    }

  private def addSingle[A1 >: A](pattern: RoutePattern[_], value: A1): RouteTree[A1] = {
    val widened  = roots.asInstanceOf[Map[Method, SegmentSubtree[A1]]]
    val segments = RouteTree.flattenPathCodec(pattern.pathCodec)
    val subtree  = SegmentSubtree.single(segments, value)
    val existing = widened.getOrElse(pattern.method, SegmentSubtree.empty[A1])
    RouteTree(widened.updated(pattern.method, existing.merge(subtree)))
  }

  /**
   * Finds the value for a request, or `None`. A `HEAD` request with no matching
   * `HEAD` route falls back to the `GET` routes. `Method.ANY` is not a key of
   * the tree, so looking it up finds nothing.
   */
  def get(method: Method, path: Path): Option[A] =
    roots.get(method).flatMap(_.get(path.segments, 0)).orElse {
      if (method == Method.HEAD) roots.get(Method.GET).flatMap(_.get(path.segments, 0)) else None
    }

  /**
   * Combines two trees; where both have a route with the same method and
   * segment shapes, the one from `that` wins.
   */
  def merge[A1 >: A](that: RouteTree[A1]): RouteTree[A1] = {
    val mergedRoots = (self.roots.keySet ++ that.roots.keySet).iterator.map { method =>
      val left  = self.roots.getOrElse(method, SegmentSubtree.empty[A1])
      val right = that.roots.getOrElse(method, SegmentSubtree.empty[A1])
      method -> left.merge(right)
    }.toMap
    RouteTree(mergedRoots)
  }

  /**
   * Applies `f` to every value, keeping the routes.
   */
  def map[B](f: A => B): RouteTree[B] =
    RouteTree(roots.map { case (method, subtree) => method -> subtree.map(f) })
}

/**
 * Builds route trees.
 */
object RouteTree {

  /**
   * A tree with no routes.
   */
  def empty[A]: RouteTree[A] = RouteTree(Map.empty)

  private[endpoint] def flattenPathCodec(codec: PathCodec[_]): Chunk[SegmentCodec[_]] =
    codec match {
      case PathCodec.Segment(seg)           => Chunk(seg)
      case PathCodec.Concat(left, right, _) => flattenPathCodec(left) ++ flattenPathCodec(right)
      case PathCodec.Transform(inner, _, _) => flattenPathCodec(inner)
      case PathCodec.Ignored(inner)         => flattenPathCodec(inner)
      case PathCodec.Fallback(_, _)         =>
        throw new IllegalStateException("Fallback paths must be expanded before flattening")
    }
}

/**
 * One level of a [[RouteTree]]: `literals` holds the branches for literal
 * segments, `others` the branches for capturing segments keyed by
 * [[SegmentCodec.Key]], and `value` the route that ends at this level. In a
 * tree built with `add` or `merge`, `others` is sorted in match order (see
 * [[RouteTree]]).
 */
final case class SegmentSubtree[A](
  literals: Map[String, SegmentSubtree[A]],
  others: ListMap[SegmentCodec.Key, (SegmentCodec[_], SegmentSubtree[A])],
  value: Option[A]
) { self =>

  /**
   * Looks up the route for `segments`, starting at `index`. A literal branch is
   * tried first, then the capturing branches in order, each tested with
   * [[SegmentCodec.matches]], which checks the segment's shape without decoding
   * it. When the segments run out, the value at this level is returned, or else
   * the value of a trailing branch below it.
   */
  def get(segments: Chunk[String], index: Int): Option[A] =
    if (index >= segments.length) {
      value.orElse(others.get(SegmentCodec.Key.Trailing).flatMap(_._2.value))
    } else {
      val segment = segments(index)
      literals.get(segment).flatMap(_.get(segments, index + 1)).orElse {
        others.iterator.flatMap { case (_, (codec, subtree)) =>
          val consumed = SegmentCodec.matches(codec, segments, index)
          if (consumed > 0) subtree.get(segments, index + consumed) else None
        }.nextOption()
      }
    }

  /**
   * Combines two levels branch by branch; where both have a value, the one from
   * `that` wins. For a capturing branch on both sides, the segment codec of
   * this level is kept.
   */
  def merge[A1 >: A](that: SegmentSubtree[A1]): SegmentSubtree[A1] = {
    val mergedLiterals = (self.literals.keySet ++ that.literals.keySet).iterator.map { key =>
      val left  = self.literals.getOrElse(key, SegmentSubtree.empty[A1])
      val right = that.literals.getOrElse(key, SegmentSubtree.empty[A1])
      key -> left.merge(right)
    }.toMap

    val mergedOthers = {
      val keys = (self.others.keySet ++ that.others.keySet).toList.distinct.sorted
      ListMap.from(keys.map { key =>
        val leftEntry =
          self.others.get(key).map { case (codec, subtree) => codec -> subtree.asInstanceOf[SegmentSubtree[A1]] }
        val rightEntry = that.others.get(key)
        val merged     = (leftEntry, rightEntry) match {
          case (Some((leftCodec, leftSubtree)), Some((_, rightSubtree))) =>
            leftCodec -> leftSubtree.merge(rightSubtree)
          case (Some(entry), None) => entry
          case (None, Some(entry)) => entry
          case (None, None)        => throw new IllegalStateException(s"Missing route subtree for key: $key")
        }
        key -> merged
      })
    }

    SegmentSubtree(mergedLiterals, mergedOthers, that.value.orElse(self.value))
  }

  /**
   * Applies `f` to every value below this level.
   */
  def map[B](f: A => B): SegmentSubtree[B] =
    SegmentSubtree(
      literals = literals.map { case (key, subtree) => key -> subtree.map(f) },
      others = ListMap.from(others.map { case (key, entries) =>
        key -> (entries._1 -> entries._2.map(f))
      }),
      value = value.map(f)
    )
}

/**
 * Builds route tree levels.
 */
object SegmentSubtree {

  /**
   * A level with no branches and no value.
   */
  def empty[A]: SegmentSubtree[A] = SegmentSubtree(Map.empty, ListMap.empty, None)

  /**
   * Builds the chain of levels for one route: one level per segment, with
   * `value` at the end. [[SegmentCodec.Empty]] segments are skipped.
   */
  def single[A](segments: Chunk[SegmentCodec[_]], value: A): SegmentSubtree[A] = {
    val filtered = segments.filter(_ != SegmentCodec.Empty)
    filtered.foldRight(SegmentSubtree[A](Map.empty, ListMap.empty, Some(value))) { case (codec, child) =>
      codec match {
        case SegmentCodec.Literal(value, _, _) =>
          SegmentSubtree(Map(value -> child), ListMap.empty, None)
        case other =>
          SegmentSubtree(Map.empty, ListMap(SegmentCodec.key(other) -> (other -> child)), None)
      }
    }
  }
}
