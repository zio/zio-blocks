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

import java.time.Instant

import zio.*
import zio.stream.ZStream

import zio.blocks.schema.{DynamicValue, Schema}
import zio.blocks.schema.binding.Binding
import zio.blocks.schema.migration.Migration
import zio.blocks.sql.{DbCon, DbValue, Transactor}
import zio.blocks.typeid.AnnotationArg

trait EventStore[E] {
  def append(entityId: String, event: E): Task[Long]
  def readFrom(afterSeq: Long, tags: Set[String] = Set.empty): ZStream[Any, Throwable, EventEnvelope[E]]
  def readAll(tags: Set[String] = Set.empty): ZStream[Any, Throwable, EventEnvelope[E]]
  def subscribe: Hub[EventEnvelope[E]]
}

final class SQLiteEventStore[E] private (
  transactor: Transactor,
  schema: Schema[E],
  val subscribe: Hub[EventEnvelope[E]],
  val tagInfo: TagInfo
) extends EventStore[E] {

  private val jsonCodec = schema.jsonCodec

  private lazy val variantCases: Map[String, zio.blocks.schema.Term[Binding, E, ? <: E]] =
    schema.reflect.asVariant match {
      case Some(variant) => variant.cases.map(term => term.name -> term).toMap
      case None          => Map.empty
    }

  private def extractNumericTag(typeId: zio.blocks.typeid.TypeId[?]): Option[Int] = {
    def search(args: List[AnnotationArg]): Option[Int] =
      args.iterator.flatMap {
        case AnnotationArg.Const(v: Int)               => Some(v)
        case AnnotationArg.Const(v: java.lang.Integer) => Some(v.intValue())
        case AnnotationArg.Const(v: Long)              => Some(v.toInt)
        case AnnotationArg.Const(v: java.lang.Long)    => Some(v.intValue())
        case AnnotationArg.Named(_, inner)             => search(List(inner))
        case AnnotationArg.ArrayArg(values)            => search(values)
        case AnnotationArg.Nested(ann)                 => search(ann.args)
        case _                                         => None
      }.nextOption()

    typeId.annotations.collectFirst {
      case ann if ann.name == "eventTag" => search(ann.args)
    }.flatten
  }

  private def deriveTag(event: E): String =
    try {
      val dv = schema.toDynamicValue(event)
      dv match {
        case v: DynamicValue.Variant =>
          val caseName = v.caseNameValue
          val numeric  = variantCases.get(caseName).flatMap(term => extractNumericTag(term.value.typeId))
          numeric.map(_.toString).getOrElse(caseName)
        case _ =>
          extractNumericTag(schema.reflect.typeId).map(_.toString).getOrElse {
            val tidName = schema.reflect.typeId.name
            if (tidName.nonEmpty && tidName != "Object" && tidName != "Any") tidName
            else event.getClass.getSimpleName.stripSuffix("$")
          }
      }
    } catch {
      case _: Throwable => event.getClass.getSimpleName.stripSuffix("$")
    }

  def append(entityId: String, event: E): Task[Long] =
    for {
      tagAndPayload <- ZIO.attempt {
                         val tag     = deriveTag(event)
                         val payload = jsonCodec.encode(event)
                         (tag, payload)
                       }
      (tag, payload) = tagAndPayload
      tsMillis       = Instant.now().toEpochMilli
      seq           <- ZIO.attemptBlocking {
               transactor.connect {
                 val con  = summon[DbCon]
                 val conn = con.connection
                 val ps   = conn.prepareStatementReturningKeys(
                   "INSERT INTO events (tag, payload, ts, entityId) VALUES (?, ?, ?, ?)"
                 )
                 try {
                   val pw = ps.paramWriter
                   pw.setString(1, tag)
                   pw.setBytes(2, payload)
                   pw.setLong(3, tsMillis)
                   pw.setString(4, entityId)
                   val rs = ps.executeUpdateReturningKeys()
                   try {
                     if (rs.next()) rs.reader.getLong(1)
                     else {
                       // Fallback: query last_insert_rowid()
                       val ps2 = conn.prepareStatement("SELECT last_insert_rowid()")
                       try {
                         val rs2 = ps2.executeQuery()
                         try {
                           if (rs2.next()) rs2.reader.getLong(1)
                           else throw new RuntimeException("Failed to obtain generated seq")
                         } finally rs2.close()
                       } finally ps2.close()
                     }
                   } finally rs.close()
                 } finally ps.close()
               }
             }
      envelope = EventEnvelope(seq, tag, event, Instant.ofEpochMilli(tsMillis), entityId)
      _       <- subscribe.publish(envelope).unit
    } yield seq

  def readFrom(afterSeq: Long, tags: Set[String] = Set.empty): ZStream[Any, Throwable, EventEnvelope[E]] =
    ZStream.unwrap {
      ZIO.attemptBlocking(fetch(afterSeq, tags)).map(ZStream.fromIterable(_))
    }

  def readAll(tags: Set[String] = Set.empty): ZStream[Any, Throwable, EventEnvelope[E]] =
    readFrom(0L, tags)

  private[projection] def distinctTagsInDb(): Set[String] =
    transactor.connect {
      val con  = summon[DbCon]
      val conn = con.connection
      val ps   = conn.prepareStatement("SELECT DISTINCT tag FROM events")
      try {
        val rs = ps.executeQuery()
        try {
          val builder = Set.newBuilder[String]
          while (rs.next()) builder += rs.reader.getString(1)
          builder.result()
        } finally rs.close()
      } finally ps.close()
    }

  private[projection] def validateTags(): Task[Unit] =
    ZIO.attemptBlocking(distinctTagsInDb()).flatMap { dbTags =>
      val unknown = tagInfo.unknownTags(dbTags)
      if (unknown.nonEmpty)
        ZIO.logWarning(
          s"Unknown event tags detected: ${unknown.mkString(", ")} not in known tags ${tagInfo.allTags.mkString(", ")}"
        )
      else ZIO.unit
    }

  private def expandedTags(requested: Set[String]): Set[String] =
    tagInfo.expandRequested(requested)

  private def decodePayload(tag: String, payload: Array[Byte], seq: Long): E =
    if (tagInfo.isOldTag(tag)) {
      val newTag = tagInfo.currentTagFor(tag).getOrElse(tag)
      // First try string replacement of case discriminator, then decode via current codec
      val jsonStr = new String(payload, java.nio.charset.StandardCharsets.UTF_8)
      // Replace JSON key "\"oldTag\"" with "\"newTag\"" - covers variant encoding {"Old": ...}
      val migratedStr     = jsonStr.replace("\"" + tag + "\"", "\"" + newTag + "\"")
      val migratedPayload = migratedStr.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      jsonCodec.decode(migratedPayload) match {
        case Right(ev)      => ev
        case Left(firstErr) =>
          // Fallback: try DynamicMigration on DynamicValue decoded via dynamic json codec
          // Decode generic JSON to DynamicValue Record then try to convert to Variant-like DynamicValue
          // As fallback, attempt to apply DynamicMigration to a synthetic Variant DynamicValue
          // constructed from JSON parsing: we decode via dynamicValueCodec then try to transform
          val attemptDynamic = try {
            import zio.blocks.schema.json.JsonCodec
            val dvCodec = JsonCodec.dynamicValueCodec
            dvCodec.decode(migratedPayload) match {
              case Right(dvRaw) =>
                // dvRaw is likely a Record {"NewTag": inner}; convert to Variant if needed
                val dvVariant = dvRaw match {
                  case DynamicValue.Record(fields) if fields.size == 1 && fields.head._1 == newTag =>
                    DynamicValue.Variant(newTag, fields.head._2)
                  case other => other
                }
                tagInfo.migrateValue(dvVariant).flatMap(schema.fromDynamicValue) match {
                  case Right(ev2) => Right(ev2)
                  case Left(e2)   => Left(e2)
                }
              case Left(err) => Left(err)
            }
          } catch { case _: Throwable => Left(firstErr) }
          attemptDynamic match {
            case Right(ev) => ev
            case Left(_)   =>
              // Last resort: try applying migration directly to DynamicValue built from tag + payload
              // Try to create Variant from payload's inner JSON: decode inner as raw then wrap
              // fallback to original error
              throw new RuntimeException(
                s"Failed to decode migrated event payload at seq $seq with tag $tag -> $newTag: $firstErr"
              )
          }
      }
    } else {
      jsonCodec.decode(payload) match {
        case Right(ev) => ev
        case Left(err) => throw new RuntimeException(s"Failed to decode event payload at seq $seq: $err")
      }
    }

  private def fetch(afterSeq: Long, tags: Set[String]): List[EventEnvelope[E]] =
    transactor.connect {
      val con           = summon[DbCon]
      val conn          = con.connection
      val effectiveTags = expandedTags(tags)
      val (sql, params) = buildSelect(afterSeq, effectiveTags)
      val ps            = conn.prepareStatement(sql)
      try {
        val pw  = ps.paramWriter
        var idx = 1
        params.foreach {
          case DbValue.DbLong(v)   => pw.setLong(idx, v); idx += 1
          case DbValue.DbString(v) => pw.setString(idx, v); idx += 1
          case DbValue.DbInt(v)    => pw.setInt(idx, v); idx += 1
          case other               => pw.setString(idx, other.toString); idx += 1
        }
        val rs = ps.executeQuery()
        try {
          val reader  = rs.reader
          val builder = List.newBuilder[EventEnvelope[E]]
          while (rs.next()) {
            val seq           = reader.getLong(1)
            val tag           = reader.getString(2)
            val payload       = reader.getBytes(3)
            val tsMillis      = reader.getLong(4)
            val entityId      = reader.getString(5)
            val event         = decodePayload(tag, payload, seq)
            val normalizedTag = tagInfo.normalize(tag)
            val ts            = Instant.ofEpochMilli(tsMillis)
            builder += EventEnvelope(seq, normalizedTag, event, ts, entityId)
          }
          builder.result()
        } finally rs.close()
      } finally ps.close()
    }

  private def buildSelect(afterSeq: Long, tags: Set[String]): (String, List[DbValue]) = {
    val sb     = new StringBuilder("SELECT seq, tag, payload, ts, entityId FROM events WHERE seq > ?")
    val params = scala.collection.mutable.ListBuffer[DbValue](DbValue.DbLong(afterSeq))
    if (tags.nonEmpty) {
      val tagList = tags.toList
      sb.append(" AND tag IN (")
      sb.append(List.fill(tagList.size)("?").mkString(", "))
      sb.append(")")
      tagList.foreach(t => params += DbValue.DbString(t))
    }
    sb.append(" ORDER BY seq ASC")
    (sb.toString(), params.toList)
  }

  private[projection] def explainQueryPlan(afterSeq: Long, tags: Set[String]): List[String] =
    transactor.connect {
      val con           = summon[DbCon]
      val conn          = con.connection
      val (sql, params) = buildSelect(afterSeq, tags)
      val explainSql    = s"EXPLAIN QUERY PLAN $sql"
      val ps            = conn.prepareStatement(explainSql)
      try {
        val pw  = ps.paramWriter
        var idx = 1
        params.foreach {
          case DbValue.DbLong(v)   => pw.setLong(idx, v); idx += 1
          case DbValue.DbString(v) => pw.setString(idx, v); idx += 1
          case DbValue.DbInt(v)    => pw.setInt(idx, v); idx += 1
          case other               => pw.setString(idx, other.toString); idx += 1
        }
        val rs = ps.executeQuery()
        try {
          val reader  = rs.reader
          val builder = List.newBuilder[String]
          while (rs.next()) {
            // EXPLAIN QUERY PLAN returns 4 columns: selectid, order, from, detail ; detail is column 4
            builder += reader.getString(4)
          }
          builder.result()
        } finally rs.close()
      } finally ps.close()
    }
}

