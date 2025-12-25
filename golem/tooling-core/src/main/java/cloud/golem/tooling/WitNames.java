package cloud.golem.tooling;

/**
 * Helpers for formatting WIT names used by golem-cli.
 *
 * <p>We keep this logic centralized so sbt/Mill tooling and docs don't drift.</p>
 */
public final class WitNames {
  private WitNames() {}

  /**
   * Formats a fully-qualified WIT function name for {@code golem-cli agent invoke}.
   *
   * <p>Example: {@code scala:name-agent/scala-name-agent.{reverse}}</p>
   */
  public static String fullyQualifiedMethod(String componentQualified, String agentType, String method) {
    requireNonBlank(componentQualified, "componentQualified");
    requireNonBlank(agentType, "agentType");
    requireNonBlank(method, "method");
    return componentQualified + "/" + agentType + ".{" + method + "}";
  }

  /**
   * Formats an agent id (constructor invocation) for {@code golem-cli agent invoke}.
   *
   * <p>Examples:
   * <ul>
   *   <li>{@code scala:name-agent/scala-name-agent()}</li>
   *   <li>{@code scala:name-agent/scala-complex-shard({ table-name: "demo", shard-id: 1 })}</li>
   * </ul>
   */
  public static String agentId(String componentQualified, String agentType, String ctorArgs) {
    requireNonBlank(componentQualified, "componentQualified");
    requireNonBlank(agentType, "agentType");
    String args = ctorArgs == null ? "" : ctorArgs.trim();
    return componentQualified + "/" + agentType + "(" + args + ")";
  }

  private static void requireNonBlank(String s, String name) {
    if (s == null || s.trim().isEmpty()) throw new IllegalArgumentException(name + " must be non-empty");
  }
}