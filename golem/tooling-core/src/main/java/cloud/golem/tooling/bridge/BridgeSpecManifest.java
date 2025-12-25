package cloud.golem.tooling.bridge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Deterministic, dependency-free manifest format for {@link BridgeSpec}.
 *
 * <p>Format: Java {@code .properties}-style key/value pairs with indexed lists.</p>
 */
public final class BridgeSpecManifest {
  private BridgeSpecManifest() {}

  public static BridgeSpec read(Path path) {
    if (path == null) throw new IllegalArgumentException("path cannot be null");
    try {
      if (!Files.exists(path)) throw new RuntimeException("BridgeSpec manifest does not exist: " + path.toAbsolutePath());
      String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
      return fromProperties(content);
    } catch (IOException e) {
      throw new RuntimeException("Failed reading BridgeSpec manifest: " + path.toAbsolutePath(), e);
    }
  }

  public static void write(Path path, BridgeSpec spec) {
    if (path == null) throw new IllegalArgumentException("path cannot be null");
    String content = toProperties(spec);
    try {
      Files.createDirectories(path.toAbsolutePath().getParent());
      Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new RuntimeException("Failed writing BridgeSpec manifest: " + path.toAbsolutePath(), e);
    }
  }

  public static String toProperties(BridgeSpec spec) {
    if (spec == null) throw new IllegalArgumentException("BridgeSpec cannot be null");

    StringBuilder sb = new StringBuilder();
    sb.append("scalaBundleImport=").append(escape(spec.scalaBundleImport)).append("\n");
    sb.append("scalaAgentsExpr=").append(escape(spec.scalaAgentsExpr)).append("\n");
    sb.append("\n");

    List<AgentSpec> agents = spec.agents == null ? new ArrayList<AgentSpec>() : spec.agents;
    for (int ai = 0; ai < agents.size(); ai++) {
      AgentSpec a = agents.get(ai);
      if (a == null) continue;
      String p = "agents." + ai + ".";
      sb.append(p).append("agentName=").append(escape(a.agentName)).append("\n");
      sb.append(p).append("className=").append(escape(a.className)).append("\n");
      sb.append(p).append("scalaFactory=").append(escape(a.scalaFactory)).append("\n");

      // constructor
      if (a.constructor instanceof NoArgConstructorSpec) {
        sb.append(p).append("constructor.kind=noarg\n");
      } else if (a.constructor instanceof ScalarConstructorSpec) {
        ScalarConstructorSpec sc = (ScalarConstructorSpec) a.constructor;
        sb.append(p).append("constructor.kind=scalar\n");
        sb.append(p).append("constructor.tsType=").append(escape(sc.tsType)).append("\n");
        if (sc.scalaFactoryArgs != null) {
          for (int i = 0; i < sc.scalaFactoryArgs.size(); i++) {
            sb.append(p).append("constructor.scalaFactoryArg.").append(i).append("=")
              .append(escape(sc.scalaFactoryArgs.get(i))).append("\n");
          }
        }
      } else if (a.constructor instanceof PositionalConstructorSpec) {
        PositionalConstructorSpec pc = (PositionalConstructorSpec) a.constructor;
        sb.append(p).append("constructor.kind=positional\n");
        if (pc.params != null) {
          for (int i = 0; i < pc.params.size(); i++) {
            FieldSpec f = pc.params.get(i);
            if (f == null) continue;
            sb.append(p).append("constructor.param.").append(i).append(".name=").append(escape(f.name)).append("\n");
            sb.append(p).append("constructor.param.").append(i).append(".tsType=").append(escape(f.tsType)).append("\n");
          }
        }
        if (pc.scalaFactoryArgs != null) {
          for (int i = 0; i < pc.scalaFactoryArgs.size(); i++) {
            sb.append(p).append("constructor.scalaFactoryArg.").append(i).append("=")
              .append(escape(pc.scalaFactoryArgs.get(i))).append("\n");
          }
        }
      } else if (a.constructor instanceof RecordConstructorSpec) {
        RecordConstructorSpec rc = (RecordConstructorSpec) a.constructor;
        sb.append(p).append("constructor.kind=record\n");
        sb.append(p).append("constructor.inputTypeName=").append(escape(rc.inputTypeName)).append("\n");
        if (rc.fields != null) {
          for (int fi = 0; fi < rc.fields.size(); fi++) {
            FieldSpec f = rc.fields.get(fi);
            if (f == null) continue;
            sb.append(p).append("constructor.field.").append(fi).append(".name=").append(escape(f.name)).append("\n");
            sb.append(p).append("constructor.field.").append(fi).append(".tsType=").append(escape(f.tsType)).append("\n");
          }
        }
        if (rc.scalaFactoryArgs != null) {
          for (int i = 0; i < rc.scalaFactoryArgs.size(); i++) {
            sb.append(p).append("constructor.scalaFactoryArg.").append(i).append("=")
              .append(escape(rc.scalaFactoryArgs.get(i))).append("\n");
          }
        }
      } else {
        sb.append(p).append("constructor.kind=unknown\n");
      }

      if (a.typeDeclarations != null) {
        for (int i = 0; i < a.typeDeclarations.size(); i++) {
          sb.append(p).append("typeDecl.").append(i).append("=").append(escape(a.typeDeclarations.get(i))).append("\n");
        }
      }

      if (a.methods != null) {
        for (int mi = 0; mi < a.methods.size(); mi++) {
          MethodSpec m = a.methods.get(mi);
          if (m == null) continue;
          String mp = p + "method." + mi + ".";
          sb.append(mp).append("name=").append(escape(m.name)).append("\n");
          sb.append(mp).append("isAsync=").append(m.isAsync ? "true" : "false").append("\n");
          sb.append(mp).append("tsReturnType=").append(escape(m.tsReturnType)).append("\n");
          sb.append(mp).append("implMethodName=").append(escape(m.implMethodName)).append("\n");
          if (m.params != null) {
            for (int pi = 0; pi < m.params.size(); pi++) {
              ParamSpec ps = m.params.get(pi);
              if (ps == null) continue;
              String pp = mp + "param." + pi + ".";
              sb.append(pp).append("name=").append(escape(ps.name)).append("\n");
              sb.append(pp).append("tsType=").append(escape(ps.tsType)).append("\n");
              sb.append(pp).append("implArgExpr=").append(escape(ps.implArgExpr)).append("\n");
            }
          }
        }
      }

      sb.append("\n");
    }

    return sb.toString();
  }

