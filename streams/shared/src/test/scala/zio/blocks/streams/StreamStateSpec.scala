package zio.blocks.streams

import zio.blocks.streams.internal.StreamState
import zio.test._

object StreamStateSpec extends ZIOSpecDefault {
  def spec: Spec[Any, Nothing] = suite("StreamState")(
    test("empty is all zeros") {
      val s = StreamState.empty
      assertTrue(
        StreamState.stageStart(s) == 0,
        StreamState.incomingLen(s) == 0,
        StreamState.stageEnd(s) == 0,
        StreamState.outgoingLen(s) == 0,
        StreamState.outputLane(s) == 0
      )
    },
    test("apply and getters round-trip") {
      val s = StreamState(10, 20, 30, 40, 3)
      assertTrue(
        StreamState.stageStart(s) == 10,
        StreamState.incomingLen(s) == 20,
        StreamState.stageEnd(s) == 30,
        StreamState.outgoingLen(s) == 40,
        StreamState.outputLane(s) == 3
      )
    },
    test("max index values (13-bit = 8191)") {
      val s = StreamState(8191, 8191, 8191, 8191, 4)
      assertTrue(
        StreamState.stageStart(s) == 8191,
        StreamState.incomingLen(s) == 8191,
        StreamState.stageEnd(s) == 8191,
        StreamState.outgoingLen(s) == 8191,
        StreamState.outputLane(s) == 4
      )
    },
    test("individual component independence") {
      assertTrue(StreamState.stageStart(StreamState(1, 0, 0, 0)) == 1) &&
      assertTrue(StreamState.incomingLen(StreamState(1, 0, 0, 0)) == 0) &&
      assertTrue(StreamState.incomingLen(StreamState(0, 1, 0, 0)) == 1) &&
      assertTrue(StreamState.stageEnd(StreamState(0, 0, 1, 0)) == 1) &&
      assertTrue(StreamState.outgoingLen(StreamState(0, 0, 0, 1)) == 1) &&
      assertTrue(StreamState.outputLane(StreamState(0, 0, 0, 0, 4)) == 4)
    },
    test("withStageStart preserves other fields") {
      val s  = StreamState(1, 2, 3, 4, 2)
      val s2 = StreamState.withStageStart(s, 100)
      assertTrue(
        StreamState.stageStart(s2) == 100,
        StreamState.incomingLen(s2) == 2,
        StreamState.stageEnd(s2) == 3,
        StreamState.outgoingLen(s2) == 4,
        StreamState.outputLane(s2) == 2
      )
    },
    test("withIncomingLen preserves other fields") {
      val s  = StreamState(1, 2, 3, 4, 2)
      val s2 = StreamState.withIncomingLen(s, 200)
      assertTrue(
        StreamState.stageStart(s2) == 1,
        StreamState.incomingLen(s2) == 200,
        StreamState.stageEnd(s2) == 3,
        StreamState.outgoingLen(s2) == 4,
        StreamState.outputLane(s2) == 2
      )
    },
    test("withStageEnd preserves other fields") {
      val s  = StreamState(1, 2, 3, 4, 2)
      val s2 = StreamState.withStageEnd(s, 300)
      assertTrue(
        StreamState.stageStart(s2) == 1,
        StreamState.incomingLen(s2) == 2,
        StreamState.stageEnd(s2) == 300,
        StreamState.outgoingLen(s2) == 4,
        StreamState.outputLane(s2) == 2
      )
    },
    test("withOutgoingLen preserves other fields") {
      val s  = StreamState(1, 2, 3, 4, 2)
      val s2 = StreamState.withOutgoingLen(s, 400)
      assertTrue(
        StreamState.stageStart(s2) == 1,
        StreamState.incomingLen(s2) == 2,
        StreamState.stageEnd(s2) == 3,
        StreamState.outgoingLen(s2) == 400,
        StreamState.outputLane(s2) == 2
      )
    },
    test("withOutputLane preserves other fields") {
      val s  = StreamState(1, 2, 3, 4, 2)
      val s2 = StreamState.withOutputLane(s, 4)
      assertTrue(
        StreamState.stageStart(s2) == 1,
        StreamState.incomingLen(s2) == 2,
        StreamState.stageEnd(s2) == 3,
        StreamState.outgoingLen(s2) == 4,
        StreamState.outputLane(s2) == 4
      )
    },
    test("all lanes round-trip") {
      (0 to 4).foldLeft(assertTrue(true)) { (acc, lane) =>
        val s = StreamState(0, 0, 0, 0, lane)
        acc && assertTrue(StreamState.outputLane(s) == lane)
      }
    },
    test("boundary: power-of-two values") {
      val s = StreamState(4096, 2048, 1024, 512, 4)
      assertTrue(
        StreamState.stageStart(s) == 4096,
        StreamState.incomingLen(s) == 2048,
        StreamState.stageEnd(s) == 1024,
        StreamState.outgoingLen(s) == 512,
        StreamState.outputLane(s) == 4
      )
    }
  )
}
