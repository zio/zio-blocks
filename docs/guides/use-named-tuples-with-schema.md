---
id: use-named-tuples-with-schema
title: "How to Use Scala 3 Named Tuples with Schema"
description: "Derive a Schema for a Scala 3 named tuple, round-trip it through JSON, update fields with optics and convert it to a case class."
keywords:
  - "Named Tuples"
  - "Scala 3"
  - "JSON"
  - "Schema"
  - "Optics"
  - "Into"
---

In this guide, we use Scala 3 named tuples as lightweight records for weather readings: a station sends `(city = "Oslo", tempC = 3.8)`, and we encode it to JSON, decode it back, correct one field and turn it into a case class, all without declaring a class for each shape. ZIO Blocks derives a `Schema` for a named tuple the same way it does for a case class, and the schema brings JSON, optics and the other codecs with it.

We take an incremental approach:

- Derive a `Schema` for a named tuple
- Round-trip it through JSON and read decode errors
- Nest named tuples, and add optional and list fields
- Go past 22 fields
- Read and update one field with optics
- Convert between a named tuple and a case class

## The Problem

Named tuples became a stable feature in Scala 3.7. They work like a case class we never declare, which suits query rows, quick JSON payloads and data that changes shape between two steps of a program. But most Scala JSON libraries have no codec for them, so we end up writing one by hand for every shape.

For example, a hand-written codec for one reading often looks like this:

```scala mdoc:compile-only
type Reading = (city: String, tempC: Double)

def toJson(reading: Reading): String =
  s"""{"city":"${reading.city}","tempC":${reading.tempC}}"""

def fromJson(json: String): Option[Reading] =
  for {
    city  <- """"city"\s*:\s*"([^"]*)"""".r.findFirstMatchIn(json).map(_.group(1))
    tempC <- """"tempC"\s*:\s*(-?[0-9.]+)""".r.findFirstMatchIn(json).map(_.group(1).toDouble)
  } yield (city = city, tempC = tempC)
```

The encoder doesn't escape quotes in a city name, the decoder answers `None` without saying which field was wrong, and every new field, nested tuple or new shape needs more of the same code. The usual way out is to declare a case class just to get a codec, which gives up the reason we picked a named tuple in the first place.

In this guide, we derive a `Schema` for the named tuple instead. The field names come from the tuple's labels, and the same schema gives us JSON, decode errors that name the field, optics and conversions.

## Prerequisites

This guide builds on [Your First Schema](./your-first-schema.md) (deriving a `Schema` and its JSON round trip) and [Navigate and Update Nested Data with Optics](./nested-data-with-optics.md) (lenses and `CompanionOptics`). Add the schema module to your `build.sbt` (see [How to Add ZIO Blocks to Your Build](../getting-started/installation.md) for the full setup):

```scala
// Scala 3.7 and 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"

// Scala 3.9 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-schema" % "@VERSION@"
scalacOptions += "-experimental"
```

Named tuples need Scala 3.7 or newer, the first release where they are a stable language feature. Scala 2.13 has no named tuples, so this guide is for Scala 3 only. We checked every example with the published `zio-blocks-schema` on Scala 3.7.0, 3.7.4 and 3.8.4, and with both artifacts on Scala 3.9.0.

We use this import throughout:

```scala mdoc:silent
import zio.blocks.schema._
```

## Derive a Schema for a Named Tuple

A named tuple type is easiest to work with under a type alias. We derive its schema with `Schema.derived`, exactly as for a case class:

```scala mdoc:silent
type Reading = (city: String, tempC: Double)

implicit val readingSchema: Schema[Reading] = Schema.derived
```

The schema is a record whose field names are the tuple's labels, in order:

```scala mdoc
Schema[Reading].reflect.asRecord.map(_.fields.map(_.name))
```

With the schema in scope, every schema-based feature works on a named tuple value. `show` prints it with its labels:

```scala mdoc
val oslo: Reading = (city = "Oslo", tempC = 3.8)

println(oslo.show)
```

:::warning[An `object` next to a type alias is not a companion]
For a case class, we usually put the schema in its companion object. A type alias has no companion: in `type Reading = (...)` plus `object Reading { implicit val schema: Schema[Reading] = Schema.derived }`, the schema is not found implicitly, and `oslo.toJsonString` fails with "value toJsonString is not a member of Reading". Define the schema where the code that uses it can see it, as we do here, or write `import Reading._` (with Scala 3 given syntax, write `import Reading.given`).
:::

## Round-Trip a Named Tuple Through JSON

The JSON codec comes from the schema, so a named tuple encodes as a JSON object keyed by its labels:

```scala mdoc:silent
val osloJson = oslo.toJsonString
```

The encoded text has one key per label:

```scala mdoc
println(osloJson)
```

Decoding gives back the same named tuple:

```scala mdoc
osloJson.fromJson[Reading]

osloJson.fromJson[Reading] == Right(oslo)
```

A named tuple prints like a plain tuple, `(Oslo,3.8)`, because its labels exist only in the type, not in the value. They are still there when we read a field of the decoded value:

