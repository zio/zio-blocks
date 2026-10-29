//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-sql:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0
// Run from this folder: scala-cli run CompleteExample.scala
// Or from sql-examples: sbt "runMain buildaqueryfromoptionalsearchfilters.CompleteExample"

package buildaqueryfromoptionalsearchfilters

import java.nio.file.Files
import java.time.LocalDate
import zio.blocks.schema.Schema
import zio.blocks.sql.query.{SortOrder, SqlQuery}
import zio.blocks.sql.*

/**
 * How to Build a Query from Optional Search Filters — Complete Example
 *
 * Creates and fills the ticket table, defines the search form and every
 * condition function, runs the support agent's search with its count, a second
 * page and a hostile input, and then runs the same search through `SqlQuery`.
 * It deletes its SQLite file at the end.
 */
object CompleteExample {
  case class Ticket(id: Long, subject: String, customer: String, status: String, priority: Int, openedOn: LocalDate)

  object Ticket {
    given Schema[Ticket] = Schema.derived
  }

  val ticketTable = Table.derived[Ticket]
  val tickets     = Repo.derived[Ticket, Long]

  enum SortBy {
    case Newest, Oldest, MostUrgent
  }

  case class TicketSearch(
    text: Option[String] = None,
    customer: Option[String] = None,
    statuses: Set[String] = Set.empty,
    openedFrom: Option[LocalDate] = None,
    openedTo: Option[LocalDate] = None,
    sortBy: SortBy = SortBy.Newest,
    page: Int = 1,
    pageSize: Int = 20
  )

  def filled(field: Option[String]): Option[String] =
    field.map(_.trim).filter(_.nonEmpty)

  def customerIs(search: TicketSearch): Option[Frag] =
    filled(search.customer).map(customer => sql"customer = $customer")

  def describe(frag: Frag): Unit = {
    println(frag.sql(SqlDialect.SQLite))
    println(frag.params)
  }

  def whereAnd(conditions: Option[Frag]*): Frag = {
    val present = conditions.flatten
    if (present.isEmpty) Frag.empty
    else Frag.literal(" WHERE ") ++ present.map(c => sql"($c)").reduce((a, b) => a ++ Frag.literal(" AND ") ++ b)
  }

  val selectTickets = Frag.literal("SELECT id, subject, customer, status, priority, opened_on FROM ticket")

  def likePattern(text: String): String =
    "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"

  def subjectContains(search: TicketSearch): Option[Frag] =
    filled(search.text).map(text => sql"subject LIKE ${likePattern(text)} ESCAPE '!'")

  def openedOnOrAfter(search: TicketSearch): Option[Frag] =
    search.openedFrom.map(from => sql"opened_on >= $from")

  def openedOnOrBefore(search: TicketSearch): Option[Frag] =
    search.openedTo.map(to => sql"opened_on <= $to")

  def commaSeparated(values: Seq[String]): Frag =
    values.map(v => sql"$v").reduce((a, b) => a ++ Frag.literal(", ") ++ b)

  def statusIn(search: TicketSearch): Option[Frag] =
    if (search.statuses.isEmpty) None
    else Some(sql"status IN (${commaSeparated(search.statuses.toSeq.sorted)})")

  def orderBy(sortBy: SortBy): Frag = sortBy match {
    case SortBy.Newest     => Frag.literal(" ORDER BY opened_on DESC, id DESC")
    case SortBy.Oldest     => Frag.literal(" ORDER BY opened_on ASC, id ASC")
    case SortBy.MostUrgent => Frag.literal(" ORDER BY priority ASC, opened_on DESC, id DESC")
  }

  def pageSizeOf(search: TicketSearch): Int = search.pageSize.max(1).min(100)

  def offsetOf(search: TicketSearch): Int = (search.page.max(1) - 1) * pageSizeOf(search)

  def pageOf(search: TicketSearch): Frag =
    sql" LIMIT ${pageSizeOf(search)} OFFSET ${offsetOf(search)}"

  def conditions(search: TicketSearch): Seq[Option[Frag]] = Seq(
    subjectContains(search),
    customerIs(search),
    statusIn(search),
    openedOnOrAfter(search),
    openedOnOrBefore(search)
  )

