package cloud.golem.tooling.bridge;

/** A field in a record constructor input type. */
public final class FieldSpec {
  public final String name;
  public final String wireType;

  public FieldSpec(String name, String wireType) {
    this.name = name;
    this.wireType = wireType;
  }
}