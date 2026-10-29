package buildaqueryfromoptionalsearchfilters

import java.time.LocalDate
import zio.blocks.schema.Schema
import zio.blocks.sql.*

/**
 * How to Build a Query from Optional Search Filters — Shared Model
 *
 * The ticket row and its table, the search form and its sort choices, and the
 * seed that creates the ticket table and adds eight tickets (section "The
 * Ticket Table and the Search Form"). It has no main method.
 */
case class Ticket(id: Long, subject: String, customer: String, status: String, priority: Int, openedOn: LocalDate)

object Ticket {
  given Schema[Ticket] = Schema.derived
}

val ticketTable = Table.derived[Ticket]
val tickets     = Repo.derived[Ticket, Long]

def seedTickets(transactor: JdbcTransactor): Unit =
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
