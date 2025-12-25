package zio.blocks.schema

import zio.test._
import java.util.concurrent.{CountDownLatch, Executors}
import scala.collection.mutable.ListBuffer

object DerivedOpticsConcurrencySpec extends ZIOSpecDefault {

  final case class ConcurrentPerson(name: String, age: Int)
  object ConcurrentPerson extends DerivedOptics[ConcurrentPerson] {
    implicit val schema: Schema[ConcurrentPerson] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("DerivedOpticsConcurrencySpec")(
    test("optics work correctly under concurrent access") {
      val executor = Executors.newFixedThreadPool(10)
      val latch    = new CountDownLatch(100)
      val results  = ListBuffer.empty[Any]

      for (_ <- 1 to 100) {
        executor.submit(new Runnable {
          def run(): Unit = {
            results.synchronized {
              results += ConcurrentPerson.optics
            }
            latch.countDown()
          }
        })
      }

      latch.await()
      executor.shutdown()

      // All results should be the same object
      assertTrue(results.toSet.size == 1)
    }
  )
}
