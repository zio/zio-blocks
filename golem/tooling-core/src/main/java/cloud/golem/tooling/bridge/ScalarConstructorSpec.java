package cloud.golem.tooling.bridge;

import java.util.List;

/** Single-argument constructor signature (e.g. {@code constructor(input: string)}). */
public final class ScalarConstructorSpec implements ConstructorSpec {
  public final String wireType;
  public final List<String> scalaFactoryArgs;

  public ScalarConstructorSpec(String wireType, List<String> scalaFactoryArgs) {
    this.wireType = wireType;
    this.scalaFactoryArgs = scalaFactoryArgs;
  }
}