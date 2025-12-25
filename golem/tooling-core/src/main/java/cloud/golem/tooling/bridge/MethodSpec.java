package cloud.golem.tooling.bridge;

import java.util.List;

/** A method exposed on the generated TypeScript agent class. */
public final class MethodSpec {
  public final String name;
  public final boolean isAsync;
  /** Return type in TS syntax (e.g. {@code string}, {@code Name}, {@code void}). */
  public final String tsReturnType;
  public final List<ParamSpec> params;
  /** Method name to call on the Scala impl (defaults to {@link #name} when empty). */
  public final String implMethodName;

  public MethodSpec(String name, boolean isAsync, String tsReturnType, List<ParamSpec> params, String implMethodName) {
    this.name = name;
    this.isAsync = isAsync;
    this.tsReturnType = tsReturnType;
    this.params = params;
    this.implMethodName = implMethodName;
  }
}