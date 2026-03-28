package zio.blocks.schema.comptime

object AllowsReproducerFails extends App {
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

    /* In Scala 2.13.18 I get below compile error, in Scala3 it compiles OK
[warn] sbt 0.13 shell syntax is deprecated; use slash syntax instead: schemaJVM / Test / compile
[info] compiling 2 Scala sources to /Users/avinder/Workspaces/git/zio-blocks/schema/jvm/target/scala-2.13/test-classes ...
[error] /Users/avinder/Workspaces/git/zio-blocks/schema/shared/src/test/scala-2/zio/blocks/schema/comptime/AllowsReproducer.scala:19:21: could not find implicit value for parameter ev: zio.blocks.schema.comptime.Allows[foo.AllowsReproducer.Person,zio.blocks.schema.comptime.Allows.Record[zio.blocks.schema.comptime.Allows.Primitive.Int]]
[error]     val x = writeCsv(Seq(Person(42)))
[error]                     ^
[error] one error found
[error] (schemaJVM / Test / compileIncremental) Compilation failed
     */

    println("OK")
  }
