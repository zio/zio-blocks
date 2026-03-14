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

package zio.blocks.context

import zio.blocks.chunk.Chunk
import zio.blocks.typeid.TypeId

/**
 * Open-addressing hash table for type-indexed entries. Uses TypeId.Erased
 * hashCode (cached in TypeId.Impl) for bucket selection and reference equality
 * fast path for key comparison.
 *
 * The table uses linear probing with a load factor ~0.5 for fast lookups. Since
 * Context sizes are small (typically 1-10 entries), the table stays compact and
 * cache-friendly.
 */
private[context] final class ContextEntries private (
  // Parallel arrays: keys[i] / values[i] form a slot. null key = empty slot.
  private val keys: Array[TypeId.Erased],
  private val values: Array[Any],
  private val mask: Int, // capacity - 1 (capacity is always power of 2)
  val size: Int,
  // Ordered list for iteration (preserves insertion order)
  private val ordered: Array[(TypeId.Erased, Any)]
) {

  def isEmpty: Boolean = size == 0

  def get(key: TypeId.Erased): Any = {
    val h = key.hashCode()
    var i = h & mask
    while (true) {
      val k = keys(i)
      if (k == null) return null
      if ((k eq key) || k == key) return values(i)
      i = (i + 1) & mask
    }
    null // unreachable, satisfies compiler
  }

  def getBySubtype(key: TypeId.Erased): Any = {
    // First: exact hash lookup (O(1) amortized)
    val exact = get(key)
    if (exact != null) return exact
    // Fallback: linear scan for subtype relationships (rare path)
    var i = ordered.length - 1
    while (i >= 0) {
      val (k, v) = ordered(i)
      if (k.isSubtypeOf(key)) return v
      i -= 1
    }
    null
  }

  def updated(key: TypeId.Erased, value: Any): ContextEntries = {
    if (value == null)
      throw new NullPointerException(
        s"ContextEntries: cannot store null value for type ${key.fullName}. " +
          "Context uses null internally as a sentinel for missing entries."
      )
    // Check if key already exists
    val existing = get(key)

    if (existing != null) {
      // Replace existing: rebuild everything
      val newOrdered = new Array[(TypeId.Erased, Any)](ordered.length)
      var j          = 0
      var k          = 0
      while (j < ordered.length) {
        val (ok, _) = ordered(j)
        if (!((ok eq key) || ok == key)) {
          newOrdered(k) = ordered(j)
          k += 1
        }
        j += 1
      }
      newOrdered(k) = (key, value)
      ContextEntries.buildFromOrdered(newOrdered, size)
    } else {
      // Append new entry
      val newSize = size + 1

      val newOrdered = new Array[(TypeId.Erased, Any)](ordered.length + 1)
      System.arraycopy(ordered, 0, newOrdered, 0, ordered.length)
      newOrdered(ordered.length) = (key, value)

      // Check if current hash table has room (load < 0.75)
      if (newSize * 4 <= keys.length * 3) {
        // Clone and insert into existing table
        val ks   = keys.clone()
        val vs   = values.clone()
        var slot = key.hashCode() & mask
        while (ks(slot) != null) slot = (slot + 1) & mask
        ks(slot) = key
        vs(slot) = value
        new ContextEntries(ks, vs, mask, newSize, newOrdered)
      } else {
        // Grow: rebuild hash table
        ContextEntries.buildFromOrdered(newOrdered, newSize)
      }
    }
  }

  def removed(key: TypeId.Erased): ContextEntries = {
    if (get(key) == null) return this
    val newOrdered = ordered.filter { case (k, _) => !((k eq key) || k == key) }
    if (newOrdered.length == 0) ContextEntries.empty
    else ContextEntries.buildFromOrdered(newOrdered, newOrdered.length)
  }

  def pruned(retainKeys: Chunk[TypeId.Erased]): ContextEntries = {
    val keySet     = retainKeys.toSet
    val newOrdered = ordered.filter { case (k, _) => keySet.contains(k) }
    if (newOrdered.length == 0) ContextEntries.empty
    else ContextEntries.buildFromOrdered(newOrdered, newOrdered.length)
  }

  def union(that: ContextEntries): ContextEntries = {
    var result = this
    var i      = 0
    while (i < that.ordered.length) {
      val (k, v) = that.ordered(i)
      result = result.updated(k, v)
      i += 1
    }
    result
  }

  def reverseIterator: Iterator[(TypeId.Erased, Any)] = ordered.reverseIterator
}

