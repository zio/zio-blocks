package customwireformat

import java.time.Instant
import zio.blocks.schema._

/**
 * How to Write a Codec for a New Wire Format — Shared Model
 *
 * The sensor readings a gateway sends, the site's zone tree and a sample batch
 * (section "The Wire Format and the Sensor Model"). It has no main method.
 */
final case class DeviceId(value: String)

object DeviceId {
  implicit val schema: Schema[DeviceId] = Schema[String].transform(
    id =>
      if (id.nonEmpty && id.length <= 16) DeviceId(id)
      else throw SchemaError.validationFailed(s"device id must be 1 to 16 characters, got ${id.length}"),
    _.value
  )
}

sealed trait Measurement
final case class Temperature(celsius: Double) extends Measurement
final case class Humidity(percent: Float)     extends Measurement
case object DoorOpened                        extends Measurement

object Measurement {
  implicit val schema: Schema[Measurement] = Schema.derived
}

final case class Reading(device: DeviceId, takenAt: Instant, measurement: Measurement, battery: Option[Int])

object Reading {
  implicit val schema: Schema[Reading] = Schema.derived
}

final case class Batch(gateway: String, readings: List[Reading], calibration: Map[String, Double])

object Batch {
  implicit val schema: Schema[Batch] = Schema.derived

  val sample: Batch = Batch(
    gateway = "gw-north",
    readings = List(
      Reading(DeviceId("t-17"), Instant.parse("2026-10-29T08:00:00Z"), Temperature(21.5), Some(87)),
      Reading(DeviceId("h-02"), Instant.parse("2026-10-29T08:00:05Z"), Humidity(41.0f), None),
      Reading(DeviceId("d-01"), Instant.parse("2026-10-29T08:00:09Z"), DoorOpened, Some(12))
    ),
    calibration = Map("t-17" -> -0.4, "h-02" -> 1.5)
  )
}

final case class Zone(name: String, devices: Vector[DeviceId], subzones: List[Zone])

object Zone {
  implicit val schema: Schema[Zone] = Schema.derived
}
