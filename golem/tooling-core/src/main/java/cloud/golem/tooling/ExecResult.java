package cloud.golem.tooling;

/** Result of executing an external command. */
public final class ExecResult {
  public final int exitCode;
  public final String output;
  public final boolean timedOut;

  public ExecResult(int exitCode, String output, boolean timedOut) {
    this.exitCode = exitCode;
    this.output = output;
    this.timedOut = timedOut;
  }
}