  public static BridgeSpec fromProperties(String content) {
    if (content == null) throw new IllegalArgumentException("content cannot be null");
    Properties p = new Properties();
    try {
      p.load(new BufferedReader(new StringReader(content)));
    } catch (IOException e) {
      throw new RuntimeException("Failed parsing BridgeSpec manifest properties", e);
    }

    String scalaBundleImport = trimToNull(p.getProperty("scalaBundleImport"));
    if (scalaBundleImport == null) scalaBundleImport = "./scala.js";
    String scalaAgentsExpr = trimToNull(p.getProperty("scalaAgentsExpr"));
    if (scalaAgentsExpr == null) scalaAgentsExpr = "";

    Set<Integer> agentIndexes = indexedPrefixes(p.stringPropertyNames(), "agents.", ".agentName");
    List<AgentSpec> agents = new ArrayList<AgentSpec>();
    int max = maxIndex(agentIndexes);
    for (int i = 0; i <= max; i++) agents.add(null);

    for (Integer ai : agentIndexes) {
      String base = "agents." + ai + ".";
      String agentName = trimToNull(p.getProperty(base + "agentName"));
      String className = trimToNull(p.getProperty(base + "className"));
      String scalaFactory = trimToNull(p.getProperty(base + "scalaFactory"));

      String ctorKind = trimToNull(p.getProperty(base + "constructor.kind"));
      ConstructorSpec ctor;
      List<String> typeDecls = readIndexed(p, base + "typeDecl.");
      List<MethodSpec> methods = readMethods(p, base + "method.");

      if ("record".equalsIgnoreCase(ctorKind)) {
        String inputTypeName = trimToNull(p.getProperty(base + "constructor.inputTypeName"));
        List<FieldSpec> fields = readFields(p, base + "constructor.field.");
        List<String> factoryArgs = readIndexed(p, base + "constructor.scalaFactoryArg.");
        ctor = new RecordConstructorSpec(inputTypeName, fields, factoryArgs);
      } else if ("scalar".equalsIgnoreCase(ctorKind)) {
        String tsType = trimToNull(p.getProperty(base + "constructor.tsType"));
        List<String> factoryArgs = readIndexed(p, base + "constructor.scalaFactoryArg.");
        ctor = new ScalarConstructorSpec(tsType, factoryArgs);
      } else if ("positional".equalsIgnoreCase(ctorKind)) {
        List<FieldSpec> params = readFields(p, base + "constructor.param.");
        List<String> factoryArgs = readIndexed(p, base + "constructor.scalaFactoryArg.");
        ctor = new PositionalConstructorSpec(params, factoryArgs);
      } else {
        ctor = NoArgConstructorSpec.INSTANCE;
      }

      agents.set(ai, new AgentSpec(agentName, className, scalaFactory, ctor, typeDecls, methods));
    }

    // drop null holes
    List<AgentSpec> compact = new ArrayList<AgentSpec>();
    for (AgentSpec a : agents) if (a != null) compact.add(a);
    return new BridgeSpec(scalaBundleImport, scalaAgentsExpr, compact);
  }

