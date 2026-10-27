package namedtuples

import zio.blocks.schema._

/**
 * How to Use Scala 3 Named Tuples with Schema — Shared Model
 *
 * The `Reading` and `Station` named tuples and their schemas, used by the
 * section examples. It has no main method.
 */
type Reading = (city: String, tempC: Double)

implicit val readingSchema: Schema[Reading] = Schema.derived

type Station = (id: String, reading: Reading, humidity: Option[Int], tags: List[String])

implicit val stationSchema: Schema[Station] = Schema.derived
