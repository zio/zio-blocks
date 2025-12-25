# 🔍 Test Diagnosis Report - Post-Cleanup

**Data:** 2024-12-25  
**Comando Eseguito:** `sbt "project schemaJVM" test`  
**Risultato:** 34 errori di compilazione (test non eseguiti a causa di errori macro)

---

## 📊 Riepilogo Errori

| Categoria | Count | Severità |
|-----------|-------|----------|
| ☠️ **Errori Critici (Macro/Runtime)** | 12 | CRITICA |
| 🔴 **Regressioni (Batch 1-3)** | 8 | ALTA |
| 🟡 **Feature Mancanti (Batch 4)** | 14 | MEDIA |

---

## ☠️ Errori Critici (Macro/Runtime)

### 1. `AssertionError: Expected fun.tpe to widen into a MethodType`
**Location:** `IntoAsVersionSpecific.scala:585` in `generateTupleConstruction`

**Test Affetti:**
- `IntoSpec.scala:108` - `RGB -> ColorTuple` (Case class → Tuple)
- `CaseClassToTupleSpec.scala:14` - `Point -> PointTuple` (Case class → Tuple)
- `TupleToTupleSpec.scala:36` - `Tuple1 -> Tuple2` (Tuple → Tuple)

**Root Cause:** Problema nella costruzione della Lambda per tuple construction. Il tipo del metodo non viene riconosciuto correttamente.

**Impatto:** 🔴 **REGRESSIONE** - Batch 1 (Products & Tuples) - Test che prima passavano ora falliscono.

---

### 2. `ExprCastException` - Type Mismatch in Collection Derivation
**Location:** `IntoAsVersionSpecific.scala:64` - `derivedIntoImpl` dopo `deriveCollectionInto`

**Pattern:** La macro genera `Into[ElementType, ElementType]` invece di `Into[Collection[ElementType], Collection[ElementType]]`

**Test Affetti:**
- `IntoCollectionSpec.scala:16` - `List[Int] -> List[Long]`
  - Genera: `Into[Int, Long]`
  - Atteso: `Into[List[Int], List[Long]]`
  
- `NestedCoproductsSpec.scala:137` - `Vector[InnerEnum] -> List[InnerEnum]`
  - Genera: `Into[InnerEnum, InnerEnum]`
  - Atteso: `Into[Vector[InnerEnum], List[InnerEnum]]`
  
- `AddOptionalFieldSpec.scala:98` - `List[ItemV1] -> List[ItemV2]`
  - Genera: `Into[ItemV1, ItemV2]`
  - Atteso: `Into[List[ItemV1], List[ItemV2]]`
  
- `NumericNarrowingSpec.scala:87` - `List[Long] -> List[Int]`
  - Genera: `Into[Long, Int]`
  - Atteso: `Into[List[Long], List[Int]]`

**Root Cause:** In `derivedIntoImpl` alla linea 64, il cast `.asExprOf[Into[A, B]]` fallisce perché `deriveCollectionInto` restituisce un tipo sbagliato.

**Impatto:** 🔴 **REGRESSIONE** - Batch 2 (NestedCoproductsSpec) e Batch 3 (Collections)

---

### 3. `AssertionError: assertion failed: TypeBounds(...)`
**Location:** `IntoAsVersionSpecific.scala:815` - Pattern matching su tipi in `findOrDeriveInto`

**Test Affetti:**
- `TypeRefinementSpec.scala:174` - Pattern matching su tipi opachi/raffinati
- `EitherCoercionSpec.scala:11` - `Either[String, Int] -> Either[String, Long]`
- `OptionCoercionSpec.scala:11` - `Option[Int] -> Option[Long]`

**Root Cause:** Problema con GADT constraints quando si fa pattern matching su tipi opachi o raffinati. Il compilatore Scala fallisce durante la costruzione dei TypeBounds.

**Impatto:** 🟡 **FEATURE MANCANTE** - Batch 4 (Evolution) - Supporto per Opaque Types e Type Refinement non completo.

---

## 🔴 Regressioni (Batch 1-3 - Test che prima passavano)

### Batch 1: Products & Tuples

1. **`IntoSpec.scala:108`** - `RGB -> ColorTuple`
   - **Errore:** `AssertionError` in `generateTupleConstruction`
   - **Status:** ❌ REGRESSIONE

2. **`CaseClassToTupleSpec.scala:14`** - `Point -> PointTuple`
   - **Errore:** `AssertionError` in `generateTupleConstruction`
   - **Status:** ❌ REGRESSIONE