```scala mdoc
osloJson.fromJson[Reading].map(_.city)
```

When a payload is wrong, the error names the field. A missing field and a value of the wrong type each give a `SchemaError`:

```scala mdoc
println("""{"city":"Oslo"}""".fromJson[Reading].left.map(_.message))

println("""{"city":"Oslo","tempC":"mild"}""".fromJson[Reading].left.map(_.message))
```

An unknown field is ignored by default, so a station that adds a `wind` field doesn't break our decoder:

```scala mdoc
"""{"city":"Oslo","tempC":3.8,"wind":4}""".fromJson[Reading]
```

To change field naming, reject unknown fields or require optional fields, derive a customized codec with `JsonCodecDeriver` settings such as `withFieldNameMapper`, `withRejectExtraFields` and `withRequireOptionFields`, as shown in [How to Customize JSON Encoding](./customize-json-encoding.md). Those global settings apply to named tuples; the per-type `@Modifier` annotations don't, because a named tuple has nothing to annotate.

## Nest Named Tuples and Add Optional Fields

A named tuple can hold another named tuple, an `Option` or a collection. A station's report nests a `Reading`, adds a humidity that some stations don't measure, and lists tags:

```scala mdoc:silent
type Station = (id: String, reading: Reading, humidity: Option[Int], tags: List[String])

implicit val stationSchema: Schema[Station] = Schema.derived

val osloStation: Station = (id = "OSL-1", reading = oslo, humidity = Some(81), tags = List("coast"))
val bergenStation: Station =
  (id = "BGO-2", reading = (city = "Bergen", tempC = 7.2), humidity = None, tags = Nil)
```

The nested tuple becomes a nested JSON object. A `None` field is left out, and so is an empty list:

```scala mdoc
println(osloStation.toJsonString)

println(bergenStation.toJsonString)
```

Decoding fills missing optional fields with `None`, and an empty list for a missing list:

```scala mdoc
"""{"id":"BGO-2","reading":{"city":"Bergen","tempC":7.2}}""".fromJson[Station]
```

A named tuple also works as a field of a case class. Deriving the case class's schema derives the schemas of the named tuples inside it, so `DailyReport` needs no extra schema:

```scala mdoc:silent
case class DailyReport(day: String, readings: List[(city: String, tempC: Double)])

object DailyReport {
  implicit val schema: Schema[DailyReport] = Schema.derived
}
```

The rows encode as JSON objects inside the case class:

```scala mdoc
println(DailyReport("2026-10-27", List(oslo, (city = "Bergen", tempC = 7.2))).toJsonString)
```

## Go Past 22 Fields

A plain Scala tuple class stops at `Tuple22`, and longer tuples are stored differently at runtime. Schema derivation handles both, so a reading per hour of the day, 24 fields, works like a short one:

```scala mdoc:silent
type HourlyTemps = (
  h00: Double, h01: Double, h02: Double, h03: Double, h04: Double, h05: Double,
  h06: Double, h07: Double, h08: Double, h09: Double, h10: Double, h11: Double,
  h12: Double, h13: Double, h14: Double, h15: Double, h16: Double, h17: Double,
  h18: Double, h19: Double, h20: Double, h21: Double, h22: Double, h23: Double
)

implicit val hourlySchema: Schema[HourlyTemps] = Schema.derived

val osloDay: HourlyTemps = (
  h00 = 1.0, h01 = 0.8, h02 = 0.5, h03 = 0.3, h04 = 0.2, h05 = 0.4,
  h06 = 1.1, h07 = 2.0, h08 = 3.2, h09 = 4.5, h10 = 5.6, h11 = 6.3,
  h12 = 6.9, h13 = 7.1, h14 = 7.0, h15 = 6.4, h16 = 5.5, h17 = 4.6,
  h18 = 3.8, h19 = 3.1, h20 = 2.6, h21 = 2.2, h22 = 1.9, h23 = 1.5
)
```

We encode the day once and keep the text:

```scala mdoc:silent
val osloDayJson = osloDay.toJsonString
```

The schema has all 24 fields, and the JSON round trip gives back an equal value:

```scala mdoc
Schema[HourlyTemps].reflect.asRecord.map(_.fields.size)

osloDayJson.fromJson[HourlyTemps] == Right(osloDay)
```

The JSON has one key per hour:

```scala mdoc
println(osloDayJson)
```

## Read and Update a Field with Optics

Optics read and change one field without rebuilding the whole tuple by hand. We extend `CompanionOptics` and build lenses with `$`, which finds the schemas we already have in scope. We give the object its own name, `Readings`, because a type alias has no companion:

```scala mdoc:silent
object Readings extends CompanionOptics[Reading] {
  val city: Lens[Reading, String]  = $(_.city)
  val tempC: Lens[Reading, Double] = $(_.tempC)
}
```

A lens gets, replaces and modifies its field, and the result is still a `Reading`:

