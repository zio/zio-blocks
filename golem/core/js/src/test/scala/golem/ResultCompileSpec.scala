package golem

import golem.Result._
import org.scalatest.funsuite.AnyFunSuite

final class ResultCompileSpec extends AnyFunSuite {

  test("Result.ok creates Ok variant") {
    val r: Result[Int, Nothing] = Result.ok(42)
    assert(r.isOk)
    assert(r.unwrap() == 42)
  }

  test("Result.err creates Err variant") {
    val r: Result[Nothing, String] = Result.err("boom")
    assert(r.isErr)
    assert(r.unwrapErr() == "boom")
  }

  test("Result.fromEither converts Right") {
    val r: Result[Int, String] = Result.fromEither(Right(42))
    assert(r.isOk)
    assert(r.unwrap() == 42)
  }

  test("Result.fromEither converts Left") {
    val r: Result[Int, String] = Result.fromEither(Left("fail"))
    assert(r.isErr)
    assert(r.unwrapErr() == "fail")
  }

  test("Result.fromOption converts Some") {
    val r = Result.fromOption(Some(42), "missing")
    assert(r.isOk)
    assert(r.unwrap() == 42)
  }

  test("Result.fromOption converts None") {
    val r = Result.fromOption(None, "missing")
    assert(r.isErr)
    assert(r.unwrapErr() == "missing")
  }

  test("Result.toEither roundtrips Ok") {
    val r = Result.ok(42)
    assert(r.toEither == Right(42))
  }

  test("Result.toEither roundtrips Err") {
    val r = Result.err("boom")
    assert(r.toEither == Left("boom"))
  }

  test("Result.map transforms Ok value") {
    val r = Result.ok(21).map(_ * 2)
    assert(r.unwrap() == 42)
  }

  test("Result.map preserves Err") {
    val r: Result[Int, String] = Result.err("fail")
    val mapped                 = r.map(_ * 2)
    assert(mapped.isErr)
  }

  test("Result.flatMap chains Ok values") {
    val r = for {
      a <- Result.ok(10)
      b <- Result.ok(20)
    } yield a + b
    assert(r.unwrap() == 30)
  }

  test("Result.flatMap short-circuits on Err") {
    val r = for {
      a <- Result.ok(10)
      _ <- Result.err[String]("fail")
    } yield a
    assert(r.isErr)
  }

  test("Result.mapError transforms Err") {
    val r = Result.err("lower").mapError(_.toUpperCase)
    assert(r.unwrapErr() == "LOWER")
  }

  test("Result type alias resolves to WitResult") {
    val _: golem.runtime.wit.WitResult[Int, String] = Result.ok[Int](42): Result[Int, String]
    assert(true)
  }
}
