package testdatabasecode

import zio.blocks.sql.*

/**
 * How to Test Database Code Against SQLite — Roll Back Instead of Cleaning Up
 *
 * Runs a test body with `TestDb.rollingBack`, so its writes are visible inside
 * the block and gone afterwards, while the block's result still reaches the
 * caller.
 *
 * Run with: cd sql-examples && sbt "Test/runMain
 * testdatabasecode.RollbackExample"
 */
object RollbackExample extends App {

  val handsOnScala = Book(4L, "Hands-on Scala Programming", "Li Haoyi", 2020, None, 2)

  TestDb.withTestDb { transactor =>
    val seenInside = TestDb.rollingBack(transactor) {
      Bookshop.books.insert(handsOnScala)
      Bookshop.restock(TestDb.fpInScala.id, 10)
      (Bookshop.books.count, Bookshop.books.find(TestDb.fpInScala.id).map(_.stock).getOrElse(-1))
    }
    println(s"Inside the test: ${seenInside._1} books, ${seenInside._2} copies of FP in Scala")

    transactor.connect {
      val stock = Bookshop.books.find(TestDb.fpInScala.id).map(_.stock).getOrElse(-1)
      println(s"After the test: ${Bookshop.books.count} books, $stock copies of FP in Scala")
    }
  }
}
