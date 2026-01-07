package golem.runtime.annotations

import java.lang.annotation.{ElementType, Retention, RetentionPolicy, Target}
import scala.annotation.StaticAnnotation

/**
 * Marks an agent trait with a Golem agent type name.
 *
 * Companion ergonomics are provided by `golem.AgentCompanion`.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(Array(ElementType.TYPE))
final class agentDefinition(
  val typeName: String = "",
  val mode: DurabilityMode = DurabilityMode.Durable
) extends StaticAnnotation
