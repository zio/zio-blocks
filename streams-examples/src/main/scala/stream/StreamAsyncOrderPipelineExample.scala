/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package stream

import java.util.concurrent.atomic.AtomicInteger

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.Stream

/** A composed asynchronous order-validation and audit pipeline. */
object StreamAsyncOrderPipelineExample {
  final case class Order(id: Int, amount: Int)

  def deferred[A](value: => A): Async[A] = {
    val result = new Completer[A]
    val thread = new Thread(() => result.succeed(value))
    thread.start()
    result
  }

  def main(args: Array[String]): Unit = {
    val finalized                         = new AtomicInteger
    val validated: Stream[Nothing, Order] = Stream
      .unwrap(deferred(Stream(Order(1, 20), Order(2, -1), Order(3, 40))))
      .filterAsync(order => Async.succeed(order.amount > 0))
      .mapAsync(order => deferred(order.copy(amount = order.amount + 5)))
      .ensuringAsync(Async.succeed { finalized.incrementAndGet(); () })

    // Real applications commonly assemble reusable generated middleware. Its
    // finite depth must not change the meaning of an identity transformation.
    val withMiddleware =
      (0 until 33000).foldLeft(validated)((orders, _) => orders.map(identity))

    val result = withMiddleware.runCollectAsync.block
    require(result == Right(Chunk(Order(1, 25), Order(3, 45))), s"unexpected result: $result")
    require(finalized.get() == 1, s"finalizer ran ${finalized.get()} times")
    println(result)
  }
}
