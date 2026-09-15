/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package stream

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger

import zio.blocks.async._
import zio.blocks.streams.Stream

/**
 * `flatMapPar(n)(a => Stream.unwrap(f(a)))` is the idiom for asynchronously
 * produced children. The point of this example is the slot accounting: the
 * effect that *builds* the child and the child that is then *drained* share one
 * of the `n` slots, so a slot is occupied from the moment construction starts
 * until the child has finished closing.
 *
 * `inFlight` is incremented when construction starts and decremented by the
 * child's finalizer, so it counts exactly the elements occupying a slot. With
 * `n = 2` its peak is 2 and never 4, even though four outer elements are
 * available immediately: the engine re-arms the outer source only while
 * `active < n`, so it will not pull ahead to start a third construction.
 *
 * The `CyclicBarrier(2)` proves the lower bound from the other side. Each
 * construction waits for a partner, so the run can only finish if two
 * constructions really are in flight at once — and it pairs off exactly twice.
 */
object FlatMapParAsyncChildrenExample {
  private val inFlight = new AtomicInteger
  private val peak     = new AtomicInteger
  private val pairUp   = new CyclicBarrier(2)

  /**
   * Produces a value on a thread of its own. Blocking inside an `Async` would
   * stall the driver that is running the stream, so the barrier wait has to
   * happen somewhere else.
   */
  private def deferred[A](value: => A): Async[A] = {
    val completer = new Completer[A]
    val thread    = new Thread(() => completer.succeed(value))
    thread.setDaemon(true)
    thread.start()
    completer
  }

  /** The asynchronously produced child for one outer element. */
  private def child(value: Int): Async[Stream[Nothing, Int]] =
    deferred {
      val current = inFlight.incrementAndGet()
      peak.getAndUpdate(seen => if (current > seen) current else seen)
      pairUp.await()
      Stream(value * 10, value * 10 + 1).ensuring {
        inFlight.decrementAndGet()
        ()
      }
    }

  def main(args: Array[String]): Unit = {
    // `.block` is JVM-only; on Scala.js drive the Async with map/flatMap.
    val collected = Stream(1, 2, 3, 4)
      .flatMapPar(2)(value => Stream.unwrap(child(value)))
      .runCollectAsync
      .block

    println(s"elements: ${collected.map(_.toSet.toList.sorted)}")
    println(s"peak slot occupancy: ${peak.get()} of 2")

    require(
      collected.map(_.toSet) == Right(Set(10, 11, 20, 21, 30, 31, 40, 41)),
      s"unexpected elements: $collected"
    )
    // Two slots, so at most two elements are ever being constructed or drained.
    require(peak.get() == 2, s"peak occupancy was ${peak.get()}, expected 2")
    // Every slot was released, which means every child was closed.
    require(inFlight.get() == 0, s"${inFlight.get()} children were left open")
  }
}
