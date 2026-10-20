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

/**
 * An append-only log of the events of one event type `E`.
 *
 * Each appended event gets the next sequence number, which also gives the order
 * of the events. The store streams the events it holds from any position, and
 * publishes every new event to a [[Hub]], so that a reader can catch up on the
 * history and then follow the new events. A [[ProjectionEngine]] reads each
 * source of a projection from one event store.
 *
 * [[SQLiteEventStore]] keeps the events in a SQLite file.
 *
 * @tparam E
 *   the event type, usually a sealed trait with one case for each kind of event
 */
trait EventStore[E] {

  /**
   * Appends `event`, recorded under the entity `entityId`, and returns its
   * sequence number.
   *
   * The event is stored first, and its [[EventEnvelope]] is then published to
   * the hub returned by `subscribe`.
   *
   * @param entityId
   *   the entity the event belongs to, which a handler sees as
   *   `ProjectionContext#entityId`
   * @param event
   *   the event to store
   * @return
   *   the sequence number given to the event, greater than that of every event
   *   stored before it
   */
  def append(entityId: String, event: E): Task[Long]

  /**
   * Streams the stored events whose sequence number is greater than `afterSeq`,
   * in order.
   *
   * The engine uses it to catch up from the last event it processed.
   *
   * @param afterSeq
   *   the sequence number to read after; `0` reads from the first event
   * @param tags
   *   the tags of the events to keep; the empty set, the default, keeps them
   *   all
   */
  def readFrom(afterSeq: Long, tags: Set[String] = Set.empty): ZStream[Any, Throwable, EventEnvelope[E]]

  /**
   * Streams every stored event, in order. The same as `readFrom(0L, tags)`.
   *
   * A schema rebuild uses it to replay all the events.
   *
   * @param tags
   *   the tags of the events to keep; the empty set, the default, keeps them
   *   all
   */
  def readAll(tags: Set[String] = Set.empty): ZStream[Any, Throwable, EventEnvelope[E]]

  /**
   * The hub that every appended event is published to.
   *
   * Despite its name, this does not subscribe. Call `subscribe` on the returned
   * hub, inside a `Scope`, to receive the events appended from then on. The
   * events stored before that are read with `readFrom`.
   */
  def subscribe: Hub[EventEnvelope[E]]
}

