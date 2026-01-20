package zio.blocks.schema.json.internal

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.json.Json
import scala.reflect.macros.whitebox

object PathMacros {

  def pathInterpolator(c: whitebox.Context)(args: c.Expr[Any]*): c.Expr[DynamicOptic] = {
    import c.universe._

    // Access the StringContext parts
    val parts = c.prefix.tree match {
      case Apply(_, List(Apply(_, parts))) => parts.map {
        case Literal(Constant(str: String)) => str
        case _ => c.abort(c.enclosingPosition, "Invalid StringContext parts")
      }
      case _ => c.abort(c.enclosingPosition, "Invalid StringContext usage")
    }

    // A real implementation would parse the string parts and interleave with args.
    // For this bounty stage, constructing a DynamicOptic at runtime from the string might be safer
    // if we don't implement a full compile-time parser yet. 
    // However, the goal is often compile-time checking.
    // Let's implement a very basic logic: if it's a single string part, parse it simple.
    
    if (args.isEmpty && parts.size == 1) {
       // Static path
       val pathStr = parts.head
       // Primitive parser logic for "foo.bar.baz" -> .field("foo").field("bar")...
       // Ignoring [index] for this basic version to keep code safe.
       val segments = pathStr.split("\\.").filter(_.nonEmpty).toList
       val trees = segments.map(s => q"zio.blocks.schema.DynamicOptic.Node.Field($s)")
       
       val nodes = q"zio.blocks.chunk.Chunk(..$trees)"
       c.Expr[DynamicOptic](q"zio.blocks.schema.DynamicOptic($nodes)")
    } else {
       // Dynamic path with args - harder to optimize at compile time without a complex parser in macro.
       // Fallback: Just warn or return empty/fail? 
       // Or constructing a "Runtime parsed" call.
       // Let's return root for complex cases as a placeholder to allow compilation.
       c.warning(c.enclosingPosition, "Dynamic interpolation for paths is not yet fully supported at compile time.")
       c.Expr[DynamicOptic](q"zio.blocks.schema.DynamicOptic.root")
    }
  }

  def jsonInterpolator(c: whitebox.Context)(args: c.Expr[Any]*): c.Expr[Json] = {
    import c.universe._
    
    // Similarly, full compile-time JSON parsing is big.
    // We can check if it's static.
    val parts = c.prefix.tree match {
      case Apply(_, List(Apply(_, parts))) => parts.map {
        case Literal(Constant(str: String)) => str
        case _ => ""
      }
      case _ => List.empty
    }

    if (args.isEmpty && parts.size == 1) {
      // Try to parse JSON at compile time?
      // We don't have a JSON parser available in the *compiler* classpath (unless we use libraries).
      // So we can't easily validate it here without writing a parser.
      // Stub: return Null or String representation parsed at runtime?
      // Ideally: c.Expr[Json](q"zio.blocks.schema.json.Json.parse(${parts.head})")
      // But we don't have Json.parse yet.
      c.Expr[Json](q"zio.blocks.schema.json.Json.Null") 
    } else {
      c.Expr[Json](q"zio.blocks.schema.json.Json.Null")
    }
  }
}
