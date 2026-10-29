package buildaqueryfromoptionalsearchfilters

import java.time.LocalDate
import zio.blocks.sql.*

/**
 * How to Build a Query from Optional Search Filters — Shared Condition
 * Functions
 *
 * The condition, sort and paging functions that sections 4 to 10 define and
 * later sections reuse, each labelled with its section. It has no main method.
 */

// Section "Turn a Filled-In Field into a Condition"
def filled(field: Option[String]): Option[String] =
  field.map(_.trim).filter(_.nonEmpty)

def customerIs(search: TicketSearch): Option[Frag] =
  filled(search.customer).map(customer => sql"customer = $customer")

def describe(frag: Frag): Unit = {
  println(frag.sql(SqlDialect.SQLite))
  println(frag.params)
}

// Section "Join the Conditions with AND"
def whereAnd(conditions: Option[Frag]*): Frag = {
  val present = conditions.flatten
  if (present.isEmpty) Frag.empty
  else Frag.literal(" WHERE ") ++ present.map(c => sql"($c)").reduce((a, b) => a ++ Frag.literal(" AND ") ++ b)
}

val selectTickets = Frag.literal("SELECT id, subject, customer, status, priority, opened_on FROM ticket")

// Section "Match Part of the Subject with LIKE"
def likePattern(text: String): String =
  "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"

def subjectContains(search: TicketSearch): Option[Frag] =
  filled(search.text).map(text => sql"subject LIKE ${likePattern(text)} ESCAPE '!'")

// Section "Filter a Date Range"
def openedOnOrAfter(search: TicketSearch): Option[Frag] =
  search.openedFrom.map(from => sql"opened_on >= $from")

def openedOnOrBefore(search: TicketSearch): Option[Frag] =
  search.openedTo.map(to => sql"opened_on <= $to")

// Section "Match Any of Several Statuses with IN"
def commaSeparated(values: Seq[String]): Frag =
  values.map(v => sql"$v").reduce((a, b) => a ++ Frag.literal(", ") ++ b)

def statusIn(search: TicketSearch): Option[Frag] =
  if (search.statuses.isEmpty) None
  else Some(sql"status IN (${commaSeparated(search.statuses.toSeq.sorted)})")

// Section "Sort by an Allowed Column"
def orderBy(sortBy: SortBy): Frag = sortBy match {
  case SortBy.Newest     => Frag.literal(" ORDER BY opened_on DESC, id DESC")
  case SortBy.Oldest     => Frag.literal(" ORDER BY opened_on ASC, id ASC")
  case SortBy.MostUrgent => Frag.literal(" ORDER BY priority ASC, opened_on DESC, id DESC")
}

// Section "Return One Page of Results"
def pageSizeOf(search: TicketSearch): Int = search.pageSize.max(1).min(100)

def offsetOf(search: TicketSearch): Int = (search.page.max(1) - 1) * pageSizeOf(search)

def pageOf(search: TicketSearch): Frag =
  sql" LIMIT ${pageSizeOf(search)} OFFSET ${offsetOf(search)}"

// Section "Run the Whole Search" (the list of conditions and the support agent's search; the queries are in Step11)
def conditions(search: TicketSearch): Seq[Option[Frag]] = Seq(
  subjectContains(search),
  customerIs(search),
  statusIn(search),
  openedOnOrAfter(search),
  openedOnOrBefore(search)
)

val loginProblems = TicketSearch(
  text = Some("log"),
  customer = Some("Acme"),
  statuses = Set("open", "pending"),
  sortBy = SortBy.MostUrgent
)
