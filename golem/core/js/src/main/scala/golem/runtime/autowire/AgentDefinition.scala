package golem.runtime.autowire

import golem.runtime.AgentMetadata

import scala.scalajs.js

/**
 * Represents a fully-wired agent definition ready for runtime use.
 *
 * An `AgentDefinition` contains everything needed to:
 *   - Initialize new agent instances
 *   - Invoke methods via RPC
 *   - Export type metadata to the host
 *
 * You typically don't create these directly - use
 * [[AgentImplementation.register]] to generate definitions at compile time.
 *
 * ==Structure==
 * {{{
 * AgentDefinition[MyAgent]
 *   ├── typeName: "my-agent"
 *   ├── metadata: AgentMetadata (name, description, methods)
 *   ├── constructor: AgentConstructor[MyAgent]
 *   ├── bindings: List[MethodBinding[MyAgent]]
 *   └── mode: AgentMode
 * }}}
 *
 * @tparam Instance
 *   The agent trait type
 * @param typeName
 *   Unique identifier for this agent type
 * @param metadata
 *   Generated metadata describing the agent
 * @param constructor
 *   Handles agent initialization with constructor payloads
 * @param bindings
 *   RPC bindings for each agent method
 * @param mode
 *   The agent's persistence mode
 */
final class AgentDefinition[Instance](
  val typeName: String,
  val metadata: AgentMetadata,
  val constructor: AgentConstructor[Instance],
  bindings: List[MethodBinding[Instance]],
  val mode: AgentMode = AgentMode.Durable
) {

  /**
   * The WIT type representation of this agent, for host registration.
   *
   * This is lazily computed and cached. It encodes the agent's schema in a
   * format suitable for the Golem runtime's type system.
   */
  lazy val agentType: js.Dynamic =
    AgentTypeEncoder.from(this)
  private val methodsByName: Map[String, MethodBinding[Instance]] =
    bindings.map(binding => binding.metadata.name -> binding).toMap

  /**
   * Initializes a new agent instance, returning as Any for type-erased
   * contexts.
   */
  def initializeAny(payload: js.Dynamic): js.Promise[Any] =
    initialize(payload).asInstanceOf[js.Promise[Any]]

  /**
   * Initializes a new agent instance from a constructor payload.
   *
   * @param payload
   *   The constructor arguments as a dynamic JS object
   * @return
   *   A Promise resolving to the initialized instance
   */
  def initialize(payload: js.Dynamic): js.Promise[Instance] =
    constructor.initialize(payload)

  /**
   * Invokes a method with type-erased instance for dynamic dispatch.
   */
  def invokeAny(instance: Any, methodName: String, payload: js.Dynamic): js.Promise[js.Dynamic] =
    invoke(instance.asInstanceOf[Instance], methodName, payload)

  /**
   * Invokes a method on an agent instance.
   *
   * @param instance
   *   The agent instance to invoke on
   * @param methodName
   *   The method to invoke
   * @param payload
   *   The method arguments as a dynamic JS object
   * @return
   *   A Promise resolving to the method result
   */
  def invoke(instance: Instance, methodName: String, payload: js.Dynamic): js.Promise[js.Dynamic] = {
    if (!methodsByName.contains(methodName)) {
      scala.scalajs.js.Dynamic.global.console.log(
        s"[AgentDefinition] Unknown method: $methodName, available: ${methodsByName.keySet.mkString(",")}"
      )
    }
    methodsByName
      .get(methodName)
      .map(_.invoke(instance, payload))
      .getOrElse(js.Promise.reject(s"Unknown method: $methodName"))
  }

  /**
   * Returns the list of method bindings for inspection or testing.
   */
  def methodMetadata: List[MethodBinding[Instance]] = bindings
}
