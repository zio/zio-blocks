package scope.examples

object MyApp extends App {

  import zio.blocks.scope.{Scope, Resource, Unscoped}
  import scala.concurrent.duration.FiniteDuration

  case class ProcessingResult(count: Int, elapsed: FiniteDuration)

  object ProcessingResult {
    implicit val unscoped: Unscoped[ProcessingResult] = new Unscoped[ProcessingResult] {}
  }

  def processData(): ProcessingResult = Scope.global.scoped { scope =>
    import scope.*

    val startTime = System.nanoTime()
    val input = allocate(Resource.fromAutoCloseable(new java.io.ByteArrayInputStream("test".getBytes)))
    val count = $(input)(_.available())

    val elapsed = System.nanoTime() - startTime

    ProcessingResult(count, FiniteDuration(elapsed, java.util.concurrent.TimeUnit.NANOSECONDS))
  }

  val result = processData()
  println(result)


}