3. **`TupleToTupleSpec.scala:36`** - `Tuple1 -> Tuple2`
   - **Errore:** `AssertionError` in `generateTupleConstruction`
   - **Status:** ❌ REGRESSIONE

### Batch 2: Coproducts

4. **`NestedCoproductsSpec.scala:137`** - `Vector[InnerEnum] -> List[InnerEnum]`
   - **Errore:** `ExprCastException` - Type mismatch
   - **Status:** ❌ REGRESSIONE

### Batch 3: Collections & Primitives

5. **`IntoCollectionSpec.scala:16`** - `List[Int] -> List[Long]`
   - **Errore:** `ExprCastException` - Type mismatch
   - **Status:** ❌ REGRESSIONE

6. **`NumericNarrowingSpec.scala:87`** - `List[Long] -> List[Int]`
   - **Errore:** `ExprCastException` - Type mismatch
   - **Status:** ❌ REGRESSIONE

7. **`IntoSpec.scala:188,199`** - `Raw -> Validated` (con `Age: Int`)
   - **Errore:** `Cannot derive Into[Int, Age]`
   - **Status:** ❌ REGRESSIONE (Opaque types)

8. **`IntoSpec.scala:210,222`** - `PersonV1 -> PersonV2` (con `UserId: String`)
   - **Errore:** `Cannot derive Into[String, UserId]`
   - **Status:** ❌ REGRESSIONE (Opaque types)

---

## 🟡 Feature Mancanti (Batch 4 - Test nuovi che falliscono come previsto)

### Disambiguation (Priority 4 - Position + Unique Type)

9. **`NameDisambiguationSpec.scala:87`** - `V1 -> V2` (field `years: Int` non trovato)
   - **Errore:** `Cannot find unique mapping for field 'years: scala.Int'`
   - **Status:** 🟡 FEATURE MANCANTE - Priority 4 non implementata correttamente

10. **`PositionDisambiguationSpec.scala:29`** - `V1 -> V2` (field `a: Long` non trovato)
    - **Errore:** `Cannot find unique mapping for field 'a: scala.Long'`
    - **Status:** 🟡 FEATURE MANCANTE

11. **`PositionDisambiguationSpec.scala:40`** - `V1 -> V2` (field `fullName: String` non trovato)
    - **Errore:** `Cannot find unique mapping for field 'fullName: java.lang.String'`
    - **Status:** 🟡 FEATURE MANCANTE

12. **`UniqueTypeDisambiguationSpec.scala:38`** - `V1 -> V2` (field `a: Long` non trovato)
    - **Errore:** `Cannot find unique mapping for field 'a: scala.Long'`
    - **Status:** 🟡 FEATURE MANCANTE

13. **`UniqueTypeDisambiguationSpec.scala:62`** - `PersonV1 -> PersonV2` (field `addr: AddressV2` non trovato)
    - **Errore:** `Cannot find unique mapping for field 'addr: AddressV2'`
    - **Status:** 🟡 FEATURE MANCANTE

14. **`UniqueTypeDisambiguationSpec.scala:91`** - `V1 -> V2` (field `a: Int` non trovato - ambiguità)
    - **Errore:** `Cannot find unique mapping for field 'a: scala.Int'`
    - **Status:** 🟡 FEATURE MANCANTE - Caso di ambiguità legittima

15. **`UniqueTypeDisambiguationSpec.scala:102`** - `V1 -> V2` (field `values: Vector[Int]` non trovato)
    - **Errore:** `Cannot find unique mapping for field 'values: Vector[Int]'`
    - **Status:** 🟡 FEATURE MANCANTE - Matching per collection types

16. **`FieldRenamingSpec.scala:78`** - `V1 -> V2` (field `quantity: Long` non trovato)
    - **Errore:** `Cannot find unique mapping for field 'quantity: scala.Long'`
    - **Status:** 🟡 FEATURE MANCANTE

17. **`FieldRenamingSpec.scala:88`** - `V1 -> V2` (field `a: Long` non trovato)
    - **Errore:** `Cannot find unique mapping for field 'a: scala.Long'`
    - **Status:** 🟡 FEATURE MANCANTE

18. **`NestedProductsSpec.scala:179`** - `PersonV1 -> PersonV2` (field `address: AddressV2` non trovato)
    - **Errore:** `Cannot find unique mapping for field 'address: AddressV2'`
    - **Status:** 🟡 FEATURE MANCANTE - Nested field matching

### Evolution (Opaque Types & Type Refinement)

19. **`TypeRefinementSpec.scala:34,47,60,75,93,108,117,132,149,161`** - 10 test cases
    - **Errore:** `Cannot derive Into[Int, PositiveInt]` o `Cannot derive Into[String, NonEmptyString]`
    - **Status:** 🟡 FEATURE MANCANTE - Supporto per Opaque Types con validation non completo

