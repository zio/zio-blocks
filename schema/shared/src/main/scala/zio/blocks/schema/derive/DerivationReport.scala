/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.derive

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.binding.Binding
import zio.blocks.typeid.TypeId

/**
 * Report of term-level derivation overrides that matched nothing in the derived
 * schema.
 *
 * Term-level overrides (`DerivationBuilder.instance(typeId, termName, ...)` and
 * `DerivationBuilder.modifier(typeId, termName, ...)`) are silently ignored
 * when no term with the given name exists in the parent type — a typo'd field
 * name therefore reports green. Use `DerivationBuilder.derivationReport` (or
 * the `instanceChecked` / `modifierChecked` variants, which fail fast) to
 * detect such mistakes.
 */
final case class DerivationReport(
  ignoredInstanceTerms: Chunk[(TypeId[?], String)],
  ignoredModifierTerms: Chunk[(TypeId[?], String)]
) {

  /**
   * Returns true when every term-level override matched at least one term.
   */
  def isEmpty: Boolean = ignoredInstanceTerms.isEmpty && ignoredModifierTerms.isEmpty

  /**
   * Returns true when at least one term-level override matched nothing.
   */
  def nonEmpty: Boolean = !isEmpty

  /**
   * Human-readable listing of the ignored overrides, empty when `isEmpty`.
   */
  def message: String = {
    val sb = new java.lang.StringBuilder
    ignoredInstanceTerms.foreach { case (typeId, termName) =>
      if (sb.length > 0) sb.append('\n')
      sb.append("Instance override for term '")
        .append(termName)
        .append("' in type ")
        .append(typeId.fullName)
        .append(" did not match any field or case")
    }
    ignoredModifierTerms.foreach { case (typeId, termName) =>
      if (sb.length > 0) sb.append('\n')
      sb.append("Modifier override for term '")
        .append(termName)
        .append("' in type ")
        .append(typeId.fullName)
        .append(" did not match any field or case")
    }
    sb.toString
  }
}

object DerivationReport {

  /**
   * An empty report: every override matched.
   */
  val empty: DerivationReport = DerivationReport(Chunk.empty, Chunk.empty)

  /**
   * Builds a report for `schema` by checking every term-level override against
   * the (parent-type, term-name) pairs present in the schema tree. Optic- and
   * type-level overrides always match by construction and are never reported.
   */
  def forSchema[A](
    schema: Schema[A],
    instanceOverrides: IndexedSeq[InstanceOverride],
    modifierOverrides: IndexedSeq[ModifierOverride]
  ): DerivationReport = {
    val known = new scala.collection.mutable.HashSet[String]
    collectTerms(new java.util.IdentityHashMap[AnyRef, java.lang.Boolean], known)(
      schema.reflect.asInstanceOf[Reflect[Binding, ?]]
    )
    def key(typeId: TypeId[?], termName: String): String = typeId.fullName + "#" + termName
    val ignoredInstances                                 = instanceOverrides.collect {
      case InstanceOverrideByTypeAndTermName(typeId, termName, _) if !known.contains(key(typeId, termName)) =>
        (typeId, termName)
    }
    val ignoredModifiers = modifierOverrides.collect {
      case ModifierTermOverrideByType(typeId, termName, _) if !known.contains(key(typeId, termName)) =>
        (typeId, termName)
    }
    DerivationReport(Chunk.fromIterable(ignoredInstances), Chunk.fromIterable(ignoredModifiers))
  }

  private def collectTerms(
    seen: java.util.IdentityHashMap[AnyRef, java.lang.Boolean],
    known: scala.collection.mutable.HashSet[String]
  )(reflect: Reflect[Binding, ?]): Unit =
    if (!seen.containsKey(reflect)) {
      seen.put(reflect, java.lang.Boolean.TRUE)
      reflect.asRecord.foreach { record =>
        record.fields.foreach { term =>
          known.add(record.typeId.fullName + "#" + term.name)
          collectTerms(seen, known)(term.value.asInstanceOf[Reflect[Binding, ?]])
        }
      }
      reflect.asVariant.foreach { variant =>
        variant.cases.foreach { term =>
          known.add(variant.typeId.fullName + "#" + term.name)
          collectTerms(seen, known)(term.value.asInstanceOf[Reflect[Binding, ?]])
        }
      }
      reflect.asSequenceUnknown.foreach { unknown =>
        collectTerms(seen, known)(unknown.sequence.element.asInstanceOf[Reflect[Binding, ?]])
      }
      reflect.asMapUnknown.foreach { unknown =>
        collectTerms(seen, known)(unknown.map.key.asInstanceOf[Reflect[Binding, ?]])
        collectTerms(seen, known)(unknown.map.value.asInstanceOf[Reflect[Binding, ?]])
      }
      reflect.asWrapperUnknown.foreach { unknown =>
        collectTerms(seen, known)(unknown.wrapper.wrapped.asInstanceOf[Reflect[Binding, ?]])
      }
    }
}
