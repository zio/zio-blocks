package cloud.golem.tooling.bridge;

/** A field in a record constructor input type. */
public final class FieldSpec {
  public final String name;
  public final String tsType;

  public FieldSpec(String name, String tsType) {
    this.name = name;
    this.tsType = tsType;
  }
}