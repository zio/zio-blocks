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

package zio.blocks.schedule

import zio.test._

import scala.concurrent.duration._

object ScheduleSpec extends ZIOSpecDefault {

  val spec: Spec[Any, Nothing] = suite("Schedule")(
    factoryMethodSuite,
    combinatorSuite,
    symbolicAliasSuite,
    varianceSuite,
    edgeCaseSuite,
    decisionSuite
  )

  private def factoryMethodSuite = suite("factory methods")(
    test("identity creates Identity node") {
      val s = Schedule.identity[Int]
      assertTrue(s.isInstanceOf[Schedule.Identity[_]])
    },
    test("succeed creates Succeed node with correct value") {
      Schedule.succeed(42) match {
        case Schedule.Succeed(value) => assertTrue(value == 42)
        case _                       => assertTrue(false)
      }
    },
    test("succeed preserves reference types") {
      val list = List(1, 2, 3)
      Schedule.succeed(list) match {
        case Schedule.Succeed(value) => assertTrue(value eq list)
        case _                       => assertTrue(false)
      }
    },
    test("unfold creates Unfold node with correct initial value") {
      Schedule.unfold(0)(_ + 1) match {
        case Schedule.Unfold(initial, _) => assertTrue(initial == 0)
        case _                           => assertTrue(false)
      }
    },
    test("unfold captures the step function") {
      Schedule.unfold(0)(_ + 1) match {
        case Schedule.Unfold(_, f) => assertTrue(f(5) == 6)
        case _                     => assertTrue(false)
      }
    },
    test("forever creates Unfold starting at 0L") {
      Schedule.forever match {
        case Schedule.Unfold(initial, f) =>
          assertTrue(initial == 0L, f(0L) == 1L, f(99L) == 100L)
        case _ => assertTrue(false)
      }
    },
    test("recurs creates WhileOutput wrapping Unfold") {
      Schedule.recurs(5) match {
        case Schedule.WhileOutput(Schedule.Unfold(initial, _), _) =>
          assertTrue(initial == 0L)
        case _ => assertTrue(false)
      }
    },
    test("recurs predicate filters correctly") {
      Schedule.recurs(5) match {
        case Schedule.WhileOutput(_, pred) =>
          assertTrue(pred(0L), pred(4L), !pred(5L), !pred(100L))
        case _ => assertTrue(false)
      }
    },
    test("spaced creates Delayed wrapping Unfold") {
      Schedule.spaced(1.second) match {
        case Schedule.Delayed(Schedule.Unfold(initial, _), f) =>
          assertTrue(initial == 0L, f(0L) == 1.second, f(999L) == 1.second)
        case _ => assertTrue(false)
      }
    },
    test("exponential creates Delayed(Map(Unfold))") {
      Schedule.exponential(1.second) match {
        case Schedule.Delayed(Schedule.Map(Schedule.Unfold(initial, _), _), _) =>
          assertTrue(initial == 0L)
        case _ => assertTrue(false)
      }
    },
    test("exponential with custom factor") {
      Schedule.exponential(100.millis, 3.0) match {
        case Schedule.Delayed(Schedule.Map(Schedule.Unfold(_, _), _), _) =>
          assertCompletes
        case _ => assertTrue(false)
      }
    },
    test("fibonacci creates Delayed(Map(Unfold))") {
      Schedule.fibonacci(1.second) match {
        case Schedule.Delayed(Schedule.Map(Schedule.Unfold(initial, _), _), _) =>
          assertTrue(initial == (1.second, 1.second))
        case _ => assertTrue(false)
      }
    },
    test("fibonacci unfold step produces Fibonacci sequence") {
      Schedule.fibonacci(1.second) match {
        case Schedule.Delayed(Schedule.Map(Schedule.Unfold(initial, f), _), _) =>
          val step1 = f(initial)
          val step2 = f(step1)
          assertTrue(
            initial == (1.second, 1.second),
            step1 == (1.second, 2.seconds),
            step2 == (2.seconds, 3.seconds)
          )
        case _ => assertTrue(false)
      }
    }
  )

