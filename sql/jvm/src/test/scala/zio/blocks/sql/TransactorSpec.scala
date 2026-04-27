package zio.blocks.sql

import zio.test.*
import zio.blocks.schema.*
import java.sql.DriverManager

object TransactorSpec extends ZIOSpecDefault {
  private val _ = Class.forName("org.sqlite.JDBC")

  case class User(id: Int, name: String, email: String)
  object User {
    implicit val schema: Schema[User] = Schema.derived
  }

  case class AllTypes(
    intVal: Int,
    longVal: Long,
    doubleVal: Double,
    floatVal: Float,
    boolVal: Boolean,
    strVal: String,
    shortVal: Short,
    byteVal: Byte
  )
  object AllTypes {
    implicit val schema: Schema[AllTypes] = Schema.derived
  }

  case class WithOption(id: Int, nickname: Option[String])
  object WithOption {
    implicit val schema: Schema[WithOption] = Schema.derived
  }

  private val transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

  private given DbCodec[User]       = User.schema.deriving(DbCodecDeriver).derive
  private given DbCodec[AllTypes]   = AllTypes.schema.deriving(DbCodecDeriver).derive
  private given DbCodec[WithOption] = WithOption.schema.deriving(DbCodecDeriver).derive

  private given DbCodec[Int]     = implicitly[Schema[Int]].deriving(DbCodecDeriver).derive
  private given DbCodec[String]  = implicitly[Schema[String]].deriving(DbCodecDeriver).derive
  private given DbCodec[Long]    = implicitly[Schema[Long]].deriving(DbCodecDeriver).derive
  private given DbCodec[Double]  = implicitly[Schema[Double]].deriving(DbCodecDeriver).derive
  private given DbCodec[Boolean] =
    implicitly[Schema[Boolean]].deriving(DbCodecDeriver).derive

  private def sharedConnTransactor(): (JdbcTransactor, java.sql.Connection) = {
    val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
    val tx   = new JdbcTransactor(() => conn, SqlDialect.SQLite) {
      override def connect[A](f: DbCon ?=> A): A = {
        val dbConn       = new JdbcConnection(conn)
        given con: DbCon = new DbCon {
          val connection: DbConnection = dbConn
          val dialect: SqlDialect      = SqlDialect.SQLite
          val logger: SqlLogger        = SqlLogger.noop
        }
        f
      }

      override def transact[A](f: DbTx ?=> A): A = {
        val dbConn = new JdbcConnection(conn)
        conn.setAutoCommit(false)
        try {
          given tx: DbTx = new DbTx {
            val connection: DbConnection = dbConn
            val dialect: SqlDialect      = SqlDialect.SQLite
            val logger: SqlLogger        = SqlLogger.noop
          }
          val result = f
          conn.commit()
          result
        } catch {
          case e: Throwable =>
            conn.rollback()
            throw e
        } finally conn.setAutoCommit(true)
      }
    }
    (tx, conn)
  }

