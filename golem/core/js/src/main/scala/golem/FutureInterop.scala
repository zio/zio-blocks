package golem

import scala.concurrent.Future
import scala.scalajs.js

/**
 * These helpers convert between Scala `Future` and JS `Promise`, and provide a
 * small `Either -> Future` adapter.
 */
object FutureInterop {
  def fromPromise[A](promise: js.Promise[A]): Future[A] =
    golem.runtime.util.FutureInterop.fromPromise(promise)

  def toPromise[A](future: Future[A]): js.Promise[A] =
    golem.runtime.util.FutureInterop.toPromise(future)

  def fromEither[A](either: Either[String, A]): Future[A] =
    golem.runtime.util.FutureInterop.fromEither(either)

  def failed[A](message: String): Future[A] =
    golem.runtime.util.FutureInterop.failed(message)
}
