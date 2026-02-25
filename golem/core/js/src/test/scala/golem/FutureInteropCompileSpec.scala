package golem

import org.scalatest.funsuite.AsyncFunSuite

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

final class FutureInteropCompileSpec extends AsyncFunSuite {
  override implicit def executionContext: ExecutionContext =
    scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  test("fromPromise converts js.Promise to Future") {
    val promise: js.Promise[Int] = js.Promise.resolve[Int](42)
    val future: Future[Int]      = FutureInterop.fromPromise(promise)
    future.map(v => assert(v == 42))
  }

  test("toPromise converts Future to js.Promise") {
    val future: Future[String]      = Future.successful("ok")
    val promise: js.Promise[String] = FutureInterop.toPromise(future)
    FutureInterop.fromPromise(promise).map(v => assert(v == "ok"))
  }

  test("fromEither converts Right to successful Future") {
    val future: Future[Int] = FutureInterop.fromEither(Right(42))
    future.map(v => assert(v == 42))
  }

  test("fromEither converts Left to failed Future") {
    val future: Future[Int] = FutureInterop.fromEither(Left("boom"))
    future.failed.map(ex => assert(ex.getMessage.contains("boom")))
  }

  test("failed creates failed Future") {
    val future: Future[Int] = FutureInterop.failed("error message")
    future.failed.map(ex => assert(ex.getMessage.contains("error message")))
  }

  test("toPromise then fromPromise roundtrips") {
    val original     = Future.successful(List(1, 2, 3))
    val roundtripped = FutureInterop.fromPromise(FutureInterop.toPromise(original))
    roundtripped.map(v => assert(v == List(1, 2, 3)))
  }
}
