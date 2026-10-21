package mapcustomcolumntypes

import java.time.Instant
import zio.blocks.schema.Schema
import zio.blocks.sql.*

/**
 * How to Map Custom Column Types — Step 8: Store an Instant on PostgreSQL
 *
 * Stores the same `Instant` in a `timestamptz` and a `timestamp` column, then
 * lets plain SQL write the same moment into both columns from a session in
 * another time zone, and reads all four values back.
 *
 * Needs a running PostgreSQL; see the guide for the docker command. Set
 * BOOKSTORE_DB_PASSWORD (and BOOKSTORE_DB_URL / BOOKSTORE_DB_USER if they
 * differ from the defaults).
 *
 * Run with: cd sql-examples && sbt "runMain
 * mapcustomcolumntypes.Step8InstantPostgresExample"
 */
object Step8InstantPostgresExample {

  case class Release(id: Long, withZone: Instant, withoutZone: Instant)

  object Release {
    given Schema[Release] = Schema.derived
  }

  val releases = Repo.derived[Release, Long]("releases", "id", _.id)

  val baseUrl  = sys.env.getOrElse("BOOKSTORE_DB_URL", "jdbc:postgresql://localhost:5432/bookstore")
  val user     = sys.env.getOrElse("BOOKSTORE_DB_USER", "bookstore")
  val password = sys.env("BOOKSTORE_DB_PASSWORD")

  // Illustrative date
  val publishedAt = Instant.parse("2021-07-15T09:30:00.123456Z")

  def main(args: Array[String]): Unit = {
    val transactor = JdbcTransactor.fromUrl(baseUrl, user, password, SqlDialect.PostgreSQL)

    transactor.connect {
      sql"DROP TABLE IF EXISTS releases".update
      sql"""CREATE TABLE releases (
        id           BIGINT PRIMARY KEY,
        with_zone    TIMESTAMPTZ NOT NULL,
        without_zone TIMESTAMP NOT NULL
      )""".update

      // Written by our Repo
      releases.insert(Release(1L, publishedAt, publishedAt))

      // Written by another client whose session runs in Tehran (UTC+03:30)
      sql"SET TIME ZONE 'Asia/Tehran'".update
      sql"""INSERT INTO releases VALUES (2,
        TIMESTAMPTZ '2021-07-15 09:30:00.123456+00',
        TIMESTAMPTZ '2021-07-15 09:30:00.123456+00')""".update
      sql"SET TIME ZONE 'UTC'".update

      println(sql"SELECT id, with_zone::text, without_zone::text FROM releases ORDER BY id".query[(Long, String, String)])
      releases.all.foreach(println)
      sql"DROP TABLE releases".update
    }
  }
}