object SQLiteEventStore {

  def make[E: Schema](transactor: Transactor): Task[SQLiteEventStore[E]] =
    makeWithResolver[E](transactor, TagResolver.resolve[E])

  def make[E: Schema](transactor: Transactor, migration: Migration[E, E]): Task[SQLiteEventStore[E]] =
    makeWithResolver[E](transactor, TagResolver.resolve[E](migration))

  def makeWithResolver[E: Schema](transactor: Transactor, tagInfo: TagInfo): Task[SQLiteEventStore[E]] =
    for {
      hub   <- Hub.unbounded[EventEnvelope[E]]
      store <- makeWithHub(transactor, hub, tagInfo)
    } yield store

  def makeWithHub[E: Schema](transactor: Transactor, hub: Hub[EventEnvelope[E]]): Task[SQLiteEventStore[E]] =
    makeWithHub(transactor, hub, TagResolver.resolve[E])

  def makeWithHub[E: Schema](
    transactor: Transactor,
    hub: Hub[EventEnvelope[E]],
    migration: Migration[E, E]
  ): Task[SQLiteEventStore[E]] =
    makeWithHub(transactor, hub, TagResolver.resolve[E](migration))

  def makeWithHub[E: Schema](
    transactor: Transactor,
    hub: Hub[EventEnvelope[E]],
    tagInfo: TagInfo
  ): Task[SQLiteEventStore[E]] =
    for {
      _ <- ZIO.attemptBlocking {
             transactor.connect {
               val con  = summon[DbCon]
               val conn = con.connection
               // Create table
               val createTable =
                 "CREATE TABLE IF NOT EXISTS events (seq INTEGER PRIMARY KEY AUTOINCREMENT, tag TEXT NOT NULL, payload BLOB NOT NULL, ts INTEGER NOT NULL, entityId TEXT NOT NULL)"
               val ps1 = conn.prepareStatement(createTable)
               try ps1.executeUpdate()
               finally ps1.close()
               // Create index
               val createIndex = "CREATE INDEX IF NOT EXISTS idx_events_tag_seq ON events(tag, seq)"
               val ps2         = conn.prepareStatement(createIndex)
               try ps2.executeUpdate()
               finally ps2.close()
             }
           }
      store = new SQLiteEventStore[E](transactor, summon[Schema[E]], hub, tagInfo)
      _    <- store.validateTags().catchAll(_ => ZIO.unit)
    } yield store

