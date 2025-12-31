package cloud.golem.tooling.bridge;

import java.util.List;

/** Multi-argument constructor signature (e.g. {@code constructor(a: string, b: number)}). */
public final class PositionalConstructorSpec implements ConstructorSpec {
  /** Ordered constructor parameters for the bridge manifest. */
  public final List<FieldSpec> params;

  /** Optional explicit args passed to the Scala factory (defaults to passing each param by name). */
  public final List<String> scalaFactoryArgs;

  public PositionalConstructorSpec(List<FieldSpec> params, List<String> scalaFactoryArgs) {
    this.params = params;
    this.scalaFactoryArgs = scalaFactoryArgs;
  }
}