/**
 * An [[EventStore]] kept in a SQLite database.
 *
 * Each event is a row of the `events` table, with the columns `seq`, `tag`,
 * `payload`, `ts` and `entityId`. `seq` is the primary key and gives the
 * sequence number. `tag` is the `@eventTag` number of the event's case, or else
 * the case's name; for an event type that is not a sealed trait, it is the
 * type's own `@eventTag` number or name. `payload` is the event encoded as
 * JSON, and `ts` is the time it was appended, in milliseconds. An index on
 * `tag` and `seq` serves filtered reads.
 *
 * The store knows the tags of `E` through a [[TagInfo]]. When a case was
 * renamed, a read that asks for the new tag also returns the events saved under
 * the old one, decodes them as the renamed case, and reports them under the new
 * tag.
 *
 * A read opens its own connection and pulls the rows through a cursor as the
 * stream is consumed. An event whose payload cannot be decoded fails the
 * stream.
 *
 * The constructor is private: create a store with the factories of the
 * companion object, which also create the table and its index if they are
 * missing.
 *
 * @param subscribe
 *   the hub that every appended event is published to
 * @param tagInfo
 *   the tags of `E`, and the old tags that were renamed to them
 */
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
    // M4: cursor streaming via ZStream.managed + ZIO.attemptBlocking(rs.next()) - uses ZStream.acquireReleaseWith for ZIO2
    ZStream
      .acquireReleaseWith(
        ZIO.attemptBlocking {
          val connField =
            transactor.getClass.getDeclaredField("connectionFactory")
          connField.setAccessible(true)
          val factory =
            connField.get(transactor).asInstanceOf[() => java.sql.Connection]
          val conn          = factory()
          val effectiveTags = expandedTags(tags)
          val (sql, params) = buildSelect(afterSeq, effectiveTags)
          // ZStream.managed(ZIO.attemptBlocking(conn.prepareStatement(sql))) >>> unfold
          val ps = conn.prepareStatement(sql)
          try {
            var idx = 1
            params.foreach {
              case DbValue.DbLong(v)   => ps.setLong(idx, v); idx += 1
              case DbValue.DbString(v) => ps.setString(idx, v); idx += 1
              case DbValue.DbInt(v)    => ps.setInt(idx, v); idx += 1
              case other               => ps.setString(idx, other.toString); idx += 1
            }
            val rs = ps.executeQuery()
            (conn, ps, rs)
          } catch {
            case e: Throwable =>
              try ps.close()
              catch { case _: Throwable => () }
              try conn.close()
              catch { case _: Throwable => () }
              throw e
          }
        }
      )(res =>
        ZIO.attemptBlocking {
          val (conn, ps, rs) = res
          try rs.close()
          catch { case _: Throwable => () }
          try ps.close()
          catch { case _: Throwable => () }
          try conn.close()
          catch { case _: Throwable => () }
        }.orDie
      )
      .flatMap { case (_, _, rs) =>
        ZStream.repeatZIOOption(
          ZIO.attemptBlocking(rs.next()).mapError(e => Some(e)).flatMap { hasNext =>
            if (!hasNext) ZIO.fail(None)
            else
              ZIO.attempt {
                val seq           = rs.getLong(1)
                val tag           = rs.getString(2)
                val payload       = rs.getBytes(3)
                val tsMillis      = rs.getLong(4)
                val entityId      = rs.getString(5)
                val event         = decodePayload(tag, payload, seq)
                val normalizedTag = tagInfo.normalize(tag)
                val ts            = Instant.ofEpochMilli(tsMillis)
                EventEnvelope(seq, normalizedTag, event, ts, entityId)
              }
                .mapError(e => Some(e))
          }
        )
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
      // Replace only discriminator key "\"oldTag\":" -> "\"newTag\":" to avoid touching values
      val migratedStr = {
        val quotedOld = java.util.regex.Pattern.quote(tag)
        jsonStr.replaceAll("\"" + quotedOld + "\"\\s*:", "\"" + newTag + "\":")
      }
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

  // $COVERAGE-OFF$
  @scala.annotation.nowarn("msg=unused")
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
  // $COVERAGE-ON$

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

  /**
   * Creates a store for `E` in the database of `transactor`.
   *
   * It creates the `events` table and its index if they are missing, and a
   * bounded hub of 4,096 events to publish to. The tags of `E` are worked out
   * from its schema with `TagResolver.resolve`. When the store starts, it
   * compares the tags in the database with the ones it knows, and logs a
   * warning for any it cannot explain.
   *
   * @param transactor
   *   the connection to the database file, usually from a [[TransactorCache]]
   */
  def make[E: Schema](transactor: Transactor): Task[SQLiteEventStore[E]] =
    makeWithResolver[E](transactor, TagResolver.resolve[E])

  // $COVERAGE-OFF$
  /**
   * Like `make`, for an event type whose cases were renamed.
   *
   * The `renameCase` steps of `migration` give the old names of the cases, so
   * that the events saved under an old name are still read, as the renamed
   * case.
   *
   * @param transactor
   *   the connection to the database file, usually from a [[TransactorCache]]
   * @param migration
   *   the renames of the cases of `E`; only its `renameCase` steps make aliases
   */
  def make[E: Schema](transactor: Transactor, migration: Migration[E, E]): Task[SQLiteEventStore[E]] =
    makeWithResolver[E](transactor, TagResolver.resolve[E](migration))

  /**
   * Like `make`, with a [[TagInfo]] you built yourself, for example with
   * `TagResolver.resolve`, instead of one worked out from the schema.
   *
   * @param transactor
   *   the connection to the database file, usually from a [[TransactorCache]]
   * @param tagInfo
   *   the tags of `E`, and the old tags that were renamed to them
   */
  def makeWithResolver[E: Schema](transactor: Transactor, tagInfo: TagInfo): Task[SQLiteEventStore[E]] =
    for {
      hub   <- Hub.bounded[EventEnvelope[E]](4096)
      store <- makeWithHub(transactor, hub, tagInfo)
    } yield store
  // $COVERAGE-ON$

  /**
   * Like `make`, but publishes to `hub` instead of creating one, for example to
   * choose its capacity or what happens when it is full.
   *
   * @param transactor
   *   the connection to the database file, usually from a [[TransactorCache]]
   * @param hub
   *   the hub that every appended event is published to
   */
  def makeWithHub[E: Schema](transactor: Transactor, hub: Hub[EventEnvelope[E]]): Task[SQLiteEventStore[E]] =
    makeWithHub(transactor, hub, TagResolver.resolve[E])

  /**
   * Like `makeWithHub`, for an event type whose cases were renamed. The
   * `renameCase` steps of `migration` give the old names of the cases.
   *
   * @param transactor
   *   the connection to the database file, usually from a [[TransactorCache]]
   * @param hub
   *   the hub that every appended event is published to
   * @param migration
   *   the renames of the cases of `E`; only its `renameCase` steps make aliases
   */
  def makeWithHub[E: Schema](
    transactor: Transactor,
    hub: Hub[EventEnvelope[E]],
    migration: Migration[E, E]
  ): Task[SQLiteEventStore[E]] =
    makeWithHub(transactor, hub, TagResolver.resolve[E](migration))

  /**
   * Like `makeWithHub`, with a [[TagInfo]] you built yourself. All the other
   * factories that return a `Task` end up here: it creates the table and its
   * index, and checks the tags in the database.
   *
   * @param transactor
   *   the connection to the database file, usually from a [[TransactorCache]]
   * @param hub
   *   the hub that every appended event is published to
   * @param tagInfo
   *   the tags of `E`, and the old tags that were renamed to them
   */
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

  // $COVERAGE-OFF$
  /**
   * Creates a store without an effect, for tests that need one outside a ZIO
   * program.
   *
   * It creates the table and its index on the calling thread, and publishes to
   * `hub`. Unlike `make`, it does not check the tags in the database.
   *
   * @param transactor
   *   the connection to the database file
   * @param hub
   *   the hub that every appended event is published to
   */
  def makeBlocking[E: Schema](transactor: Transactor, hub: Hub[EventEnvelope[E]]): SQLiteEventStore[E] =
    makeBlockingWithResolver[E](transactor, hub, TagResolver.resolve[E])

  /**
   * Like `makeBlocking`, with a [[TagInfo]] you built yourself.
   *
   * @param transactor
   *   the connection to the database file
   * @param hub
   *   the hub that every appended event is published to
   * @param tagInfo
   *   the tags of `E`, and the old tags that were renamed to them
   */
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

  /**
   * Like `makeBlocking`, for an event type whose cases were renamed. The
   * `renameCase` steps of `migration` give the old names of the cases.
   *
   * @param transactor
   *   the connection to the database file
   * @param hub
   *   the hub that every appended event is published to
   * @param migration
   *   the renames of the cases of `E`; only its `renameCase` steps make aliases
   */
  def makeBlocking[E: Schema](
    transactor: Transactor,
    hub: Hub[EventEnvelope[E]],
    migration: Migration[E, E]
  ): SQLiteEventStore[E] =
    makeBlockingWithResolver[E](transactor, hub, TagResolver.resolve[E](migration))
  // $COVERAGE-ON$
}
