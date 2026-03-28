package foo

object AllowsReproducerOk extends App {
    import zio.blocks.schema.Schema
    import zio.blocks.schema.comptime.Allows
    import Allows._

    def writeCsv[A: Schema](rows: Seq[A])(implicit ev: Allows[A, Record[Primitive.Int]]): Unit = ???

    def insert[A: Schema](value: A)(implicit
      ev: Allows[A, Record[Primitive.Int]]
    ): String = ???

    final case class Person(age: Int)
    object Person {
      implicit val schema: Schema[Person] = Schema.derived
    }

    val x = writeCsv(Seq(Person(42)))

    println("OK")
  }
