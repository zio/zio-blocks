package cloud.golem.tooling.bridge;

/** A method parameter entry in the bridge manifest. */
public final class ParamSpec {
  public final String name;
  public final String wireType;
  /** Expression passed into the Scala impl call (defaults to the parameter name). */
  public final String implArgExpr;

  public ParamSpec(String name, String wireType, String implArgExpr) {
    this.name = name;
    this.wireType = wireType;
    this.implArgExpr = implArgExpr;
  }
}