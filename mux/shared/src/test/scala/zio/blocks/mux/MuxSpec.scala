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

package zio.blocks.mux

import scala.compiletime.testing.typeCheckErrors

import zio._
import zio.test._

object MuxSpec extends ZIOSpecDefault {

  // Positive compile-time proof: these summons only compile because the
  // Pollable macro proved the types disjoint from MuxError/MuxEmpty
  // (SafeStr also proves transparent aliases resolve to their right-hand side).
  summon[Pollable[String]]
  summon[Pollable[Http2Frame]]
  summon[Pollable[SafeStr]]
  summon[Pollable[SafeUnion]]
  summon[Pollable[ChainedB]]
  summon[Pollable[SafeList]]
  // Applied parameterized aliases beta-reduce to their expansion before the
  // gate runs (PList[String] is List[String], a safe container), so this
  // summon must compile.
  summon[Pollable[PList[String]]]

  // HTTP/2-style frame shapes: a sealed hierarchy of final case classes, the
  // same shape real protocol message types (e.g. HTTP/2 DATA/HEADERS/RST_STREAM
  // frames) have. Kept local so the mux test module gains no new dependency.
  sealed trait Http2Frame
  final case class DataFrame(payload: String, endStream: Boolean) extends Http2Frame
  final case class HeadersFrame(headers: List[(String, String)])  extends Http2Frame
  final case class RstStreamFrame(errorCode: Long)                extends Http2Frame

  // Opaque aliases viewed from outside their defining scope: the macro must
  // reject all three, including the safe-underneath StrAlias, because the
  // right-hand side cannot be proven disjoint from this scope.
  object OpaqueProbes {
    opaque type ErrAlias = MuxError
    opaque type AnyAlias = Any
    opaque type StrAlias = String
    def wrapErr(e: MuxError): ErrAlias = e
  }

  // Stable values whose singleton types must not bypass the blocker check.
  object Singletons {
    val q: MuxError.QueueFull = MuxError.QueueFull(8)
  }

  // Transparent aliases: resolution must see through these, so the error
  // aliases are rejected while the safe String alias (SafeStr) is accepted.
  type EmptyAlias           = MuxEmpty
  type TransparentErr       = MuxError
  type TransparentQueueFull = MuxError.QueueFull
  type SafeStr              = String
  type SafeUnion            = String | Int
  type ChainedA             = String
  type ChainedB             = ChainedA
  type SafeList             = List[String]
  type PList[T]             = List[T]
  type PE[T]                = T

