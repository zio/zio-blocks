package zio.blocks.scope

object ShutdownHookTestMain {
  def main(args: Array[String]): Unit = {
    Scope.global.defer {
      println("SHUTDOWN_HOOK_RAN")
    }
    println("MAIN_FINISHED")
  }
}
