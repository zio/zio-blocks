package migratefromquill

import zio.blocks.schema.Schema
import zio.blocks.sql.query.*
import zio.blocks.sql.*
import scala.util.{Failure, Success, Try}

/**
 * Coming from Quill — Complete Example
 *
 * A clinic's patients and appointments after a migration from Quill: a derived
 * Repo in place of `query[Patient]`, a SqlQuery in place of `filter`/`sortBy`/
 * `take`, a Rel join in place of a for-comprehension join, a batch insert in
 * place of `liftQuery(...).foreach`, generated keys in place of
 * `returningGenerated`, a custom column type in place of a `MappedEncoding`
 * and a transaction that rolls back.
 *
 * Run with: cd sql-examples && sbt "runMain migratefromquill.CompleteExample"
 */
object CompleteExample extends App {

  case class Patient(patientId: Long, name: String, birthYear: Int, phone: Option[String])

  object Patient {
    given Schema[Patient] = Schema.derived
  }

  case class Appointment(appointmentId: Long, forPatient: Long, day: String, reason: String, cancelled: Boolean)

  object Appointment {
    given Schema[Appointment] = Schema.derived
  }

  // Replaces a pair of Quill MappedEncoding instances
  case class Phone(value: String)

  given DbCodec[Phone] = DbCodec[String].transform(Phone(_))(_.value)

  val dbFile = java.nio.file.Files.createTempFile("clinic", ".db")
  dbFile.toFile.deleteOnExit()

  // Replaces new SqliteJdbcContext(SnakeCase, config)
  val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

  // Replaces query[Patient] and query[Appointment]
  val patientTable     = Table.derived[Patient]
  val appointmentTable = Table.derived[Appointment]
  val patients         = Repo.derived[Patient, Long]
  val appointments     = Repo.derived[Appointment, Long]

  // Replaces a Quill inline def with quote { query[Patient].filter(...) }
  def bornBefore(year: Int)(using DbCon): List[Patient] =
    sql"SELECT patient_id, name, birth_year, phone FROM patient WHERE birth_year < $year".query[Patient]

  transactor.transact {
    // Table#createTable declares no PRIMARY KEY, so the tables are written by hand
    sql"""CREATE TABLE patient (
      patient_id INTEGER PRIMARY KEY,
      name       TEXT NOT NULL,
      birth_year INTEGER NOT NULL,
      phone      TEXT
    )""".update
    sql"""CREATE TABLE appointment (
      appointment_id INTEGER PRIMARY KEY,
      for_patient    INTEGER NOT NULL REFERENCES patient (patient_id),
      day            TEXT NOT NULL,
      reason         TEXT NOT NULL,
      cancelled      INTEGER NOT NULL
    )""".update

    // Replaces liftQuery(...).foreach(p => query[Patient].insertValue(p))
    patients.insertBatch(
      List(
        Patient(1L, "Ann", 1980, Some("555-0100")),
        Patient(2L, "Ben", 1995, None),
        Patient(3L, "Cara", 1970, None)
      )
    )
    appointments.insertBatch(
      List(
        Appointment(1L, 1L, "2026-01-05", "checkup", false),
        Appointment(2L, 1L, "2026-02-10", "flu", false),
        Appointment(3L, 2L, "2026-01-07", "back pain", true),
        Appointment(4L, 3L, "2026-03-01", "vaccine", false)
      )
    )
  }

  transactor.connect {
    println(s"Patients: ${patients.count}")

    println("Born before 1990:")
    bornBefore(1990).foreach(patient => println(s"  ${patient.name}"))

    // Replaces query[Patient].filter(...).sortBy(...)(Ord.desc).drop(1).take(2)
    val latest = SqlQuery.from(appointmentTable).orderBy("day", SortOrder.Desc).offset(1).limit(2)
    println(latest.sql(SqlDialect.SQLite))
    println("Appointments, second and third newest:")
    latest.toFrag(SqlDialect.SQLite).query[Appointment].foreach(a => println(s"  ${a.day} ${a.reason}"))

    // Replaces a Quill join with a for-comprehension
    val booked = SqlQuery
      .from(appointmentTable)
      .join(Rel.manyToOne(appointmentTable, _.forPatient, patientTable, _.patientId))
      .where(sql"cancelled = 0")
    println("Booked appointments:")
    booked.toFrag(SqlDialect.SQLite).query[(Appointment, Patient)].foreach { case (a, p) =>
      println(s"  ${p.name}: ${a.day} ${a.reason}")
    }

    // Replaces a MappedEncoding in a query
    val phones = sql"SELECT phone FROM patient WHERE phone IS NOT NULL".query[Phone]
    println(s"Phones: ${phones.map(_.value).mkString(", ")}")
  }

  transactor.transact {
    // Replaces insert(_.name -> lift(...)).returningGenerated(_.patientId)
    val keys = sql"INSERT INTO patient (name, birth_year) VALUES (${"Dee"}, ${1985})".updateReturningKeys[Long]
    println(s"Generated key: ${keys.mkString(", ")}")

    // Replaces updateValue and delete
    patients.update(Patient(2L, "Ben", 1995, Some("555-0199")))
    patients.delete(3L)
  }

  // Replaces ctx.transaction { ... }: an exception rolls everything back
  Try {
    transactor.transact {
      patients.insert(Patient(9L, "Eli", 1990, None))
      throw new IllegalStateException("Clinic is closed")
    }
  } match {
    case Failure(error) => println(s"Transaction rolled back: ${error.getMessage}")
    case Success(_)     => println("Transaction committed")
  }

  transactor.connect {
    println(s"Patients at the end: ${patients.all.map(_.name).mkString(", ")}")
  }
}
