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
 * Routing trie keyed by HTTP method, then by path segments. Literals are
 * matched first, then dynamic segments in priority order (int > long > uuid >
 * bool > string > combined > trailing). HEAD requests fall back to GET
 * handlers. Merge prefers right-hand-side values on conflict.
 */
final case class RouteTree[A](
  roots: Map[Method, SegmentSubtree[A]]
) { self =>

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

  def get(method: Method, path: Path): Option[A] =
    roots.get(method).flatMap(_.get(path.segments, 0)).orElse {
      if (method == Method.HEAD) roots.get(Method.GET).flatMap(_.get(path.segments, 0)) else None
    }

  def merge[A1 >: A](that: RouteTree[A1]): RouteTree[A1] = {
    val mergedRoots = (self.roots.keySet ++ that.roots.keySet).iterator.map { method =>
      val left  = self.roots.getOrElse(method, SegmentSubtree.empty[A1])
      val right = that.roots.getOrElse(method, SegmentSubtree.empty[A1])
      method -> left.merge(right)
    }.toMap
    RouteTree(mergedRoots)
  }

  def map[B](f: A => B): RouteTree[B] =
    RouteTree(roots.map { case (method, subtree) => method -> subtree.map(f) })
}

object RouteTree {
  def empty[A]: RouteTree[A] = RouteTree(Map.empty)

  private[endpoint] def flattenPathCodec(codec: PathCodec[_]): Chunk[SegmentCodec[_]] =
    codec match {
      case PathCodec.Segment(seg)           => Chunk(seg)
      case PathCodec.Concat(left, right, _) => flattenPathCodec(left) ++ flattenPathCodec(right)
      case PathCodec.Fallback(_, _)         =>
        throw new IllegalStateException("Fallback paths must be expanded before flattening")
    }
}

/**
 * A single level of the routing trie. `literals` holds exact-match branches,
 * `others` holds dynamic segment branches keyed by [[SegmentCodec.Key]] and
 * ordered by match priority.
 */
final case class SegmentSubtree[A](
  literals: Map[String, SegmentSubtree[A]],
  others: ListMap[SegmentCodec.Key, (SegmentCodec[_], SegmentSubtree[A])],
  value: Option[A]
) { self =>

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

  def map[B](f: A => B): SegmentSubtree[B] =
    SegmentSubtree(
      literals = literals.map { case (key, subtree) => key -> subtree.map(f) },
      others = ListMap.from(others.map { case (key, entries) =>
        key -> (entries._1 -> entries._2.map(f))
      }),
      value = value.map(f)
    )
}

object SegmentSubtree {
  def empty[A]: SegmentSubtree[A] = SegmentSubtree(Map.empty, ListMap.empty, None)

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
