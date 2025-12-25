package cloud.golem.tooling.bridge;

/** No-arg constructor (required for TS schema generation compatibility). */
public final class NoArgConstructorSpec implements ConstructorSpec {
  public static final NoArgConstructorSpec INSTANCE = new NoArgConstructorSpec();

  private NoArgConstructorSpec() {}
}