  def spec: Spec[TestEnvironment, Any] = suite("TransactorSpec")(
    test("connect executes queries") {
      transactor.connect {
        SqlOps.update(Frag.const("CREATE TABLE IF NOT EXISTS test_connect (id INTEGER NOT NULL)"))
        SqlOps.update(
          sql"INSERT INTO test_connect (id) VALUES (${DbValue.DbInt(1)})"
        )
        val ids = SqlOps.query[Int](sql"SELECT id FROM test_connect")
        assertTrue(ids == List(1))
      }
    },
    test("INSERT and SELECT roundtrip") {
      transactor.connect {
        SqlOps.update(
          Frag.const(
            "CREATE TABLE IF NOT EXISTS users (id INTEGER NOT NULL, name TEXT NOT NULL, email TEXT NOT NULL)"
          )
        )
        SqlOps.update(
          sql"INSERT INTO users (id, name, email) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("Alice")}, ${DbValue.DbString("alice@example.com")})"
        )
        SqlOps.update(
          sql"INSERT INTO users (id, name, email) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("Bob")}, ${DbValue.DbString("bob@example.com")})"
        )
        val users = SqlOps.query[User](sql"SELECT id, name, email FROM users ORDER BY id")
        assertTrue(
          users.length == 2,
          users.head.id == 1,
          users.head.name == "Alice",
          users.head.email == "alice@example.com",
          users(1).name == "Bob"
        )
      }
    },
    test("queryOne returns first result") {
      transactor.connect {
        SqlOps.update(
          Frag.const(
            "CREATE TABLE IF NOT EXISTS query_one_test (id INTEGER NOT NULL, val TEXT NOT NULL)"
          )
        )
        SqlOps.update(
          sql"INSERT INTO query_one_test (id, val) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("first")})"
        )
        SqlOps.update(
          sql"INSERT INTO query_one_test (id, val) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("second")})"
        )
        val result = SqlOps.queryOne[String](
          sql"SELECT val FROM query_one_test WHERE id = ${DbValue.DbInt(1)}"
        )
        assertTrue(result == Some("first"))
      }
    },
    test("empty result returns empty List") {
      transactor.connect {
        SqlOps.update(Frag.const("CREATE TABLE IF NOT EXISTS empty_test (id INTEGER NOT NULL)"))
        val result = SqlOps.query[Int](sql"SELECT id FROM empty_test")
        assertTrue(result.isEmpty)
      }
    },
    test("queryOne on empty result returns None") {
      transactor.connect {
        SqlOps.update(Frag.const("CREATE TABLE IF NOT EXISTS empty_one_test (id INTEGER NOT NULL)"))
        val result = SqlOps.queryOne[Int](sql"SELECT id FROM empty_one_test")
        assertTrue(result.isEmpty)
      }
    },
    test("transaction commits on success") {
      val (tx, conn) = sharedConnTransactor()
      try {
        tx.connect {
          SqlOps.update(Frag.const("CREATE TABLE tx_commit (id INTEGER NOT NULL, name TEXT NOT NULL)"))
        }
        tx.transact {
          SqlOps.update(
            sql"INSERT INTO tx_commit (id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("committed")})"
          )
        }
        tx.connect {
          val rows = SqlOps.query[String](sql"SELECT name FROM tx_commit")
          assertTrue(rows == List("committed"))
        }
      } finally conn.close()
    },
    test("transaction rolls back on exception") {
      val (tx, conn) = sharedConnTransactor()
      try {
        tx.connect {
          SqlOps.update(Frag.const("CREATE TABLE tx_rollback (id INTEGER NOT NULL, name TEXT NOT NULL)"))
          SqlOps.update(
            sql"INSERT INTO tx_rollback (id, name) VALUES (${DbValue.DbInt(0)}, ${DbValue.DbString("before")})"
          )
        }
        try {
          tx.transact {
            SqlOps.update(
              sql"INSERT INTO tx_rollback (id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("should_rollback")})"
            )
            throw new RuntimeException("forced error")
          }
        } catch {
          case _: RuntimeException => ()
        }
        tx.connect {
          val rows = SqlOps.query[String](sql"SELECT name FROM tx_rollback WHERE id = ${DbValue.DbInt(1)}")
          assertTrue(rows.isEmpty)
        }
      } finally conn.close()
    },
    test("update returns affected row count") {
      transactor.connect {
        SqlOps.update(Frag.const("CREATE TABLE IF NOT EXISTS count_test (id INTEGER NOT NULL)"))
        SqlOps.update(
          sql"INSERT INTO count_test (id) VALUES (${DbValue.DbInt(1)})"
        )
        SqlOps.update(
          sql"INSERT INTO count_test (id) VALUES (${DbValue.DbInt(2)})"
        )
        val deleted = SqlOps.update(Frag.const("DELETE FROM count_test"))
        assertTrue(deleted == 2)
      }
    },
    suite("type roundtrip tests")(
      test("Long roundtrip") {
        transactor.connect {
          SqlOps.update(Frag.const("CREATE TABLE rt_long (v INTEGER NOT NULL)"))
          SqlOps.update(sql"INSERT INTO rt_long (v) VALUES (${DbValue.DbLong(9876543210L)})")
          val result = SqlOps.query[Long](sql"SELECT v FROM rt_long")
          assertTrue(result == List(9876543210L))
        }
      },
      test("Double roundtrip") {
        transactor.connect {
          SqlOps.update(Frag.const("CREATE TABLE rt_double (v REAL NOT NULL)"))
          SqlOps.update(sql"INSERT INTO rt_double (v) VALUES (${DbValue.DbDouble(3.14159)})")
          val result = SqlOps.query[Double](sql"SELECT v FROM rt_double")
          assertTrue(result == List(3.14159))
        }
      },
      test("Boolean roundtrip") {
        transactor.connect {
          SqlOps.update(Frag.const("CREATE TABLE rt_bool (v INTEGER NOT NULL)"))
          SqlOps.update(sql"INSERT INTO rt_bool (v) VALUES (${DbValue.DbBoolean(true)})")
          SqlOps.update(sql"INSERT INTO rt_bool (v) VALUES (${DbValue.DbBoolean(false)})")
          val result = SqlOps.query[Boolean](sql"SELECT v FROM rt_bool ORDER BY v")
          assertTrue(result == List(false, true))
        }
      },
      test("all primitive types roundtrip") {
        transactor.connect {
          SqlOps.update(
            Frag.const(
              "CREATE TABLE rt_all (" +
                "int_val INTEGER NOT NULL, " +
                "long_val INTEGER NOT NULL, " +
                "double_val REAL NOT NULL, " +
                "float_val REAL NOT NULL, " +
                "bool_val INTEGER NOT NULL, " +
                "str_val TEXT NOT NULL, " +
                "short_val INTEGER NOT NULL, " +
                "byte_val INTEGER NOT NULL)"
            )
          )
          SqlOps.update(
            sql"INSERT INTO rt_all VALUES (${DbValue.DbInt(42)}, ${DbValue.DbLong(123456789L)}, ${DbValue.DbDouble(2.718)}, ${DbValue.DbFloat(1.5f)}, ${DbValue.DbBoolean(true)}, ${DbValue.DbString("test")}, ${DbValue.DbShort(100.toShort)}, ${DbValue.DbByte(7.toByte)})"
          )
          val result = SqlOps.query[AllTypes](sql"SELECT * FROM rt_all")
          assertTrue(
            result.length == 1,
            result.head.intVal == 42,
            result.head.longVal == 123456789L,
            result.head.doubleVal == 2.718,
            result.head.floatVal == 1.5f,
            result.head.boolVal == true,
            result.head.strVal == "test",
            result.head.shortVal == 100.toShort,
            result.head.byteVal == 7.toByte
          )
        }
      },
      test("DbNull writeParams via Option None roundtrip") {
        transactor.connect {
          SqlOps.update(Frag.const("CREATE TABLE rt_null (id INTEGER NOT NULL, nick TEXT)"))
          SqlOps.update(
            sql"INSERT INTO rt_null (id, nick) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("present")})"
          )
          SqlOps.update(
            sql"INSERT INTO rt_null (id, nick) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbNull})"
          )
          val results = SqlOps.query[WithOption](sql"SELECT id, nick FROM rt_null ORDER BY id")
          assertTrue(
            results.length == 2,
            results(0) == WithOption(1, Some("present")),
            results(1) == WithOption(2, None)
          )
        }
      },
      test("multiple rows insert and select") {
        transactor.connect {
          SqlOps.update(
            Frag.const("CREATE TABLE rt_multi (id INTEGER NOT NULL, name TEXT NOT NULL, email TEXT NOT NULL)")
          )
          SqlOps.update(
            sql"INSERT INTO rt_multi VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("a")}, ${DbValue.DbString("a@test.com")})"
          )
          SqlOps.update(
            sql"INSERT INTO rt_multi VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("b")}, ${DbValue.DbString("b@test.com")})"
          )
          SqlOps.update(
            sql"INSERT INTO rt_multi VALUES (${DbValue.DbInt(3)}, ${DbValue.DbString("c")}, ${DbValue.DbString("c@test.com")})"
          )
          val results = SqlOps.query[User](
            sql"SELECT id, name, email FROM rt_multi ORDER BY id"
          )
          assertTrue(
            results.length == 3,
            results(0) == User(1, "a", "a@test.com"),
            results(1) == User(2, "b", "b@test.com"),
            results(2) == User(3, "c", "c@test.com")
          )
        }
      },
      test("queryOne returns None for non-existing row") {
        transactor.connect {
          SqlOps.update(Frag.const("CREATE TABLE rt_qone (id INTEGER NOT NULL)"))
          SqlOps.update(sql"INSERT INTO rt_qone VALUES (${DbValue.DbInt(1)})")
          val existing    = SqlOps.queryOne[Int](sql"SELECT id FROM rt_qone WHERE id = ${DbValue.DbInt(1)}")
          val nonExisting = SqlOps.queryOne[Int](sql"SELECT id FROM rt_qone WHERE id = ${DbValue.DbInt(999)}")
          assertTrue(
            existing == Some(1),
            nonExisting.isEmpty
          )
        }
      },
      test("BigDecimal writeParams") {
        transactor.connect {
          SqlOps.update(Frag.const("CREATE TABLE rt_bigdec (v TEXT NOT NULL)"))
          SqlOps.update(sql"INSERT INTO rt_bigdec (v) VALUES (${DbValue.DbBigDecimal(BigDecimal("123.456"))})")
          val ps = summon[DbCon].connection.prepareStatement("SELECT v FROM rt_bigdec")
          try {
            val rs = ps.executeQuery()
            try {
              rs.next()
              val str = rs.reader.getString(1)
              assertTrue(str == "123.456")
            } finally rs.close()
          } finally ps.close()
        }
      },
      test("Short and Byte writeParams") {
        transactor.connect {
          SqlOps.update(Frag.const("CREATE TABLE rt_small (s INTEGER NOT NULL, b INTEGER NOT NULL)"))
          SqlOps.update(
            sql"INSERT INTO rt_small VALUES (${DbValue.DbShort(32000.toShort)}, ${DbValue.DbByte(127.toByte)})"
          )
          val ps = summon[DbCon].connection.prepareStatement("SELECT s, b FROM rt_small")
          try {
            val rs = ps.executeQuery()
            try {
              rs.next()
              val reader = rs.reader
              val s      = reader.getShort(1)
              val b      = reader.getByte(2)
              assertTrue(s == 32000.toShort, b == 127.toByte)
            } finally rs.close()
          } finally ps.close()
        }
      },
      test("Float writeParams and read") {
        transactor.connect {
          SqlOps.update(Frag.const("CREATE TABLE rt_float (v REAL NOT NULL)"))
          SqlOps.update(sql"INSERT INTO rt_float (v) VALUES (${DbValue.DbFloat(2.5f)})")
          val ps = summon[DbCon].connection.prepareStatement("SELECT v FROM rt_float")
          try {
            val rs = ps.executeQuery()
            try {
              rs.next()
              val v = rs.reader.getFloat(1)
              assertTrue(v == 2.5f)
            } finally rs.close()
          } finally ps.close()
        }
      },
      test("Char roundtrip via DbChar") {
        transactor.connect {
          SqlOps.update(Frag.const("CREATE TABLE rt_char (v TEXT NOT NULL)"))
          SqlOps.update(sql"INSERT INTO rt_char (v) VALUES (${DbValue.DbChar('X')})")
          val result = SqlOps.query[String](sql"SELECT v FROM rt_char")
          assertTrue(result == List("X"))
        }
      },
      test("UUID roundtrip via TEXT") {
        transactor.connect {
          val uuid = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
          SqlOps.update(Frag.const("CREATE TABLE rt_uuid (v TEXT NOT NULL)"))
          SqlOps.update(sql"INSERT INTO rt_uuid (v) VALUES (${DbValue.DbUUID(uuid)})")
          val ps = summon[DbCon].connection.prepareStatement("SELECT v FROM rt_uuid")
          try {
            val rs = ps.executeQuery()
            try {
              rs.next()
              val v = rs.reader.getUUID(1)
              assertTrue(v == uuid)
            } finally rs.close()
          } finally ps.close()
        }
      },
      test("Duration roundtrip via TEXT") {
        transactor.connect {
          val dur = java.time.Duration.ofHours(2).plusMinutes(30)
          SqlOps.update(Frag.const("CREATE TABLE rt_dur (v TEXT NOT NULL)"))
          SqlOps.update(sql"INSERT INTO rt_dur (v) VALUES (${DbValue.DbDuration(dur)})")
          val ps = summon[DbCon].connection.prepareStatement("SELECT v FROM rt_dur")
          try {
            val rs = ps.executeQuery()
            try {
              rs.next()
              val v = rs.reader.getDuration(1)
              assertTrue(v == dur)
            } finally rs.close()
          } finally ps.close()
        }
      },
      test("Instant writeParams via setTimestamp") {
        transactor.connect {
          val instant = java.time.Instant.parse("2024-06-15T10:30:00Z")
          SqlOps.update(Frag.const("CREATE TABLE rt_inst (v TEXT NOT NULL)"))
          SqlOps.update(sql"INSERT INTO rt_inst (v) VALUES (${DbValue.DbInstant(instant)})")
          val ps = summon[DbCon].connection.prepareStatement("SELECT v FROM rt_inst")
          try {
            val rs = ps.executeQuery()
            try {
              rs.next()
              val v = rs.reader.getString(1)
              assertTrue(v != null && v.nonEmpty)
            } finally rs.close()
          } finally ps.close()
        }
      },
      test("Bytes roundtrip") {
        transactor.connect {
          val bytes = Array[Byte](10, 20, 30, 40, 50)
          SqlOps.update(Frag.const("CREATE TABLE rt_bytes (v BLOB NOT NULL)"))
          SqlOps.update(sql"INSERT INTO rt_bytes (v) VALUES (${DbValue.DbBytes(bytes)})")
          val ps = summon[DbCon].connection.prepareStatement("SELECT v FROM rt_bytes")
          try {
            val rs = ps.executeQuery()
            try {
              rs.next()
              val read = rs.reader.getBytes(1)
              assertTrue(read.sameElements(bytes))
            } finally rs.close()
          } finally ps.close()
        }
      }
    ),
    suite("Frag extension methods")(
      test("frag.query delegates to SqlOps.query") {
        transactor.connect {
          SqlOps.update(Frag.const("CREATE TABLE ext_query (id INTEGER NOT NULL, name TEXT NOT NULL)"))
          SqlOps.update(sql"INSERT INTO ext_query VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("a")})")
          SqlOps.update(sql"INSERT INTO ext_query VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("b")})")
          val viaOps = SqlOps.query[Int](sql"SELECT id FROM ext_query ORDER BY id")
          val viaExt = sql"SELECT id FROM ext_query ORDER BY id".query[Int]
          assertTrue(viaOps == viaExt, viaExt == List(1, 2))
        }
      },
      test("frag.queryOne returns Some for match, None for no match") {
        transactor.connect {
          SqlOps.update(Frag.const("CREATE TABLE ext_qone (id INTEGER NOT NULL)"))
          SqlOps.update(sql"INSERT INTO ext_qone VALUES (${DbValue.DbInt(42)})")
          val found    = sql"SELECT id FROM ext_qone WHERE id = ${DbValue.DbInt(42)}".queryOne[Int]
          val notFound = sql"SELECT id FROM ext_qone WHERE id = ${DbValue.DbInt(999)}".queryOne[Int]
          assertTrue(found == Some(42), notFound.isEmpty)
        }
      },
      test("frag.queryLimit returns at most N rows") {
        transactor.connect {
          SqlOps.update(Frag.const("CREATE TABLE ext_qlimit (id INTEGER NOT NULL)"))
          SqlOps.update(sql"INSERT INTO ext_qlimit VALUES (${DbValue.DbInt(1)})")
          SqlOps.update(sql"INSERT INTO ext_qlimit VALUES (${DbValue.DbInt(2)})")
          SqlOps.update(sql"INSERT INTO ext_qlimit VALUES (${DbValue.DbInt(3)})")
          SqlOps.update(sql"INSERT INTO ext_qlimit VALUES (${DbValue.DbInt(4)})")
          SqlOps.update(sql"INSERT INTO ext_qlimit VALUES (${DbValue.DbInt(5)})")
          val limited = sql"SELECT id FROM ext_qlimit ORDER BY id".queryLimit[Int](2)
          val all     = sql"SELECT id FROM ext_qlimit ORDER BY id".query[Int]
          assertTrue(limited == List(1, 2), all.length == 5)
        }
      },
      test("frag.queryLimit with limit larger than result set returns all") {
        transactor.connect {
          SqlOps.update(Frag.const("CREATE TABLE ext_qlimit2 (id INTEGER NOT NULL)"))
          SqlOps.update(sql"INSERT INTO ext_qlimit2 VALUES (${DbValue.DbInt(1)})")
          SqlOps.update(sql"INSERT INTO ext_qlimit2 VALUES (${DbValue.DbInt(2)})")
          val result = sql"SELECT id FROM ext_qlimit2 ORDER BY id".queryLimit[Int](100)
          assertTrue(result == List(1, 2))
        }
      },
      test("frag.update returns affected row count") {
        transactor.connect {
          SqlOps.update(Frag.const("CREATE TABLE ext_upd (id INTEGER NOT NULL)"))
          sql"INSERT INTO ext_upd VALUES (${DbValue.DbInt(1)})".update
          sql"INSERT INTO ext_upd VALUES (${DbValue.DbInt(2)})".update
          val count = Frag.const("DELETE FROM ext_upd").update
          assertTrue(count == 2)
        }
      },
      test("SqlOps.queryLimit stops early") {
        transactor.connect {
          SqlOps.update(Frag.const("CREATE TABLE qlimit_ops (id INTEGER NOT NULL)"))
          SqlOps.update(sql"INSERT INTO qlimit_ops VALUES (${DbValue.DbInt(1)})")
          SqlOps.update(sql"INSERT INTO qlimit_ops VALUES (${DbValue.DbInt(2)})")
          SqlOps.update(sql"INSERT INTO qlimit_ops VALUES (${DbValue.DbInt(3)})")
          val result = SqlOps.queryLimit[Int](sql"SELECT id FROM qlimit_ops ORDER BY id", 2)
          assertTrue(result == List(1, 2))
        }
      },
      test("SqlOps.queryLimit with zero returns empty") {
        transactor.connect {
          SqlOps.update(Frag.const("CREATE TABLE qlimit_zero (id INTEGER NOT NULL)"))
          SqlOps.update(sql"INSERT INTO qlimit_zero VALUES (${DbValue.DbInt(1)})")
          val result = SqlOps.queryLimit[Int](sql"SELECT id FROM qlimit_zero", 0)
          assertTrue(result.isEmpty)
        }
      }
    )
  )
}
