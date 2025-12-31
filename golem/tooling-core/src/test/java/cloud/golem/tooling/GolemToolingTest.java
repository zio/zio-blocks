package cloud.golem.tooling;

import org.junit.Test;

import static org.junit.Assert.*;

public class GolemToolingTest {

  @Test
  public void noisyWarningFilterRecognizesKnownLines() {
    assertTrue(GolemTooling.isNoisyUpstreamWarning("[E0055] Warning: FFI parameter of pointer type should be annotated"));
    assertTrue(GolemTooling.isNoisyUpstreamWarning("[E0027] Warning: Using a multiline string directly"));
    assertFalse(GolemTooling.isNoisyUpstreamWarning("some other warning"));
  }

  @Test
  public void requireCommandOnPathSucceedsForBash() {
    // CI/dev on Linux should always have bash.
    GolemTooling.requireCommandOnPath("bash", "bash");
  }

  @Test
  public void requireCommandOnPathFailsForMissingBinary() {
    try {
      GolemTooling.requireCommandOnPath("definitely-not-a-real-binary-xyz", "missing");
      fail("expected RuntimeException");
    } catch (RuntimeException e) {
      assertTrue(e.getMessage().contains("not found on PATH"));
    }
  }
}