  // Compile-negative probes: each snippet must fail with exactly the
  // macro-authored diagnostic (no compiler-version-specific text inside).
  private val rejectsAnyIn: List[String] =
    typeCheckErrors("""zio.blocks.mux.Mux[Int, Any, String](4)""").map(_.message)
  private val rejectsAnyOut: List[String] =
    typeCheckErrors("""zio.blocks.mux.Mux[Int, String, Any](4)""").map(_.message)
  private val rejectsAnyRef: List[String] =
    typeCheckErrors("""zio.blocks.mux.Mux[Int, AnyRef, String](4)""").map(_.message)
  private val rejectsMatchable: List[String] =
    typeCheckErrors("""zio.blocks.mux.Mux[Int, String, Matchable](4)""").map(_.message)
  private val rejectsProduct: List[String] =
    typeCheckErrors("""zio.blocks.mux.Mux[Int, Product, String](4)""").map(_.message)
  private val rejectsMuxError: List[String] =
    typeCheckErrors("""zio.blocks.mux.Mux[Int, zio.blocks.mux.MuxError, String](4)""").map(_.message)
  private val rejectsErrorSubtype: List[String] =
    typeCheckErrors("""zio.blocks.mux.Mux[Int, zio.blocks.mux.MuxError.StreamClosed, String](4)""").map(_.message)
  private val rejectsMuxEmpty: List[String] =
    typeCheckErrors("""zio.blocks.mux.Mux[Int, zio.blocks.mux.MuxEmpty.type, String](4)""").map(_.message)
  private val rejectsUnion: List[String] =
    typeCheckErrors("""zio.blocks.mux.Mux[Int, String, String | zio.blocks.mux.MuxError](4)""").map(_.message)
  private val rejectsAbstract: List[String] =
    typeCheckErrors("""def hold[A]: zio.blocks.mux.Pollable[A] = summon[zio.blocks.mux.Pollable[A]]""").map(_.message)
  private val rejectsAbstractUnion: List[String] =
    typeCheckErrors(
      """def hold[A]: zio.blocks.mux.Pollable[A | String] = summon[zio.blocks.mux.Pollable[A | String]]"""
    ).map(_.message)
  private val rejectsAbstractIntersection: List[String] =
    typeCheckErrors(
      """def hold[A]: zio.blocks.mux.Pollable[A & AnyRef] = summon[zio.blocks.mux.Pollable[A & AnyRef]]"""
    ).map(_.message)
  private val rejectsErrorIntersection: List[String] =
    typeCheckErrors("""zio.blocks.mux.Mux[Int, String & zio.blocks.mux.MuxError, String](4)""").map(_.message)
  private val rejectsOpaqueErr: List[String] =
    typeCheckErrors("""zio.blocks.mux.Mux[Int, zio.blocks.mux.MuxSpec.OpaqueProbes.ErrAlias, String](4)""").map(
      _.message
    )
  private val rejectsOpaqueAny: List[String] =
    typeCheckErrors("""zio.blocks.mux.Mux[Int, zio.blocks.mux.MuxSpec.OpaqueProbes.AnyAlias, String](4)""").map(
      _.message
    )
  private val rejectsOpaqueStr: List[String] =
    typeCheckErrors("""zio.blocks.mux.Mux[Int, zio.blocks.mux.MuxSpec.OpaqueProbes.StrAlias, String](4)""").map(
      _.message
    )
  private val rejectsOpaqueUnion: List[String] =
    typeCheckErrors("""zio.blocks.mux.Mux[Int, zio.blocks.mux.MuxSpec.OpaqueProbes.ErrAlias | String, String](4)""")
      .map(_.message)
  private val rejectsStableErrorSingleton: List[String] =
    typeCheckErrors("""zio.blocks.mux.Mux[Int, zio.blocks.mux.MuxSpec.Singletons.q.type, String](4)""").map(_.message)
  private val rejectsEmptyAlias: List[String] =
    typeCheckErrors("""zio.blocks.mux.Mux[Int, zio.blocks.mux.MuxSpec.EmptyAlias, String](4)""").map(_.message)
  private val rejectsTransparentErr: List[String] =
    typeCheckErrors("""zio.blocks.mux.Mux[Int, zio.blocks.mux.MuxSpec.TransparentErr, String](4)""").map(_.message)
  private val rejectsTransparentQueueFull: List[String] =
    typeCheckErrors("""zio.blocks.mux.Mux[Int, zio.blocks.mux.MuxSpec.TransparentQueueFull, String](4)""").map(
      _.message
    )
  private val rejectsAliasedAbstract: List[String] =
    typeCheckErrors(
      """def hold[A]: zio.blocks.mux.Pollable[A] = { type Alias = A; summon[zio.blocks.mux.Pollable[Alias]] }"""
    ).map(_.message)
  private val rejectsAliasedUnion: List[String] =
    typeCheckErrors(
      """def hold[A]: zio.blocks.mux.Pollable[A | String] = { type U = A | String; summon[zio.blocks.mux.Pollable[U]] }"""
    ).map(_.message)
  private val rejectsAliasedIntersection: List[String] =
    typeCheckErrors(
      """def hold[A]: zio.blocks.mux.Pollable[A & AnyRef] = { type I = A & AnyRef; summon[zio.blocks.mux.Pollable[I]] }"""
    ).map(_.message)
  private val rejectsParameterizedAlias: List[String] =
    typeCheckErrors("""zio.blocks.mux.Mux[Int, zio.blocks.mux.MuxSpec.PE[zio.blocks.mux.MuxError], String](4)""").map(
      _.message
    )

  private def makeMux(capacity: Int = 100): Mux[Int, String, String] =
    Mux[Int, String, String](capacity)

  private def openStream(mux: Mux[Int, String, String], id: Int): MuxStream[Int, String, String] =
    mux.open(id) match {
      case stream: MuxStream[?, ?, ?] =>
        stream.asInstanceOf[MuxStream[Int, String, String]]
      case error: MuxError =>
        throw new RuntimeException(s"Failed to open stream: $error")
    }

  private def isProtocolError(result: MuxStream[Int, String, String] | MuxError): Boolean =
    result match {
      case MuxError.ProtocolError(_) => true
      case _                         => false
    }

