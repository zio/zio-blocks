package cloud.golem.tooling.bridge;

import java.util.List;

/** Specification for generating a single {@code @agent} TypeScript class. */
public final class AgentSpec {
  /** Name passed to {@code @agent({ name: ... })}. */
  public final String agentName;
  /** TypeScript class name to generate. */
  public final String className;
  /** Scala factory function on {@code scalaAgents}, e.g. {@code newNameAgent}. */
  public final String scalaFactory;
  public final ConstructorSpec constructor;
  /** Optional TS declarations emitted before the class (e.g. {@code type Foo = ...}). */
  public final List<String> typeDeclarations;
  public final List<MethodSpec> methods;

  public AgentSpec(
    String agentName,
    String className,
    String scalaFactory,
    ConstructorSpec constructor,
    List<String> typeDeclarations,
    List<MethodSpec> methods
  ) {
    this.agentName = agentName;
    this.className = className;
    this.scalaFactory = scalaFactory;
    this.constructor = constructor;
    this.typeDeclarations = typeDeclarations;
    this.methods = methods;
  }
}