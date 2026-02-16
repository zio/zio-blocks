/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

package zio.blocks.chunk

import zio.test._

object ChunkMapSpec extends ChunkBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("ChunkMapSpec")(
    suite("construction")(
      test("empty creates an empty map") {
        val map = ChunkMap.empty[String, Int]
        assertTrue(
          map.isEmpty,
          map.size == 0,
          map.empty.isEmpty,
          map.empty.size == 0
        )
      },
      test("apply creates map from varargs") {
        val map = ChunkMap("a" -> 1, "b" -> 2, "c" -> 3)
        assertTrue(map.size == 3, map("a") == 1, map("b") == 2, map("c") == 3)
      },
      test("from creates map from iterable") {
        val map = ChunkMap.from(List("x" -> 10, "y" -> 20))
        assertTrue(map.size == 2, map("x") == 10, map("y") == 20)
      },
      test("fromChunk creates map from Chunk of pairs") {
        val chunk = Chunk(("a", 1), ("b", 2))
        val map   = ChunkMap.fromChunk(chunk)
        assertTrue(map.size == 2, map("a") == 1, map("b") == 2)
      },
      test("fromChunks creates map from parallel chunks") {
        val keys   = Chunk("a", "b", "c")
        val values = Chunk(1, 2, 3)
        val map    = ChunkMap.fromChunks(keys, values)
        assertTrue(map.size == 3, map("a") == 1, map("b") == 2, map("c") == 3)
      }
    ),
    suite("order preservation")(
      test("iteration preserves insertion order") {
        val map      = ChunkMap("z" -> 1, "a" -> 2, "m" -> 3)
        val iterated = map.toList.map(_._1)
        assertTrue(iterated == List("z", "a", "m"))
      },
      test("keysChunk preserves insertion order") {
        val map  = ChunkMap("z" -> 1, "a" -> 2, "m" -> 3)
        val keys = map.keysChunk
        assertTrue(keys == Chunk("z", "a", "m"))
      },
      test("valuesChunk preserves insertion order") {
        val map    = ChunkMap("z" -> 1, "a" -> 2, "m" -> 3)
        val values = map.valuesChunk
        assertTrue(values == Chunk(1, 2, 3))
      },
      test("toChunk preserves insertion order") {
        val map   = ChunkMap("z" -> 1, "a" -> 2, "m" -> 3)
        val chunk = map.toChunk
        assertTrue(chunk == Chunk(("z", 1), ("a", 2), ("m", 3)))
      }
    ),
    suite("lookup")(
      test("get returns Some for existing key") {
        val map = ChunkMap("a" -> 1, "b" -> 2)
        assertTrue(map.get("a") == Some(1), map.get("b") == Some(2))
      },
      test("get returns None for missing key") {
        val map = ChunkMap("a" -> 1)
        assertTrue(map.get("missing") == None)
      },
      test("apply throws for missing key") {
        val map    = ChunkMap("a" -> 1)
        val caught = try {
          map("missing")
          false
        } catch {
          case _: NoSuchElementException => true
        }
        assertTrue(caught)
      },
      test("contains returns true for existing key") {
        val map = ChunkMap("a" -> 1, "b" -> 2)
        assertTrue(map.contains("a"), map.contains("b"))
      },
      test("contains returns false for missing key") {
        val map = ChunkMap("a" -> 1)
        assertTrue(!map.contains("missing"))
      },
      test("atIndex accesses by position") {
        val map = ChunkMap("z" -> 1, "a" -> 2, "m" -> 3)
        assertTrue(map.atIndex(0) == ("z", 1), map.atIndex(1) == ("a", 2), map.atIndex(2) == ("m", 3))
      }
    ),
    suite("modification")(
      test("updated adds new key-value pair") {
        val map     = ChunkMap("a" -> 1)
        val updated = map.updated("b", 2)
        assertTrue(updated.size == 2, updated("a") == 1, updated("b") == 2)
      },
      test("updated replaces existing key") {
        val map     = ChunkMap("a" -> 1, "b" -> 2)
        val updated = map.updated("a", 100)
        assertTrue(updated.size == 2, updated("a") == 100, updated("b") == 2)
      },
      test("updated preserves order when replacing") {
        val map     = ChunkMap("z" -> 1, "a" -> 2, "m" -> 3)
        val updated = map.updated("a", 100)
        assertTrue(updated.keysChunk == Chunk("z", "a", "m"))
      },
      test("updated appends new key at end") {
        val map     = ChunkMap("z" -> 1, "a" -> 2)
        val updated = map.updated("m", 3)
        assertTrue(updated.keysChunk == Chunk("z", "a", "m"))
      },
      test("removed removes existing key") {
        val map     = ChunkMap("a" -> 1, "b" -> 2, "c" -> 3)
        val removed = map.removed("b")
        assertTrue(removed.size == 2, removed.contains("a"), !removed.contains("b"), removed.contains("c"))
      },
      test("removed returns same map for missing key") {
        val map     = ChunkMap("a" -> 1)
        val removed = map.removed("missing")
        assertTrue(map eq removed)
      },
      test("removed preserves order") {
        val map     = ChunkMap("z" -> 1, "a" -> 2, "m" -> 3)
        val removed = map.removed("a")
        assertTrue(removed.keysChunk == Chunk("z", "m"))
      }
    ),
    suite("transformation")(
      test("map transforms key-value pairs") {
        val map1    = ChunkMap("a" -> 1, "b" -> 2)
        val map2    = ChunkMap.empty[String, Int]
        val mapped1 = map1.map { case (k, v) => (k.toUpperCase, v * 10) }
        val mapped2 = map2.map { case (k, v) => (k.toUpperCase, v * 10) }
        assertTrue(
          mapped1("A") == 10,
          mapped1("B") == 20,
          mapped2.isEmpty
        )
      },
      test("filter keeps matching pairs") {
        val map1      = ChunkMap("a" -> 1, "b" -> 2, "c" -> 3)
        val map2      = ChunkMap.empty[String, Int]
        val filtered1 = map1.filter { case (_, v) => v % 2 == 1 }
        val filtered2 = map2.filter { case (_, v) => v % 2 == 1 }
        assertTrue(
          filtered1.size == 2,
          filtered1.contains("a"),
          filtered1.contains("c"),
          !filtered1.contains("b"),
          filtered2.isEmpty
        )
      },
      test("flatMap transforms to multiple pairs") {
        val map1        = ChunkMap("a" -> 1)
        val map2        = ChunkMap.empty[String, Int]
        val flatMapped1 = map1.flatMap { case (k, v) => List((k, v), (k + k, v + v)) }
        val flatMapped2 = map2.flatMap { case (k, v) => List((k, v), (k + k, v + v)) }
        assertTrue(flatMapped1.size == 2, flatMapped1("a") == 1, flatMapped1("aa") == 2, flatMapped2.isEmpty)
      },
      test("transformValues transforms only values") {
        val map         = ChunkMap("a" -> 1, "b" -> 2)
        val transformed = map.transformValues(_ * 10)
        assertTrue(transformed("a") == 10, transformed("b") == 20, transformed.keysChunk == Chunk("a", "b"))
      },
      test("concat combines maps") {
        val map1   = ChunkMap("a" -> 1, "b" -> 2)
        val map2   = ChunkMap("c" -> 3, "d" -> 4)
        val concat = map1.concat(map2)
        assertTrue(concat.size == 4)
      }
    ),
    suite("iteration")(
      test("foreach") {
        val map  = ChunkMap("a" -> 1, "b" -> 2)
        var map2 = ChunkMap.empty[String, Int]
        map.foreach { case (k, v) => map2 = map2.updated(k.toUpperCase, v * 10) }
        assertTrue(map2("A") == 10, map2("B") == 20)
      },
      test("foreachEntry") {
        val map  = ChunkMap("a" -> 1, "b" -> 2)
        var map2 = ChunkMap.empty[String, Int]
        map.foreachEntry { (k, v) => map2 = map2.updated(k.toUpperCase, v * 10) }
        assertTrue(map2("A") == 10, map2("B") == 20)
      },
      test("keysIterator and valuesIterator") {
        val map            = ChunkMap("a" -> 1, "b" -> 2)
        var map2           = ChunkMap.empty[String, Int]
        val keysIterator   = map.keysIterator
        val valuesIterator = map.valuesIterator
        while (keysIterator.hasNext && valuesIterator.hasNext) {
          map2 = map2.updated(keysIterator.next().toUpperCase, valuesIterator.next() * 10)
        }
        assertTrue(map2("A") == 10, map2("B") == 20)
      },
      test("keyAtIndex and valueAtIndex") {
        val map  = ChunkMap("a" -> 1, "b" -> 2)
        var map2 = ChunkMap.empty[String, Int]
        val len  = map.size
        var idx  = 0
        while (idx < len) {
          map2 = map2.updated(map.keyAtIndex(idx).toUpperCase, map.valueAtIndex(idx) * 10)
          idx += 1
        }
        assertTrue(map2("A") == 10, map2("B") == 20)
      }
    ),
    suite("equality")(
      test("equals returns true for same content different order") {
        val map1 = ChunkMap("a" -> 1, "b" -> 2)
        val map2 = ChunkMap("b" -> 2, "a" -> 1)
        assertTrue(map1 == map2)
      },
      test("equals returns false for different content") {
        val map1 = ChunkMap("a" -> 1, "b" -> 2)
        val map2 = ChunkMap("a" -> 1, "b" -> 3)
        assertTrue(map1 != map2)
      },
      test("equals works with scala.collection.Map") {
        val chunkMap = ChunkMap("a" -> 1, "b" -> 2)
        val scalaMap = Map("a" -> 1, "b" -> 2)
        assertTrue(chunkMap == scalaMap, scalaMap == chunkMap)
      },
      test("hashCode is same for equal maps") {
        val map1 = ChunkMap("a" -> 1, "b" -> 2)
        val map2 = ChunkMap("b" -> 2, "a" -> 1)
        assertTrue(map1.hashCode() == map2.hashCode())
      }
    ),
    suite("indexed")(
      test("indexed provides O(1) lookup") {
        val map     = ChunkMap("a" -> 1, "b" -> 2, "c" -> 3)
        val indexed = map.indexed
        assertTrue(indexed("a") == 1, indexed("b") == 2, indexed("c") == 3)
      },
      test("indexed.contains is O(1)") {
        val map     = ChunkMap("a" -> 1, "b" -> 2)
        val indexed = map.indexed
        assertTrue(indexed.contains("a"), !indexed.contains("missing"))
      },
      test("indexed preserves iteration order") {
        val map     = ChunkMap("z" -> 1, "a" -> 2, "m" -> 3)
        val indexed = map.indexed
        assertTrue(indexed.toList.map(_._1) == List("z", "a", "m"))
      },
      test("indexed.toChunkMap returns original") {
        val map     = ChunkMap("a" -> 1, "b" -> 2)
        val indexed = map.indexed
        assertTrue(indexed.toChunkMap eq map)
      }
    ),
    suite("builder")(
      test("builder handles duplicate keys by keeping last value") {
        val builder = ChunkMap.newBuilder[String, Int]
        builder.addAll(Seq("a" -> 1, "b" -> 2, "a" -> 100, "c" -> 3, "d" -> 4, "e" -> 5))
        val map = builder.result()
        assertTrue(map.size == 5, map("a") == 100, map("b") == 2)
      },
      test("builder preserves first occurrence order for duplicates") {
        val builder = ChunkMap.newBuilder[String, Int]
        builder.addAll(Seq("a" -> 1, "b" -> 2, "a" -> 100))
        val map = builder.result()
        assertTrue(map.keysChunk == Chunk("a", "b"))
      },
      test("builder can be reused after cleaning") {
        val builder = ChunkMap.newBuilder[String, Int]
        builder.sizeHint(10)
        builder.addOne("a" -> 1)
        builder.clear()
        val map = builder.result()
        builder.addOne("b" -> 2)
        assertTrue(map.isEmpty, builder.result().keysChunk == Chunk("b"))
      }
    )
  )
}
