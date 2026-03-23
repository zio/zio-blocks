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

package zio.blocks.streams

import zio.blocks.chunk.Chunk
import zio.test._
import zio.test.Assertion._
import StreamsGen._

object PipelineLawsSpec extends StreamsBaseSpec {

  private def collect[A](s: Stream[Nothing, A]): Chunk[A] =
    s.runCollect.fold[Chunk[A]](_ => Chunk.empty[A], x => x)

  def spec: Spec[TestEnvironment, Any] = suite("Pipeline laws")(
    suite("Category")(
      test("left identity: Pipeline.identity andThen p == p") {
        check(genIntStream, genIntPipeline) { (s, p) =>
          assert(collect((Pipeline.identity[Int] andThen p).applyToStream(s)))(
            equalTo(collect(p.applyToStream(s)))
          )
        }
      },
      test("right identity: p andThen Pipeline.identity == p") {
        check(genIntStream, genIntPipeline) { (s, p) =>
          assert(collect((p andThen Pipeline.identity[Int]).applyToStream(s)))(
            equalTo(collect(p.applyToStream(s)))
          )
        }
      },
      test("associativity: (p andThen q) andThen r == p andThen (q andThen r)") {
        check(genIntStream, genIntPipeline, genIntPipeline, genIntPipeline) { (s, p, q, r) =>
          assert(collect(((p andThen q) andThen r).applyToStream(s)))(
            equalTo(collect((p andThen (q andThen r)).applyToStream(s)))
          )
        }
      }
    ),

    suite("via / applyToStream consistency")(
      test("q.applyToStream(p.applyToStream(s)) == (p andThen q).applyToStream(s)") {
        check(genIntStream, genIntPipeline, genIntPipeline) { (s, p, q) =>
          assert(collect(q.applyToStream(p.applyToStream(s))))(
            equalTo(collect((p andThen q).applyToStream(s)))
          )
        }
      },
      test("p.applyToStream(s) == s.via(p)") {
        check(genIntStream, genIntPipeline) { (s, p) =>
          assert(collect(p.applyToStream(s)))(equalTo(collect(s.via(p))))
        }
      }
    ),

    suite("andThenSink")(
      test("s.run(p andThenSink sink) == s.via(p).run(sink)") {
        check(genIntStream, genIntPipeline) { (s, p) =>
          val data = collect(s)
          val r1   = Stream.fromChunk(data).run(p andThenSink Sink.collectAll[Int])
          val r2   = Stream.fromChunk(data).via(p).run(Sink.collectAll[Int])
          assert(r1)(equalTo(r2))
        }
      }
    ),

    suite("Constructors")(
      test("map applies function to each element") {
        check(genIntStream, Gen.function(genInt)) { (s, f) =>
          val data = collect(s)
          assert(collect(Stream.fromChunk(data).map(f)))(equalTo(data.map(f)))
        }
      },
      test("filter keeps matching elements") {
        check(genIntStream, Gen.function(Gen.boolean)) { (s, pred) =>
          val data = collect(s)
          assert(collect(Stream.fromChunk(data).filter(pred)))(equalTo(data.filter(pred)))
        }
      },
      test("take(n) emits exactly min(n, len) elements") {
        check(genIntStream, Gen.int(0, 60)) { (s, n) =>
          val data = collect(s)
          assert(collect(Stream.fromChunk(data).take(n.toLong)))(equalTo(data.take(n)))
        }
      },
      test("drop(n) skips first min(n, len) elements") {
        check(genIntStream, Gen.int(0, 60)) { (s, n) =>
          val data = collect(s)
          assert(collect(Stream.fromChunk(data).drop(n.toLong)))(equalTo(data.drop(n)))
        }
      },
      test("collect applies partial function") {
        check(genIntStream) { s =>
          val data                          = collect(s)
          val pf: PartialFunction[Int, Int] = { case x if x > 0 => x * 2 }
          assert(collect(Stream.fromChunk(data).via(Pipeline.collect(pf))))(equalTo(data.collect(pf)))
        }
      },
      test("Pipeline.identity passes all elements through") {
        check(genIntStream) { s =>
          assert(collect(s.via(Pipeline.identity[Int])))(equalTo(collect(s)))
        }
      },
      test("map applyToSink == contramap on sink") {
        check(genIntStream, Gen.function(genInt)) { (s, f) =>
          val data = collect(s)
          val r1   = Stream
            .fromChunk(data)
            .run(
              Pipeline.map[Int, Int](f).andThenSink[Nothing, Chunk[Int]](Sink.collectAll[Int])
            )
          val r2 = Stream.fromChunk(data).run(Sink.collectAll[Int].contramap(f))
          assert(r1)(equalTo(r2))
        }
      },
      test("filter applyToSink == filter applyToStream then sink") {
        check(genIntStream, Gen.function(Gen.boolean)) { (s, pred) =>
          val data = collect(s)
          val r1   = Stream
            .fromChunk(data)
            .run(
              Pipeline.filter[Int](pred).andThenSink[Nothing, Chunk[Int]](Sink.collectAll[Int])
            )
          val r2 = Stream.fromChunk(data).filter(pred).run(Sink.collectAll[Int])
          assert(r1)(equalTo(r2))
        }
      },
      test("take applyToSink == take applyToStream then sink") {
        check(genIntStream, Gen.int(0, 30)) { (s, n) =>
          val data = collect(s)
          val r1   = Stream
            .fromChunk(data)
            .run(
              Pipeline.take[Int](n.toLong).andThenSink[Nothing, Chunk[Int]](Sink.collectAll[Int])
            )
          val r2 = Stream.fromChunk(data).take(n.toLong).run(Sink.collectAll[Int])
          assert(r1)(equalTo(r2))
        }
      }
    )
  )
}
