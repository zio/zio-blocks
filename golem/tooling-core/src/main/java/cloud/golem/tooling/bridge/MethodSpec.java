package cloud.golem.tooling.bridge;

import java.util.List;

/** A method entry in the bridge manifest. */
public final class MethodSpec {
  public final String name;
  public final boolean isAsync;
  /** Return type in the bridge manifest syntax (e.g. {@code string}, {@code Name}, {@code void}). */
  public final String wireReturnType;
  public final List<ParamSpec> params;
  /** Method name to call on the Scala impl (defaults to {@link #name} when empty). */
  public final String implMethodName;

  public MethodSpec(String name, boolean isAsync, String wireReturnType, List<ParamSpec> params, String implMethodName) {
    this.name = name;
    this.isAsync = isAsync;
    this.wireReturnType = wireReturnType;
    this.params = params;
    this.implMethodName = implMethodName;
  }
}