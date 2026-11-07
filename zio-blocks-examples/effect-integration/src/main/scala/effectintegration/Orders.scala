package effectintegration

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._
import zio.blocks.schema._
import zio.blocks.sql._
import zio.blocks.streams.Stream

final case class Order(id: Long, customer: String, totalCents: Long)

object Order {
  implicit val schema: Schema[Order] = Schema.derived
}

final case class OrderEvent(orderId: Long, status: String)

object OrderEvent {
  implicit val schema: Schema[OrderEvent] = Schema.derived
}

object OrdersDb {
  def createTable()(using DbCon): Unit =
    sql"""CREATE TABLE orders (
      id INTEGER PRIMARY KEY,
      customer TEXT NOT NULL,
      total_cents INTEGER NOT NULL
    )""".update

  def insert(order: Order)(using DbCon): Int =
    sql"INSERT INTO orders (id, customer, total_cents) VALUES (${order.id}, ${order.customer}, ${order.totalCents})".update

  def all()(using DbCon): List[Order] =
    sql"SELECT id, customer, total_cents FROM orders ORDER BY id".query[Order]

  def totals()(using DbCon): Stream[Throwable, Long] =
    sql"SELECT total_cents FROM orders".queryStream[Long].flatMap(chunk => Stream.fromIterable(chunk))

  // A fresh SQLite file with an empty orders table.
  def open(): JdbcTransactor = {
    val file = Files.createTempFile("orders", ".db")
    file.toFile.deleteOnExit()
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$file", SqlDialect.SQLite)
    transactor.connect(createTable())
    transactor
  }
}

object OrderEvents {
  // One JSON object per line. The file opens when the stream runs and closes when the run ends.
  def fromFile(path: Path): Stream[SchemaError, OrderEvent] =
    Stream.fromAcquireRelease(
      Files.newBufferedReader(path, UTF_8),
      reader => { reader.close(); println("order-events file closed") }
    ) { reader =>
      Stream
        .fromIterator(reader.lines().iterator().asScala)
        .flatMap(line => line.fromJson[OrderEvent].fold(Stream.fail(_), Stream.succeed(_)))
    }

  def write(lines: String*): Path = {
    val path = Files.createTempFile("order-events", ".jsonl")
    path.toFile.deleteOnExit()
    Files.write(path, lines.asJava, UTF_8)
  }
}
