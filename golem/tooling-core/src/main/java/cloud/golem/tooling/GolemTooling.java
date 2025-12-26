package cloud.golem.tooling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import cloud.golem.tooling.bridge.BridgeSpec;
import cloud.golem.tooling.bridge.BridgeSpecManifest;
import cloud.golem.tooling.bridge.TypeScriptBridgeGenerator;

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

  /** Filters known noisy upstream warnings from golem-cli toolchain output. */
  public static boolean isNoisyUpstreamWarning(String line) {
    return line.contains("[E0055] Warning: FFI parameter of pointer type should be annotated") ||
      line.contains("[E0027] Warning: Using a multiline string directly") ||
      // golem-cli `agent update --await` sometimes emits this as an "error" even though the update can complete
      // successfully moments later (transient status). We handle it as a soft-success in the plugins; suppress the noise
      // so scripts don't look like they "errored" while still succeeding.
      line.contains("Unexpected agent state: update is not pending anymore, but no outcome has been found") ||
      line.contains("Agent update is not pending anymore, but no outcome has been found");
  }

  /**
   * golem-cli's `component new ts` scaffold includes a sample `httpApi` section that is not usable for our components.
   * This removes that root-level YAML key block using a conservative text transformation (no YAML dependency).
   */
  public static void stripHttpApiFromGolemYaml(Path componentDir, Consumer<String> logInfo) {
    Path yaml = componentDir.resolve("golem.yaml");
    if (!Files.exists(yaml)) return;

    List<String> lines;
    try {
      lines = Files.readAllLines(yaml, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Failed reading " + yaml, e);
    }

    int start = -1;
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).startsWith("httpApi:")) {
        start = i;
        break;
      }
    }
    if (start < 0) return;

    int end = lines.size();
    for (int i = start + 1; i < lines.size(); i++) {
      String l = lines.get(i);
      if (l.matches("^[A-Za-z][A-Za-z0-9_-]*:\\s*$")) {
        end = i;
        break;
      }
    }

    List<String> updated = new ArrayList<String>(lines.size());
    updated.addAll(lines.subList(0, start));
    if (end < lines.size()) updated.addAll(lines.subList(end, lines.size()));

    StringBuilder out = new StringBuilder();
    for (String l : updated) out.append(l).append('\n');
    String content = out.toString().trim() + "\n";

    try {
      Files.write(yaml, content.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new RuntimeException("Failed writing " + yaml, e);
    }

    if (logInfo != null) logInfo.accept("[golem] Stripped scaffold httpApi section from " + yaml.toAbsolutePath());
  }

  public static void ensurePortFree(String host, int port) {
    ServerSocket socket = null;
    try {
      socket = new ServerSocket();
      socket.setReuseAddress(true);
      socket.bind(new InetSocketAddress(host, port));
    } catch (Throwable t) {
      throw new RuntimeException(
        "Port " + host + ":" + port + " is already in use; stop the existing process or set GOLEM_ROUTER_PORT to a free port.",
        t
      );
    } finally {
      if (socket != null) {
        try {
          socket.close();
        } catch (IOException ignored) {
          // ignore
        }
      }
    }
  }

  public static void waitForRouter(String host, int port, int attempts, int sleepMillis) {
    int remaining = attempts;
    boolean connected = false;
    while (!connected && remaining > 0) {
      Socket socket = new Socket();
      try {
        socket.connect(new InetSocketAddress(host, port), 1000);
        connected = true;
      } catch (Throwable ignored) {
        try {
          Thread.sleep(sleepMillis);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted while waiting for Golem router at " + host + ":" + port, ie);
        }
        remaining -= 1;
      } finally {
        try {
          socket.close();
        } catch (IOException ignored2) {
          // ignore
        }
      }
    }
    if (!connected) throw new RuntimeException("Timed out waiting for Golem router at " + host + ":" + port);
  }

  /** Ensures the given binary is present on PATH (uses {@code bash -lc "command -v <cmd>"}). */
  public static void requireCommandOnPath(String cmd, String friendly) {
    if (cmd == null || cmd.trim().isEmpty()) throw new IllegalArgumentException("cmd must be non-empty");
    String label = (friendly == null || friendly.trim().isEmpty()) ? cmd : friendly.trim();
    List<String> check = new ArrayList<String>();
    check.add("bash");
    check.add("-lc");
    check.add("command -v " + cmd.trim());
    ExecResult res = runWithTimeoutCapture(check, java.nio.file.Paths.get("."), Duration.ofSeconds(10), null, null);
    if (res.exitCode != 0) {
      throw new RuntimeException(label + " not found on PATH (looked for '" + cmd.trim() + "').");
    }
  }

  /**
   * Wires a Scala.js bundle into a deterministic TS component scaffold:
   * - copies the bundle into {@code <componentDir>/src/<bundleFileName>} (overwrites)
   * - writes {@code <componentDir>/src/main.ts} (overwrites)
   *
   * <p>Does not touch {@code src/user.ts}.</p>
   */
  public static void wireTsComponent(
    Path componentDir,
    Path scalaJsBundle,
    String bundleFileName,
    String mainTs,
    Consumer<String> logInfo
  ) {
    if (componentDir == null) throw new IllegalArgumentException("componentDir cannot be null");
    if (scalaJsBundle == null) throw new IllegalArgumentException("scalaJsBundle cannot be null");
    if (bundleFileName == null || bundleFileName.trim().isEmpty())
      throw new IllegalArgumentException("bundleFileName must be non-empty");
    if (mainTs == null || mainTs.trim().isEmpty()) throw new IllegalArgumentException("mainTs must be non-empty");

    Path srcDir = componentDir.resolve("src");
    try {
      Files.createDirectories(srcDir);
    } catch (IOException e) {
      throw new RuntimeException("Failed creating src directory: " + srcDir, e);
    }

    Path destBundle = srcDir.resolve(bundleFileName.trim());
    try {
      Files.copy(scalaJsBundle, destBundle, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new RuntimeException("Failed copying Scala.js bundle to " + destBundle, e);
    }

    Path mainTsPath = srcDir.resolve("main.ts");
    String ts = mainTs.endsWith("\n") ? mainTs : (mainTs + "\n");
    try {
      Files.write(mainTsPath, ts.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new RuntimeException("Failed writing " + mainTsPath, e);
    }

    if (logInfo != null)
      logInfo.accept(
        "[golem] Wired bundle (" + scalaJsBundle.getFileName() + " -> " + bundleFileName.trim() + ") and wrote src/main.ts"
      );
  }

  /**
   * Run a command with a hard timeout. Captures combined stdout/stderr in the returned result.
   * Returns exitCode = -999 when timed out (mirrors prior plugin behavior).
   */
  public static ExecResult runWithTimeoutCapture(
    List<String> cmd,
    Path cwd,
    Duration timeout,
    Consumer<String> logInfo,
    Consumer<String> logErr
  ) {
    if (logInfo != null) logInfo.accept("[golem] starting (cwd=" + cwd.toAbsolutePath() + "): " + join(cmd));

    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.directory(cwd.toFile());

    final StringBuilder out = new StringBuilder();

    Process proc;
    try {
      proc = pb.start();
    } catch (IOException e) {
      throw new RuntimeException("Failed to start process: " + join(cmd), e);
    }

    Thread tOut = streamThread(proc.getInputStream(), out, logInfo);
    Thread tErr = streamThread(proc.getErrorStream(), out, logErr);

    boolean finished;
    try {
      finished = proc.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      proc.destroyForcibly();
      return new ExecResult(-999, out.toString(), true);
    }

    if (!finished) {
      if (logErr != null) logErr.accept("[golem] timed out after " + timeout.getSeconds() + "s; killing process");
      proc.destroyForcibly();
      try {
        proc.waitFor(2, TimeUnit.SECONDS);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
      // best-effort join reader threads
      joinQuietly(tOut, 200);
      joinQuietly(tErr, 200);
      return new ExecResult(-999, out.toString(), true);
    }

    int exit = proc.exitValue();
    joinQuietly(tOut, 2000);
    joinQuietly(tErr, 2000);
    return new ExecResult(exit, out.toString(), false);
  }

  private static Thread streamThread(final InputStream is, final StringBuilder sink, final Consumer<String> log) {
    Thread t = new Thread(() -> {
      BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
      try {
        String line;
        while ((line = br.readLine()) != null) {
          sink.append(line).append('\n');
          if (log != null && !isNoisyUpstreamWarning(line)) log.accept(line);
        }
      } catch (IOException ignored) {
        // ignore
      }
    }, "golem-tooling-stream");
    t.setDaemon(true);
    t.start();
    return t;
  }

  private static void joinQuietly(Thread t, long millis) {
    if (t == null) return;
    try {
      t.join(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static String join(List<String> parts) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.size(); i++) {
      if (i > 0) sb.append(' ');
      sb.append(parts.get(i));
    }
    return sb.toString();
  }

  /**
   * Deterministic TypeScript component scaffold.
   *
   * <p>Creates missing files and directories; never overwrites existing user content.</p>
   *
   * <p>Returns the component directory: {@code <appDir>/components-ts/<componentSlug>}</p>
   */
  public static Path ensureTsComponentScaffold(Path appDir, String componentQualified, Consumer<String> logInfo) {
    if (appDir == null) throw new IllegalArgumentException("appDir cannot be null");
    if (componentQualified == null || componentQualified.trim().isEmpty())
      throw new IllegalArgumentException("componentQualified must be non-empty");

    String slug = componentQualified.trim().replace(":", "-");
    Path componentDir = appDir.resolve("components-ts").resolve(slug);
    Path srcDir = componentDir.resolve("src");

    try {
      Files.createDirectories(srcDir);
    } catch (IOException e) {
      throw new RuntimeException("Failed creating component directories under " + componentDir, e);
    }

    // Default golem.yaml for a TS component.
    Path golemYaml = componentDir.resolve("golem.yaml");
    if (!Files.exists(golemYaml)) {
      String yaml =
        ""
          + "components:\n"
          + "  " + componentQualified + ":\n"
          + "    template: ts\n"
          + "\n"
          + "dependencies:\n"
          + "  " + componentQualified + ":\n";
      writeUtf8IfMissing(golemYaml, yaml, logInfo);
    }

    // Rollup config expected by the shared ts template (run via `npx rollup -- -c`).
    Path rollupConfig = componentDir.resolve("rollup.config.mjs");
    if (!Files.exists(rollupConfig)) {
      String ts =
        ""
          + "import componentRollupConfig from \"../../common-ts/rollup.config.component.mjs\";\n"
          + "\n"
          + "export default componentRollupConfig(\"" + escapeJson(slug) + "\");\n";
      writeUtf8IfMissing(rollupConfig, ts, logInfo);
    }

    // Default TypeScript config (match golem-cli scaffold; extends common-ts config).
    Path tsconfig = componentDir.resolve("tsconfig.json");
    String tsconfigContent =
      ""
        + "{\n"
        + "  \"$schema\": \"https://json.schemastore.org/tsconfig\",\n"
        + "  \"extends\": \"../../common-ts/tsconfig.component.json\",\n"
        + "  \"include\": [\n"
        + "    \"src/**/*.ts\",\n"
        + "    \".agent/**/*\",\n"
        + "    \"../../../.metadata/**/*\"\n"
        + "  ]\n"
        + "}\n";
    if (!Files.exists(tsconfig)) {
      writeUtf8IfMissing(tsconfig, tsconfigContent, logInfo);
    } else {
      // Upgrade older deterministic scaffold that didn't extend the common decorators config.
      try {
        String existing = new String(Files.readAllBytes(tsconfig), StandardCharsets.UTF_8);
        if (!existing.contains("\"extends\": \"../../common-ts/tsconfig.component.json\"")) {
          Files.write(tsconfig, tsconfigContent.getBytes(StandardCharsets.UTF_8));
          if (logInfo != null) logInfo.accept("[golem] Updated deterministic component tsconfig.json at " + tsconfig.toAbsolutePath());
        }
      } catch (IOException e) {
        throw new RuntimeException("Failed reading/writing component tsconfig " + tsconfig.toAbsolutePath(), e);
      }
    }

    // Minimal package.json (match golem-cli scaffold).
    Path pkg = componentDir.resolve("package.json");
    if (!Files.exists(pkg)) {
      String json =
        ""
          + "{\n"
          + "  \"name\": \"" + escapeJson(slug) + "\"\n"
          + "}\n";
      writeUtf8IfMissing(pkg, json, logInfo);
    }

    // Placeholder bridge. The plugins will overwrite src/main.ts during golemWire.
    Path mainTs = srcDir.resolve("main.ts");
    if (!Files.exists(mainTs)) {
      String ts =
        ""
          + "// This file is generated/overwritten by tooling (golemWire).\n"
          + "export {};\n";
      writeUtf8IfMissing(mainTs, ts, logInfo);
    }

    // User extension point. This file is never overwritten by tooling and can be imported from main.ts.
    Path userTs = srcDir.resolve("user.ts");
    if (!Files.exists(userTs)) {
      String ts =
        ""
          + "// Optional user extension point.\n"
          + "// This file is NOT overwritten by tooling; you may add custom agents, helpers, or side effects here.\n"
          + "export {};\n";
      writeUtf8IfMissing(userTs, ts, logInfo);
    }

    if (logInfo != null) logInfo.accept("[golem] Ensured deterministic TS component scaffold at " + componentDir.toAbsolutePath());
    return componentDir;
  }

  /**
   * Deterministic (owned) app scaffold.
   *
   * <p>This creates the app directory and a minimal root {@code golem.yaml} if missing. It is intentionally
   * conservative: it never overwrites existing user files.</p>
   */
  public static Path ensureTsAppScaffold(Path appRoot, String appName, String componentQualified, Consumer<String> logInfo) {
    if (appRoot == null) throw new IllegalArgumentException("appRoot cannot be null");
    if (appName == null || appName.trim().isEmpty()) throw new IllegalArgumentException("appName must be non-empty");
    if (componentQualified == null || componentQualified.trim().isEmpty())
      throw new IllegalArgumentException("componentQualified must be non-empty");

    Path appDir = appRoot.resolve(appName.trim());
    try {
      Files.createDirectories(appDir.resolve("components-ts"));
    } catch (IOException e) {
      throw new RuntimeException("Failed creating app scaffold under " + appDir, e);
    }

    Path yaml = appDir.resolve("golem.yaml");
    // The TS component template relies on common "ts" template definitions. We keep these in a deterministic
    // common-ts scaffold and reference them from the root golem.yaml using includes (matching golem-cli output).
    Path commonDir = appDir.resolve("common-ts");
    Path commonSrc = commonDir.resolve("src");
    try {
      Files.createDirectories(commonSrc);
    } catch (IOException e) {
      throw new RuntimeException("Failed creating common-ts scaffold under " + commonDir, e);
    }

    Path commonYaml = commonDir.resolve("golem.yaml");
    if (!Files.exists(commonYaml)) {
      String content =
        ""
          + "# Schema for IDEA:\n"
          + "# $schema: https://schema.golem.cloud/app/golem/1.3.0/golem.schema.json\n"
          + "# Schema for vscode-yaml:\n"
          + "# yaml-language-server: $schema=https://schema.golem.cloud/app/golem/1.3.0/golem.schema.json\n"
          + "\n"
          + "# Field reference: https://learn.golem.cloud/app-manifest#field-reference\n"
          + "# Creating HTTP APIs: https://learn.golem.cloud/invoke/making-custom-apis\n"
          + "\n"
          + "templates:\n"
          + "  ts:\n"
          + "    build:\n"
          + "    - command: npx tsc --noEmit false --emitDeclarationOnly --declaration --outFile ../../golem-temp/ts-check/{{ component_name | to_kebab_case }}/main.d.ts\n"
          + "      sources:\n"
          + "      - src\n"
          + "      - ../../common-ts\n"
          + "      - tsconfig.js\n"
          + "      targets:\n"
          + "      - ../../golem-temp/ts-check/{{ component_name | to_kebab_case }}/main.d.ts\n"
          + "    - command: npx --no golem-typegen ../../../components-ts/{{ component_name | to_kebab_case }}/tsconfig.json --files ../../../components-ts/{{ component_name | to_kebab_case }}/src/**/*.ts\n"
          + "      dir: ../../golem-temp/ts-metadata/{{ component_name | to_kebab_case }}\n"
          + "      sources:\n"
          + "      - ../../../components-ts/{{ component_name | to_kebab_case }}/src\n"
          + "      targets:\n"
          + "      - .metadata\n"
          + "    - command: npx --no rollup -- -c\n"
          + "      sources:\n"
          + "      - src\n"
          + "      - ../../common-ts\n"
          + "      - rollup.config.mjs\n"
          + "      - tsconfig.js\n"
          + "      - ../../golem-temp/ts-metadata/{{ component_name | to_kebab_case }}\n"
          + "      targets:\n"
          + "      - ../../golem-temp/ts-dist/{{ component_name | to_kebab_case }}/main.js\n"
          + "    - injectToPrebuiltQuickjs: ../../node_modules/@golemcloud/golem-ts-sdk/wasm/agent_guest.wasm\n"
          + "      module: ../../golem-temp/ts-dist/{{ component_name | to_kebab_case }}/main.js\n"
          + "      moduleWasm: ../../golem-temp/agents/{{ component_name | to_snake_case }}.module.wasm\n"
          + "      into: ../../golem-temp/agents/{{ component_name | to_snake_case }}.dynamic.wasm\n"
          + "    - generateAgentWrapper: ../../golem-temp/agents/{{ component_name | to_snake_case }}.wrapper.wasm\n"
          + "      basedOnCompiledWasm: ../../golem-temp/agents/{{ component_name | to_snake_case }}.dynamic.wasm\n"
          + "    - composeAgentWrapper: ../../golem-temp/agents/{{ component_name | to_snake_case }}.wrapper.wasm\n"
          + "      withAgent: ../../golem-temp/agents/{{ component_name | to_snake_case }}.dynamic.wasm\n"
          + "      to: ../../golem-temp/agents/{{ component_name | to_snake_case }}.static.wasm\n"
          + "    sourceWit: ../../node_modules/@golemcloud/golem-ts-sdk/wasm/agent_guest.wasm\n"
          + "    generatedWit: ../../golem-temp/agents/{{ component_name | to_snake_case }}/wit-generated\n"
          + "    componentWasm: ../../golem-temp/agents/{{ component_name | to_snake_case }}.static.wasm\n"
          + "    linkedWasm: ../../golem-temp/agents/{{ component_name | to_snake_case }}.wasm\n"
          + "customCommands:\n"
          + "  ts-npm-install:\n"
          + "  - command: npm install\n";
      writeUtf8IfMissing(commonYaml, content, logInfo);
    }

    Path commonRollup = commonDir.resolve("rollup.config.component.mjs");
    if (!Files.exists(commonRollup)) {
      String content =
        ""
          + "import alias from \"@rollup/plugin-alias\";\n"
          + "import commonjs from \"@rollup/plugin-commonjs\";\n"
          + "import json from \"@rollup/plugin-json\";\n"
          + "import nodeResolve from \"@rollup/plugin-node-resolve\";\n"
          + "import typescript from \"@rollup/plugin-typescript\";\n"
          + "import url from \"node:url\";\n"
          + "import path from \"node:path\";\n"
          + "\n"
          + "export default function componentRollupConfig(componentName) {\n"
          + "    const dir = path.dirname(url.fileURLToPath(import.meta.url));\n"
          + "\n"
          + "    const externalPackages = (id) => {\n"
          + "        return (\n"
          + "            id === \"@golemcloud/golem-ts-sdk\" ||\n"
          + "            id.startsWith(\"golem:\")\n"
          + "        );\n"
          + "    };\n"
          + "\n"
          + "    const virtualAgentMainId = \"virtual:agent-main\";\n"
          + "    const resolvedVirtualAgentMainId = \"\\0virtual:agent-main\";\n"
          + "\n"
          + "    const virtualAgentMainPlugin = () => {\n"
          + "        return {\n"
          + "            name: \"agent-main\",\n"
          + "            resolveId(id) {\n"
          + "                if (id === virtualAgentMainId) {\n"
          + "                    return resolvedVirtualAgentMainId;\n"
          + "                }\n"
          + "            },\n"
          + "            load(id) {\n"
          + "                if (id === resolvedVirtualAgentMainId) {\n"
          + "                    return `\n"
          + "import { TypescriptTypeRegistry } from '@golemcloud/golem-ts-sdk';\n"
          + "import { Metadata } from '../../golem-temp/ts-metadata/${componentName}/.metadata/generated-types';\n"
          + "\n"
          + "TypescriptTypeRegistry.register(Metadata);\n"
          + "\n"
          + "// Using an async function to prevent rollup from reordering registration and main import.\n"
          + "export default (async () => { return await import(\"./src/main\");})();\n"
          + "`\n"
          + "                }\n"
          + "            }\n"
          + "        };\n"
          + "    }\n"
          + "\n"
          + "    return {\n"
          + "        input: virtualAgentMainId,\n"
          + "        output: {\n"
          + "            file: `../../golem-temp/ts-dist/${componentName}/main.js`,\n"
          + "            format: \"esm\",\n"
          + "            inlineDynamicImports: true,\n"
          + "            sourcemap: false,\n"
          + "        },\n"
          + "        external: externalPackages,\n"
          + "        plugins: [\n"
          + "            virtualAgentMainPlugin(),\n"
          + "            alias({\n"
          + "                entries: [\n"
          + "                    {\n"
          + "                        find: \"common\",\n"
          + "                        replacement: path.resolve(dir, \"../common-ts/src\"),\n"
          + "                    },\n"
          + "                ],\n"
          + "            }),\n"
          + "            nodeResolve({\n"
          + "                extensions: [\".mjs\", \".js\", \".node\", \".ts\"],\n"
          + "            }),\n"
          + "            commonjs({\n"
          + "                include: [\"../../node_modules/**\"],\n"
          + "            }),\n"
          + "            json(),\n"
          + "            typescript({\n"
          + "                noEmitOnError: true,\n"
          + "                include: [\n"
          + "                    \"./src/**/*.ts\",\n"
          + "                    \".agent/**/*.ts\",\n"
          + "                    \".metadata/**/*.ts\",\n"
          + "                    \"../../common-ts/src/**/*.ts\",\n"
          + "                ],\n"
          + "            }),\n"
          + "        ],\n"
          + "    };\n"
          + "}\n";
      writeUtf8IfMissing(commonRollup, content, logInfo);
    }

    Path commonTsconfig = commonDir.resolve("tsconfig.component.json");
    if (!Files.exists(commonTsconfig)) {
      String content =
        ""
          + "{\n"
          + "  \"$schema\": \"https://json.schemastore.org/tsconfig\",\n"
          + "  \"compilerOptions\": {\n"
          + "    \"skipLibCheck\": true,\n"
          + "    \"target\": \"ES2020\",\n"
          + "    \"noEmit\": true,\n"
          + "    \"lib\": [\"ES2020\"],\n"
          + "    \"types\": [\"node\"],\n"
          + "    \"moduleResolution\": \"bundler\",\n"
          + "    \"checkJs\": false,\n"
          + "    \"strict\": true,\n"
          + "    \"noUncheckedIndexedAccess\": true,\n"
          + "    \"noImplicitOverride\": true,\n"
          + "    \"resolveJsonModule\": true,\n"
          + "    \"esModuleInterop\": true,\n"
          + "    \"experimentalDecorators\": true,\n"
          + "    \"emitDecoratorMetadata\": true,\n"
          + "    \"useDefineForClassFields\": false,\n"
          + "    \"paths\": {\n"
          + "      \"common/*\": [\"../common-ts/src/*\"]\n"
          + "    }\n"
          + "  }\n"
          + "}\n";
      writeUtf8IfMissing(commonTsconfig, content, logInfo);
    }

    Path commonLib = commonSrc.resolve("lib.ts");
    if (!Files.exists(commonLib)) {
      String content =
        ""
          + "export function exampleCommonFunction() {\n"
          + "    return \"hello common\";\n"
          + "}\n";
      writeUtf8IfMissing(commonLib, content, logInfo);
    }

    // App-level package.json used by the ts template's npm install.
    Path appPkg = appDir.resolve("package.json");
    if (!Files.exists(appPkg)) {
      String content =
        ""
          + "{\n"
          + "  \"name\": \"app\",\n"
          + "  \"workspaces\": [\n"
          + "    \"common-js/*/*\",\n"
          + "    \"components-js/*/*\",\n"
          + "    \"common-ts/*/*\",\n"
          + "    \"components-ts/*/*\"\n"
          + "  ],\n"
          + "  \"dependencies\": {\n"
          + "    \"@golemcloud/golem-ts-sdk\": \"0.0.65\"\n"
          + "  },\n"
          + "  \"devDependencies\": {\n"
          + "    \"@rollup/plugin-alias\": \"^5.1.1\",\n"
          + "    \"@rollup/plugin-node-resolve\": \"^16.0.1\",\n"
          + "    \"@rollup/plugin-typescript\": \"^12.1.4\",\n"
          + "    \"@rollup/plugin-commonjs\": \"^28.0.6\",\n"
          + "    \"@rollup/plugin-json\": \"^6.1.0\",\n"
          + "    \"@types/node\": \"^24.3.1\",\n"
          + "    \"rollup\": \"^4.50.1\",\n"
          + "    \"tslib\": \"^2.8.1\",\n"
          + "    \"typescript\": \"^5.9.2\",\n"
          + "    \"@golemcloud/golem-ts-typegen\": \"0.0.65\"\n"
          + "  }\n"
          + "}\n";
      writeUtf8IfMissing(appPkg, content, logInfo);
    }

    // Root manifest should only include common + components (no component definitions here),
    // otherwise golem-cli sees duplicate component definitions.
    String rootYamlContent =
      ""
        + "# Schema for IDEA:\n"
        + "# $schema: https://schema.golem.cloud/app/golem/1.3.0/golem.schema.json\n"
        + "# Schema for vscode-yaml:\n"
        + "# yaml-language-server: $schema=https://schema.golem.cloud/app/golem/1.3.0/golem.schema.json\n"
        + "\n"
        + "# Field reference: https://learn.golem.cloud/app-manifest#field-reference\n"
        + "# Creating HTTP APIs: https://learn.golem.cloud/invoke/making-custom-apis\n"
        + "\n"
        + "includes:\n"
        + "- common-*/golem.yaml\n"
        + "- components-*/*/golem.yaml\n";

    if (!Files.exists(yaml)) {
      writeUtf8IfMissing(yaml, rootYamlContent, logInfo);
    } else {
      // Upgrade older deterministic scaffold that incorrectly inlined the component definition.
      try {
        String existing = new String(Files.readAllBytes(yaml), StandardCharsets.UTF_8);
        String trimmed = existing.trim();
        if (!trimmed.startsWith("includes:") && trimmed.contains("template: ts") && trimmed.startsWith("components:")) {
          Files.write(yaml, rootYamlContent.getBytes(StandardCharsets.UTF_8));
          if (logInfo != null) logInfo.accept("[golem] Updated deterministic app golem.yaml to include-based manifest at " + yaml.toAbsolutePath());
        }
      } catch (IOException e) {
        throw new RuntimeException("Failed reading/writing app manifest " + yaml.toAbsolutePath(), e);
      }
    }

    if (logInfo != null) logInfo.accept("[golem] Ensured deterministic app scaffold at " + appDir.toAbsolutePath());
    return appDir;
  }

  /**
   * Loads a project-provided {@link Supplier} that returns a {@link BridgeSpec}, then generates {@code src/main.ts}.
   *
   * <p>This is used by build-tool plugins to support metadata-driven bridge generation without forcing the build-tool
   * itself to reference tooling-core bridge classes directly (or implement classloading logic in Scala).</p>
   *
   * @param classpathEntries classpath entries (jars/dirs) needed to load the provider
   * @param classesDir the project's compiled classes directory (typically included too, but kept explicit)
   * @param providerClassName fully-qualified provider class name
   */
  public static String generateBridgeMainTsFromProvider(
    List<Path> classpathEntries,
    Path classesDir,
    String providerClassName
  ) {
    if (providerClassName == null || providerClassName.trim().isEmpty())
      throw new IllegalArgumentException("providerClassName must be non-empty");

    List<URL> urls = new ArrayList<URL>();
    try {
      if (classpathEntries != null) {
        for (Path p : classpathEntries) {
          if (p == null) continue;
          urls.add(p.toUri().toURL());
        }
      }
      if (classesDir != null) urls.add(classesDir.toUri().toURL());
    } catch (Exception e) {
      throw new RuntimeException("Failed building classpath URLs for provider: " + providerClassName, e);
    }

    URLClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]), null);
    try {
      Class<?> providerCls = cl.loadClass(providerClassName);
      Object provider = providerCls.getDeclaredConstructor().newInstance();

      if (!(provider instanceof Supplier)) {
        // allow duck-typing for Scala/Java interop; require a get() method
        try {
          providerCls.getMethod("get");
        } catch (NoSuchMethodException e) {
          throw new RuntimeException(providerClassName + " must implement Supplier<BridgeSpec> or define get()");
        }
      }

      Object specObj;
      try {
        specObj = providerCls.getMethod("get").invoke(provider);
      } catch (Exception e) {
        throw new RuntimeException("Failed invoking " + providerClassName + ".get()", e);
      }

      // Invoke the generator in the provider classloader to avoid classloader type mismatches.
      Class<?> genCls = cl.loadClass("cloud.golem.tooling.bridge.TypeScriptBridgeGenerator");
      java.lang.reflect.Method mGen = null;
      for (java.lang.reflect.Method m : genCls.getMethods()) {
        if ("generate".equals(m.getName()) && m.getParameterTypes().length == 1) {
          mGen = m;
          break;
        }
      }
      if (mGen == null) throw new RuntimeException("Missing TypeScriptBridgeGenerator.generate(BridgeSpec) on provider classpath");
      return (String) mGen.invoke(null, specObj);
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException("Failed generating bridge from provider: " + providerClassName, e);
    } finally {
      try {
        cl.close();
      } catch (IOException ignored) {
        // ignore
      }
    }
  }

  /** Generates a Scala.js shim source file from the BridgeSpec manifest. */
  public static String generateScalaShimFromManifest(
    Path manifestPath,
    String exportTopLevel,
    String objectName,
    String packageName
  ) {
    return cloud.golem.tooling.bridge.ScalaShimGenerator.generateFromManifest(
      manifestPath,
      exportTopLevel,
      objectName,
      packageName
    );
  }

  /**
   * Generates {@code src/main.ts} from a deterministic BridgeSpec manifest file.
   *
   * <p>Manifest format: {@link BridgeSpecManifest} (.properties).</p>
   */
  public static String generateBridgeMainTsFromManifest(Path manifestPath) {
    if (manifestPath == null) throw new IllegalArgumentException("manifestPath cannot be null");
    BridgeSpec spec = BridgeSpecManifest.read(manifestPath);
    return TypeScriptBridgeGenerator.generate(spec);
  }

  /** Returns true if the provided object is a BridgeSpec with a non-empty agent list. */
  public static boolean bridgeSpecHasAgents(Object maybeBridgeSpec) {
    if (maybeBridgeSpec == null) return false;
    BridgeSpec spec = (BridgeSpec) maybeBridgeSpec;
    return spec.agents != null && !spec.agents.isEmpty();
  }

  /** Generates {@code src/main.ts} from an in-memory BridgeSpec instance. */
  public static String generateBridgeMainTsFromBridgeSpec(Object bridgeSpec) {
    if (bridgeSpec == null) throw new IllegalArgumentException("bridgeSpec cannot be null");
    return TypeScriptBridgeGenerator.generate((BridgeSpec) bridgeSpec);
  }

  private static void writeUtf8IfMissing(Path path, String content, Consumer<String> logInfo) {
    if (Files.exists(path)) return;
    try {
      Files.write(path, content.getBytes(StandardCharsets.UTF_8));
      if (logInfo != null) logInfo.accept("[golem] Created " + path.toAbsolutePath());
    } catch (IOException e) {
      throw new RuntimeException("Failed writing " + path, e);
    }
  }

  private static String escapeJson(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}



