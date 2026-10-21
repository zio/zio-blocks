package rolloutfeaturegradually

import java.util.concurrent.{Executors, TimeUnit}

import zio.blocks.config._

/**
 * How to Roll Out a Feature Gradually with Flags — Putting It Together
 *
 * The Scala bookstore's flags: a restart-only free-shipping threshold, a
 * recommendations panel rolled out to half of the eu readers, a startup report
 * with near-miss warnings, a validated runtime change, and a scheduled reload.
 *
 * Run with: cd config-examples && sbt "runMain
 * rolloutfeaturegradually.CompleteExample"
 */
object CompleteExample {

  object FreeShippingThreshold extends StaticFlag[Int](35)

  object RecommendationsPanel extends DynamicFlag[Boolean](false, "false")

  final case class Book(title: String, price: Int)

  final case class Reader(id: String, region: String)

  val catalog = List(
    Book("Zionomicon", 45),
    Book("Programming in Scala", 40),
    Book("Functional Programming in Scala", 30)
  )

  // Stands in for a remote flag service: reload re-reads whatever it holds now.
  @volatile var flagService: Map[String, String] = Map(
    "rolloutfeaturegradually.CompleteExample.FreeShippingThreshold" -> "40",
    "rolloutfeaturegradually.CompleteExample.RecommendationsPanel"  -> "true@*/eu/50%; false"
  )

  val flagServiceSource: FlagSource = new FlagSource {
    val sourceId: String  = "flag-service"
    def get(name: String) = FlagSource.fromMap(flagService, sourceId).get(name)
  }

  def checkout(reader: Reader, book: Book): String = {
    val shipping = if (book.price >= FreeShippingThreshold()) "free shipping" else "standard shipping"
    val panel    = if (RecommendationsPanel(s"recommendations:${reader.id}", reader.region)) "panel" else "no panel"
    s"${reader.id} (${reader.region}) buys ${book.title}: $shipping, $panel"
  }

  def main(args: Array[String]): Unit = {
    // 1. Register sources before any flag is touched.
    FlagSource.Registry.register(flagServiceSource)

    // 2. Startup report: typos and the resolved values.
    val flags = List(FreeShippingThreshold.name, RecommendationsPanel.name)
    flags.flatMap(Flag.nearMissWarnings).foreach(println)
    println(Flag.dump())

    // 3. Serve some readers.
    val readers = List(Reader("reader-7", "eu"), Reader("reader-8", "eu"), Reader("reader-9", "us"))
    readers.zip(catalog).foreach { case (reader, book) => println(checkout(reader, book)) }

    // 4. Operators push a wider rollout; validate it before the service picks it up.
    val proposed = "true@*/eu/100%; true@*/us/25%; false"
    Rollout.validate(proposed) match {
      case Right(Nil)  => flagService = flagService.updated(RecommendationsPanel.name, proposed)
      case Right(warn) => println(s"held back: ${warn.mkString("; ")}")
      case Left(error) => println(s"rejected: ${error.message}")
    }

    // 5. Reload on a schedule: the library never polls on its own.
    val scheduler = Executors.newSingleThreadScheduledExecutor()
    scheduler.scheduleAtFixedRate(
      () =>
        RecommendationsPanel.reload() match {
          case Flag.ReloadResult.Updated(from, to) => println(s"rollout changed: $from -> $to")
          case Flag.ReloadResult.Failed(error)     => println(s"rollout rejected: ${error.message}")
          case _                                   => ()
        },
      0,
      30,
      TimeUnit.SECONDS
    )
    Thread.sleep(500)
    scheduler.shutdown()

    readers.zip(catalog).foreach { case (reader, book) => println(checkout(reader, book)) }
  }
}