  private static List<FieldSpec> readFields(Properties p, String prefix) {
    Set<Integer> idx = indexedPrefixes(p.stringPropertyNames(), prefix, ".name");
    List<FieldSpec> out = new ArrayList<FieldSpec>();
    int max = maxIndex(idx);
    for (int i = 0; i <= max; i++) out.add(null);
    for (Integer i : idx) {
      String name = trimToNull(p.getProperty(prefix + i + ".name"));
      String tsType = trimToNull(p.getProperty(prefix + i + ".tsType"));
      out.set(i, new FieldSpec(name, tsType));
    }
    List<FieldSpec> compact = new ArrayList<FieldSpec>();
    for (FieldSpec f : out) if (f != null) compact.add(f);
    return compact;
  }

  private static List<MethodSpec> readMethods(Properties p, String prefix) {
    Set<Integer> idx = indexedPrefixes(p.stringPropertyNames(), prefix, ".name");
    List<MethodSpec> out = new ArrayList<MethodSpec>();
    int max = maxIndex(idx);
    for (int i = 0; i <= max; i++) out.add(null);
    for (Integer i : idx) {
      String mp = prefix + i + ".";
      String name = trimToNull(p.getProperty(mp + "name"));
      boolean isAsync = "true".equalsIgnoreCase(trimToNull(p.getProperty(mp + "isAsync")));
      String tsReturnType = trimToNull(p.getProperty(mp + "tsReturnType"));
      String implMethodName = trimToNull(p.getProperty(mp + "implMethodName"));
      List<ParamSpec> params = readParams(p, mp + "param.");
      out.set(i, new MethodSpec(name, isAsync, tsReturnType, params, implMethodName));
    }
    List<MethodSpec> compact = new ArrayList<MethodSpec>();
    for (MethodSpec m : out) if (m != null) compact.add(m);
    return compact;
  }

  private static List<ParamSpec> readParams(Properties p, String prefix) {
    Set<Integer> idx = indexedPrefixes(p.stringPropertyNames(), prefix, ".name");
    List<ParamSpec> out = new ArrayList<ParamSpec>();
    int max = maxIndex(idx);
    for (int i = 0; i <= max; i++) out.add(null);
    for (Integer i : idx) {
      String pp = prefix + i + ".";
      String name = trimToNull(p.getProperty(pp + "name"));
      String tsType = trimToNull(p.getProperty(pp + "tsType"));
      String implArgExpr = trimToNull(p.getProperty(pp + "implArgExpr"));
      out.set(i, new ParamSpec(name, tsType, implArgExpr));
    }
    List<ParamSpec> compact = new ArrayList<ParamSpec>();
    for (ParamSpec ps : out) if (ps != null) compact.add(ps);
    return compact;
  }

  private static List<String> readIndexed(Properties p, String prefix) {
    Set<Integer> idx = indexedPrefixes(p.stringPropertyNames(), prefix, "");
    List<String> out = new ArrayList<String>();
    int max = maxIndex(idx);
    for (int i = 0; i <= max; i++) out.add(null);
    for (Integer i : idx) out.set(i, p.getProperty(prefix + i));
    List<String> compact = new ArrayList<String>();
    for (String s : out) if (s != null) compact.add(s);
    return compact;
  }

  private static Set<Integer> indexedPrefixes(Set<String> keys, String prefix, String suffix) {
    Set<Integer> out = new TreeSet<Integer>();
    for (String k : keys) {
      if (!k.startsWith(prefix)) continue;
      String rest = k.substring(prefix.length());
      int dot = rest.indexOf('.');
      // For list-like keys (e.g. "typeDecl.0="), there is no dot after the index.
      // For nested keys (e.g. "method.0.name="), there is.
      String idxStr =
        dot >= 0
          ? rest.substring(0, dot)
          : rest;
      if (idxStr.isEmpty()) continue;
      try {
        int idx = Integer.parseInt(idxStr);
        if (!suffix.isEmpty()) {
          if (k.equals(prefix + idx + suffix)) out.add(idx);
        } else {
          out.add(idx);
        }
      } catch (NumberFormatException ignored) {
        // ignore
      }
    }
    return out;
  }

  private static int maxIndex(Set<Integer> idx) {
    int max = -1;
    for (Integer i : idx) if (i != null && i > max) max = i;
    return max;
  }

  private static String trimToNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  private static String escape(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
  }
}