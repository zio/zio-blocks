package buildalivedashboardwithdatastar

import java.util.Locale

/**
 * Build a Live Counter and Dashboard with Datastar — the weather station model
 *
 * Three cities and a function that gives each city a reading for every tick.
 * The readings drift up and down in a fixed pattern, so every run of the
 * tutorial prints the same numbers. Every step reuses these.
 */
final case class Reading(city: String, tenthsCelsius: Int, windKmh: Int, humidity: Int) {

  /** The temperature as text with one decimal, such as `4.0`. */
  def celsius: String = String.format(Locale.ROOT, "%.1f", Double.box(tenthsCelsius / 10.0))
}

object Weather {
  val cities: Vector[String] = Vector("Oslo", "Lisbon", "Nairobi")

  private val typical: Map[String, Reading] = Map(
    "Oslo"    -> Reading("Oslo", 40, 14, 71),
    "Lisbon"  -> Reading("Lisbon", 195, 18, 64),
    "Nairobi" -> Reading("Nairobi", 230, 11, 55)
  )

  /**
   * The reading of `city` at the given tick: its typical weather plus a drift.
   */
  def reading(city: String, tick: Int): Reading = {
    val base  = typical(city)
    val drift = ((tick * 7 + city.length * 3) % 11) - 5
    Reading(
      city,
      base.tenthsCelsius + drift * 2,
      math.max(0, base.windKmh + drift),
      math.min(100, math.max(0, base.humidity - drift))
    )
  }

  /** One reading per city at the given tick. */
  def readings(tick: Int): Vector[Reading] = cities.map(reading(_, tick))
}
