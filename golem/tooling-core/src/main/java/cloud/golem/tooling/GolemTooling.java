package cloud.golem.tooling;

import cloud.golem.tooling.bridge.ScalaShimGenerator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Minimal tooling surface for the Scala SDK. */
public final class GolemTooling {

  private GolemTooling() {}

  public static String readResourceUtf8(ClassLoader loader, String path) {
    InputStream is = loader.getResourceAsStream(path);
    if (is == null) throw new RuntimeException("Missing classpath resource: " + path);
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) {
        sb.append(line).append('\n');
      }
      return sb.toString();
    } catch (IOException e) {
      throw new RuntimeException("Failed reading classpath resource: " + path, e);
    } finally {
      try {
        is.close();
      } catch (IOException ignored) {
        // ignore
      }
    }
  }

  public static void copyClasspathResourceToPath(ClassLoader loader, String resourcePath, Path dest) {
    if (loader == null) throw new IllegalArgumentException("loader must not be null");
    if (resourcePath == null || resourcePath.trim().isEmpty())
      throw new IllegalArgumentException("resourcePath must be non-empty");
    if (dest == null) throw new IllegalArgumentException("dest must not be null");

    InputStream is = loader.getResourceAsStream(resourcePath);
    if (is == null) throw new RuntimeException("Missing classpath resource: " + resourcePath);

    try {
      Path parent = dest.getParent();
      if (parent != null) Files.createDirectories(parent);
      try (OutputStream os = Files.newOutputStream(dest)) {
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) >= 0) {
          os.write(buf, 0, n);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed copying classpath resource: " + resourcePath + " to " + dest, e);
    } finally {
      try {
        is.close();
      } catch (IOException ignored) {
        // ignore
      }
    }
  }

  public static void ensureAgentGuestWasm(Path dest) {
    if (dest == null) throw new IllegalArgumentException("dest must not be null");
    if (Files.exists(dest)) return;
    try {
      copyClasspathResourceToPath(
        GolemTooling.class.getClassLoader(),
        "cloud/golem/tooling/wasm/agent_guest.wasm",
        dest
      );
    } catch (RuntimeException e) {
      // Repo-local fallback for source builds where tooling-core resources may not be on the task classloader.
      Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
      Path[] candidates =
        new Path[] {
          cwd.resolve("golem/examples/app/wasm/agent_guest.wasm"),
          cwd.resolve("golem/quickstart/app/wasm/agent_guest.wasm")
        };
      for (Path p : candidates) {
        if (Files.exists(p)) {
          try {
            Path parent = dest.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.copy(p, dest);
            return;
          } catch (IOException io) {
            throw new RuntimeException("Failed copying agent_guest.wasm from " + p + " to " + dest, io);
          }
        }
      }
      throw e;
    }
  }

  /** Filters known noisy upstream warnings. */
  public static boolean isNoisyUpstreamWarning(String line) {
    return line.contains("[E0055] Warning: FFI parameter of pointer type should be annotated") ||
      line.contains("[E0027] Warning: Using a multiline string directly") ||
      line.contains("Unexpected agent state: update is not pending anymore, but no outcome has been found") ||
      line.contains("Agent update is not pending anymore, but no outcome has been found");
  }

  /** Ensures the given binary is present on PATH (uses {@code bash -lc "command -v <cmd>"}). */
  public static void requireCommandOnPath(String cmd, String friendly) {
    if (cmd == null || cmd.trim().isEmpty()) throw new IllegalArgumentException("cmd must be non-empty");
    String label = (friendly == null || friendly.trim().isEmpty()) ? cmd.trim() : friendly.trim();

    List<String> check = new ArrayList<String>();
    check.add("bash");
    check.add("-lc");
    check.add("command -v " + cmd.trim());

    int exit = run(check, Duration.ofSeconds(10));
    if (exit != 0) {
      throw new RuntimeException(label + " not found on PATH (looked for '" + cmd.trim() + "').");
    }
  }

  /** Generates a Scala.js shim source file from the BridgeSpec manifest. */
  public static String generateScalaShimFromManifest(
    Path manifestPath,
    String exportTopLevel,
    String objectName,
    String packageName
  ) {
    return ScalaShimGenerator.generateFromManifest(manifestPath, exportTopLevel, objectName, packageName);
  }

  // ----------------------------------------------------------------------------
  // Internals
  // ----------------------------------------------------------------------------

  private static int run(List<String> cmd, Duration timeout) {
    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.directory(new java.io.File("."));
    try {
      Process proc = pb.start();
      boolean finished = proc.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
      if (!finished) {
        proc.destroyForcibly();
        proc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
        return -999;
      }
      return proc.exitValue();
    } catch (Exception e) {
      throw new RuntimeException("Failed to run command: " + String.join(" ", cmd), e);
    }
  }
}

