/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

import zio.test.Assertion._
import zio.test._
import scala.collection.mutable

object ChunkSpec extends ChunkBaseSpec {
  case class Value(i: Int) extends AnyVal

  val intGen: Gen[Any, Int] = Gen.int(-10, 10)

  def toBoolFn[R, A]: Gen[R, A => Boolean] = Gen.function(Gen.boolean)

  def tinyChunks[R, A](a: Gen[R, A]): Gen[R, Chunk[A]] = genChunkBounded(0, 3)(a)

  def smallChunks[R, A](a: Gen[R, A]): Gen[R, Chunk[A]] = Gen.small(n => genChunkN(n)(a))

  def chunkWithIndex[R, A](a: Gen[R, A]): Gen[R, (Chunk[A], Int)] =
    for {
      chunk <- Gen.chunkOf1(a)
      idx   <- Gen.int(0, chunk.length - 1)
    } yield (zioChunkToChunk(chunk), idx)

  override def spec: Spec[TestEnvironment, Any] = suite("ChunkSpec")(
    suite("size/length")(
      test("concatenated size must match length") {
        val chunk = Chunk.empty ++ Chunk.fromArray(Array(1, 2)) ++ Chunk(3, 4, 5) ++ Chunk.single(6)
        assert(chunk.size)(equalTo(chunk.length))
      },
      test("empty size must match length") {
        val chunk = Chunk.empty
        assert(chunk.size)(equalTo(chunk.length))
      },
      test("fromArray size must match length") {
        val chunk = Chunk.fromArray(Array(1, 2, 3))
        assert(chunk.size)(equalTo(chunk.length))
      },
      test("fromIterable size must match length") {
        val chunk = Chunk.fromIterable(List("1", "2", "3"))
        assert(chunk.size)(equalTo(chunk.length))
      },
      test("single size must match length") {
        val chunk = Chunk.single(true)
        assert(chunk.size)(equalTo(chunk.length))
      }
    ),
    suite("append")(
      test("apply") {
        val chunksWithIndex: Gen[Any, (Chunk[Int], Chunk[Int], Int)] =
          for {
            p  <- Gen.boolean
            as <- Gen.chunkOf(Gen.int)
            bs <- Gen.chunkOf1(Gen.int)
            n  <- Gen.int(0, as.length + bs.length - 1)
          } yield
            if (p) (zioChunkToChunk(as), zioChunkToChunk(bs), n) else (zioChunkToChunk(bs), zioChunkToChunk(as), n)
        check(chunksWithIndex) { case (as, bs, n) =>
          assert(bs.foldLeft(as)(_ :+ _).apply(n))(equalTo((as ++ bs).apply(n)))
        }
      },
      test("buffer full") {
        check(genChunk(Gen.int), genChunk(Gen.int)) { (as, bs) =>
          def addAll[A](l: Chunk[A], r: Chunk[A]): Chunk[A] = r.foldLeft(l)(_ :+ _)

          assert(List.fill(100)(bs).foldLeft(as)(addAll))(equalTo(List.fill(100)(bs).foldLeft(as)(_ ++ _)))
        }
      },
      test("equals") {
        check(genChunk(Gen.int), genChunk(Gen.int)) { (as, bs) =>
          assert(bs.foldLeft(as)(_ :+ _))(equalTo(as ++ bs))
        }
      },
      test("length") {
        check(genChunk(Gen.int), smallChunks(Gen.int)) { (as, bs) =>
          val actual   = bs.foldLeft(as)(_ :+ _).length
          val expected = (as ++ bs).length
          assert(actual)(equalTo(expected))
        }
      },
      test("returns most specific type") {
        assert((Chunk("foo"): Seq[String]) :+ "post1")(isSubtype[Chunk[String]](equalTo(Chunk("foo", "post1"))))
      },
      test("fails if the chunk does not contain the specified index") {
        val chunk    = Chunk(1, 2, 3)
        val appended = chunk :+ 4
        val _        = chunk :+ 5
        assert(appended(4))(throwsA[IndexOutOfBoundsException])
      }
    ),
    suite("prepend")(
      test("apply") {
        val chunksWithIndex: Gen[Any, (Chunk[Int], Chunk[Int], Int)] =
          for {
            p  <- Gen.boolean
            as <- Gen.chunkOf(Gen.int)
            bs <- Gen.chunkOf1(Gen.int)
            n  <- Gen.int(0, as.length + bs.length - 1)
          } yield
            if (p) (zioChunkToChunk(as), zioChunkToChunk(bs), n) else (zioChunkToChunk(bs), zioChunkToChunk(as), n)
        check(chunksWithIndex) { case (as, bs, n) =>
          assert(as.foldRight(bs)(_ +: _).apply(n))(equalTo((as ++ bs).apply(n)))
        }
      },
      test("buffer full") {
        check(genChunk(Gen.int), genChunk(Gen.int)) { (as, bs) =>
          def addAll[A](l: Chunk[A], r: Chunk[A]): Chunk[A] = l.foldRight(r)(_ +: _)

          assert(List.fill(100)(as).foldRight(bs)(addAll))(equalTo(List.fill(100)(as).foldRight(bs)(_ ++ _)))
        }
      },
      test("equals") {
        check(genChunk(Gen.int), genChunk(Gen.int)) { (as, bs) =>
          assert(as.foldRight(bs)(_ +: _))(equalTo(as ++ bs))
        }
      },
      test("length") {
        check(genChunk(Gen.int), smallChunks(Gen.int)) { (as, bs) =>
          assert(as.foldRight(bs)(_ +: _).length)(equalTo((as ++ bs).length))
        }
      },
      test("returns most specific type") {
        assert("pre1" +: (Chunk("foo"): Seq[String]))(isSubtype[Chunk[String]](equalTo(Chunk("pre1", "foo"))))
      },
      test("fails if the chunk does not contain the specified index") {
        assert((0 +: Chunk(1, 2, 3))(-1))(throwsA[IndexOutOfBoundsException])
      }
    ),
    test("apply") {
      check(chunkWithIndex(Gen.unit)) { case (chunk, i) =>
        assert(chunk.apply(i))(equalTo(chunk.toList.apply(i)))
      }
    },
    suite("specialized accessors")(
      test("boolean") {
        check(chunkWithIndex(Gen.boolean)) { case (chunk, i) =>
          assert(chunk.boolean(i))(equalTo(chunk.toList.apply(i)))
        }
      },
      test("byte") {
        check(chunkWithIndex(Gen.byte(0, 127))) { case (chunk, i) =>
          assert(chunk.byte(i))(equalTo(chunk.toList.apply(i)))
        }
      },
      test("char") {
        check(chunkWithIndex(Gen.char(33, 123))) { case (chunk, i) =>
          assert(chunk.char(i))(equalTo(chunk.toList.apply(i)))
        }
      },
      test("short") {
        check(chunkWithIndex(Gen.short(5, 100))) { case (chunk, i) =>
          assert(chunk.short(i))(equalTo(chunk.toList.apply(i)))
        }
      },
      test("int") {
        check(chunkWithIndex(Gen.int(1, 142))) { case (chunk, i) =>
          assert(chunk.int(i))(equalTo(chunk.toList.apply(i)))
        }
      },
      test("long") {
        check(chunkWithIndex(Gen.long(1, 142))) { case (chunk, i) =>
          assert(chunk.long(i))(equalTo(chunk.toList.apply(i)))
        }
      },
      test("float") {
        check(chunkWithIndex(Gen.double(0.0, 100.0).map(_.toFloat))) { case (chunk, i) =>
          assert(chunk.float(i))(equalTo(chunk.toList.apply(i)))
        }
      },
      test("dedupe") {
        assert(Chunk(1, 2, 2, 3, 4, 4).dedupe)(equalTo(Chunk(1, 2, 3, 4)))
      },
      test("double") {
        check(chunkWithIndex(Gen.double(1.0, 200.0))) { case (chunk, i) =>
          assert(chunk.double(i))(equalTo(chunk.toList.apply(i)))
        }
      }
    ),
    test("corresponds") {
      val genChunk    = smallChunks(intGen)
      val genFunction = Gen.function[Any, (Int, Int), Boolean](Gen.boolean).map(Function.untupled(_))
      check(genChunk, genChunk, genFunction) { (as, bs, f) =>
        assert(as.corresponds(bs)(f))(equalTo(as.toList.corresponds(bs.toList)(f)))
      }
    },
    test("chunkIterator") {
      def roundTrip[A](c: Chunk[A]): Chunk[A] = {
        val ci      = c.chunkIterator
        val builder = ChunkBuilder.make[A](c.length)
        var idx     = 0
        if (ci.hasNextAt(idx)) builder.addOne(ci.nextAt(idx))
        val sci = ci.sliceIterator(1, c.length)
        while (sci.hasNextAt(idx)) {
          builder.addOne(sci.nextAt(idx))
          idx += 1
        }
        builder.result()
      }

      check(smallChunks(Gen.boolean))(c => assert(roundTrip(c))(equalTo(c))) &&
      check(smallChunks(Gen.byte))(c => assert(roundTrip(c))(equalTo(c))) &&
      check(smallChunks(Gen.char))(c => assert(roundTrip(c))(equalTo(c))) &&
      check(smallChunks(Gen.short))(c => assert(roundTrip(c))(equalTo(c))) &&
      check(smallChunks(Gen.float))(c => assert(roundTrip(c))(equalTo(c))) &&
      check(smallChunks(Gen.int))(c => assert(roundTrip(c))(equalTo(c))) &&
      check(smallChunks(Gen.double))(c => assert(roundTrip(c))(equalTo(c))) &&
      check(smallChunks(Gen.long))(c => assert(roundTrip(c))(equalTo(c))) &&
      check(smallChunks(Gen.string))(c => assert(roundTrip(c))(equalTo(c)))
    },
    test("fill") {
      val smallInt = Gen.int(-10, 10)
      check(smallInt, smallInt) { (n, elem) =>
        assert(Chunk.fill(n)(elem))(equalTo(Chunk.fromArray(Array.fill(n)(elem))))
      }
    },
    test("iterate") {
      val smallInt = Gen.int(-10, 10)
      check(smallInt, smallInt, Gen.function(Gen.int)) { (start, len, f) =>
        val actual   = Chunk.iterate(start, len)(f)
        val expected = Chunk.fromArray(Array.iterate(start, len)(f))
        assert(actual)(equalTo(expected))
      }
    },
    test("splitWhere") {
      assert(Chunk(1, 2, 3, 4).splitWhere(_ == 2))(equalTo((Chunk(1), Chunk(2, 3, 4)))) &&
      assert(Chunk(1, 2, 3, 4).splitWhere(_ => false))(equalTo((Chunk(1, 2, 3, 4), Chunk.empty)))
    },
    test("span") {
      assert(Chunk(1, 2, 3, 4).span(_ != 2))(equalTo((Chunk(1), Chunk(2, 3, 4)))) &&
      assert(Chunk(1, 2, 3, 4).span(_ => true))(equalTo((Chunk(1, 2, 3, 4), Chunk.empty)))
    },
    test("length") {
      check(genChunk(intGen))(chunk => assert(chunk.length)(equalTo(chunk.toList.length)))
    },
    test("equals") {
      check(genChunk(intGen), genChunk(intGen)) { (c1, c2) =>
        assert(c1.equals(c2))(equalTo(c1.toList.equals(c2.toList)))
      } &&
      assert(Chunk(1, 2, 3, 4, 5))(Assertion.not(equalTo(Chunk(1, 2, 3, 4, 5, 6))))
    },
    test("materialize") {
      check(genChunk(intGen))(c => assert(c.materialize.toList)(equalTo(c.toList)))
    },
    test("nonEmptyOrElse") {
      check(genChunk(intGen))(c => assert(c.nonEmptyOrElse(0)(_.length))(equalTo(c.length)))
    },
    test("foldLeft") {
      check(Gen.alphaNumericString, Gen.function2(Gen.alphaNumericString), smallChunks(Gen.alphaNumericString)) {
        (s0, f, c) => assert(c.fold(s0)(f))(equalTo(c.toArray.foldLeft(s0)(f)))
      }
    },
    test("foldRight") {
      assert((Chunk("a") ++ Chunk("b") ++ Chunk("c")).foldRight("d")(_ + _))(equalTo("abcd"))
    },
    test("foldWhile") {
      assert(Chunk(1, 2, 3, 4, 5).foldWhile(0)(_ < 10)(_ + _))(equalTo(10))
    },
    test("fromArray") {
      check(smallChunks(Gen.boolean))(c => assert(Chunk.fromArray(c.toArray))(equalTo(c))) &&
      check(smallChunks(Gen.byte))(c => assert(Chunk.fromArray(c.toArray))(equalTo(c))) &&
      check(smallChunks(Gen.char))(c => assert(Chunk.fromArray(c.toArray))(equalTo(c))) &&
      check(smallChunks(Gen.short))(c => assert(Chunk.fromArray(c.toArray))(equalTo(c))) &&
      check(smallChunks(Gen.float))(c => assert(Chunk.fromArray(c.toArray))(equalTo(c))) &&
      check(smallChunks(Gen.int))(c => assert(Chunk.fromArray(c.toArray))(equalTo(c))) &&
      check(smallChunks(Gen.double))(c => assert(Chunk.fromArray(c.toArray))(equalTo(c))) &&
      check(smallChunks(Gen.long))(c => assert(Chunk.fromArray(c.toArray))(equalTo(c))) &&
      check(smallChunks(Gen.string))(c => assert(Chunk.fromArray(c.toArray))(equalTo(c)))
    },
    test("fromJavaIterable") {
      check(smallChunks(Gen.int)) { c =>
        val arr  = c.toArray
        val list = new java.util.ArrayList[Int](arr.length)
        arr.foreach(list.add)
        assert(Chunk.fromJavaIterable(list))(equalTo(c))
      }
    },
    test("fromJavaIterator") {
      check(smallChunks(Gen.int)) { c =>
        val arr  = c.toArray
        val list = new java.util.ArrayList[Int](arr.length)
        arr.foreach(list.add)
        assert(Chunk.fromJavaIterator(list.iterator()))(equalTo(c))
      }
    },
    test("map") {
      val fn1 = Gen.function[Any, Boolean, Boolean](Gen.boolean)
      val fn2 = Gen.function[Any, Byte, Byte](Gen.byte)
      val fn3 = Gen.function[Any, Char, Char](Gen.char)
      val fn4 = Gen.function[Any, Short, Short](Gen.short)
      val fn5 = Gen.function[Any, Float, Float](Gen.float)
      val fn6 = Gen.function[Any, Int, Int](Gen.int)
      val fn7 = Gen.function[Any, Double, Double](Gen.double)
      val fn8 = Gen.function[Any, Long, Long](Gen.long)
      val fn9 = Gen.function[Any, String, String](Gen.string)
      val f1  = (x: Boolean) => if (x) x else x.toString
      val f2  = (x: Byte) => if (x > 0) x else x.toString
      val f3  = (x: Char) => if ((x & 1) == 0) x else x.toString
      val f4  = (x: Short) => if (x > 0) x else x.toString
      val f5  = (x: Float) => if ((java.lang.Float.floatToIntBits(x) & 1) == 0) x else x.toString
      val f6  = (x: Int) => if (x > 0) x else x.toString
      val f7  = (x: Double) => if ((java.lang.Double.doubleToLongBits(x) & 1) == 0) x else x.toString
      val f8  = (x: Long) => if (x > 0) x else x.toString
      check(smallChunks(Gen.boolean), fn1)((c, f) => assert(c.map(f).toList)(equalTo(c.toList.map(f)))) &&
      check(smallChunks(Gen.byte), fn2)((c, f) => assert(c.map(f).toList)(equalTo(c.toList.map(f)))) &&
      check(smallChunks(Gen.char), fn3)((c, f) => assert(c.map(f).toList)(equalTo(c.toList.map(f)))) &&
      check(smallChunks(Gen.short), fn4)((c, f) => assert(c.map(f).toList)(equalTo(c.toList.map(f)))) &&
      check(smallChunks(Gen.float), fn5)((c, f) => assert(c.map(f).toList)(equalTo(c.toList.map(f)))) &&
      check(smallChunks(Gen.int), fn6)((c, f) => assert(c.map(f).toList)(equalTo(c.toList.map(f)))) &&
      check(smallChunks(Gen.double), fn7)((c, f) => assert(c.map(f).toList)(equalTo(c.toList.map(f)))) &&
      check(smallChunks(Gen.long), fn8)((c, f) => assert(c.map(f).toList)(equalTo(c.toList.map(f)))) &&
      check(smallChunks(Gen.string), fn9)((c, f) => assert(c.map(f).toList)(equalTo(c.toList.map(f)))) &&
      check(smallChunks(Gen.boolean))(c => assert(c.map(f1).toList)(equalTo(c.toList.map(f1)))) &&
      check(smallChunks(Gen.byte))(c => assert(c.map(f2).toList)(equalTo(c.toList.map(f2)))) &&
      check(smallChunks(Gen.char))(c => assert(c.map(f3).toList)(equalTo(c.toList.map(f3)))) &&
      check(smallChunks(Gen.short))(c => assert(c.map(f4).toList)(equalTo(c.toList.map(f4)))) &&
      check(smallChunks(Gen.float))(c => assert(c.map(f5).toList)(equalTo(c.toList.map(f5)))) &&
      check(smallChunks(Gen.int))(c => assert(c.map(f6).toList)(equalTo(c.toList.map(f6)))) &&
      check(smallChunks(Gen.double))(c => assert(c.map(f7).toList)(equalTo(c.toList.map(f7)))) &&
      check(smallChunks(Gen.long))(c => assert(c.map(f8).toList)(equalTo(c.toList.map(f8))))
    },
    test("mapAccum") {
      assert(Chunk(1, 1, 1).mapAccum(0)((s, el) => (s + el, s + el)))(equalTo((3, Chunk(1, 2, 3))))
    },
    test("flatMap") {
      val fn = Gen.function[Any, Int, Chunk[Int]](smallChunks(intGen))
      check(smallChunks(intGen), fn) { (c, f) =>
        assert(c.flatMap(f).toList)(equalTo(c.toList.flatMap(f.andThen(_.toList))))
      }
    },
    test("flatten") {
      assertTrue(Chunk(Some(1), Some(2), None, None).flatten == Chunk(1, 2))
    },
    test("headOption") {
      check(genChunk(intGen))(c => assert(c.headOption)(equalTo(c.toList.headOption)))
    },
    test("lastOption") {
      check(genChunk(intGen))(c => assert(c.lastOption)(equalTo(c.toList.lastOption)))
    },
    test("indexWhere") {
      val fn = Gen.function[Any, Int, Boolean](Gen.boolean)
      check(smallChunks(intGen), smallChunks(intGen), fn, intGen) { (left, right, p, from) =>
        val actual   = (left ++ right).indexWhere(p, from)
        val expected = (left.toVector ++ right.toVector).indexWhere(p, from)
        assert(actual)(equalTo(expected))
      }
    },
    test("exists") {
      val fn = Gen.function[Any, Int, Boolean](Gen.boolean)
      check(genChunk(intGen), fn)((chunk, p) => assert(chunk.exists(p))(equalTo(chunk.toList.exists(p))))
    },
    test("forall") {
      val fn = Gen.function[Any, Int, Boolean](Gen.boolean)
      check(genChunk(intGen), fn)((chunk, p) => assert(chunk.forall(p))(equalTo(chunk.toList.forall(p))))
    },
    test("find") {
      val fn = Gen.function[Any, Int, Boolean](Gen.boolean)
      check(genChunk(intGen), fn)((chunk, p) => assert(chunk.find(p))(equalTo(chunk.toList.find(p))))
    },
    test("filter") {
      val fn1 = Gen.function[Any, Boolean, Boolean](Gen.boolean)
      val fn2 = Gen.function[Any, Byte, Boolean](Gen.boolean)
      val fn3 = Gen.function[Any, Char, Boolean](Gen.boolean)
      val fn4 = Gen.function[Any, Short, Boolean](Gen.boolean)
      val fn5 = Gen.function[Any, Float, Boolean](Gen.boolean)
      val fn6 = Gen.function[Any, Int, Boolean](Gen.boolean)
      val fn7 = Gen.function[Any, Double, Boolean](Gen.boolean)
      val fn8 = Gen.function[Any, Long, Boolean](Gen.boolean)
      val fn9 = Gen.function[Any, String, Boolean](Gen.boolean)
      check(smallChunks(Gen.boolean), fn1)((c, p) => assert(c.filter(p).toList)(equalTo(c.toList.filter(p)))) &&
      check(smallChunks(Gen.byte), fn2)((c, p) => assert(c.filter(p).toList)(equalTo(c.toList.filter(p)))) &&
      check(smallChunks(Gen.char), fn3)((c, p) => assert(c.filter(p).toList)(equalTo(c.toList.filter(p)))) &&
      check(smallChunks(Gen.short), fn4)((c, p) => assert(c.filter(p).toList)(equalTo(c.toList.filter(p)))) &&
      check(smallChunks(Gen.float), fn5)((c, p) => assert(c.filter(p).toList)(equalTo(c.toList.filter(p)))) &&
      check(smallChunks(Gen.int), fn6)((c, p) => assert(c.filter(p).toList)(equalTo(c.toList.filter(p)))) &&
      check(smallChunks(Gen.double), fn7)((c, p) => assert(c.filter(p).toList)(equalTo(c.toList.filter(p)))) &&
      check(smallChunks(Gen.long), fn8)((c, p) => assert(c.filter(p).toList)(equalTo(c.toList.filter(p)))) &&
      check(smallChunks(Gen.string), fn9)((c, p) => assert(c.filter(p).toList)(equalTo(c.toList.filter(p))))
    },
    test("drop chunk") {
      check(genChunk(intGen), genChunk(intGen), intGen) { (chunk1, chunk2, n) =>
        val chunk = chunk1 ++ chunk2
        assert(chunk.drop(n).toList)(equalTo(chunk.toList.drop(n)))
      }
    },
    test("dropRight chunk") {
      check(genChunk(intGen), genChunk(intGen), intGen) { (chunk1, chunk2, n) =>
        val chunk = chunk1 ++ chunk2
        assert(chunk.dropRight(n).toList)(equalTo(chunk.toList.dropRight(n)))
      }
    },
    test("take chunk") {
      check(genChunk(intGen), genChunk(intGen), intGen) { (chunk1, chunk2, n) =>
        val chunk = chunk1 ++ chunk2
        assert(chunk.take(n).toList)(equalTo(chunk.toList.take(n)))
      }
    },
    test("takeRight chunk") {
      check(genChunk(intGen), genChunk(intGen), intGen) { (chunk1, chunk2, n) =>
        val chunk = chunk1 ++ chunk2
        assert(chunk.takeRight(n).toList)(equalTo(chunk.toList.takeRight(n)))
      }
    },
    test("dropWhile chunk") {
      check(genChunk(intGen), genChunk(intGen), toBoolFn[Any, Int]) { (c1, c2, p) =>
        val c = c1 ++ c2
        assert(c.dropWhile(p).toList)(equalTo(c.toList.dropWhile(p)))
      }
    },
    test("dropUntil chunk") {
      check(genChunk(intGen), toBoolFn[Any, Int]) { (c, p) =>
        assert(c.dropUntil(p).toList)(equalTo(c.toList.dropWhile(a => !p(a)).drop(1)))
      }
    },
    test("takeWhile chunk") {
      check(smallChunks(Gen.boolean), toBoolFn[Any, Boolean]) { (c, p) =>
        assert(c.takeWhile(p).toList)(equalTo(c.toList.takeWhile(p)))
      } &&
      check(smallChunks(Gen.byte), toBoolFn[Any, Byte]) { (c, p) =>
        assert(c.takeWhile(p).toList)(equalTo(c.toList.takeWhile(p)))
      } &&
      check(smallChunks(Gen.short), toBoolFn[Any, Short]) { (c, p) =>
        assert(c.takeWhile(p).toList)(equalTo(c.toList.takeWhile(p)))
      } &&
      check(smallChunks(Gen.char), toBoolFn[Any, Char]) { (c, p) =>
        assert(c.takeWhile(p).toList)(equalTo(c.toList.takeWhile(p)))
      } &&
      check(smallChunks(intGen), toBoolFn[Any, Int]) { (c, p) =>
        assert(c.takeWhile(p).toList)(equalTo(c.toList.takeWhile(p)))
      } &&
      check(smallChunks(Gen.float), toBoolFn[Any, Float]) { (c, p) =>
        assert(c.takeWhile(p).toList)(equalTo(c.toList.takeWhile(p)))
      } &&
      check(smallChunks(Gen.long), toBoolFn[Any, Long]) { (c, p) =>
        assert(c.takeWhile(p).toList)(equalTo(c.toList.takeWhile(p)))
      } &&
      check(smallChunks(Gen.double), toBoolFn[Any, Double]) { (c, p) =>
        assert(c.takeWhile(p).toList)(equalTo(c.toList.takeWhile(p)))
      } &&
      check(smallChunks(Gen.string), toBoolFn[Any, String]) { (c, p) =>
        assert(c.takeWhile(p).toList)(equalTo(c.toList.takeWhile(p)))
      }
    },
    test("toArray") {
      check(genChunk(Gen.alphaNumericString))(c => assert(c.toArray.toList)(equalTo(c.toList)))
    },
    test("non-homogeneous element type") {
      trait Animal
      trait Cat extends Animal
      trait Dog extends Animal

      val vector = Vector(new Cat {}, new Dog {}, new Animal {})
      assert(Chunk.fromIterable(vector).map(identity))(equalTo(Chunk.fromArray(vector.toArray)))
    },
    test("toArray for an empty Chunk of type String") {
      assert(Chunk.empty[String].toArray)(equalTo(Array.empty[String]))
    },
    test("to Array for an empty Chunk using filter") {
      assert(Chunk(1).filter(_ == 2).map(_.toString).toArray[String])(equalTo(Array.empty[String]))
    },
    test("toArray with elements of type String") {
      check(genChunk(Gen.alphaNumericString))(c => assert(c.toArray.toList)(equalTo(c.toList)))
    },
    test("toArray for a Chunk of any type") {
      val v: Vector[Any] = Vector("String", 1, Value(2))
      assert(Chunk.fromIterable(v).toArray.toVector)(equalTo(v))
    },
    test("collect") {
      check(genChunk(intGen), genChunk(intGen), Gen.partialFunction[Any, Int, Int](intGen)) { (c1, c2, pf) =>
        val c = c1 ++ c2
        assert(c.collect(pf).toList)(equalTo(c.toList.collect(pf)))
      }
    },
    test("collectWhile") {
      check(genChunk(intGen), genChunk(intGen), Gen.partialFunction[Any, Int, Int](intGen)) { (c1, c2, pf) =>
        val c = c1 ++ c2
        assert(c.collectWhile(pf).toList)(equalTo(c.toList.takeWhile(pf.isDefinedAt).map(pf.apply)))
      }
    },
    test("foreach") {
      check(smallChunks(intGen), smallChunks(intGen)) { (c1, c2) =>
        val c   = c1 ++ c2
        var sum = c.headOption.getOrElse(0)
        c.drop(1).foreach(sum += _)
        assert(sum)(equalTo(c.sum))
      }
    },
    test("concat chunk") {
      check(smallChunks(intGen), smallChunks(intGen)) { (c1, c2) =>
        assert((c1 ++ c2).toList)(equalTo(c1.toList ++ c2.toList))
      }
    },
    test("chunk transitivity") {
      val c1 = Chunk(1, 2, 3)
      val c2 = Chunk(1, 2, 3)
      val c3 = Chunk(1, 2, 3)
      assert(c1 == c2 && c2 == c3 && c1 == c3)(Assertion.isTrue)
    },
    test("chunk symmetry") {
      val c1 = Chunk(1, 2, 3)
      val c2 = Chunk(1, 2, 3)
      assert(c1 == c2 && c2 == c1)(Assertion.isTrue)
    },
    test("chunk reflexivity") {
      val c1 = Chunk(1, 2, 3)
      assert(c1 == c1)(Assertion.isTrue)
    },
    test("chunk negation") {
      val c1 = Chunk(1, 2, 3)
      val c2 = Chunk(1, 2, 3)
      assert(c1 != c2 == !(c1 == c2))(Assertion.isTrue)
    },
    test("chunk substitutivity") {
      val c1 = Chunk(1, 2, 3)
      val c2 = Chunk(1, 2, 3)
      assert(c1 == c2 && c1.toString == c2.toString)(Assertion.isTrue)
    },
    test("chunk consistency") {
      val c1 = Chunk(1, 2, 3)
      val c2 = Chunk(1, 2, 3)
      assert(c1 == c2 && c1.hashCode == c2.hashCode)(Assertion.isTrue)
    },
    test("seq consistency") {
      val c1 = Chunk(1, 2, 3)
      val c2 = List(1, 2, 3)
      assert(c1 == c2 && c1.hashCode == c2.hashCode)(Assertion.isTrue)
    },
    test("empty seq consistency") {
      val c1 = Chunk()
      val c2 = List()
      val c3 = Vector()
      assert(c1 == c2 && c1 == c3 && c1.hashCode == c2.hashCode && c1.hashCode == c3.hashCode)(Assertion.isTrue)
    },
    test("nullArrayBug") {
      val c = Chunk.fromArray(Array(1, 2, 3, 4, 5))
      c.foreach(_ => ()) // foreach should not throw
      assert(c.filter(_ => false).map(_ * 2).length)(equalTo(0))
    },
    test("toArrayOnConcatOfSlice") {
      val onlyOdd: Int => Boolean = _ % 2 != 0
      val concat                  = Chunk(1, 1, 1).filter(onlyOdd) ++
        Chunk(2, 2, 2).filter(onlyOdd) ++
        Chunk(3, 3, 3).filter(onlyOdd)
      val array = concat.toArray
      assert(array)(equalTo(Array(1, 1, 1, 3, 3, 3)))
    },
    test("toArrayOnConcatOfEmptyAndInts") {
      assert(Chunk.empty ++ Chunk.fromArray(Array(1, 2, 3)))(equalTo(Chunk(1, 2, 3)))
    },
    test("filterConstFalseResultsInEmptyChunk") {
      assert(Chunk.fromArray(Array(1, 2, 3)).filter(_ => false))(equalTo(Chunk.empty))
    },
    test("zip") {
      check(genChunk(Gen.int), genChunk(Gen.int)) { (as, bs) =>
        val actual   = as.zip(bs).toList
        val expected = as.toList.zip(bs.toList)
        assert(actual)(equalTo(expected))
      }
    },
    test("zipAll") {
      val a = Chunk(1, 2, 3)
      val b = Chunk(1, 2)
      val c = Chunk((Some(1), Some(1)), (Some(2), Some(2)), (Some(3), Some(3)))
      val d = Chunk((Some(1), Some(1)), (Some(2), Some(2)), (Some(3), None))
      val e = Chunk((Some(1), Some(1)), (Some(2), Some(2)), (None, Some(3)))
      assert(a.zipAll(a))(equalTo(c)) &&
      assert(a.zipAll(b))(equalTo(d)) &&
      assert(b.zipAll(a))(equalTo(e))
    },
    test("zipAllWith") {
      assert(Chunk(1, 2, 3).zipAllWith(Chunk(3, 2, 1))(_ => 0, _ => 0)(_ + _))(equalTo(Chunk(4, 4, 4))) &&
      assert(Chunk(1, 2, 3).zipAllWith(Chunk(3, 2))(_ => 0, _ => 0)(_ + _))(equalTo(Chunk(4, 4, 0))) &&
      assert(Chunk(1, 2).zipAllWith(Chunk(3, 2, 1))(_ => 0, _ => 0)(_ + _))(equalTo(Chunk(4, 4, 0)))
    },
    test("zipWithIndex") {
      val (ch1, ch2) = Chunk("a", "b", "c", "d").splitAt(2)
      val ch         = ch1 ++ ch2
      assert(ch.zipWithIndex.toList)(equalTo(ch.toList.zipWithIndex))
    },
    test("zipWithIndex on concatenated chunks") {
      check(smallChunks(intGen), smallChunks(intGen)) { (c1, c2) =>
        val items   = (c1 ++ c2).zipWithIndex.map(_._1)
        val indices = (c1 ++ c2).zipWithIndex.map(_._2)
        assert(items.toList)(equalTo(c1.toList ++ c2.toList)) &&
        assert(indices.toList)(equalTo((0 until (c1.size + c2.size)).toList))
      }
    },
    test("zipWithIndexFrom on concatenated chunks") {
      check(smallChunks(intGen), smallChunks(intGen), Gen.int(0, 10)) { (c1, c2, from) =>
        val items   = (c1 ++ c2).zipWithIndexFrom(from).map(_._1)
        val indices = (c1 ++ c2).zipWithIndexFrom(from).map(_._2)
        assert(items.toList)(equalTo(c1.toList ++ c2.toList)) &&
        assert(indices.toList)(equalTo((from until (c1.size + c2.size + from)).toList))
      }
    },
    test("partitionMap") {
      val as       = Chunk(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
      val (bs, cs) = as.partitionMap(n => if (n % 2 == 0) Left(n) else Right(n))
      assert(bs)(equalTo(Chunk(0, 2, 4, 6, 8))) &&
      assert(cs)(equalTo(Chunk(1, 3, 5, 7, 9)))
    },
    test("stack safety concat") {
      val n  = 100000
      val as = List.range(0, n).foldRight[Chunk[Int]](Chunk.empty)((a, as) => Chunk(a) ++ as)
      assert(as.toArray)(equalTo(Array.range(0, n)))
    },
    test("stack safety append") {
      val n  = 100000
      val as = List.range(0, n).foldRight[Chunk[Int]](Chunk.empty)((a, as) => as :+ a)
      assert(as.toArray)(equalTo(Array.range(0, n).reverse))
    },
    test("stack safety prepend") {
      val n  = 100000
      val as = List.range(0, n).foldRight[Chunk[Int]](Chunk.empty)((a, as) => a +: as)
      assert(as.toArray)(equalTo(Array.range(0, n)))
    },
    test("stack safety concat and append") {
      val n  = 100000
      val as = List.range(0, n).foldRight[Chunk[Int]](Chunk.empty) { (a, as) =>
        if (a % 2 == 0) as :+ a else as ++ Chunk(a)
      }
      assert(as.toArray)(equalTo(Array.range(0, n).reverse))
    },
    test("stack safety concat and prepend") {
      val n  = 100000
      val as = List.range(0, n).foldRight[Chunk[Int]](Chunk.empty) { (a, as) =>
        if (a % 2 == 0) a +: as else Chunk(a) ++ as
      }
      assert(as.toArray)(equalTo(Array.range(0, n)))
    },
    test("toArray does not throw ClassCastException") {
      assert(Chunk("a").toArray)(anything)
    },
    test("Tags.fromValue is safe on Scala.is") {
      val _ = Chunk(1, 128)
      assertCompletes
    },
    test("Tags.fromValue is safe with null") {
      val _ = Chunk(null)
      assertCompletes
    },
    test("chunks can be constructed from heterogeneous collections") {
      check(Gen.listOf(Gen.oneOf(Gen.int, Gen.string, Gen.none))) { as =>
        assert(Chunk.fromIterable(as).toList)(equalTo(as))
      }
    },
    test("chunks can be mapped with heterogeneous functions") {
      check(Gen.listOf(Gen.int), Gen.function(Gen.oneOf(Gen.int, Gen.string, Gen.none))) { (as, f) =>
        assert(Chunk.fromIterable(as).map(f).toList)(equalTo(as.map(f)))
      }
    },
    test("unfold") {
      assert(Chunk.unfold(0)(n => if (n < 10) Some((n, n + 1)) else None))(equalTo(Chunk.fromIterable(0 to 9)))
    },
    test("split") {
      val smallInts = Gen.small(n => Gen.const(n), 1)
      val chunks    = genChunk(Gen.int)
      check(smallInts, chunks) { (n, chunk) =>
        val groups = chunk.split(n)
        assert(groups.flatten)(equalTo(chunk)) &&
        assert(groups.size)(equalTo(n min chunk.length))
      }
    },
    test("from") {
      check(genChunk(Gen.int))(c => assert(Chunk.from(c))(equalTo(c))) &&
      check(genChunk(Gen.int))(c => assert(Chunk.from(c.iterator))(equalTo(c)))
    },
    test("fromIterator") {
      check(genChunk(Gen.int))(c => assert(Chunk.fromIterator(c.iterator))(equalTo(c)))
    },
    test("fromIterable") {
      val traversableOnceIterable = new Iterable[Int] {
        private val it = new Iterator[Int] {
          private var c: Int            = 3
          override def hasNext: Boolean = c > 0
          override def next(): Int      = { c = c - 1; c }
        }
        override def iterator: Iterator[Int] = it
      }
      assert(Chunk.fromIterable(traversableOnceIterable))(equalTo(Chunk(2, 1, 0))) &&
      assert(Chunk.fromIterable(Vector.empty[Int]))(equalTo(Chunk())) &&
      assert(Chunk.fromIterable(Vector(2, 1, 0)))(equalTo(Chunk(2, 1, 0)))
      assert(Chunk.fromIterable(mutable.ArraySeq(2, 1, 0)))(equalTo(Chunk(2, 1, 0)))
    },
    test("fromByteBuffer") {
      check(genChunk(Gen.byte)) { as =>
        assert(Chunk.fromByteBuffer(java.nio.ByteBuffer.wrap(as.toArray)))(equalTo(as))
      }
    },
    test("fromShortBuffer") {
      check(genChunk(Gen.short)) { as =>
        assert(Chunk.fromShortBuffer(java.nio.ShortBuffer.wrap(as.toArray)))(equalTo(as))
      }
    },
    test("fromCharBuffer") {
      check(genChunk(Gen.char)) { as =>
        assert(Chunk.fromCharBuffer(java.nio.CharBuffer.wrap(as.toArray)))(equalTo(as))
      }
    },
    test("fromIntBuffer") {
      check(genChunk(Gen.int)) { as =>
        assert(Chunk.fromIntBuffer(java.nio.IntBuffer.wrap(as.toArray)))(equalTo(as))
      }
    },
    test("fromLongBuffer") {
      check(genChunk(Gen.long)) { as =>
        assert(Chunk.fromLongBuffer(java.nio.LongBuffer.wrap(as.toArray)))(equalTo(as))
      }
    },
    test("fromFloatBuffer") {
      check(genChunk(Gen.float)) { as =>
        assert(Chunk.fromFloatBuffer(java.nio.FloatBuffer.wrap(as.toArray)))(equalTo(as))
      }
    },
    test("fromDoubleBuffer") {
      check(genChunk(Gen.double)) { as =>
        assert(Chunk.fromDoubleBuffer(java.nio.DoubleBuffer.wrap(as.toArray)))(equalTo(as))
      }
    },
    suite("unapplySeq")(
      test("matches a nonempty chunk") {
        assert(Chunk(1, 2, 3) match {
          case Chunk(x, y, z) => Some((x, y, z))
          case _              => None
        })(equalTo(Some((1, 2, 3))))
      },
      test("matches an empty chunk") {
        assert(Chunk.empty[Int] match {
          case Chunk() => Some(())
          case _       => None
        })(equalTo(Some(())))
      }
    ),
    suite("updated")(
      test("updates the chunk at the specified index") {
        check(genChunkN(10)(Gen.int), Gen.listOf(Gen.int(0, 9)), Gen.listOf(Gen.int)) { (chunk, indices, values) =>
          val actual =
            indices.zip(values).foldLeft(chunk) { case (chunk, (index, value)) => chunk.updated(index, value) }
          val expected =
            indices.zip(values).foldLeft(chunk.toList) { case (chunk, (index, value)) => chunk.updated(index, value) }
          assert(actual.toList)(equalTo(expected))
        }
      },
      test("fails if the chunk does not contain the specified index") {
        assert(Chunk(1, 2, 3).updated(3, 4))(throwsA[IndexOutOfBoundsException])
      },
      test("apply") {
        val chunk = Chunk.fill(256)(1).foldLeft(Chunk(0)) { case (as, a) =>
          as.updated(0, as(0) + a)
        }
        assertTrue(chunk(0) == 256)
      },
      test("buffer size") {
        val chunk = Chunk.fill(257)(1).zipWithIndex.foldLeft(Chunk.fill(256)(0)) { case (as, (a, i)) =>
          as.updated(i % 256, as(i % 256) + a)
        }
        assertTrue(chunk.sum == 257)
      }
    ),
    test("toArray works correctly with concatenated bit chunks") {
      check(genChunk(Gen.byte), genChunk(Gen.byte)) { (left, right) =>
        val actual   = Chunk.fromArray((left.asBitsByte ++ right.asBitsByte).toArray)
        val expected = (left ++ right).asBitsByte
        assertTrue(actual == expected)
      }
    },
    suite("Concat")(
      test("right only slice of concat converted to vector") {
        assertTrue((Chunk(1, 2) ++ Chunk(3, 4)).slice(2, 4).toVector == Vector(3, 4))
      },
      test("right slice of concat converted to vector") {
        assertTrue((Chunk(1, 2, 3) ++ Chunk(4, 5, 6)).slice(4, 6).toVector == Vector(5, 6))
      },
      test("left only slice of concat converted to vector") {
        assertTrue((Chunk(1, 2, 3) ++ Chunk(4, 5, 6)).slice(1, 2).toVector == Vector(2))
      },
      test("slice of concat covering both sides converted to vector") {
        assertTrue((Chunk(1, 2, 3) ++ Chunk(4, 5, 6)).slice(1, 5).toVector == Vector(2, 3, 4, 5))
      },
      test("concatenated chunks to vector") {
        check(genChunk(Gen.int), genChunk(Gen.int)) { case (chunk1, chunk2) =>
          assertTrue((chunk1 ++ chunk2).toVector == chunk1.toVector ++ chunk2.toVector)
        }
      }
    ),
    suite("Append")(
      test("right only slice of append converted to vector") {
        assertTrue((Chunk(1, 2) :+ 3 :+ 4).slice(2, 4).toVector == Vector(3, 4))
      },
      test("right slice of append converted to vector") {
        assertTrue((Chunk(1, 2, 3) :+ 4 :+ 5 :+ 6).slice(4, 6).toVector == Vector(5, 6))
      },
      test("left only slice of append converted to vector") {
        assertTrue((Chunk(1, 2, 3) :+ 4 :+ 5 :+ 6).slice(1, 2).toVector == Vector(2))
      },
      test("slice of append covering both sides converted to vector") {
        assertTrue((Chunk(1, 2, 3) :+ 4 :+ 5 :+ 6).slice(1, 5).toVector == Vector(2, 3, 4, 5))
      },
      test("appended chunks to vector") {
        check(genChunk(Gen.int), genChunk(Gen.int)) { case (chunk1, chunk2) =>
          assertTrue(chunk2.foldLeft(chunk1)(_ :+ _).toVector == chunk1.toVector ++ chunk2.toVector)
        }
      }
    ),
    suite("Prepend")(
      test("right only slice of prepend converted to vector") {
        assertTrue((1 +: 2 +: Chunk(3, 4)).slice(2, 4).toVector == Vector(3, 4))
      },
      test("right slice of prepend converted to vector") {
        assertTrue((1 +: 2 +: 3 +: Chunk(4, 5, 6)).slice(4, 6).toVector == Vector(5, 6))
      },
      test("left only slice of prepend converted to vector") {
        assertTrue((1 +: 2 +: 3 +: Chunk(4, 5, 6)).slice(1, 2).toVector == Vector(2))
      },
      test("slice of prepend covering both sides converted to vector") {
        assertTrue((1 +: 2 +: 3 +: Chunk(4, 5, 6)).slice(1, 5).toVector == Vector(2, 3, 4, 5))
      },
      test("prepended chunks to vector") {
        check(genChunk(Gen.int), genChunk(Gen.int)) { case (chunk1, chunk2) =>
          assertTrue(chunk1.foldRight(chunk2)(_ +: _).toVector == chunk1.toVector ++ chunk2.toVector)
        }
      }
    ),
    test("sorted") {
      check(genChunk(Gen.byte))(chunk => assertTrue(chunk.sorted.toVector == chunk.toVector.sorted)) &&
      check(genChunk(Gen.char))(chunk => assertTrue(chunk.sorted.toVector == chunk.toVector.sorted)) &&
      check(genChunk(Gen.double))(chunk => assertTrue(chunk.sorted.toVector == chunk.toVector.sorted)) &&
      check(genChunk(Gen.float))(chunk => assertTrue(chunk.sorted.toVector == chunk.toVector.sorted)) &&
      check(genChunk(Gen.int))(chunk => assertTrue(chunk.sorted.toVector == chunk.toVector.sorted)) &&
      check(genChunk(Gen.long))(chunk => assertTrue(chunk.sorted.toVector == chunk.toVector.sorted)) &&
      check(genChunk(Gen.short))(chunk => assertTrue(chunk.sorted.toVector == chunk.toVector.sorted)) &&
      check(genChunk(Gen.string))(chunk => assertTrue(chunk.sorted.toVector == chunk.toVector.sorted))
    },
    suite("combinators on chunks with different underlying primitives")(
      test("concat on small chunks") {
        assertTrue(Chunk(1) ++ Chunk(2L) == Chunk(1L, 2L))
      },
      test("concat on large chunks") {
        val arr1 = Array.fill(1000)(1)
        val arr2 = Array.fill(1000)(1L)
        assertTrue(Chunk.fromArray(arr1) ++ Chunk.fromArray(arr2) == Chunk.fromArray(arr1 ++ arr2))
      },
      test("flatmap") {
        val arr1 = Array.fill(100)(1)
        val arr2 = Array.fill(100)(1L)
        assertTrue(Chunk.fromArray(arr1).flatMap(_ => Chunk.fromArray(arr2)) == Chunk.fill(10000)(1L))
      }
    ),
    suite("fromIterable array")(
      test("objects") {
        assert(Chunk.fromIterable(Array("a")).getClass.toString)(equalTo("class zio.blocks.chunk.Chunk$AnyRefArray")) &&
        assert(Chunk.fromIterable(Array(1)).getClass.toString)(equalTo("class zio.blocks.chunk.Chunk$IntArray")) &&
        assert(Chunk.fromIterable(Array(1L)).getClass.toString)(equalTo("class zio.blocks.chunk.Chunk$LongArray")) &&
        assert(Chunk.fromIterable(Array(1.0f)).getClass.toString)(equalTo("class zio.blocks.chunk.Chunk$FloatArray")) &&
        assert(Chunk.fromIterable(Array(1.0)).getClass.toString)(equalTo("class zio.blocks.chunk.Chunk$DoubleArray")) &&
        assert(Chunk.fromIterable(Array(1, "a")).getClass.toString)(equalTo("class zio.blocks.chunk.Chunk$AnyRefArray"))
      }
    )
  )
}
