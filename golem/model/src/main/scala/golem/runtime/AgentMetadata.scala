package golem.runtime

import golem.data.StructuredSchema

/**
 * Describes a single method on an agent.
 *
 * This metadata is generated at compile time by the autowiring macros and used
 * for WIT type generation and runtime dispatch.
 *
 * @param name
 *   The method name as it appears in the trait
 * @param description
 *   Human-readable description (from `@description` annotation)
 * @param prompt
 *   Optional LLM prompt (from `@prompt` annotation)
 * @param mode
 *   Optional method mode override
 * @param input
 *   Schema describing the method's input parameters
 * @param output
 *   Schema describing the method's return type
 */
final case class MethodMetadata(
  name: String,
  description: Option[String],
  prompt: Option[String],
  mode: Option[String],
  input: StructuredSchema,
  output: StructuredSchema
)

/**
 * Describes an agent's complete interface.
 *
 * This metadata is generated at compile time from the agent trait and includes
 * all information needed for:
 *   - WIT type generation
 *   - Host registration
 *   - Runtime method dispatch
 *
 * @param name
 *   The agent's type name (from the trait name)
 * @param description
 *   Human-readable description (from `@description` annotation)
 * @param mode
 *   The agent's persistence mode (from `@agentDefinition(mode = ...)`)
 * @param methods
 *   Metadata for each method in the agent trait
 * @param constructor
 *   Schema for the agent's constructor parameters
 */
final case class AgentMetadata(
  name: String,
  description: Option[String],
  mode: Option[String],
  methods: List[MethodMetadata],
  constructor: StructuredSchema
)
