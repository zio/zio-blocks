---
id: db-param
title: "DbParam"
description: "Reference for DbParam, the typeclass that converts Scala values to DbValue for the sql interpolator and bound SQL parameters in the sql module."
keywords:
  - "DbParam typeclass"
  - "sql interpolator parameters"
  - "toDbValue conversion"
  - "DbParam given instances"
  - "fromDbCodec bridge"
  - "Option Maybe nullable parameters"
  - "Compile-time SQL parameters"
---

`DbParam[A]` is a single-method typeclass that converts a Scala value of type `A` to a `DbValue` for use as a bound SQL parameter. Its sole abstract method is `toDbValue(value: A): DbValue`. The `sql"..."` string interpolator summons a `DbParam[A]` instance at compile time for every interpolated expression and delegates to it during execution to produce the typed `DbValue` that is stored in `Frag#params`. 

The structural shape of `DbParam` is:

```scala
trait DbParam[A] {
  def toDbValue(value: A): DbValue
}

object DbParam {
  def apply[A](implicit p: DbParam[A]): DbParam[A]
  // given instances for:
  //   Int, Long, Double, Float, Boolean, String, Short, Byte,
  //   BigDecimal, Array[Byte], LocalDate, LocalDateTime, LocalTime,
  //   Instant, Duration, UUID, DbValue (identity),
  //   Option[A], Maybe[A], and fromDbCodec[A]
}
```

## Quick Showcase

The following example shows the three common entry points for `DbParam`: the `sql"..."` interpolator (which calls `toDbValue` behind the scenes), direct instance summoning for inspection:

```scala
import zio.blocks.sql._
import java.time.Instant
import java.util.UUID

// 1. Implicitly used by the sql"..." interpolator — no explicit call needed
val userId  = 42
val active  = true
val query   = sql"SELECT * FROM users WHERE id = $userId AND active = $active"
query.params // IndexedSeq(DbValue.DbInt(42), DbValue.DbBoolean(true))

// 2. Summon an instance explicitly with DbParam.apply to inspect conversion
val instant = Instant.parse("2024-01-15T10:00:00Z")
DbParam[Instant].toDbValue(instant) // DbValue.DbInstant(2024-01-15T10:00:00Z)

DbParam[Option[Int]].toDbValue(Some(7))  // DbValue.DbInt(7)
DbParam[Option[Int]].toDbValue(None)     // DbValue.DbNull
```
