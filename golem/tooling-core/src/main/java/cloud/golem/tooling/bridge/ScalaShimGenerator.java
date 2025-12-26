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
 * Generates a Scala.js-visible shim object (exported via {@code @JSExportTopLevel}) used by the TS template.
 *
 * <p>This replaces hand-written {@code ScalaAgents.scala}-style glue with a deterministic, compile-time generated
 * source file produced by the build-tool plugins.</p>
 *
 * <p>Input is the same {@code .properties}-style manifest used for bridge generation, with one extra key per agent:
 *
 * <pre>
 * agents.N.scalaShimImplClass=com.example.MyAgentShimImpl
 * </pre>
 *
 * The referenced class must be Scala.js-compileable and have a constructor matching the BridgeSpec constructor args.
 * Its methods should use JS-friendly shapes (e.g. {@code js.Dynamic} / primitives) matching the TS bridge types.
 */
public final class ScalaShimGenerator {
  private ScalaShimGenerator() {}

  public static String generateFromManifest(Path manifestPath, String exportTopLevel, String objectName, String packageName) {
    if (manifestPath == null) throw new IllegalArgumentException("manifestPath cannot be null");
    if (exportTopLevel == null || exportTopLevel.trim().isEmpty())
      throw new IllegalArgumentException("exportTopLevel must be non-empty");
    if (objectName == null || objectName.trim().isEmpty())
      throw new IllegalArgumentException("objectName must be non-empty");
    if (packageName == null || packageName.trim().isEmpty())
      throw new IllegalArgumentException("packageName must be non-empty");

    String content;
    try {
      if (!Files.exists(manifestPath))
        throw new RuntimeException("BridgeSpec manifest does not exist: " + manifestPath.toAbsolutePath());
      content = new String(Files.readAllBytes(manifestPath), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Failed reading BridgeSpec manifest: " + manifestPath.toAbsolutePath(), e);
    }

    Properties p = new Properties();
    try {
      p.load(new BufferedReader(new StringReader(content)));
    } catch (IOException e) {
      throw new RuntimeException("Failed parsing BridgeSpec manifest properties", e);
    }

    Set<Integer> agentIndexes = indexedPrefixes(p.stringPropertyNames(), "agents.", ".agentName");
    if (agentIndexes.isEmpty()) return "";

    StringBuilder sb = new StringBuilder();
    sb.append("package ").append(packageName.trim()).append("\n\n");
    sb.append("import cloud.golem.runtime.util.FutureInterop\n");
    sb.append("import cloud.golem.runtime.autowire.JsPlainSchemaCodec\n");
    sb.append("import scala.concurrent.Future\n");
    sb.append("import scala.scalajs.js\n");
    sb.append("import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue\n");
    sb.append("import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}\n\n");
    sb.append("@JSExportTopLevel(\"").append(escapeString(exportTopLevel.trim())).append("\")\n");
    sb.append("object ").append(objectName.trim()).append(" {\n");

    for (Integer ai : agentIndexes) {
      String base = "agents." + ai + ".";
      String scalaFactory = trimToNull(p.getProperty(base + "scalaFactory"));
      if (scalaFactory == null) continue;
      String implClass = trimToNull(p.getProperty(base + "scalaShimImplClass"));
      if (implClass == null) {
        throw new RuntimeException("Missing required key: " + base + "scalaShimImplClass");
      }

      String ctorKind = trimToNull(p.getProperty(base + "constructor.kind"));
      if (ctorKind == null) ctorKind = "noarg";

      List<Field> fields = readCtorFields(p, base + "constructor.field.");
      String scalarTsType = trimToNull(p.getProperty(base + "constructor.tsType"));
      String scalarScalaType = trimToNull(p.getProperty(base + "constructor.scalaType"));
      String scalarArgName = trimToNull(p.getProperty(base + "constructor.argName"));
      if (scalarArgName == null || !scalarArgName.matches("[A-Za-z_][A-Za-z0-9_]*")) scalarArgName = "input";
      List<Field> positionalParams = readCtorFields(p, base + "constructor.param.");

      // Emit factory signature + body.
      sb.append("  @JSExport\n");
      sb.append("  def ").append(scalaFactory).append("(");
      if ("scalar".equalsIgnoreCase(ctorKind)) {
        String scalaT = scalaTypeOrTsType(scalarScalaType, scalarTsType);
        sb.append(scalarArgName).append(": ").append(scalaT);
      } else if ("positional".equalsIgnoreCase(ctorKind) && !positionalParams.isEmpty()) {
        for (int i = 0; i < positionalParams.size(); i++) {
          Field f = positionalParams.get(i);
          if (i > 0) sb.append(", ");
          String name = (f.name == null || f.name.isEmpty()) ? ("arg" + i) : f.name;
          String scalaT = scalaTypeOrTsType(f.scalaType, f.tsType);
          sb.append(name).append(": ").append(scalaT);
        }
      } else if ("record".equalsIgnoreCase(ctorKind) && !fields.isEmpty()) {
        for (int i = 0; i < fields.size(); i++) {
          Field f = fields.get(i);
          if (i > 0) sb.append(", ");
          String scalaT = scalaTypeOrTsType(f.scalaType, f.tsType);
          sb.append(f.name).append(": ").append(scalaT);
        }
      }
      sb.append("): js.Dynamic = {\n");

      sb.append("    val impl = new ").append(implClass).append("(");
      if ("scalar".equalsIgnoreCase(ctorKind)) {
        sb.append(scalarArgName);
      } else if ("positional".equalsIgnoreCase(ctorKind) && !positionalParams.isEmpty()) {
        for (int i = 0; i < positionalParams.size(); i++) {
          if (i > 0) sb.append(", ");
          Field f = positionalParams.get(i);
          String name = (f.name == null || f.name.isEmpty()) ? ("arg" + i) : f.name;
          sb.append(name);
        }
      } else if ("record".equalsIgnoreCase(ctorKind) && !fields.isEmpty()) {
        for (int i = 0; i < fields.size(); i++) {
          Field f = fields.get(i);
          if (i > 0) sb.append(", ");
          sb.append(f.name);
        }
      }
      sb.append(")\n");

      // Methods
      Set<Integer> methodIdx = indexedPrefixes(p.stringPropertyNames(), base + "method.", ".name");
      if (methodIdx.isEmpty()) {
        sb.append("    js.Dynamic.literal()\n");
      } else {
        sb.append("    js.Dynamic.literal(\n");
        boolean first = true;
        for (Integer mi : methodIdx) {
          String mp = base + "method." + mi + ".";
          String mName = trimToNull(p.getProperty(mp + "name"));
          if (mName == null) continue;
          boolean isAsync = "true".equalsIgnoreCase(trimToNull(p.getProperty(mp + "isAsync")));
          String implMethodName = trimToNull(p.getProperty(mp + "implMethodName"));
          if (implMethodName == null || implMethodName.isEmpty()) implMethodName = mName;

          List<Param> params = readParams(p, mp + "param.");

          if (!first) sb.append(",\n");
          first = false;

          sb.append("      ").append(mName).append(" = (");
          // signature
          for (int pi = 0; pi < params.size(); pi++) {
            Param par = params.get(pi);
            if (pi > 0) sb.append(", ");
            String scalaT = scalaTypeOrTsType(par.scalaType, par.tsType);
            // For complex Scala types (case classes, etc), accept js.Any from JS and decode inside.
            if (needsDecode(scalaT)) scalaT = "js.Any";
            sb.append(par.name).append(": ").append(scalaT);
          }
          sb.append(") => ");

          // body expression
          if (isAsync) {
            sb.append("FutureInterop.toPromise(");
            sb.append("{\n");
            // decode params (if needed)
            List<String> argExprs = new ArrayList<>();
            for (int pi = 0; pi < params.size(); pi++) {
              Param par = params.get(pi);
              String scalaT = trimToNull(par.scalaType);
              if (scalaT == null) scalaT = tsTypeToScalaParamType(par.tsType);
              if (needsDecode(scalaT)) {
                String local = par.name + "Decoded";
                sb.append("        val ").append(local).append(": ").append(scalaT).append(" = ")
                  .append("JsPlainSchemaCodec.decode[").append(scalaT).append("](").append(par.name).append(")")
                  .append(".fold(err => throw js.JavaScriptException(err), identity)\n");
                argExprs.add(local);
              } else {
                argExprs.add(par.name);
              }
            }
            sb.append("        impl.").append(implMethodName).append("(");
            for (int pi = 0; pi < argExprs.size(); pi++) {
              if (pi > 0) sb.append(", ");
              sb.append(argExprs.get(pi));
            }
            sb.append(")")
              .append(".map(v => JsPlainSchemaCodec.encode(v)).asInstanceOf[Future[js.Any]]\n");
            sb.append("      }");
            sb.append(")");
          } else {
            sb.append("{ impl.").append(implMethodName).append("(");
            for (int pi = 0; pi < params.size(); pi++) {
              if (pi > 0) sb.append(", ");
              sb.append(params.get(pi).name);
            }
            sb.append(") }");
          }
        }
        sb.append("\n    )\n");
      }

      sb.append("  }\n\n");
    }

    sb.append("}\n");
    return sb.toString();
  }

  private static String tsTypeToScalaParamType(String tsType) {
    String t = trimToNull(tsType);
    if (t == null) return "js.Any";
    t = t.trim();
    if (t.equals("string")) return "String";
    if (t.equals("number")) return "Double";
    if (t.equals("boolean")) return "Boolean";
    if (t.equals("void")) return "Unit";
    if (t.endsWith("[]")) return "js.Array[js.Any]";
    if (t.contains("| null") || t.contains("| undefined")) return "js.Any";
    // Unknown / custom types are represented as plain JS objects in the TS runtime.
    return "js.Dynamic";
  }

  private static boolean needsDecode(String scalaType) {
    if (scalaType == null) return false;
    String t = scalaType.trim();
    // Only decode for "complex" Scala types; primitives and js.* are passed through.
    if (t.equals("String") || t.equals("Boolean") || t.equals("Int") || t.equals("Long") || t.equals("Double") ||
      t.equals("Float") || t.equals("Short") || t.equals("Byte") || t.equals("Unit")) return false;
    if (t.startsWith("js.")) return false;
    if (t.equals("js.Any") || t.equals("js.Array[js.Any]")) return false;
    return true;
  }

  private static String scalaTypeOrTsType(String scalaType, String tsType) {
    String st = trimToNull(scalaType);
    if (st == null) return tsTypeToScalaParamType(tsType);
    st = st.trim();
    // Keep this conservative: only allow a small safe set of Scala type spellings.
    if (st.equals("String")) return "String";
    if (st.equals("Boolean")) return "Boolean";
    if (st.equals("Double")) return "Double";
    if (st.equals("Float")) return "Float";
    if (st.equals("Int")) return "Int";
    if (st.equals("Long")) return "Long";
    if (st.equals("Short")) return "Short";
    if (st.equals("Byte")) return "Byte";
    if (st.equals("Unit")) return "Unit";
    if (st.equals("js.Any")) return "js.Any";
    if (st.equals("js.Dynamic")) return "js.Dynamic";
    if (st.equals("js.Array[js.Any]")) return "js.Array[js.Any]";
    // Unknown hint -> fallback to ts mapping
    return tsTypeToScalaParamType(tsType);
  }

  private static final class Field {
    final String name;
    final String tsType;
    final String scalaType;
    Field(String name, String tsType, String scalaType) { this.name = name; this.tsType = tsType; this.scalaType = scalaType; }
  }

  private static final class Param {
    final String name;
    final String tsType;
    final String scalaType;
    Param(String name, String tsType, String scalaType) { this.name = name; this.tsType = tsType; this.scalaType = scalaType; }
  }

  private static List<Field> readCtorFields(Properties p, String prefix) {
    Set<Integer> idx = indexedPrefixes(p.stringPropertyNames(), prefix, ".name");
    List<Field> out = new ArrayList<Field>();
    int max = maxIndex(idx);
    for (int i = 0; i <= max; i++) out.add(null);
    for (Integer i : idx) {
      String name = trimToNull(p.getProperty(prefix + i + ".name"));
      String tsType = trimToNull(p.getProperty(prefix + i + ".tsType"));
      String scalaType = trimToNull(p.getProperty(prefix + i + ".scalaType"));
      out.set(i, new Field(name == null ? ("arg" + i) : name, tsType, scalaType));
    }
    List<Field> compact = new ArrayList<Field>();
    for (Field f : out) if (f != null) compact.add(f);
    return compact;
  }

  private static List<Param> readParams(Properties p, String prefix) {
    Set<Integer> idx = indexedPrefixes(p.stringPropertyNames(), prefix, ".name");
    List<Param> out = new ArrayList<Param>();
    int max = maxIndex(idx);
    for (int i = 0; i <= max; i++) out.add(null);
    for (Integer i : idx) {
      String name = trimToNull(p.getProperty(prefix + i + ".name"));
      String tsType = trimToNull(p.getProperty(prefix + i + ".tsType"));
      String scalaType = trimToNull(p.getProperty(prefix + i + ".scalaType"));
      out.set(i, new Param(name == null ? ("arg" + i) : name, tsType, scalaType));
    }
    List<Param> compact = new ArrayList<Param>();
    for (Param ps : out) if (ps != null) compact.add(ps);
    return compact;
  }

  private static Set<Integer> indexedPrefixes(Set<String> keys, String prefix, String suffix) {
    Set<Integer> out = new TreeSet<Integer>();
    for (String k : keys) {
      if (!k.startsWith(prefix)) continue;
      String rest = k.substring(prefix.length());
      int dot = rest.indexOf('.');
      if (dot <= 0) continue;
      String idxStr = rest.substring(0, dot);
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

  private static String escapeString(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}


