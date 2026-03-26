package golem.runtime.annotations

import java.lang.annotation.{ElementType, Retention, RetentionPolicy, Target}
import scala.annotation.StaticAnnotation

/**
 * Marks a method as an HTTP endpoint.
 *
 * Usage:
 * {{{
 * @endpoint(method = "GET", path = "/weather/{city}")
 * def getWeather(city: String): Future[WeatherReport]
 * }}}
 *
 * @param method
 *   HTTP method: GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, etc.
 * @param path
 *   Path suffix with optional query: "/items/{id}?format={fmt}"
 * @param auth
 *   Whether authentication is required. If omitted, inherits from mount.
 * @param cors
 *   CORS allowed patterns; empty = inherit from mount
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(Array(ElementType.METHOD))
final class endpoint(
  val method: String,
  val path: String,
  val auth: Boolean = false,
  val cors: Array[String] = Array.empty
) extends StaticAnnotation
