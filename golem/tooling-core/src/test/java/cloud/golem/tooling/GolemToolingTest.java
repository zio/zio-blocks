package cloud.golem.tooling;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class GolemToolingTest {

  @Test
  public void stripHttpApiRemovesOnlyThatTopLevelBlock() throws Exception {
    Path dir = Files.createTempDirectory("golem-tooling-test-");
    Path yaml = dir.resolve("golem.yaml");
    String input =
      ""
        + "components:\n"
        + "  demo:component:\n"
        + "    template: ts\n"
        + "\n"
        + "httpApi:\n"
        + "  bindings:\n"
        + "    - name: counter\n"
        + "      path: /counter\n"
        + "      method: POST\n"
        + "      response: string\n"
        + "\n"
        + "dependencies:\n"
        + "  demo:component:\n";
    Files.write(yaml, input.getBytes(StandardCharsets.UTF_8));

    GolemTooling.stripHttpApiFromGolemYaml(dir, null);

    String out = new String(Files.readAllBytes(yaml), StandardCharsets.UTF_8);
    assertTrue(out.contains("components:"));
    assertTrue(out.contains("dependencies:"));
    assertFalse(out.contains("httpApi:"));
    // Ensure it still ends with a newline (convenient for idempotence / git diffs).
    assertTrue(out.endsWith("\n"));
  }

  @Test
  public void stripHttpApiIsIdempotent() throws Exception {
    Path dir = Files.createTempDirectory("golem-tooling-test-");
    Path yaml = dir.resolve("golem.yaml");
    String input =
      ""
        + "components:\n"
        + "  demo:component:\n"
        + "    template: ts\n"
        + "\n"
        + "httpApi:\n"
        + "  x: y\n"
        + "\n"
        + "dependencies:\n"
        + "  demo:component:\n";
    Files.write(yaml, input.getBytes(StandardCharsets.UTF_8));

    GolemTooling.stripHttpApiFromGolemYaml(dir, null);
    String once = new String(Files.readAllBytes(yaml), StandardCharsets.UTF_8);

    GolemTooling.stripHttpApiFromGolemYaml(dir, null);
    String twice = new String(Files.readAllBytes(yaml), StandardCharsets.UTF_8);

    assertEquals(once, twice);
    assertFalse(twice.contains("httpApi:"));
  }

  @Test
  public void stripHttpApiNoopsWhenAbsent() throws Exception {
    Path dir = Files.createTempDirectory("golem-tooling-test-");
    Path yaml = dir.resolve("golem.yaml");
    String input =
      ""
        + "components:\n"
        + "  demo:component:\n"
        + "    template: ts\n"
        + "\n"
        + "dependencies:\n"
        + "  demo:component:\n";
    Files.write(yaml, input.getBytes(StandardCharsets.UTF_8));

    GolemTooling.stripHttpApiFromGolemYaml(dir, null);
    String out = new String(Files.readAllBytes(yaml), StandardCharsets.UTF_8);
    assertEquals(input, out);
  }

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


