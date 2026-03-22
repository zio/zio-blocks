package golem.runtime.annotations

import java.lang.annotation.{ElementType, Retention, RetentionPolicy, Target}
import scala.annotation.StaticAnnotation

/**
 * Maps an HTTP header to a method parameter.
 *
 * Usage:
 * {{{
 * def create(@header("X-Tenant") tenantId: String): Future[Unit]
 * }}}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(Array(ElementType.PARAMETER))
final class header(val name: String) extends StaticAnnotation
