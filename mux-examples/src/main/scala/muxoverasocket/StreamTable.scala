package muxoverasocket

import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport

// The bookkeeping a Mux leaves to the protocol:
//   draining - the streams the writer loop drains. A Mux can't list its
//              streams, and a closed stream leaves the Mux before its
//              outbound queue is empty.
//   usedIds  - every id this connection has used, so a late frame for a
//              finished stream can't reopen it.
//   control  - frames that belong to no stream's queue (RESET), waiting
//              for the writer loop.
final class StreamTable {
  val draining = new ConcurrentHashMap[Int, MessageStream]()
  val usedIds  = ConcurrentHashMap.newKeySet[Int]()
  val control  = new ConcurrentLinkedQueue[Frame]()
  val writer   = new AtomicReference[Thread]()

  def track(stream: MessageStream): Unit = {
    usedIds.add(stream.id)
    draining.put(stream.id, stream)
    wakeWriter()
  }

  def sendControl(frame: Frame): Unit = {
    control.add(frame)
    wakeWriter()
  }

  // The writer parks while it has nothing to write; wake it when there is.
  def wakeWriter(): Unit = {
    val thread = writer.get
    if (thread != null) LockSupport.unpark(thread)
  }
}