  private def combinatorSuite = suite("combinators")(
    test("map wraps in Map") {
      val s = Schedule.succeed(42).map(_.toString)
      s match {
        case Schedule.Map(Schedule.Succeed(42), _) => assertCompletes
        case _                                     => assertTrue(false)
      }
    },
    test("map function is applied correctly") {
      val s = Schedule.succeed(42).map(_.toString)
      s match {
        case Schedule.Map(_, f) =>
          assertTrue(f.asInstanceOf[Int => String](42) == "42")
        case _ => assertTrue(false)
      }
    },
    test("contramap wraps in Contramap") {
      val s = Schedule.identity[Int].contramap[String](_.length)
      s match {
        case cm: Schedule.Contramap[_, _, _] =>
          assertTrue(cm.schedule.isInstanceOf[Schedule.Identity[_]])
        case _ => assertTrue(false)
      }
    },
    test("contramap function is applied correctly") {
      val s = Schedule.identity[Int].contramap[String](_.length)
      s match {
        case cm: Schedule.Contramap[_, _, _] =>
          assertTrue(cm.f.asInstanceOf[String => Int]("hello") == 5)
        case _ => assertTrue(false)
      }
    },
    test("as wraps in Map that ignores input") {
      val s = Schedule.succeed(42).as("constant")
      s match {
        case Schedule.Map(Schedule.Succeed(42), f) =>
          assertTrue(f.asInstanceOf[Int => String](42) == "constant")
        case _ => assertTrue(false)
      }
    },
    test("unit wraps in Map producing ()") {
      val s = Schedule.succeed(42).unit
      s match {
        case Schedule.Map(_, f) =>
          assertTrue(f.asInstanceOf[Int => Unit](42) == (()))
        case _ => assertTrue(false)
      }
    },
    test("both wraps in Both") {
      val s1 = Schedule.succeed(1)
      val s2 = Schedule.succeed("a")
      s1.both(s2) match {
        case Schedule.Both(Schedule.Succeed(1), Schedule.Succeed("a")) => assertCompletes
        case _                                                         => assertTrue(false)
      }
    },
    test("either wraps in Either") {
      val s1 = Schedule.succeed(1)
      val s2 = Schedule.succeed("a")
      s1.either(s2) match {
        case Schedule.Either(Schedule.Succeed(1), Schedule.Succeed("a")) => assertCompletes
        case _                                                           => assertTrue(false)
      }
    },
    test("compose wraps in Compose") {
      val outer: Schedule[Int, String] = Schedule.identity[Int].map(_.toString)
      val inner: Schedule[String, Int] = Schedule.identity[String].map(_.length)
      outer.compose(inner) match {
        case Schedule.Compose(_, _) => assertCompletes
        case _                      => assertTrue(false)
      }
    },
    test("andThen wraps in AndThen") {
      val s1 = Schedule.succeed(1)
      val s2 = Schedule.succeed("done")
      s1.andThen(s2) match {
        case Schedule.AndThen(Schedule.Succeed(1), Schedule.Succeed("done")) => assertCompletes
        case _                                                               => assertTrue(false)
      }
    },
    test("addDelay wraps in Delayed") {
      val s = Schedule.succeed(42).addDelay(_ => 1.second)
      s match {
        case Schedule.Delayed(Schedule.Succeed(42), f) => assertTrue(f(42) == 1.second)
        case _                                         => assertTrue(false)
      }
    },
    test("jittered wraps in Jittered with defaults 0.0 and 1.0") {
      val s = Schedule.succeed(42).jittered
      s match {
        case Schedule.Jittered(Schedule.Succeed(42), min, max) =>
          assertTrue(min == 0.0, max == 1.0)
        case _ => assertTrue(false)
      }
    },
    test("jittered with custom bounds") {
      val s = Schedule.succeed(42).jittered(0.5, 2.0)
      s match {
        case Schedule.Jittered(Schedule.Succeed(42), min, max) =>
          assertTrue(min == 0.5, max == 2.0)
        case _ => assertTrue(false)
      }
    },
    test("whileInput wraps in WhileInput") {
      val s = Schedule.identity[Int].whileInput[Int](_ > 0)
      s match {
        case Schedule.WhileInput(Schedule.Identity(), pred) =>
          assertTrue(pred(1), !pred(0), !pred(-1))
        case _ => assertTrue(false)
      }
    },
    test("whileOutput wraps in WhileOutput") {
      val s = Schedule.succeed(42).whileOutput(_ < 100)
      s match {
        case Schedule.WhileOutput(Schedule.Succeed(42), pred) =>
          assertTrue(pred(42), pred(99), !pred(100))
        case _ => assertTrue(false)
      }
    },
    test("reconsider wraps in Reconsider") {
      val f: (Any, Int, Decision) => (String, Decision) = (_, out, dec) => (out.toString, dec)
      val s                                             = Schedule.succeed(42).reconsider(f)
      s match {
        case Schedule.Reconsider(Schedule.Succeed(42), _) => assertCompletes
        case _                                            => assertTrue(false)
      }
    },
    test("reconsider function is applied correctly") {
      val f: (Any, Int, Decision) => (String, Decision) =
        (_, out, _) => (out.toString, Decision.Done)
      val s = Schedule.succeed(42).reconsider(f)
      s match {
        case Schedule.Reconsider(_, g) =>
          val fn            = g.asInstanceOf[(Any, Int, Decision) => (String, Decision)]
          val (outStr, dec) = fn((), 42, Decision.Continue(1.second))
          assertTrue(outStr == "42", dec == Decision.Done)
        case _ => assertTrue(false)
      }
    }
  )