20. **`TypeRefinementSpec.scala:174`** - Pattern matching su tipi opachi
    - **Errore:** `AssertionError: assertion failed: TypeBounds(...)`
    - **Status:** 🟡 FEATURE MANCANTE - GADT constraints con Opaque Types

21. **`AddOptionalFieldSpec.scala:98`** - `List[ItemV1] -> List[ItemV2]`
    - **Errore:** `ExprCastException` - Type mismatch (vedi errore critico #2)
    - **Status:** 🟡 FEATURE MANCANTE (ma anche regressione per collections)

22. **`EitherCoercionSpec.scala:11`** - `Either[String, Int] -> Either[String, Long]`
    - **Errore:** `AssertionError: assertion failed: TypeBounds(...)`
    - **Status:** 🟡 FEATURE MANCANTE - Supporto per `Either` non implementato

23. **`OptionCoercionSpec.scala:11`** - `Option[Int] -> Option[Long]`
    - **Errore:** `AssertionError: assertion failed: TypeBounds(...)`
    - **Status:** 🟡 FEATURE MANCANTE - Supporto per `Option` coercione non implementato

---

## 🎯 Priorità Fix

### 🔴 PRIORITÀ ALTA (Regressioni - Fix Immediato)

1. **Fix `generateTupleConstruction` (linea 585)**
   - Problema: `AssertionError: Expected fun.tpe to widen into a MethodType`
   - Impatto: 3 test Batch 1 falliscono
   - Azione: Correggere la costruzione della Lambda per tuple

2. **Fix Collection Type Cast (linea 64)**
   - Problema: `ExprCastException` - tipo sbagliato restituito da `deriveCollectionInto`
   - Impatto: 4 test Batch 2-3 falliscono
   - Azione: Verificare che `deriveCollectionInto` restituisca `Into[Collection[A], Collection[B]]` non `Into[A, B]`

3. **Fix Opaque Types Derivation**
   - Problema: `Cannot derive Into[Int, Age]` per tipi opachi
   - Impatto: 2 test Batch 3 falliscono
   - Azione: Verificare che la logica per Opaque Types funzioni correttamente

### 🟡 PRIORITÀ MEDIA (Feature Mancanti - Batch 4)

4. **Implementare Priority 4 Disambiguation**
   - Problema: Position + Unique Type matching non funziona
   - Impatto: 10+ test Batch 4 falliscono
   - Azione: Correggere `findMatchingField` per implementare Priority 4

5. **Fix GADT Constraints per Opaque Types**
   - Problema: `AssertionError: assertion failed: TypeBounds(...)`
   - Impatto: 3 test Batch 4 falliscono
   - Azione: Evitare pattern matching problematici su tipi opachi

6. **Implementare Supporto per `Either` e `Option` Coercion**
   - Problema: Pattern matching fallisce per tipi parametrici
   - Impatto: 2 test Batch 4 falliscono
   - Azione: Aggiungere supporto esplicito per `Either` e `Option`

---

## 📝 Note Tecniche

### Pattern Comune negli Errori

1. **Type Mismatch in Collection Derivation:**
   - La macro genera correttamente `Into[Element, Element]` ma fallisce il cast a `Into[Collection[Element], Collection[Element]]`
   - Probabile causa: `deriveCollectionInto` non preserva correttamente i tipi di collezione

2. **Lambda Construction per Tuple:**
   - `generateTupleConstruction` usa `Lambda` con `MethodType` ma il tipo del metodo non viene riconosciuto
   - Probabile causa: Il tipo restituito dalla lambda non corrisponde a quello atteso

3. **GADT Constraints:**
   - Pattern matching su tipi opachi/raffinati causa errori interni del compilatore Scala
   - Probabile causa: I TypeBounds vengono costruiti in modo errato durante il pattern matching

---

## 🔗 File Coinvolti

- `schema/shared/src/main/scala-3/zio/blocks/schema/IntoAsVersionSpecific.scala`
  - Linea 64: Cast dopo `deriveCollectionInto`
  - Linea 585: Costruzione Lambda in `generateTupleConstruction`
  - Linea 815: Pattern matching su tipi in `findOrDeriveInto`
  - `deriveCollectionInto`: Restituisce tipo sbagliato
  - `findMatchingField`: Priority 4 non implementata correttamente

---

**Prossimi Passi:**
1. Fixare regressioni critiche (Priorità Alta)
2. Verificare che Batch 1-3 passino completamente
3. Implementare feature mancanti Batch 4 (Priorità Media)


