package golem.runtime.wit

import zio.test._

object WitResultSpec extends ZIOSpecDefault {
  def spec = suite("WitResultSpec")(
    test("ok unwraps to value and stays Ok through map") {
      val result = WitResult.ok(21)
      val mapped = result.map(_ * 2)

      assertTrue(
        mapped.isOk,
        mapped.unwrap() == 42,
        mapped.toEither == Right(42)
      )
    },
    test("err unwrapErr returns payload and mapError transforms it") {
      val err    = WitResult.err("boom")
      val mapped = err.mapError(_.toUpperCase)

      assertTrue(
        mapped.isErr,
        mapped.unwrapErr() == "BOOM",
        mapped.toEither == Left("BOOM")
      )
    },
    test("flatMap short-circuits on error") {
      val first  = WitResult.ok(1)
      val second = WitResult.err[String]("fail")

      val combined = for {
        a <- first
        _ <- second
      } yield a + 1

      assertTrue(combined.isErr) &&
      assertTrue {
        try {
          combined.unwrap()
          false
        } catch {
          case _: UnwrapError => true
        }
      }
    },
    test("unwrapForWit rethrows Throwable payloads directly") {
      val boom = new IllegalStateException("boom")
      val err  = WitResult.err[Throwable](boom)

      assertTrue {
        try {
          err.unwrapForWit()
          false
        } catch {
          case ex: IllegalStateException => ex eq boom
        }
      }
    },
    test("unwrapForWit wraps non-throwable payloads") {
      val err = WitResult.err("boom")

      assertTrue {
        try {
          err.unwrapForWit()
          false
        } catch {
          case ex: UnwrapError => ex.payload == "boom"
        }
      }
    }
  )
}
