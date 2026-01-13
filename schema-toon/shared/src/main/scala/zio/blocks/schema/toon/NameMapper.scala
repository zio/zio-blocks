package zio.blocks.schema.toon

import java.lang.Character._

/**
 * A sealed trait that represents a generic contract for mapping string input to
 * string output. Classes or objects that extend `NameMapper` provide an
 * implementation of the `apply` method, which specifies how a string
 * transformation is applied. This transformation can be used for enforcing
 * naming conventions or other string-based manipulations in TOON serialization.
 */
sealed trait NameMapper extends (String => String) {
  def map(s: String): String = apply(s)
}

/**
 * The `NameMapper` object provides a set of predefined strategies for
 * transforming string names into common naming conventions, such as snake_case,
 * camelCase, PascalCase, and kebab-case. Additionally, it allows for custom
 * transformations via the `Custom` case class.
 *
 * Available mappers:
 *   - `SnakeCase`: Transforms strings to snake_case (e.g., "exampleName" →
 *     "example_name")
 *   - `CamelCase`: Transforms strings to camelCase (e.g., "example_name" →
 *     "exampleName")
 *   - `PascalCase`: Transforms strings to PascalCase (e.g., "example_name" →
 *     "ExampleName")
 *   - `KebabCase`: Transforms strings to kebab-case (e.g., "exampleName" →
 *     "example-name")
 *   - `Identity`: Returns the input string as-is
 *   - `Custom`: User-defined transformation
 */
object NameMapper {
  private[this] def enforceCamelOrPascalCase(s: String, toPascal: Boolean): String =
    if (s.indexOf('_') == -1 && s.indexOf('-') == -1) {
      if (s.isEmpty) s
      else {
        val ch      = s.charAt(0)
        val fixedCh =
          if (toPascal) toUpperCase(ch)
          else toLowerCase(ch)
        s"$fixedCh${s.substring(1)}"
      }
    } else {
      val len             = s.length
      val sb              = new StringBuilder(len)
      var i               = 0
      var isPrecedingDash = toPascal
      while (i < len) isPrecedingDash = {
        val ch = s.charAt(i)
        i += 1
        (ch == '_' || ch == '-') || {
          val fixedCh =
            if (isPrecedingDash) toUpperCase(ch)
            else toLowerCase(ch)
          sb.append(fixedCh)
          false
        }
      }
      sb.toString
    }

  private[this] def enforceSnakeOrKebabCase(s: String, separator: Char): String = {
    val len                      = s.length
    val sb                       = new StringBuilder(len << 1)
    var i                        = 0
    var isPrecedingNotUpperCased = false
    while (i < len) isPrecedingNotUpperCased = {
      val ch = s.charAt(i)
      i += 1
      if (ch == '_' || ch == '-') {
        sb.append(separator)
        false
      } else if (!isUpperCase(ch)) {
        sb.append(ch)
        true
      } else {
        if (isPrecedingNotUpperCased || i > 1 && i < len && !isUpperCase(s.charAt(i))) sb.append(separator)
        sb.append(toLowerCase(ch))
        false
      }
    }
    sb.toString
  }

  /**
   * Custom name mapper with user-defined transformation function.
   */
  case class Custom(f: String => String) extends NameMapper {
    override def apply(memberName: String): String = f(memberName)
  }

  /**
   * Converts member names to snake_case format.
   */
  case object SnakeCase extends NameMapper {
    override def apply(memberName: String): String = enforceSnakeOrKebabCase(memberName, '_')
  }

  /**
   * Converts member names to camelCase format.
   */
  case object CamelCase extends NameMapper {
    override def apply(memberName: String): String = enforceCamelOrPascalCase(memberName, toPascal = false)
  }

  /**
   * Converts member names to PascalCase format.
   */
  case object PascalCase extends NameMapper {
    override def apply(memberName: String): String = enforceCamelOrPascalCase(memberName, toPascal = true)
  }

  /**
   * Converts member names to kebab-case format.
   */
  case object KebabCase extends NameMapper {
    override def apply(memberName: String): String = enforceSnakeOrKebabCase(memberName, '-')
  }

  /**
   * Returns the input string unchanged.
   */
  case object Identity extends NameMapper {
    override def apply(memberName: String): String = memberName
  }
}
