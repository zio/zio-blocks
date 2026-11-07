package testdatabasecode

import zio.blocks.sql.*

/**
 * How to Test Database Code Against SQLite — Seed Fixtures for Every Test
 *
 * Opens a database with `TestDb.withTestDb`, changes the fixture data the way a
 * test would, and restores it with `TestDb.resetFixtures`.
 *
 * Run with: cd sql-examples && sbt "Test/runMain
 * testdatabasecode.FixturesExample"
 */
object FixturesExample extends App {

  TestDb.withTestDb { transactor =>
    transactor.connect {
      Bookshop.books.all.foreach(println)

      Bookshop.books.delete(TestDb.zionomicon.id)
      println(s"After a test deletes a book: ${Bookshop.books.count} books")

      TestDb.resetFixtures()
      println(s"After resetFixtures: ${Bookshop.books.count} books")
    }
  }
}
