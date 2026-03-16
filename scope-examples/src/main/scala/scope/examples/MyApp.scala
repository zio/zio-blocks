package scope.examples

object MyApp extends App {

  import zio.blocks.scope._

  final class Config(val dbUrl: String)

  final class Logger extends AutoCloseable {
    def log(msg: String): Unit = println(s"[LOG] $msg")
    def close(): Unit          = println("logger closed")
  }

  final class Database(config: Config) extends AutoCloseable {
    def query(sql: String): String = s"result: $sql"
    def close(): Unit              = println(s"db closed (${config.dbUrl})")
  }

  final class UserService(db: Database, logger: Logger) {
    def getUser(id: Int): String = {
      logger.log(s"fetching user $id")
      s"user $id from ${db.query("SELECT * FROM users")}"
    }
  }

  final class App(service: UserService) {
    def run(): Unit = println(service.getUser(1))
  }

  // Wire describes the dependency graph: App -> UserService -> (Database, Logger) -> Config
  // Resource.from uses the Wire to automatically construct the entire graph
  Scope.global.scoped { scope =>
    import scope._
    val config = Config("jdbc:postgres://localhost/db")
    val app    = allocate(
      Resource.from[App](
        Wire(config),
        Wire(new Logger)
      )
    )
    $(app)(_.run())
    // All resources (Logger, Database, App) clean up automatically in reverse order
  }

}