```scala mdoc
Readings.tempC.get(oslo)

Readings.tempC.replace(oslo, 4.1)

Readings.tempC.modify(oslo, _ + 0.5)
```

A path goes through nested named tuples and into collections, as on case classes:

```scala mdoc:silent
object Stations extends CompanionOptics[Station] {
  val tempC: Lens[Station, Double]         = $(_.reading.tempC)
  val humidity: Lens[Station, Option[Int]] = $(_.humidity)
  val tags: Traversal[Station, String]     = $(_.tags.each)
}
```

Each optic updates its target and leaves the rest of the station alone:

```scala mdoc
Stations.tempC.modify(osloStation, _ - 0.3)

Stations.humidity.modify(osloStation, _.map(_ + 2))

Stations.tags.modify(osloStation, _.toUpperCase)
```

:::warning[A `when[...]` step after a named-tuple field fails at runtime]
In a known bug in 0.0.56, a path that selects a case after a named-tuple field, such as `$(_.humidity.when[Some[Int]].value)` or `$(_.sky.when[Cloudy].cover)`, compiles but throws `RuntimeException: Expected a variant` the first time the code reads the optic. The same path on a case class works. Until a fix ships, use a lens to the whole field and change the value inside it, as `Stations.humidity` does with `Option#map` above.
:::

## Convert Between a Named Tuple and a Case Class

Named tuples are handy at the edges of a program, but the rest of the code often has a case class for the same data. `Into` converts between them through the plain tuple: `NamedTuple#toTuple` drops the labels, and `Into` converts a tuple to a case class with matching field types, in order:

```scala mdoc:silent
case class WeatherReading(city: String, tempC: Double)

val toWeatherReading: Into[(String, Double), WeatherReading] =
  Into.derived[(String, Double), WeatherReading]

val fromWeatherReading: Into[WeatherReading, (String, Double)] =
  Into.derived[WeatherReading, (String, Double)]
```

The conversion returns an `Either`, as every `Into` does:

```scala mdoc
toWeatherReading.into(oslo.toTuple)
```

The other direction needs no extra step. `fromWeatherReading` produces a plain tuple, and Scala accepts a plain tuple wherever it expects a named tuple with the same element types:

```scala mdoc
val bergen: Either[SchemaError, Reading] = fromWeatherReading.into(WeatherReading("Bergen", 7.2))

bergen.map(_.city)
```

`As` works the same way, `As.derived[(String, Double), WeatherReading]`, when you need both directions in one value. Both match fields by position, not by label, so keep the tuple's element order the same as the case class's fields. For more about `Into` and `As`, see [Evolve Your Data](./evolve-your-data.md).

:::note
In 0.0.56, `Into.derived` and `As.derived` don't accept a named tuple directly: `Into.derived[Reading, WeatherReading]` fails to compile with "Unsupported type combination", because the macro doesn't recognize a named tuple as a tuple. Converting through the plain tuple, as above, works.
:::

## Putting It Together

The complete example handles a small station feed. It decodes a JSON array of station reports, which uses the schema that ZIO Blocks provides for a `List` of any type with a schema, prints the decode error for a damaged report, corrects each reading's temperature with the `Stations.tempC` lens, converts the readings to `WeatherReading` with `Into`, and encodes a `DailyReport`. Beyond the sections, it decodes the feed with `fromJson[List[Station]]` and stops with `sys.error` if the feed doesn't decode, and it adds a third station, Tromsø. It takes `Reading`, `Station`, `WeatherReading` and `DailyReport` from the shared `Model.scala`:

```scala mdoc:embed:schema-examples/src/main/scala/namedtuples/CompleteExample.scala
```

Running it prints:

```text
Decoded 3 stations
Damaged report: missing required field "tempC" at: .reading
Corrected: OSL-1 4.3, BGO-2 7.7, TOS-1 -1.0
Case classes: List(WeatherReading(Oslo,4.3), WeatherReading(Bergen,7.7), WeatherReading(Tromsø,-1.0))
Report: {"day":"2026-10-27","readings":[{"city":"Oslo","tempC":4.3},{"city":"Bergen","tempC":7.7},{"city":"Tromsø","tempC":-1.0}]}
```

## Running the Examples

All runnable code from this guide is in [`schema-examples/src/main/scala/namedtuples/`](https://github.com/zio/zio-blocks/tree/main/schema-examples/src/main/scala/namedtuples). Run any file from that folder with `scala-cli run <File>.scala`, or from `schema-examples` with `sbt "runMain namedtuples.<ObjectName>"`.

## Where to Go Next

- [Your First Schema](./your-first-schema.md) covers deriving a schema and the JSON round trip for case classes.
- [Navigate and Update Nested Data with Optics](./nested-data-with-optics.md) shows every kind of optic path, including `each` and `atKey` steps and the `p"..."` path syntax.
- [How to Customize JSON Encoding](./customize-json-encoding.md) changes field names and optional-field handling.
- [Evolve Your Data](./evolve-your-data.md) explains `Into`, `As` and migrations between versions of a type.
