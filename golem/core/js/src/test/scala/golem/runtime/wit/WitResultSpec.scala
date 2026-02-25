package golem.runtime.wit

import org.scalatest.funsuite.AnyFunSuite

class WitResultSpec extends AnyFunSuite {
  test("ok unwraps to value and stays Ok through map") {
    val result = WitResult.ok(21)
    val mapped = result.map(_ * 2)

    assert(mapped.isOk)
    assert(mapped.unwrap() == 42)
    assert(mapped.toEither == Right(42))
  }

  test("err unwrapErr returns payload and mapError transforms it") {
    val err    = WitResult.err("boom")
    val mapped = err.mapError(_.toUpperCase)

    assert(mapped.isErr)
    assert(mapped.unwrapErr() == "BOOM")
    assert(mapped.toEither == Left("BOOM"))
  }

  test("flatMap short-circuits on error") {
    val first  = WitResult.ok(1)
    val second = WitResult.err[String]("fail")

    val combined = for {
      a <- first
      _ <- second
    } yield a + 1

    assert(combined.isErr)
    assertThrows[WitResult.UnwrapError](combined.unwrap())
  }

  test("unwrapForWit rethrows Throwable payloads directly") {
    val boom = new IllegalStateException("boom")
    val err  = WitResult.err[Throwable](boom)

    val thrown = intercept[IllegalStateException] {
      err.unwrapForWit()
    }
    assert(thrown eq boom)
  }

  test("unwrapForWit wraps non-throwable payloads") {
    val err    = WitResult.err("boom")
    val thrown = intercept[WitResult.UnwrapError] {
      err.unwrapForWit()
    }
    assert(thrown.payload == "boom")
  }
}
