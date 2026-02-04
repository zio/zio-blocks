package zio.blocks.schema.json

import java.lang.Character._
import java.lang

/**
 * A sealed trait that represents a generic contract for mapping string input to
 * string output. Classes or objects that extend `NameMapper` provide an
 * implementation of the `apply` method, which specifies how a string
 * transformation is applied. This transformation can be used for enforcing
 * naming conventions or other string-based manipulations.
 */
sealed trait NameMapper extends (String => String)

/**
 * The `NameMapper` object provides a set of predefined strategies for
 * transforming string names into common naming conventions, such as snake_case,
 * camelCase, PascalCase, and kebab-case. Additionally, it allows for custom
 * transformations via the `Custom` case class.
 *
 * This object defines several `NameMapper` implementations:
 *
 *   - `SnakeCase`: Transforms strings to snake_case (e.g., "exampleName" →
 *     "example_name").
 *   - `CamelCase`: Transforms strings to camelCase (e.g., "example_name" →
 *     "exampleName").
 *   - `PascalCase`: Transforms strings to PascalCase (e.g., "example_name" →
 *     "ExampleName").
 *   - `KebabCase`: Transforms strings to kebab-case (e.g., "exampleName" →
 *     "example-name").
 *   - `Identity`: Returns the input string as-is, performing no transformation.
 *   - `Custom`: Allows for user-defined transformations by applying a given
 *     function to the input string.
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
      val sb              = new lang.StringBuilder(len)
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
    val sb                       = new lang.StringBuilder(len << 1)
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
   * A case class that provides a custom implementation of the `NameMapper`
   * trait.
   *
   * The `Custom` class allows for the transformation of strings (typically
   * member names) using a user-defined function. This transformation logic is
   * encapsulated in the function `f` provided at instantiation.
   *
   * @param f
   *   A function that defines how to transform a string. The function takes a
   *   string (e.g., a member name) as input and returns the transformed string
   *   as output.
   */
  case class Custom(f: String => String) extends NameMapper {
    override def apply(memberName: String): String = f(memberName)
  }

  /**
   * A predefined implementation of the [[NameMapper]] trait that converts a
   * given string into snake_case format by replacing transitions between
   * uppercase and lowercase letters with underscores (`_`) and converting all
   * characters to lowercase.
   *
   * For example, "memberName" would be transformed into "member_name".
   */
  case object SnakeCase extends NameMapper {
    override def apply(memberName: String): String = enforceSnakeOrKebabCase(memberName, '_')
  }

  /**
   * A `NameMapper` implementation that transforms a string into camelCase
   * format.
   *
   * This object overrides the `apply` method to enforce the camelCase
   * convention on the input string, while leaving the rest of the characters
   * unmodified, except for case adjustments as required by the camelCase
   * format.
   *
   * The transformation is applied using an internal implementation that ensures
   * the following:
   *   - Underscores (`_`) and dashes (`-`) are treated as delimiters and are
   *     removed.
   *   - The character immediately following a delimiter is converted to
   *     uppercase.
   *   - The first character is always converted to lowercase unless the input
   *     string already conforms to camelCase.
   */
  case object CamelCase extends NameMapper {
    override def apply(memberName: String): String = enforceCamelOrPascalCase(memberName, toPascal = false)
  }

  /**
   * A `NameMapper` implementation that converts string names to PascalCase.
   * PascalCase is a naming convention where each word starts with an uppercase
   * letter and is concatenated without any separators.
   */
  case object PascalCase extends NameMapper {
    override def apply(memberName: String): String = enforceCamelOrPascalCase(memberName, toPascal = true)
  }

  /**
   * A `NameMapper` implementation that converts member names to kebab-case.
   *
   * By overriding the `apply` method, this object enforces a kebab-case naming
   * convention for input strings. It uses a helper function to normalize the
   * member name, replacing uppercase letters or existing separators with
   * lowercase letters and a hyphen (`-`) as the separator.
   */
  case object KebabCase extends NameMapper {
    override def apply(memberName: String): String = enforceSnakeOrKebabCase(memberName, '-')
  }

  /**
   * An implementation of the `NameMapper` trait that performs no transformation
   * on the provided member name. The identity operation is applied, where the
   * input string is returned unchanged.
   *
   * It is a default implementation of the `NameMapper` trait, which can be used
   * in the JSON codec derivation.
   *
   * Also, this is useful in cases where there is a need to override field or
   * case names during custom codec derivation to be different from the
   * top-level one (e.g., when using a custom codec for a nested case class).
   */
  case object Identity extends NameMapper {
    override def apply(memberName: String): String = memberName
  }
}
