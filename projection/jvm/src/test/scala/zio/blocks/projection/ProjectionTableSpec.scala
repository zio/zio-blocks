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

import zio.blocks.schema.{Modifier, Schema}
import zio.blocks.sql.DbValue
import zio.test.*

import java.time.*
import java.util.UUID

object ProjectionTableSpec extends ZIOSpecDefault {

  case class AllTypes(
    @Modifier.id id: String,
    b: Boolean,
    by: Byte,
    sh: Short,
    i: Int,
    l: Long,
    f: Float,
    d: Double,
    c: Char,
    s: String,
    bd: BigDecimal,
    dur: Duration,
    inst: Instant,
    ld: LocalDate,
    ldt: LocalDateTime,
    lt: LocalTime,
    uuid: UUID,
    opt: Option[String],
    seq: List[String],
    m: Map[String, Int]
  )
  object AllTypes { implicit val schema: Schema[AllTypes] = Schema.derived[AllTypes] }

  case class Nested(a: String, b: Int)
  object Nested { implicit val schema: Schema[Nested] = Schema.derived[Nested] }

  case class WithNested(@Modifier.id id: String, nested: Nested, seq: List[String])
  object WithNested { implicit val schema: Schema[WithNested] = Schema.derived[WithNested] }

  case class WithInline(
    @Modifier.id id: String,
    @Modifier.config("sql.inline", "true") nested: Nested,
    seq: List[String]
  )
  object WithInline { implicit val schema: Schema[WithInline] = Schema.derived[WithInline] }

  sealed trait Kind
  case object KindA extends Kind
  case object KindB extends Kind
  object Kind { implicit val schema: Schema[Kind] = Schema.derived[Kind] }

  sealed trait Shape
  case class Circle(radius: Double) extends Shape
  case class Square(side: Double)   extends Shape
  object Shape { implicit val schema: Schema[Shape] = Schema.derived[Shape] }

  case class WithVariants(@Modifier.id id: String, kind: Kind, shape: Shape, seq: List[String])
  object WithVariants { implicit val schema: Schema[WithVariants] = Schema.derived[WithVariants] }

  case class WithTransient(@Modifier.id id: String, @Modifier.transient() hidden: Int = 0, seq: List[String])
  object WithTransient { implicit val schema: Schema[WithTransient] = Schema.derived[WithTransient] }

  private def defaultOf[A](table: zio.blocks.sql.Table[A], column: String): Option[DbValue] =
    table.columnsMeta.find(_.name == column).map(_.dbValue)

  def spec: Spec[Any, Any] = suite("ProjectionTableSpec")(
    test("derives every primitive default for sequence-bearing records") {
      val table = ProjectionTable.derived[AllTypes]
      assertTrue(
        table.columns == Vector(
          "id",
          "b",
          "by",
          "sh",
          "i",
          "l",
          "f",
          "d",
          "c",
          "s",
          "bd",
          "dur",
          "inst",
          "ld",
          "ldt",
          "lt",
          "uuid",
          "opt",
          "seq",
          "m"
        ),
        defaultOf(table, "b").contains(DbValue.DbBoolean(false)),
        defaultOf(table, "by").contains(DbValue.DbByte(0)),
        defaultOf(table, "sh").contains(DbValue.DbShort(0)),
        defaultOf(table, "i").contains(DbValue.DbInt(0)),
        defaultOf(table, "l").contains(DbValue.DbLong(0L)),
        defaultOf(table, "f").contains(DbValue.DbFloat(0f)),
        defaultOf(table, "d").contains(DbValue.DbDouble(0d)),
        defaultOf(table, "c").contains(DbValue.DbChar(' ')),
        defaultOf(table, "s").contains(DbValue.DbString("")),
        defaultOf(table, "bd").contains(DbValue.DbBigDecimal(BigDecimal(0))),
        defaultOf(table, "dur").contains(DbValue.DbDuration(Duration.ZERO)),
        defaultOf(table, "inst").contains(DbValue.DbInstant(Instant.EPOCH)),
        defaultOf(table, "ld").contains(DbValue.DbLocalDate(LocalDate.ofEpochDay(0))),
        defaultOf(table, "uuid").contains(DbValue.DbUUID(new UUID(0L, 0L))),
        defaultOf(table, "seq").contains(DbValue.DbString("[]")),
        defaultOf(table, "m").contains(DbValue.DbString("{}"))
      )
    },
    test("renders a nested record as one JSON column when a sequence forces the fallback") {
      val table = ProjectionTable.derived[WithNested]
      assertTrue(
        table.columns == Vector("id", "nested", "seq"),
        defaultOf(table, "nested").contains(DbValue.DbString("{}"))
      )
    },
    test("inlines a nested record marked with sql.inline") {
      val table = ProjectionTable.derived[WithInline]
      assertTrue(table.columns == Vector("id", "nested_a", "nested_b", "seq"))
    },
    test("renders simple and complex variants as single columns") {
      val table = ProjectionTable.derived[WithVariants]
      assertTrue(
        table.columns == Vector("id", "kind", "shape", "seq"),
        defaultOf(table, "kind").contains(DbValue.DbString("")),
        defaultOf(table, "shape").contains(DbValue.DbString(""))
      )
    },
    test("omits transient fields") {
      val table = ProjectionTable.derived[WithTransient]
      assertTrue(table.columns == Vector("id", "seq"))
    }
  )
}