  private def symbolicAliasSuite = suite("symbolic aliases")(
    test("&& is alias for both and produces Both") {
      val s1 = Schedule.succeed(1)
      val s2 = Schedule.succeed("a")
      (s1 && s2) match {
        case Schedule.Both(Schedule.Succeed(1), Schedule.Succeed("a")) => assertCompletes
        case _                                                         => assertTrue(false)
      }
    },
    test("|| is alias for either and produces Either") {
      val s1 = Schedule.succeed(1)
      val s2 = Schedule.succeed("a")
      (s1 || s2) match {
        case Schedule.Either(Schedule.Succeed(1), Schedule.Succeed("a")) => assertCompletes
        case _                                                           => assertTrue(false)
      }
    },
    test("zip is alias for both and produces Both") {
      val s1 = Schedule.succeed(1)
      val s2 = Schedule.succeed("a")
      s1.zip(s2) match {
        case Schedule.Both(Schedule.Succeed(1), Schedule.Succeed("a")) => assertCompletes
        case _                                                         => assertTrue(false)
      }
    }
  )

  private def varianceSuite = suite("variance")(
    test("contravariant In: Schedule[Any, Int] assignable to Schedule[String, Int]") {
      val s: Schedule[Any, Int]    = Schedule.succeed(42)
      val _: Schedule[String, Int] = s
      assertCompletes
    },
    test("covariant Out: Schedule[Any, Int] assignable to Schedule[Any, AnyVal]") {
      val s: Schedule[Any, Int]    = Schedule.succeed(42)
      val _: Schedule[Any, AnyVal] = s
      assertCompletes
    },
    test("both with different In types narrows to most specific") {
      val s1: Schedule[String, Int]                 = Schedule.succeed(42)
      val s2: Schedule[Any, String]                 = Schedule.succeed("hello")
      val combined: Schedule[String, (Int, String)] = s1.both(s2)
      assertTrue(combined.isInstanceOf[Schedule.Both[_, _, _]])
    },
    test("either with different In types narrows to most specific") {
      val s1: Schedule[String, Int]                 = Schedule.succeed(42)
      val s2: Schedule[Any, String]                 = Schedule.succeed("hello")
      val combined: Schedule[String, (Int, String)] = s1.either(s2)
      assertTrue(combined.isInstanceOf[Schedule.Either[_, _, _]])
    },
    test("succeed has type Schedule[Any, A]") {
      val s: Schedule[Any, Int] = Schedule.succeed(42)
      assertCompletes
    },
    test("identity preserves type parameter") {
      val s: Schedule[Int, Int] = Schedule.identity[Int]
      assertCompletes
    },
    test("contramap widens input type") {
      val s: Schedule[Int, Int]     = Schedule.identity[Int]
      val s2: Schedule[String, Int] = s.contramap[String](_.length)
      assertCompletes
    }
  )

