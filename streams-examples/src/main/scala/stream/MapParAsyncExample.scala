/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package stream

import java.util.concurrent.{ConcurrentHashMap, CountDownLatch}

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.Stream

/**
 * `mapParAsync` keeps at most `n` `Async` callbacks in flight and emits each
 * result the moment it completes. Output is therefore in arrival order, not
 * input order.
 *
 * This example makes that visible rather than asserting it. Each callback hands
 * back a `Completer` instead of a value, so nothing completes on its own; a
 * driver thread then settles the four callbacks in reverse order. The collected
 * chunk comes back reversed with respect to the input.
 */
object MapParAsyncExample {
  def main(args: Array[String]): Unit = {
    val inputs = Chunk(1, 2, 3, 4)

    // Every callback registers its completer and reports for duty. Nothing
    // resolves until the driver thread below decides that it should.
    val pending     = new ConcurrentHashMap[Int, Completer[Int]]
    val allInFlight = new CountDownLatch(inputs.length)

    val arrivals: Async[Either[Nothing, Chunk[Int]]] =
      Stream
        .fromChunk(inputs)
        .mapParAsync(4) { value =>
          val completer = new Completer[Int]
          pending.put(value, completer)
          allInFlight.countDown()
          completer
        }
        .runCollectAsync

    // n = 4 and there are four elements, so all four callbacks are admitted
    // before any of them completes. The driver settles 4 first and 1 last.
    val driver = new Thread(() => {
      allInFlight.await()
      List(4, 3, 2, 1).foreach { value =>
        pending.get(value).succeed(value * 10)
        Thread.sleep(100L)
      }
    })
    driver.setDaemon(true)
    driver.start()

    // `.block` is JVM-only; on Scala.js drive the Async with map/flatMap.
    val collected = arrivals.block

    println(s"input order:   ${inputs.map(_ * 10)}")
    println(s"arrival order: $collected")

    // The elements are all there...
    require(
      collected.map(_.toSet) == Right(Set(10, 20, 30, 40)),
      s"unexpected elements: $collected"
    )
    // ...but not in the order they went in.
    require(
      collected != Right(inputs.map(_ * 10)),
      "mapParAsync emitted input order; arrival order was expected"
    )
  }
}