  def searchQuery(search: TicketSearch): Frag =
    selectTickets ++ whereAnd(conditions(search)*) ++ orderBy(search.sortBy) ++ pageOf(search)

  def countQuery(search: TicketSearch): Frag =
    sql"SELECT COUNT(*) FROM ticket" ++ whereAnd(conditions(search)*)

  def searchWithBuilder(search: TicketSearch): SqlQuery[Ticket] = {
    val filtered = conditions(search).flatten.foldLeft(SqlQuery.from(ticketTable)) { (query, condition) =>
      query.where(sql"($condition)")
    }
    val sorted = search.sortBy match {
      case SortBy.Newest     => filtered.orderBy("opened_on", SortOrder.Desc).orderBy("id", SortOrder.Desc)
      case SortBy.Oldest     => filtered.orderBy("opened_on").orderBy("id")
      case SortBy.MostUrgent =>
        filtered.orderBy("priority").orderBy("opened_on", SortOrder.Desc).orderBy("id", SortOrder.Desc)
    }
    sorted.limit(pageSizeOf(search)).offset(offsetOf(search))
  }

  def main(args: Array[String]): Unit = {
    val dbFile     = Files.createTempFile("helpdesk", ".db")
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

    transactor.connect {
      ticketTable.createTable(SqlDialect.SQLite).update
      List(
        Ticket(1L, "Cannot log in after password reset", "Acme", "open", 1, LocalDate.of(2026, 9, 28)),
        Ticket(2L, "Invoice shows the 50% discount twice", "Globex", "closed", 2, LocalDate.of(2026, 10, 2)),
        Ticket(3L, "Login page loads slowly", "Acme", "pending", 2, LocalDate.of(2026, 10, 6)),
        Ticket(4L, "Export to CSV drops the last row", "Initech", "open", 3, LocalDate.of(2026, 10, 9)),
        Ticket(5L, "Two-factor code never arrives", "Globex", "open", 1, LocalDate.of(2026, 10, 14)),
        Ticket(6L, "Typo on the pricing page", "Acme", "closed", 3, LocalDate.of(2026, 10, 17)),
        Ticket(7L, "API returns 500 on large uploads", "Initech", "pending", 1, LocalDate.of(2026, 10, 21)),
        Ticket(8L, "Cannot change the billing email", "Acme", "open", 2, LocalDate.of(2026, 10, 24))
      ).foreach(tickets.insert)
    }

    val loginProblems = TicketSearch(
      text = Some("log"),
      customer = Some("Acme"),
      statuses = Set("open", "pending"),
      sortBy = SortBy.MostUrgent
    )

    println("support agent's search:")
    describe(searchQuery(loginProblems))

    transactor.connect {
      println(s"matching tickets: ${countQuery(loginProblems).queryOne[Long]}")
      searchQuery(loginProblems).query[Ticket].foreach(println)
    }

    transactor.connect {
      val secondPage = TicketSearch(sortBy = SortBy.Newest, page = 2, pageSize = 3)
      val where      = whereAnd()
      print("all tickets: ")
      println((sql"SELECT COUNT(*) FROM ticket" ++ where).queryOne[Long])
      print("second page of three, newest first: ")
      println(
        (Frag.literal("SELECT id FROM ticket") ++ where ++ orderBy(secondPage.sortBy) ++ pageOf(secondPage)).query[Long]
      )
    }

    val hostile = TicketSearch(text = Some("'; DROP TABLE ticket; --"))

    println("hostile input:")
    describe(searchQuery(hostile))

    transactor.connect {
      print("hostile search finds: ")
      println(searchQuery(hostile).query[Ticket])
      println(s"tickets in the table: ${tickets.count}")
    }

    val builderQuery = searchWithBuilder(loginProblems)

    println("same search with SqlQuery:")
    println(builderQuery.explain(SqlDialect.SQLite))

    transactor.connect {
      builderQuery.toFrag(SqlDialect.SQLite).query[Ticket].foreach(println)
    }

    Files.deleteIfExists(dbFile)
  }
}
