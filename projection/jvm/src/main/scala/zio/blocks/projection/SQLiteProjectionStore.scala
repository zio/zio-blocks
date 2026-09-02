/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.projection

import zio.*
import zio.blocks.chunk.Chunk
import zio.blocks.schema.Schema
import zio.blocks.sql.*

class SQLiteProjectionStore[A: Schema: EntityPath] private (
  path: String,
  cache: TransactorCache,
  private val initFlag: Ref[Boolean],
  private val initSem: Semaphore
) extends ProjectionStore[A] {

  private val entityPath = summon[EntityPath[A]]
  private val table      = Table.derived[A]
  private val tblName    = table.name
  private val allCols    = table.columns.mkString(", ")
  private val codec      = table.codec
  private val idColumn   = SqlNameMapper.SnakeCase(entityPath.entityIdField)
  private val metaTable  = "_projection_meta"
  private val metaKey    = tblName

  private val validatedIdColumn = idColumn
  private val validatedTblName  = tblName

  private def quoted(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

  private def validateField(field: String): Unit =
    if (!table.columns.contains(field))
      throw new IllegalArgumentException(s"unknown field '$field' not in ${table.columns.mkString(",")}")
    else if (!field.matches("[a-zA-Z_][a-zA-Z0-9_]*"))
      throw new IllegalArgumentException(s"invalid field name '$field'")

  private def validateColumnName(name: String): Unit =
    require(name.matches("[a-zA-Z_][a-zA-Z0-9_]*"), s"invalid column name $name")

  private def ensureTables: Task[Unit] =
    initFlag.get.flatMap {
      case true  => ZIO.unit
      case false =>
        initSem.withPermit {
          initFlag.get.flatMap {
            case true  => ZIO.unit
            case false =>
              for {
                tx <- cache.get(path)
                _  <- ZIO.attemptBlocking {
                       tx.connect {
                         val con        = summon[DbCon]
                         val conn       = con.connection
                         val createFrag = table.createTable(SqlDialect.SQLite)
                         val createSql  = createFrag.sql(SqlDialect.SQLite)
                         val ps1        = conn.prepareStatement(createSql)
                         try ps1.executeUpdate()
                         finally ps1.close()
                         val metaSql =
                           "CREATE TABLE IF NOT EXISTS _projection_meta (name TEXT PRIMARY KEY, last_seq INTEGER NOT NULL DEFAULT 0, schema_hash TEXT, updated_at INTEGER NOT NULL)"
                         val ps2 = conn.prepareStatement(metaSql)
                         try ps2.executeUpdate()
                         finally ps2.close()
                         val idxSql =
                           s"CREATE UNIQUE INDEX IF NOT EXISTS idx_${validatedTblName}_${validatedIdColumn} ON ${quoted(validatedTblName)} (${quoted(validatedIdColumn)})"
                         val ps3 = conn.prepareStatement(idxSql)
                         try ps3.executeUpdate()
                         finally ps3.close()
                       }
                     }
                _ <- initFlag.set(true)
              } yield ()
          }
        }
    }

  private def writeDbValue(writer: DbParamWriter, idx: Int, v: DbValue): Unit =
    v match {
      case DbValue.DbNull             => writer.setNull(idx, 0)
      case DbValue.DbInt(x)           => writer.setInt(idx, x)
      case DbValue.DbLong(x)          => writer.setLong(idx, x)
      case DbValue.DbDouble(x)        => writer.setDouble(idx, x)
      case DbValue.DbFloat(x)         => writer.setFloat(idx, x)
      case DbValue.DbBoolean(x)       => writer.setBoolean(idx, x)
      case DbValue.DbString(x)        => writer.setString(idx, x)
      case DbValue.DbBigDecimal(x)    => writer.setBigDecimal(idx, x.bigDecimal)
      case DbValue.DbBytes(x)         => writer.setBytes(idx, x)
      case DbValue.DbShort(x)         => writer.setShort(idx, x)
      case DbValue.DbByte(x)          => writer.setByte(idx, x)
      case DbValue.DbChar(x)          => writer.setString(idx, x.toString)
      case DbValue.DbLocalDate(x)     => writer.setLocalDate(idx, x)
      case DbValue.DbLocalDateTime(x) => writer.setLocalDateTime(idx, x)
      case DbValue.DbLocalTime(x)     => writer.setLocalTime(idx, x)
      case DbValue.DbInstant(x)       => writer.setInstant(idx, x)
      case DbValue.DbDuration(x)      => writer.setDuration(idx, x)
      case DbValue.DbUUID(x)          => writer.setUUID(idx, x)
      case DbValue.DbArray(t, elems)  => writer.setArray(idx, t, elems)
    }

  private def writeAny(writer: DbParamWriter, idx: Int, v: Any): Unit =
    v match {
      case null    => writer.setNull(idx, 0)
      case None    => writer.setNull(idx, 0)
      case Some(x) => writeAny(writer, idx, x)

      case s: String                    => writer.setString(idx, s)
      case i: Int                       => writer.setInt(idx, i)
      case l: Long                      => writer.setLong(idx, l)
      case d: Double                    => writer.setDouble(idx, d)
      case f: Float                     => writer.setFloat(idx, f)
      case b: Boolean                   => writer.setBoolean(idx, b)
      case bd: BigDecimal               => writer.setBigDecimal(idx, bd.bigDecimal)
      case jbd: java.math.BigDecimal    => writer.setBigDecimal(idx, jbd)
      case arr: Array[Byte]             => writer.setBytes(idx, arr)
      case sh: Short                    => writer.setShort(idx, sh)
      case by: Byte                     => writer.setByte(idx, by)
      case c: Char                      => writer.setString(idx, c.toString)
      case inst: java.time.Instant      => writer.setInstant(idx, inst)
      case ld: java.time.LocalDate      => writer.setLocalDate(idx, ld)
      case ldt: java.time.LocalDateTime => writer.setLocalDateTime(idx, ldt)
      case lt: java.time.LocalTime      => writer.setLocalTime(idx, lt)
      case dur: java.time.Duration      => writer.setDuration(idx, dur)
      case uuid: java.util.UUID         => writer.setUUID(idx, uuid)
      case other                        => writer.setString(idx, other.toString)
    }

  def insert(a: A): Task[Unit] =
    ensureTables *>
      cache.get(path).flatMap { tx =>
        ZIO.attemptBlocking {
          tx.connect {
            val con          = summon[DbCon]
            val conn         = con.connection
            val values       = codec.toDbValues(a)
            val placeholders = List.fill(values.size)("?").mkString(", ")
            val sql          = s"INSERT INTO $validatedTblName ($allCols) VALUES ($placeholders)"
            val ps           = conn.prepareStatement(sql)
            try {
              val pw = ps.paramWriter
              var i  = 0
              while (i < values.size) {
                writeDbValue(pw, i + 1, values(i))
                i += 1
              }
              ps.executeUpdate()
              ()
            } finally ps.close()
          }
        }
      }

  def upsert(a: A): Task[Unit] =
    ensureTables *>
      cache.get(path).flatMap { tx =>
        ZIO.attemptBlocking {
          tx.connect {
            val con          = summon[DbCon]
            val conn         = con.connection
            val values       = codec.toDbValues(a)
            val placeholders = List.fill(values.size)("?").mkString(", ")
            val nonIdCols    = table.columns.filter(_ != validatedIdColumn)
            val excludedSets = nonIdCols.map(col => s"${quoted(col)}=excluded.${quoted(col)}").mkString(", ")
            val sql          =
              if (excludedSets.nonEmpty)
                s"INSERT INTO ${quoted(validatedTblName)} ($allCols) VALUES ($placeholders) ON CONFLICT(${quoted(validatedIdColumn)}) DO UPDATE SET $excludedSets"
              else
                s"INSERT OR REPLACE INTO ${quoted(validatedTblName)} ($allCols) VALUES ($placeholders)"
            try {
              val ps = conn.prepareStatement(sql)
              try {
                val pw = ps.paramWriter
                var i  = 0
                while (i < values.size) {
                  writeDbValue(pw, i + 1, values(i))
                  i += 1
                }
                ps.executeUpdate()
                ()
              } finally ps.close()
            } catch {
              case e: java.sql.SQLException =>
                // Log original and fallback only for SQLException (not all Throwables)
                java.lang.System.err
                  .println(s"upsert ON CONFLICT failed: ${e.getMessage}, falling back to INSERT OR REPLACE")
                val fallback = s"INSERT OR REPLACE INTO ${quoted(validatedTblName)} ($allCols) VALUES ($placeholders)"
                val ps2      = conn.prepareStatement(fallback)
                try {
                  val pw = ps2.paramWriter
                  var i  = 0
                  while (i < values.size) {
                    writeDbValue(pw, i + 1, values(i))
                    i += 1
                  }
                  ps2.executeUpdate()
                  ()
                } finally ps2.close()
            }
          }
        }
      }

  def updateFields(entityId: String, updates: Chunk[FieldUpdate]): Task[Unit] =
    if (updates.isEmpty) ZIO.unit
    else
      ensureTables *>
        cache.get(path).flatMap { tx =>
          // Validate fields before transaction (fail fast on bad input)
          updates.foreach {
            case FieldUpdate.Set(f, _)       => validateField(f)
            case FieldUpdate.Increment(f, _) => validateField(f)
            case FieldUpdate.Decrement(f, _) => validateField(f)
            case FieldUpdate.Max(f, _)       => validateField(f)
            case FieldUpdate.Min(f, _)       => validateField(f)
          }
          ZIO.attemptBlocking {
            tx.transact {
              val con  = summon[DbTx]
              val conn = con.connection
              // Ensure row exists before atomic updates: INSERT OR IGNORE with defaults for all columns
              val placeholderAll = List.fill(table.columns.size)("?").mkString(", ")
              val ensureSql      = s"INSERT OR IGNORE INTO ${quoted(validatedTblName)} ($allCols) VALUES ($placeholderAll)"
              val psEnsure       = conn.prepareStatement(ensureSql)
              try {
                val pw = psEnsure.paramWriter
                var i  = 0
                while (i < table.columnsMeta.size) {
                  val meta = table.columnsMeta(i)
                  if (meta.name == validatedIdColumn) pw.setString(i + 1, entityId)
                  else writeDbValue(pw, i + 1, meta.dbValue)
                  i += 1
                }
                psEnsure.executeUpdate()
                ()
              } finally psEnsure.close()
              updates.foreach {
                case FieldUpdate.Set(field, value) =>
                  val col = quoted(field)
                  val sql = s"UPDATE ${quoted(validatedTblName)} SET $col = ? WHERE ${quoted(validatedIdColumn)} = ?"
                  val ps  = conn.prepareStatement(sql)
                  try {
                    val pw = ps.paramWriter
                    writeAny(pw, 1, value)
                    pw.setString(2, entityId)
                    ps.executeUpdate()
                    ()
                  } finally ps.close()
                case FieldUpdate.Increment(field, by) =>
                  val col = quoted(field)
                  val sql =
                    s"UPDATE ${quoted(validatedTblName)} SET $col = COALESCE($col, 0) + ? WHERE ${quoted(validatedIdColumn)} = ?"
                  val ps = conn.prepareStatement(sql)
                  try {
                    val pw = ps.paramWriter
                    pw.setLong(1, by)
                    pw.setString(2, entityId)
                    ps.executeUpdate()
                    ()
                  } finally ps.close()
                case FieldUpdate.Decrement(field, by) =>
                  val col = quoted(field)
                  val sql =
                    s"UPDATE ${quoted(validatedTblName)} SET $col = COALESCE($col, 0) - ? WHERE ${quoted(validatedIdColumn)} = ?"
                  val ps = conn.prepareStatement(sql)
                  try {
                    val pw = ps.paramWriter
                    pw.setLong(1, by)
                    pw.setString(2, entityId)
                    ps.executeUpdate()
                    ()
                  } finally ps.close()
                case FieldUpdate.Max(field, value) =>
                  val col = quoted(field)
                  val sql =
                    s"UPDATE ${quoted(validatedTblName)} SET $col = MAX(COALESCE($col, ?), ?) WHERE ${quoted(validatedIdColumn)} = ?"
                  val ps = conn.prepareStatement(sql)
                  try {
                    val pw = ps.paramWriter
                    pw.setLong(1, value)
                    pw.setLong(2, value)
                    pw.setString(3, entityId)
                    ps.executeUpdate()
                    ()
                  } finally ps.close()
                case FieldUpdate.Min(field, value) =>
                  val col = quoted(field)
                  val sql =
                    s"UPDATE ${quoted(validatedTblName)} SET $col = MIN(COALESCE($col, ?), ?) WHERE ${quoted(validatedIdColumn)} = ?"
                  val ps = conn.prepareStatement(sql)
                  try {
                    val pw = ps.paramWriter
                    pw.setLong(1, value)
                    pw.setLong(2, value)
                    pw.setString(3, entityId)
                    ps.executeUpdate()
                    ()
                  } finally ps.close()
              }
            }
          }
        }

  def delete(entityId: String): Task[Unit] =
    ensureTables *>
      cache.get(path).flatMap { tx =>
        ZIO.attemptBlocking {
          tx.connect {
            val con  = summon[DbCon]
            val conn = con.connection
            val sql  = s"DELETE FROM $validatedTblName WHERE $validatedIdColumn = ?"
            val ps   = conn.prepareStatement(sql)
            try {
              ps.paramWriter.setString(1, entityId)
              ps.executeUpdate()
              ()
            } finally ps.close()
          }
        }
      }

  def truncate: Task[Unit] =
    ensureTables *>
      cache.get(path).flatMap { tx =>
        ZIO.attemptBlocking {
          tx.connect {
            val con  = summon[DbCon]
            val conn = con.connection
            val sql  = s"DELETE FROM $validatedTblName"
            val ps   = conn.prepareStatement(sql)
            try ps.executeUpdate()
            finally ps.close()
            ()
          }
        }
      }

  def findById(entityId: String): Task[Option[A]] =
    ensureTables *>
      cache.get(path).flatMap { tx =>
        ZIO.attemptBlocking {
          tx.connect {
            val con  = summon[DbCon]
            val conn = con.connection
            val sql  = s"SELECT $allCols FROM $validatedTblName WHERE $validatedIdColumn = ?"
            val ps   = conn.prepareStatement(sql)
            try {
              ps.paramWriter.setString(1, entityId)
              val rs = ps.executeQuery()
              try {
                if (rs.next()) Some(codec.readValue(rs.reader, 1))
                else None
              } finally rs.close()
            } finally ps.close()
          }
        }
      }

  def getLastProcessedSeq: Task[Long] =
    ensureTables *>
      cache.get(path).flatMap { tx =>
        ZIO.attemptBlocking {
          tx.connect {
            val con  = summon[DbCon]
            val conn = con.connection
            val sql  = s"SELECT last_seq FROM $metaTable WHERE name = ?"
            val ps   = conn.prepareStatement(sql)
            try {
              ps.paramWriter.setString(1, metaKey)
              val rs = ps.executeQuery()
              try {
                if (rs.next()) rs.reader.getLong(1)
                else 0L
              } finally rs.close()
            } finally ps.close()
          }
        }
      }

  def updateLastProcessedSeq(seq: Long): Task[Unit] =
    ensureTables *>
      cache.get(path).flatMap { tx =>
        ZIO.attemptBlocking {
          tx.connect {
            val con  = summon[DbCon]
            val conn = con.connection
            val now  = java.lang.System.currentTimeMillis()
            // ON CONFLICT atomic upsert (no UPDATE-then-INSERT race)
            val sql =
              s"INSERT INTO $metaTable (name, last_seq, updated_at) VALUES (?, ?, ?) ON CONFLICT(name) DO UPDATE SET last_seq=excluded.last_seq, updated_at=excluded.updated_at"
            val ps = conn.prepareStatement(sql)
            try {
              val pw = ps.paramWriter
              pw.setString(1, metaKey)
              pw.setLong(2, seq)
              pw.setLong(3, now)
              ps.executeUpdate()
              ()
            } finally ps.close()
          }
        }
      }

  def getSchemaHash: Task[Option[String]] =
    ensureTables *>
      cache.get(path).flatMap { tx =>
        ZIO.attemptBlocking {
          tx.connect {
            val con  = summon[DbCon]
            val conn = con.connection
            val sql  = s"SELECT schema_hash FROM $metaTable WHERE name = ?"
            val ps   = conn.prepareStatement(sql)
            try {
              ps.paramWriter.setString(1, metaKey)
              val rs = ps.executeQuery()
              try {
                if (rs.next()) {
                  val v = rs.reader.getString(1)
                  if (rs.reader.wasNull || v == null) None else Some(v)
                } else None
              } finally rs.close()
            } finally ps.close()
          }
        }
      }

  def updateSchemaHash(hash: String): Task[Unit] =
    ensureTables *>
      cache.get(path).flatMap { tx =>
        ZIO.attemptBlocking {
          tx.connect {
            val con  = summon[DbCon]
            val conn = con.connection
            val now  = java.lang.System.currentTimeMillis()
            val sql  =
              s"INSERT INTO $metaTable (name, last_seq, schema_hash, updated_at) VALUES (?, 0, ?, ?) ON CONFLICT(name) DO UPDATE SET schema_hash=excluded.schema_hash, updated_at=excluded.updated_at"
            val ps = conn.prepareStatement(sql)
            try {
              val pw = ps.paramWriter
              pw.setString(1, metaKey)
              pw.setString(2, hash)
              pw.setLong(3, now)
              ps.executeUpdate()
              ()
            } finally ps.close()
          }
        }
      }

  override def recreateTable(): Task[Unit] =
    ensureTables *>
      cache.get(path).flatMap { tx =>
        ZIO.attemptBlocking {
          tx.connect {
            val con  = summon[DbCon]
            val conn = con.connection
            // Drop existing table if exists, then recreate via Table.derived DDL + index + meta
            val dropSql = s"DROP TABLE IF EXISTS ${quoted(validatedTblName)}"
            val psDrop  = conn.prepareStatement(dropSql)
            try psDrop.executeUpdate()
            finally psDrop.close()
            // Recreate
            val createFrag = table.createTable(SqlDialect.SQLite)
            val createSql  = createFrag.sql(SqlDialect.SQLite)
            val ps1        = conn.prepareStatement(createSql)
            try ps1.executeUpdate()
            finally ps1.close()
            val idxSql =
              s"CREATE UNIQUE INDEX IF NOT EXISTS idx_${validatedTblName}_${validatedIdColumn} ON ${quoted(validatedTblName)} (${quoted(validatedIdColumn)})"
            val ps3 = conn.prepareStatement(idxSql)
            try ps3.executeUpdate()
            finally ps3.close()
          }
        }
      }

  override def addColumn(columnName: String, sqlType: String): Task[Unit] =
    ZIO.attempt(validateColumnName(columnName)).flatMap { _ =>
      ensureTables *>
        cache.get(path).flatMap { tx =>
          ZIO.attemptBlocking {
            tx.connect {
              val con  = summon[DbCon]
              val conn = con.connection
              // Check if column already exists via PRAGMA table_info
              val pragma = conn.prepareStatement(s"PRAGMA table_info(${quoted(validatedTblName)})")
              try {
                val rs = pragma.executeQuery()
                try {
                  var exists = false
                  while (rs.next()) {
                    val colName = rs.reader.getString(2) // name is column 2
                    if (colName == columnName) exists = true
                  }
                  if (!exists) {
                    val addSql = s"ALTER TABLE ${quoted(validatedTblName)} ADD COLUMN ${quoted(columnName)} $sqlType"
                    val psAdd  = conn.prepareStatement(addSql)
                    try psAdd.executeUpdate()
                    finally psAdd.close()
                  }
                } finally rs.close()
              } finally pragma.close()
              ()
            }
          }
        }
    }
}

object SQLiteProjectionStore {

  def make[A: Schema: EntityPath](path: String, cache: TransactorCache): Task[SQLiteProjectionStore[A]] =
    for {
      flag <- Ref.make(false)
      sem  <- Semaphore.make(1)
    } yield new SQLiteProjectionStore[A](path, cache, flag, sem)

  /** Unsafe synchronous creation for tests that need immediate instance */
  def makeSync[A: Schema: EntityPath](path: String, cache: TransactorCache): SQLiteProjectionStore[A] = {
    import zio.Unsafe
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          for {
            flag <- Ref.make(false)
            sem  <- Semaphore.make(1)
          } yield new SQLiteProjectionStore[A](path, cache, flag, sem)
        )
        .getOrThrow()
    }
  }
}
