package cloud.golem.tooling.bridge;

/** A method parameter in the generated TypeScript agent class. */
public final class ParamSpec {
  public final String name;
  public final String tsType;
  /** Expression passed into the Scala impl call (defaults to the parameter name). */
  public final String implArgExpr;

  public ParamSpec(String name, String tsType, String implArgExpr) {
    this.name = name;
    this.tsType = tsType;
    this.implArgExpr = implArgExpr;
  }
}