  private def isStreamClosed(result: Any): Boolean =
    result match {
      case MuxError.StreamClosed(_) => true
      case _                        => false
    }

  private def isMuxError(result: Any): Boolean =
    result.isInstanceOf[MuxError]

  def spec: Spec[Any, Any] = suite("Mux")(
    basicOperationsSuite,
    multiplexingSuite,
    halfCloseSuite,
    cancellationSuite,
    backpressureSuite,
    closeAllSuite,
    edgeCasesSuite,
    capacityValidationSuite,
    streamQueueCapacitySuite,
    errorReuseSuite,
    pollableSuite,
    httpFrameSuite
  )

  private val basicOperationsSuite = suite("basic operations")(
    test("open stream and get by ID returns same instance") {
      val mux    = makeMux()
      val stream = mux.open(1)
      val opened = stream match {
        case stream: MuxStream[?, ?, ?] =>
          stream.asInstanceOf[MuxStream[Int, String, String]]
        case error: MuxError =>
          throw new RuntimeException(s"Failed to open stream: $error")
      }
      assertTrue(
        stream.isInstanceOf[MuxStream[?, ?, ?]],
        mux.get(1).isDefined,
        mux.get(1).get eq opened
      )
    },
    test("open stream, offer inbound message, receive message") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      stream.offerInbound("hello")
      val msg = stream.receive()
      assertTrue(msg == "hello")
    },
    test("send goes to outbound queue, takeOutbound retrieves it") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      stream.send("outgoing")
      val taken = stream.takeOutbound()
      assertTrue(taken == "outgoing")
    },
    test("multiple streams with different IDs are independent") {
      val mux = makeMux()
      val s1  = openStream(mux, 1)
      val s2  = openStream(mux, 2)
      s1.offerInbound("msg-1")
      s2.offerInbound("msg-2")
      assertTrue(
        s1.receive() == "msg-1",
        s2.receive() == "msg-2"
      )
    },
    test("open stream with duplicate ID returns ProtocolError") {
      val mux = makeMux()
      mux.open(1)
      val result = mux.open(1)
      assertTrue(isProtocolError(result))
    },
    test("activeCount reflects open streams") {
      val mux    = makeMux()
      val count0 = mux.activeCount
      mux.open(1)
      val count1 = mux.activeCount
      mux.open(2)
      val count2 = mux.activeCount
      assertTrue(count0 == 0, count1 == 1, count2 == 2)
    },
    test("receive returns MuxEmpty when no message is available") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      val result = stream.receive()
      assertTrue(result == MuxEmpty)
    }
  )

  private val multiplexingSuite = suite("multiplexing")(
    test("100 multiplexed streams operate independently") {
      for {
        result <- ZIO.attempt {
                    val mux     = makeMux()
                    val streams = (0 until 100).map { i =>
                      val s = openStream(mux, i)
                      s.offerInbound(s"msg-$i")
                      (i, s)
                    }
                    val results = streams.map { case (i, s) =>
                      s.receive() == s"msg-$i"
                    }
                    (mux.activeCount, results.forall(identity))
                  }
      } yield assertTrue(result._1 == 100, result._2)
    },
    test("no cross-contamination between streams") {
      val mux = makeMux()
      val s1  = openStream(mux, 1)
      val s2  = openStream(mux, 2)
      (1 to 10).foreach(i => s1.offerInbound(s"s1-$i"))
      (1 to 10).foreach(i => s2.offerInbound(s"s2-$i"))
      val s1Msgs = (1 to 10).map(_ => s1.receive())
      val s2Msgs = (1 to 10).map(_ => s2.receive())
      assertTrue(
        s1Msgs == (1 to 10).map(i => s"s1-$i"),
        s2Msgs == (1 to 10).map(i => s"s2-$i")
      )
    }
  )

  private val halfCloseSuite = suite("half-close lifecycle")(
    test("halfClose then send returns StreamClosed") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      stream.halfClose()
      val result = stream.send("fail")
      assertTrue(isStreamClosed(result))
    },
    test("halfClose then receive still works for buffered messages") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      stream.offerInbound("before-close")
      stream.halfClose()
      val msg = stream.receive()
      assertTrue(msg == "before-close")
    },
    test("halfClose sets isHalfClosed") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      val before = stream.isHalfClosed
      stream.halfClose()
      val after = stream.isHalfClosed
      assertTrue(!before, after)
    },
    test("close after halfClose sets isClosed") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      stream.halfClose()
      stream.close()
      assertTrue(stream.isClosed)
    },
    test("double halfClose transitions to closed via HALF_CLOSED_REMOTE") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      stream.halfClose()
      val halfClosed = stream.isHalfClosed
      val notClosed  = !stream.isClosed
      stream.signalRemoteClose()
      val closed = stream.isClosed
      assertTrue(halfClosed, notClosed, closed)
    },
    test("signalRemoteClose transitions OPEN to HALF_CLOSED_REMOTE") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      stream.signalRemoteClose()
      assertTrue(stream.isHalfClosed, !stream.isClosed)
    },
    test("signalRemoteClose on HALF_CLOSED_LOCAL transitions to CLOSED") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      stream.halfClose()
      stream.signalRemoteClose()
      assertTrue(stream.isClosed)
    }
  )

  private val cancellationSuite = suite("cancellation")(
    test("cancel removes stream from mux") {
      val mux = makeMux()
      mux.open(1)
      mux.cancel(1, MuxError.Cancelled(1, "test"))
      assertTrue(mux.get(1).isEmpty, mux.activeCount == 0)
    },
    test("cancelled stream returns error on send") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      mux.cancel(1, MuxError.Cancelled(1, "abort"))
      val result = stream.send("fail")
      assertTrue(isMuxError(result))
    },
    test("cancelled stream returns the cancel reason on receive") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      val reason = MuxError.Cancelled(1, "abort")
      mux.cancel(1, reason)
      val result = stream.receive()
      assertTrue(result == reason, result.asInstanceOf[AnyRef] eq reason.asInstanceOf[AnyRef])
    },
    test("cancel 25 of 50 streams leaves 25 active") {
      val mux = makeMux()
      (0 until 50).foreach(i => mux.open(i))
      (0 until 25).foreach(i => mux.cancel(i, MuxError.Cancelled(i, "cancel")))
      assertTrue(mux.activeCount == 25) &&
      assertTrue((0 until 25).forall(i => mux.get(i).isEmpty)) &&
      assertTrue((25 until 50).forall(i => mux.get(i).isDefined))
    },
    test("remaining streams after partial cancel work normally") {
      val mux = makeMux()
      (0 until 50).foreach(i => mux.open(i))
      (0 until 25).foreach(i => mux.cancel(i, MuxError.Cancelled(i, "cancel")))
      val stream = mux.get(30).get
      stream.offerInbound("still-alive")
      assertTrue(stream.receive() == "still-alive")
    }
  )

  private val backpressureSuite = suite("backpressure (capacity)")(
    test("capacity exceeded returns error") {
      val mux = makeMux(capacity = 5)
      (0 until 5).foreach(i => mux.open(i))
      val result = mux.open(5)
      assertTrue(result == MuxError.CapacityExceeded(5))
    },
    test("closing a stream frees capacity") {
      val mux = makeMux(capacity = 5)
      (0 until 5).foreach(i => mux.open(i))
      mux.get(0).get.close()
      val result = mux.open(5)
      assertTrue(result.isInstanceOf[MuxStream[?, ?, ?]], mux.activeCount == 5)
    },
    test("cancelling a stream frees capacity") {
      val mux = makeMux(capacity = 5)
      (0 until 5).foreach(i => mux.open(i))
      mux.cancel(0, MuxError.Cancelled(0, "done"))
      val result = mux.open(5)
      assertTrue(result.isInstanceOf[MuxStream[?, ?, ?]])
    },
    test("send returns QueueFull when outbound queue is full") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      (0 until 256).foreach(i => stream.send(s"msg-$i"))
      val result = stream.send("overflow")
      assertTrue(result == MuxError.QueueFull(256))
    },
    test("offerInbound returns QueueFull when inbound queue is full") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      (0 until 256).foreach(i => stream.offerInbound(s"msg-$i"))
      val result = stream.offerInbound("overflow")
      assertTrue(result == MuxError.QueueFull(256))
    },
    test("send null returns ProtocolError") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      val result = stream.send(null)
      assertTrue(result == MuxError.ProtocolError("null message"))
    },
    test("offerInbound null returns ProtocolError") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      val result = stream.offerInbound(null)
      assertTrue(result == MuxError.ProtocolError("null message"))
    }
  )

  private val closeAllSuite = suite("closeAll")(
    test("closeAll closes all streams") {
      val mux     = makeMux()
      val streams = (0 until 10).map(i => openStream(mux, i))
      mux.closeAll(MuxError.MuxClosed)
      assertTrue(
        mux.activeCount == 0,
        streams.forall(_.isClosed)
      )
    },
    test("closeAll makes pending receives return error") {
      for {
        result <- ZIO.attempt {
                    // receive() is a non-blocking poll: it returns MuxEmpty
                    // when empty and a message directly when available. After
                    // closeAll, receive() returns a MuxError instead of
                    // MuxEmpty.
                    val mux    = makeMux()
                    val stream = openStream(mux, 1)
                    mux.closeAll(MuxError.MuxClosed)
                    stream.receive()
                  }
      } yield assertTrue(isMuxError(result))
    },
    test("open after closeAll returns MuxClosed") {
      val mux = makeMux()
      mux.open(1)
      mux.closeAll(MuxError.MuxClosed)
      val result = mux.open(2)
      assertTrue(result == MuxError.MuxClosed)
    }
  )

  private val capacityValidationSuite = suite("capacity validation")(
    test("Mux rejects zero capacity") {
      assertTrue(
        try {
          Mux[Int, String, String](0)
          false
        } catch {
          case _: IllegalArgumentException => true
        }
      )
    },
    test("Mux rejects negative capacity") {
      assertTrue(
        try {
          Mux[Int, String, String](-1)
          false
        } catch {
          case _: IllegalArgumentException => true
        }
      )
    },
    test("Mux rejects non-power-of-two stream queue capacity") {
      assertTrue(
        try {
          Mux[Int, String, String](10, 100)
          false
        } catch {
          case _: IllegalArgumentException => true
        }
      )
    },
    test("Mux rejects non-positive stream queue capacity") {
      assertTrue(
        try {
          Mux[Int, String, String](10, 0)
          false
        } catch {
          case _: IllegalArgumentException => true
        }
      )
    }
  )

  private val streamQueueCapacitySuite = suite("stream queue capacity")(
    test("default queues hold 256 messages") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      (0 until 256).foreach(i => stream.offerInbound(s"msg-$i"))
      assertTrue(stream.offerInbound("overflow") == MuxError.QueueFull(256))
    },
    test("custom capacity tunes per-stream queues") {
      val mux    = Mux[Int, String, String](10, 8)
      val stream = openStream(mux, 1)
      (0 until 8).foreach(i => stream.offerInbound(s"msg-$i"))
      val full = stream.offerInbound("overflow")
      // drained messages free space again under the same capacity
      val first = stream.receive()
      val after = stream.offerInbound("again")
      assertTrue(full == MuxError.QueueFull(8), first == "msg-0", after == ())
    },
    test("custom capacity applies to the outbound queue") {
      val mux    = Mux[Int, String, String](10, 8)
      val stream = openStream(mux, 1)
      (0 until 8).foreach(i => stream.send(s"msg-$i"))
      assertTrue(stream.send("overflow") == MuxError.QueueFull(8))
    },
    test("receive drains buffered messages before the terminal error") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      stream.offerInbound("hello")
      stream.close()
      val first  = stream.receive()
      val second = stream.receive()
      assertTrue(first == "hello", isMuxError(second))
    },
    test("takeOutbound drains buffered messages before the closed error") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      stream.send("hello")
      stream.close()
      val first  = stream.takeOutbound()
      val second = stream.takeOutbound()
      assertTrue(first == "hello", second == MuxError.StreamClosed(1))
    },
    test("receive returns buffered message then MuxEmpty on an open stream") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      stream.offerInbound("hello")
      assertTrue(stream.receive() == "hello", stream.receive() == MuxEmpty)
    },
    test("repeated drains preserve order and recover capacity") {
      val mux    = Mux[Int, String, String](10, 8)
      val stream = openStream(mux, 1)
      (0 until 8).foreach(i => stream.offerInbound(s"msg-$i"))
      val drained  = (0 until 8).map(_ => stream.receive())
      val empty    = stream.receive()
      val refilled = stream.offerInbound("again")
      val reread   = stream.receive()
      assertTrue(
        drained == (0 until 8).map(i => s"msg-$i"),
        empty == MuxEmpty,
        refilled == (),
        reread == "again",
        stream.receive() == MuxEmpty
      )
    },
    test("repeated outbound drains preserve order then report MuxEmpty") {
      val mux    = Mux[Int, String, String](10, 8)
      val stream = openStream(mux, 1)
      (0 until 8).foreach(i => stream.send(s"msg-$i"))
      val drained = (0 until 8).map(_ => stream.takeOutbound())
      assertTrue(
        drained == (0 until 8).map(i => s"msg-$i"),
        stream.takeOutbound() == MuxEmpty
      )
    }
  )

  private val edgeCasesSuite = suite("edge cases")(
    test("cancel non-existent stream is no-op") {
      val mux = makeMux()
      mux.cancel(999, MuxError.Cancelled(999, "nope"))
      assertTrue(mux.activeCount == 0)
    },
    test("get non-existent stream returns None") {
      val mux = makeMux()
      assertTrue(mux.get(42).isEmpty)
    },
    test("send after close returns StreamClosed") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      stream.close()
      val result = stream.send("fail")
      assertTrue(isStreamClosed(result))
    },
    test("receive after close returns StreamClosed") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      stream.close()
      val result = stream.receive()
      assertTrue(isStreamClosed(result))
    },
    test("stream id is accessible and correctly typed") {
      val mux    = makeMux()
      val stream = openStream(mux, 42)
      assertTrue(stream.id == 42)
    },
    test("offerInbound multiple messages, receive in order") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      stream.offerInbound("a")
      stream.offerInbound("b")
      stream.offerInbound("c")
      assertTrue(
        stream.receive() == "a",
        stream.receive() == "b",
        stream.receive() == "c"
      )
    },
    test("send multiple messages, takeOutbound in order") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      stream.send("x")
      stream.send("y")
      stream.send("z")
      assertTrue(
        stream.takeOutbound() == "x",
        stream.takeOutbound() == "y",
        stream.takeOutbound() == "z"
      )
    },
    test("takeOutbound returns MuxEmpty when empty") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      assertTrue(stream.takeOutbound() == MuxEmpty)
    },
    test("offerInbound on closed stream returns error") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      stream.close()
      val result = stream.offerInbound("fail")
      assertTrue(isMuxError(result))
    }
  )

  private val errorReuseSuite = suite("error instance reuse")(
    test("QueueFull returns the same instance on repeated overflow") {
      val mux    = Mux[Int, String, String](10, 8)
      val stream = openStream(mux, 1)
      (0 until 8).foreach(i => stream.send(s"msg-$i"))
      val first  = stream.send("overflow-1")
      val second = stream.send("overflow-2")
      assertTrue(
        first == MuxError.QueueFull(8),
        second == MuxError.QueueFull(8),
        first.asInstanceOf[AnyRef] eq second.asInstanceOf[AnyRef]
      )
    },
    test("send and offerInbound share one QueueFull instance per stream") {
      val mux    = Mux[Int, String, String](10, 8)
      val stream = openStream(mux, 1)
      (0 until 8).foreach(i => stream.send(s"msg-$i"))
      (0 until 8).foreach(i => stream.offerInbound(s"in-$i"))
      val sendFull  = stream.send("overflow")
      val offerFull = stream.offerInbound("overflow")
      assertTrue(
        sendFull == MuxError.QueueFull(8),
        offerFull == MuxError.QueueFull(8),
        sendFull.asInstanceOf[AnyRef] eq offerFull.asInstanceOf[AnyRef]
      )
    },
    test("CapacityExceeded returns the same instance for one mux") {
      val mux = makeMux(capacity = 2)
      mux.open(0)
      mux.open(1)
      val first  = mux.open(2)
      val second = mux.open(3)
      assertTrue(
        first == MuxError.CapacityExceeded(2),
        second == MuxError.CapacityExceeded(2),
        first.asInstanceOf[AnyRef] eq second.asInstanceOf[AnyRef]
      )
    },
    test("StreamClosed signals share one instance per stream") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      stream.close()
      val sendClosed    = stream.send("fail")
      val offerClosed   = stream.offerInbound("fail")
      val receiveClosed = stream.receive()
      val takeClosed    = stream.takeOutbound()
      assertTrue(
        sendClosed == MuxError.StreamClosed(1),
        offerClosed == MuxError.StreamClosed(1),
        receiveClosed == MuxError.StreamClosed(1),
        takeClosed == MuxError.StreamClosed(1),
        sendClosed.asInstanceOf[AnyRef] eq offerClosed.asInstanceOf[AnyRef],
        sendClosed.asInstanceOf[AnyRef] eq receiveClosed.asInstanceOf[AnyRef],
        sendClosed.asInstanceOf[AnyRef] eq takeClosed.asInstanceOf[AnyRef]
      )
    },
    test("null rejection reuses the shared ProtocolError instance") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      val first  = stream.send(null)
      val second = stream.offerInbound(null)
      assertTrue(
        first == MuxError.ProtocolError("null message"),
        second == MuxError.ProtocolError("null message"),
        first.asInstanceOf[AnyRef] eq MuxError.nullMessage.asInstanceOf[AnyRef],
        second.asInstanceOf[AnyRef] eq MuxError.nullMessage.asInstanceOf[AnyRef]
      )
    }
  )

  private val pollableSuite = suite("Pollable compile-time disjointness")(
    test("rejects Any as inbound message type") {
      assertTrue(rejectsAnyIn == List(PollableMacros.overlapMessage))
    },
    test("rejects Any as outbound message type") {
      assertTrue(rejectsAnyOut == List(PollableMacros.overlapMessage))
    },
    test("rejects AnyRef as message type") {
      assertTrue(rejectsAnyRef == List(PollableMacros.overlapMessage))
    },
    test("rejects Matchable as message type") {
      assertTrue(rejectsMatchable == List(PollableMacros.overlapMessage))
    },
    test("rejects Product as message type") {
      assertTrue(rejectsProduct == List(PollableMacros.overlapMessage))
    },
    test("rejects MuxError itself as message type") {
      assertTrue(rejectsMuxError == List(PollableMacros.overlapMessage))
    },
    test("rejects an error subtype as message type") {
      assertTrue(rejectsErrorSubtype == List(PollableMacros.overlapMessage))
    },
    test("rejects MuxEmpty as message type") {
      assertTrue(rejectsMuxEmpty == List(PollableMacros.overlapMessage))
    },
    test("rejects unions overlapping MuxError") {
      assertTrue(rejectsUnion == List(PollableMacros.overlapMessage))
    },
    test("rejects abstract message types") {
      assertTrue(rejectsAbstract == List(PollableMacros.abstractMessage))
    },
    test("rejects unions hiding an abstract member") {
      assertTrue(rejectsAbstractUnion == List(PollableMacros.abstractMessage))
    },
    test("rejects intersections hiding an abstract member") {
      assertTrue(rejectsAbstractIntersection == List(PollableMacros.abstractMessage))
    },
    test("rejects intersections where every message is an error") {
      assertTrue(rejectsErrorIntersection == List(PollableMacros.overlapMessage))
    },
    test("rejects opaque alias for MuxError") {
      assertTrue(rejectsOpaqueErr == List(PollableMacros.opaqueMessage))
    },
    test("rejects opaque alias for Any") {
      assertTrue(rejectsOpaqueAny == List(PollableMacros.opaqueMessage))
    },
    test("rejects opaque alias even when the hidden type is safe") {
      assertTrue(rejectsOpaqueStr == List(PollableMacros.opaqueMessage))
    },
    test("rejects unions hiding an opaque alias") {
      assertTrue(rejectsOpaqueUnion == List(PollableMacros.opaqueMessage))
    },
    test("rejects stable singleton of an error value") {
      assertTrue(rejectsStableErrorSingleton == List(PollableMacros.overlapMessage))
    },
    test("rejects transparent alias for MuxEmpty") {
      assertTrue(rejectsEmptyAlias == List(PollableMacros.overlapMessage))
    },
    test("rejects transparent alias for MuxError") {
      assertTrue(rejectsTransparentErr == List(PollableMacros.overlapMessage))
    },
    test("rejects transparent alias for an error subtype") {
      assertTrue(rejectsTransparentQueueFull == List(PollableMacros.overlapMessage))
    },
    test("rejects aliases that rename a bare type variable") {
      assertTrue(rejectsAliasedAbstract == List(PollableMacros.abstractMessage))
    },
    test("rejects aliases hiding abstract members in unions") {
      assertTrue(rejectsAliasedUnion == List(PollableMacros.abstractMessage))
    },
    test("rejects aliases hiding abstract members in intersections") {
      assertTrue(rejectsAliasedIntersection == List(PollableMacros.abstractMessage))
    },
    test("rejects parameterized aliases without beta-reduction") {
      // PE[MuxError] beta-reduces to MuxError itself, so the forward
      // conformance check — not the parameterized-alias abort — rejects it.
      // Either rejection is sound; the exact message pins which path fired.
      assertTrue(rejectsParameterizedAlias == List(PollableMacros.overlapMessage))
    },
    test("opaque error alias legally carries an error value") {
      // Pins the threat the opaque rejection answers: inside its defining
      // scope the alias IS MuxError, so this compiles and the carried value
      // is a genuine error. Only the Mux construction gate keeps such values
      // out of the message position (see rejectsOpaqueErr above).
      val carried: OpaqueProbes.ErrAlias = OpaqueProbes.wrapErr(MuxError.MuxClosed)
      assertTrue(carried == MuxError.MuxClosed)
    },
    test("generic code propagates evidence to concrete call sites") {
      // In generic code the pattern matcher cannot subtract MuxEmpty/MuxError
      // from an abstract A, so the final case binds the full union. The
      // asInstanceOf below is sound exactly because the Pollable[A] evidence
      // in scope proves no value inhabits both sides; concrete call sites
      // (like the frame suite) narrow with no cast at all.
      def unpack[A](result: A | MuxEmpty | MuxError)(using Pollable[A]): A =
        result match {
          case MuxEmpty    => throw new RuntimeException("empty")
          case e: MuxError => throw new RuntimeException(s"failed: $e")
          case message     => message.asInstanceOf[A]
        }
      def drainAll[A](mux: Mux[Int, A, A], message: A)(using Pollable[A]): List[A] = {
        val stream = mux.open(1) match {
          case s: MuxStream[?, ?, ?] => s.asInstanceOf[MuxStream[Int, A, A]]
          case e: MuxError           => throw new RuntimeException(s"open failed: $e")
        }
        stream.send(message)
        stream.offerInbound(unpack(stream.takeOutbound()))
        List(unpack(stream.receive()))
      }
      val mux = Mux[Int, String, String](4)
      assertTrue(drainAll(mux, "roundtrip") == List("roundtrip"))
    },
    test("generic containers over abstract element types stay provable") {
      // List[A] is disjoint from the error family for every A: no error class
      // extends List, so the element type cannot change the outcome. The
      // asInstanceOf is justified exactly as in drainAll above.
      def roundtrip[A](mux: Mux[Int, List[A], List[A]], first: A, second: A)(using
        Pollable[List[A]]
      ): List[A] = {
        val stream = mux.open(1) match {
          case s: MuxStream[?, ?, ?] => s.asInstanceOf[MuxStream[Int, List[A], List[A]]]
          case e: MuxError           => throw new RuntimeException(s"open failed: $e")
        }
        stream.send(List(first, second))
        stream.takeOutbound() match {
          case MuxEmpty    => Nil
          case e: MuxError => throw new RuntimeException(s"take failed: $e")
          case messages    => messages.asInstanceOf[List[A]]
        }
      }
      val mux = Mux[Int, List[String], List[String]](4)
      assertTrue(roundtrip(mux, "a", "b") == List("a", "b"))
    }
  )

  private val httpFrameSuite = suite("protocol frame message types")(
    test("sealed frame hierarchy round-trips through both queues") {
      val mux    = Mux[Int, Http2Frame, Http2Frame](4)
      val stream = mux.open(1) match {
        case s: MuxStream[Int, Http2Frame, Http2Frame] => s
        case e: MuxError                               => throw new RuntimeException(s"open failed: $e")
      }
      val data    = DataFrame("payload", endStream = true)
      val headers = HeadersFrame(List("content-type" -> "text/plain"))
      stream.send(data)
      stream.offerInbound(headers)
      assertTrue(
        stream.takeOutbound() == data,
        stream.receive() == headers,
        stream.takeOutbound() == MuxEmpty,
        stream.receive() == MuxEmpty
      )
    },
    test("frame hierarchy matches exhaustively on poll outcomes") {
      val mux    = Mux[Int, Http2Frame, Http2Frame](4)
      val stream = mux.open(7) match {
        case s: MuxStream[Int, Http2Frame, Http2Frame] => s
        case e: MuxError                               => throw new RuntimeException(s"open failed: $e")
      }
      stream.offerInbound(RstStreamFrame(8L))
      val rendered = stream.receive() match {
        case MuxEmpty             => "empty"
        case e: MuxError          => s"error: $e"
        case DataFrame(p, _)      => s"data:$p"
        case HeadersFrame(h)      => s"headers:${h.size}"
        case RstStreamFrame(code) => s"rst:$code"
      }
      assertTrue(rendered == "rst:8")
    }
  )
}