private[context] object ContextEntries {
  // Shared static arrays for the empty singleton. These are safe because updated()
  // always clones before mutating — the originals are never written to.
  private val emptyKeys: Array[TypeId.Erased]           = new Array[TypeId.Erased](2)
  private val emptyVals: Array[Any]                     = new Array[Any](2)
  private val emptyOrdered: Array[(TypeId.Erased, Any)] = Array.empty

  val empty: ContextEntries = new ContextEntries(emptyKeys, emptyVals, 1, 0, emptyOrdered)

  def apply(entries: (TypeId.Erased, Any)*): ContextEntries = {
    var result = empty
    entries.foreach { case (k, v) => result = result.updated(k, v) }
    result
  }

  /** Build from pairs in one shot — no intermediate ContextEntries. */
  private[context] def fromPairs(pairs: Array[(TypeId.Erased, Any)]): ContextEntries = {
    if (pairs.length == 0) return empty
    var i = 0
    while (i < pairs.length) {
      val (key, value) = pairs(i)
      if (value == null)
        throw new NullPointerException(
          s"Context: cannot store null value for type ${key.fullName}. " +
            "Context uses null internally as a sentinel for missing entries."
        )
      i += 1
    }
    buildFromOrdered(pairs, pairs.length)
  }

  private[context] def buildFromOrdered(
    ordered: Array[(TypeId.Erased, Any)],
    size: Int
  ): ContextEntries = {
    // Capacity must be > size to guarantee at least one null slot for linear probing termination.
    // nextPow2(size * 2) gives load factor ~0.5, but we add a floor of size + 1 for safety
    // in case of degenerate inputs (e.g. duplicate keys passed to fromPairs).
    val cap = math.max(nextPow2(size * 2), nextPow2(size + 1))
    val msk = cap - 1
    val ks  = new Array[TypeId.Erased](cap)
    val vs  = new Array[Any](cap)

    var distinctCount = 0
    var idx           = 0
    while (idx < ordered.length) {
      val (ek, ev) = ordered(idx)
      var slot     = ek.hashCode() & msk
      // Probe for existing key (duplicate) or empty slot
      var found = false
      while (ks(slot) != null && !found) {
        if ((ks(slot) eq ek) || ks(slot) == ek) {
          // Duplicate key: overwrite value (last-write-wins)
          vs(slot) = ev
          found = true
        } else {
          slot = (slot + 1) & msk
        }
      }
      if (!found) {
        ks(slot) = ek
        vs(slot) = ev
        distinctCount += 1
      }
      idx += 1
    }

    // Build de-duplicated ordered array if there were duplicates
    val finalOrdered = if (distinctCount == ordered.length) {
      ordered
    } else {
      val seen    = new java.util.HashSet[TypeId.Erased](distinctCount * 2)
      val deduped = new Array[(TypeId.Erased, Any)](distinctCount)
      // Walk backwards keeping the last occurrence of each key, filling from the end
      // to preserve original insertion order among the surviving entries.
      var i = ordered.length - 1
      var j = distinctCount - 1
      while (i >= 0) {
        val (k, _) = ordered(i)
        if (seen.add(k)) {
          deduped(j) = ordered(i)
          j -= 1
        }
        i -= 1
      }
      deduped
    }

    new ContextEntries(ks, vs, msk, distinctCount, finalOrdered)
  }

  private def nextPow2(n: Int): Int = {
    if (n <= 2) return 2
    Integer.highestOneBit(n - 1) << 1
  }
}
