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

package zio.blocks.async

import zio.test._
import zio.test.Assertion._

/**
 * ADVERSARIAL PROBE (Category 1 — over-tight / inference-degrading type
 * constraints). Each snippet is semantically valid per the documented contract
 * and SHOULD compile; we assert `isRight`. A `Left` means a constraint or
 * inference defect rejects legitimate code.
 */
object AsyncTypeConstraintProbeSpec extends ZIOSpecDefault {

  def spec = suite("AsyncTypeConstraintProbeSpec")(
    // Pollable.scala / AsyncEncoding.scala promise: "A Pollable[A] is itself an
    // Async[A], so it can be used wherever an Async[A] is expected." So the
    // extension ops (`map`, `flatMap`, ...) must be available on a value whose
    // STATIC type is `Pollable[A]`, not just `Async[A]`.
    test("a value statically typed Pollable[A] can use the Async ops (map)") {
      typeCheck {
        """
        import zio.blocks.async._
        val p: Pollable[Int] = new Pollable[Int] { def poll(w: Waker): Async[Int] = Async.succeed(1) }
        val r: Async[Int] = p.map(_ + 1)
        r
        """
      }.map(result => assert(result)(isRight))
    },
    test("a value statically typed Pollable[A] can use flatMap") {
      typeCheck {
        """
        import zio.blocks.async._
        val p: Pollable[Int] = new Pollable[Int] { def poll(w: Waker): Async[Int] = Async.succeed(1) }
        val r: Async[String] = p.flatMap(i => Async.succeed(i.toString))
        r
        """
      }.map(result => assert(result)(isRight))
    },
    // collectAll is fixed to `Iterable[Async[A]] => Async[List[A]]`. Passing a
    // Vector (an Iterable) is legitimate and should compile.
    test("collectAll accepts a Vector input") {
      typeCheck {
        """
        import zio.blocks.async._
        val r: Async[List[Int]] = Async.collectAll(Vector(Async.succeed(1), Async.succeed(2)))
        r
        """
      }.map(result => assert(result)(isRight))
    },
    // Covariance: Async[+A]. Async[Dog] should be usable as Async[Animal].
    test("covariant widening Async[Dog] <: Async[Animal] is accepted") {
      typeCheck {
        """
        import zio.blocks.async._
        class Animal
        class Dog extends Animal
        val d: Async[Dog] = Async.succeed(new Dog)
        val a: Async[Animal] = d
        a
        """
      }.map(result => assert(result)(isRight))
    },
    // for-comprehension desugars to flatMap/map on the extension ops; should
    // compile outside an async block.
    test("two-generator for-comprehension over Async desugars and compiles") {
      typeCheck {
        """
        import zio.blocks.async._
        val r: Async[Int] = for {
          x <- Async.succeed(1)
          y <- Async.succeed(2)
        } yield x + y
        r
        """
      }.map(result => assert(result)(isRight))
    },
    // collectAll fed by an eta-expanded polymorphic succeed.
    test("collectAll over List(...).map(Async.succeed) compiles") {
      typeCheck {
        """
        import zio.blocks.async._
        val r: Async[List[Int]] = Async.collectAll(List(1, 2, 3).map(Async.succeed))
        r
        """
      }.map(result => assert(result)(isRight))
    },
    // catchAll widening the error-recovery branch to a common supertype.
    test("catchAll with a wider recovery type infers the lub") {
      typeCheck {
        """
        import zio.blocks.async._
        val r: Async[Any] = Async.succeed(1).catchAll(_ => Async.succeed("recovered"))
        r
        """
      }.map(result => assert(result)(isRight))
    },
    // zipWith over genuinely distinct element types.
    test("zipWith over distinct element types compiles") {
      typeCheck {
        """
        import zio.blocks.async._
        val r: Async[String] = Async.succeed(1).zipWith(Async.succeed("a"))((i, s) => s * i)
        r
        """
      }.map(result => assert(result)(isRight))
    },
    // orElse falling back to a value of a supertype.
    test("orElse falls back to a wider type") {
      typeCheck {
        """
        import zio.blocks.async._
        val r: Async[Any] = Async.succeed(1).orElse(Async.succeed("x"))
        r
        """
      }.map(result => assert(result)(isRight))
    }
  )
}
