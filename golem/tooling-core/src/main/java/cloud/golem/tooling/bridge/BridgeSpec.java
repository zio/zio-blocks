package cloud.golem.tooling.bridge;

import java.util.Collections;
import java.util.List;

/** Top-level spec for generating {@code src/main.ts}. */
public final class BridgeSpec {
  /** Import path for the Scala.js bundle (e.g. {@code ./scala-autowired.js}). */
  public final String scalaBundleImport;
  /**
   * How to resolve the Scala agent factories.
   * Defaults to: {@code (scalaExports as any).scalaAgents ?? (globalThis as any).scalaAgents}
   */
  public final String scalaAgentsExpr;
  public final List<AgentSpec> agents;

  public BridgeSpec(String scalaBundleImport, String scalaAgentsExpr, List<AgentSpec> agents) {
    this.scalaBundleImport = scalaBundleImport;
    this.scalaAgentsExpr = scalaAgentsExpr;
    this.agents = agents;
  }

  public static BridgeSpec empty() {
    return new BridgeSpec("./scala.js", "(scalaExports as any).scalaAgents ?? (globalThis as any).scalaAgents", Collections.emptyList());
  }
}