  /** Blocking variant for tests that need synchronous construction */
  def makeBlocking[E: Schema](transactor: Transactor, hub: Hub[EventEnvelope[E]]): SQLiteEventStore[E] =
    makeBlockingWithResolver[E](transactor, hub, TagResolver.resolve[E])

  def makeBlockingWithResolver[E: Schema](
    transactor: Transactor,
    hub: Hub[EventEnvelope[E]],
    tagInfo: TagInfo
  ): SQLiteEventStore[E] = {
    transactor.connect {
      val con  = summon[DbCon]
      val conn = con.connection
      val ps1  = conn.prepareStatement(
        "CREATE TABLE IF NOT EXISTS events (seq INTEGER PRIMARY KEY AUTOINCREMENT, tag TEXT NOT NULL, payload BLOB NOT NULL, ts INTEGER NOT NULL, entityId TEXT NOT NULL)"
      )
      try ps1.executeUpdate()
      finally ps1.close()
      val ps2 = conn.prepareStatement("CREATE INDEX IF NOT EXISTS idx_events_tag_seq ON events(tag, seq)")
      try ps2.executeUpdate()
      finally ps2.close()
    }
    new SQLiteEventStore[E](transactor, summon[Schema[E]], hub, tagInfo)
  }

  def makeBlocking[E: Schema](
    transactor: Transactor,
    hub: Hub[EventEnvelope[E]],
    migration: Migration[E, E]
  ): SQLiteEventStore[E] =
    makeBlockingWithResolver[E](transactor, hub, TagResolver.resolve[E](migration))
}
