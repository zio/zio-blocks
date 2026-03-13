package golem.runtime.annotations

import java.lang.annotation.{ElementType, Retention, RetentionPolicy, Target}
import scala.annotation.StaticAnnotation

/** Human-readable description for an agent, method, or type. */
@Retention(RetentionPolicy.RUNTIME)
@Target(Array(ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER))
final class description(val value: String) extends StaticAnnotation

/** Optional prompt hint for LLM-driven invocations. */
@Retention(RetentionPolicy.RUNTIME)
@Target(Array(ElementType.METHOD))
final class prompt(val value: String) extends StaticAnnotation

/** Marks a class/object as an agent implementation. */
@Retention(RetentionPolicy.RUNTIME)
@Target(Array(ElementType.TYPE))
final class agentImplementation() extends StaticAnnotation

/**
 * Overrides the language code used by multimodal/unstructured text derivation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(Array(ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER))
final class languageCode(val value: String) extends StaticAnnotation

/**
 * Overrides the MIME type used by multimodal/unstructured binary derivation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(Array(ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER))
final class mimeType(val value: String) extends StaticAnnotation