  private def edgeCaseSuite = suite("edge cases")(
    test("recurs(0) compiles and creates WhileOutput") {
      val s = Schedule.recurs(0)
      s match {
        case Schedule.WhileOutput(_, pred) => assertTrue(!pred(0L))
        case _                             => assertTrue(false)
      }
    },
    test("recurs(1) creates WhileOutput that allows exactly one step") {
      val s = Schedule.recurs(1)
      s match {
        case Schedule.WhileOutput(_, pred) => assertTrue(pred(0L), !pred(1L))
        case _                             => assertTrue(false)
      }
    },
    test("recurs with Long.MaxValue compiles") {
      val s = Schedule.recurs(Long.MaxValue)
      assertTrue(s.isInstanceOf[Schedule.WhileOutput[_, _]])
    },
    test("deeply nested composition builds correct AST") {
      val s = Schedule.forever
        .map(_.toString)
        .addDelay(_ => 1.second)
        .whileOutput(_.length < 5)
        .jittered
      s match {
        case Schedule.Jittered(
              Schedule.WhileOutput(
                Schedule.Delayed(
                  Schedule.Map(Schedule.Unfold(_, _), _),
                  _
                ),
                _
              ),
              _,
              _
            ) =>
          assertCompletes
        case _ => assertTrue(false)
      }
    },
    test("chained map operations nest correctly") {
      val s = Schedule.succeed(1).map(_ + 1).map(_.toString).map(_.length)
      s match {
        case Schedule.Map(Schedule.Map(Schedule.Map(Schedule.Succeed(1), _), _), _) =>
          assertCompletes
        case _ => assertTrue(false)
      }
    },
    test("spaced with Duration.Zero compiles") {
      val s = Schedule.spaced(Duration.Zero)
      s match {
        case Schedule.Delayed(Schedule.Unfold(_, _), f) =>
          assertTrue(f(0L) == Duration.Zero)
        case _ => assertTrue(false)
      }
    },
    test("fibonacci initial state is (one, one)") {
      Schedule.fibonacci(500.millis) match {
        case Schedule.Delayed(Schedule.Map(Schedule.Unfold(initial, _), _), _) =>
          assertTrue(initial == (500.millis, 500.millis))
        case _ => assertTrue(false)
      }
    },
    test("exponential with default factor 2.0") {
      Schedule.exponential(1.second) match {
        case Schedule.Delayed(Schedule.Map(Schedule.Unfold(0L, _), mapFn0), delayFn0) =>
          val mapFn   = mapFn0.asInstanceOf[Long => Duration]
          val delayFn = delayFn0.asInstanceOf[Duration => Duration]
          val dur0    = mapFn(0L)
          val dur1    = mapFn(1L)
          val dur2    = mapFn(2L)
          assertTrue(
            dur0 == 1.second,
            dur1 == 2.seconds,
            dur2 == 4.seconds,
            delayFn(dur0) == dur0
          )
        case _ => assertTrue(false)
      }
    }
  )

  private def decisionSuite = suite("Decision ADT")(
    test("Continue holds delay duration") {
      val d = Decision.Continue(1.second)
      assertTrue(d.delay == 1.second)
    },
    test("Done is a singleton") {
      assertTrue(Decision.Done eq Decision.Done)
    },
    test("Continue and Done are distinct") {
      val continue: Decision = Decision.Continue(Duration.Zero)
      val done: Decision     = Decision.Done
      assertTrue(continue != done)
    }
  )
}
