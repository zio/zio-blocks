package zio.blocks.mux

import zio._
import zio.test._

object MuxSpec extends ZIOSpecDefault {

  private def makeMux(capacity: Int = 100): Mux[Int, String, String] =
    Mux[Int, String, String](capacity)

  private def openStream(mux: Mux[Int, String, String], id: Int): MuxStream[Int, String, String] =
    mux.open(id) match {
      case stream: MuxStream[?, ?, ?] =>
        stream.asInstanceOf[MuxStream[Int, String, String]]
      case error: MuxError            =>
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
    edgeCasesSuite
  )

  private val basicOperationsSuite = suite("basic operations")(
    test("open stream and get by ID returns same instance") {
      val mux    = makeMux()
      val stream = mux.open(1)
      val opened = stream match {
        case stream: MuxStream[?, ?, ?] =>
          stream.asInstanceOf[MuxStream[Int, String, String]]
        case error: MuxError            =>
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
      assertTrue(msg == Some("hello"))
    },
    test("send goes to outbound queue, takeOutbound retrieves it") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      stream.send("outgoing")
      val taken = stream.takeOutbound()
      assertTrue(taken == Some("outgoing"))
    },
    test("multiple streams with different IDs are independent") {
      val mux = makeMux()
      val s1  = openStream(mux, 1)
      val s2  = openStream(mux, 2)
      s1.offerInbound("msg-1")
      s2.offerInbound("msg-2")
      assertTrue(
        s1.receive() == Some("msg-1"),
        s2.receive() == Some("msg-2")
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
    test("receive returns None when no message is available") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      val result = stream.receive()
      assertTrue(result == None)
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
                      s.receive() == Some(s"msg-$i")
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
        s1Msgs == (1 to 10).map(i => Some(s"s1-$i")),
        s2Msgs == (1 to 10).map(i => Some(s"s2-$i"))
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
      assertTrue(msg == Some("before-close"))
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
    test("cancelled stream returns error on receive") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      mux.cancel(1, MuxError.Cancelled(1, "abort"))
      val result = stream.receive()
      assertTrue(isMuxError(result))
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
      assertTrue(stream.receive() == Some("still-alive"))
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
        stream.receive() == Some("a"),
        stream.receive() == Some("b"),
        stream.receive() == Some("c")
      )
    },
    test("send multiple messages, takeOutbound in order") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      stream.send("x")
      stream.send("y")
      stream.send("z")
      assertTrue(
        stream.takeOutbound() == Some("x"),
        stream.takeOutbound() == Some("y"),
        stream.takeOutbound() == Some("z")
      )
    },
    test("takeOutbound returns None when empty") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      assertTrue(stream.takeOutbound() == None)
    },
    test("offerInbound on closed stream returns error") {
      val mux    = makeMux()
      val stream = openStream(mux, 1)
      stream.close()
      val result = stream.offerInbound("fail")
      assertTrue(isMuxError(result))
    }
  )
}
