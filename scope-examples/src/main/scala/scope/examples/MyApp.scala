package scope.examples

object MyApp extends App {

  import zio.blocks.scope.*
  import scala.concurrent.{Future, ExecutionContext}
  import scala.concurrent.ExecutionContext.Implicits.global

  class Database extends AutoCloseable {
    override def close(): Unit = ()
  }

  Scope.global.scoped { scope =>
    import scope.*

    val db = allocate(Resource(new Database()))

    Future {
      $(db) { _ => } // ERROR: ownership violation
    }
  }

}
