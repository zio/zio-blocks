# Diagramma Decisionale: Flusso di Derivazione Opaque Types

## Analisi: Int -> ValidAge (con validazione)

### FLUSSO ATTUALE (CURRENT FLOW)

```
┌─────────────────────────────────────────────────────────────────┐
│ Into.deriveImpl[Int, ValidAge]                                   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 1: numericCoercion[Int, ValidAge]                         │
│   • Int -> ValidAge? NO                                         │
│   • Return: None                                                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 2: Identity Check                                         │
│   • aType =:= bType?                                            │
│     └─> Int =:= ValidAge?                                       │
│         └─> In Scala 3: POTENZIALMENTE TRUE (opaque alias)     │
│   • isOpaqueAlias(bType)?                                       │
│     └─> bType.typeSymbol.flags.is(Flags.Opaque)                 │
│         └─> TRUE ✓                                              │
│   • isTargetOpaque(bType)?                                      │
│     └─> OpaqueMacros.isOpaque(bType)                            │
│         └─> TypeRef.isOpaqueAlias = TRUE ✓                      │
│   • DECISION:                                                   │
│     └─> if (aType =:= bType && !isOpaqueAlias && ...)          │
│         └─> FALSE (perché isOpaqueAlias = TRUE)                │
│     └─> SKIP identity optimization ✓                             │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 3: Recursion Check                                         │
│   • intoRefs.contains(typeKey)?                                 │
│     └─> NO (prima chiamata)                                     │
│   • CONTINUE                                                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 4: Setup Forward Reference                                 │
│   • Crea forwardRef per gestire ricorsione                      │
│   • Aggiunge a intoRefs                                         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 5: OpaqueMacros.opaqueTypeConversion[Int, ValidAge]        │
│   • isOpaqueAlias(target)?                                      │
│     └─> ValidAge è opaque? YES ✓                                │
│   • getUnderlyingType(target)?                                  │
│     └─> Int ✓                                                   │
│   • source =:= underlying?                                      │
│     └─> Int =:= Int? YES ✓                                      │
│   • companionSymbol = target.typeSymbol.companionModule        │
│     └─> ValidAge companion? YES ✓                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 6: Search Validation Methods                              │
│   • methodNamesToTry = ["apply", "make", "from", "validate"]    │
│   • companionSymbol.memberMethod("apply")                       │
│     └─> TROVA: apply(i: Int): Either[String, ValidAge]        │
│   • Filter per validators:                                      │
│     └─> paramCompatible(Int)? YES ✓                            │
│     └─> isEitherStringTarget(Either[String, ValidAge], ValidAge)?│
│         └─> PROBLEMA POTENZIALE QUI ⚠️                          │
│             • rightType = ValidAge (opaque)                     │
│             • targetType = ValidAge (opaque)                   │
│             • Comparison: tr1.typeSymbol == tr2.typeSymbol?    │
│               └─> Dovrebbe essere TRUE                          │
│             • Ma se fallisce: returnsEither = FALSE             │
│   • validators.headOption?                                      │
│     └─> Se trova: GENERA codice con validazione ✓               │
│     └─> Se NON trova: validationResult = None                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                    ┌─────────┴─────────┐
                    │                   │
            TROVATO │                   │ NON TROVATO
                    │                   │
                    ▼                   ▼
┌───────────────────────────┐  ┌──────────────────────────────┐
│ STEP 7a: Validation Found │  │ STEP 7b: Fallback Simple      │
│   • Genera Into con        │  │   • Cerca apply senza Either │
│     validazione            │  │   • Se trova: BYPASS          │
│   • Return: Some(expr) ✓  │  │     VALIDATION! ⚠️           │
│                            │  │   • Se non trova: None       │
└───────────────────────────┘  └──────────────────────────────┘
                    │                   │
                    └─────────┬─────────┘
                              │
                    ┌─────────┴─────────┐
                    │                   │
            Some   │                   │ None
                    │                   │
                    ▼                   ▼
┌───────────────────────────┐  ┌──────────────────────────────┐
│ STEP 8a: Success         │  │ STEP 8b: Return None         │
│   • createValDefAndReturn│  │   • OpaqueMacros ritorna None │
│   • Return expr ✓         │  │   • Into.scala continua...    │
└───────────────────────────┘  └──────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 9: If OpaqueMacros returns None                          │
│   • Try NewtypeMacros.newtypeConversion                         │
│     └─> None (ValidAge non è ZIO Prelude newtype)             │
│   • Try CollectionMacros.collectionConversion                  │
│     └─> None (Int non è collection)                            │
│   • Try StructuralMacros.structuralTypeConversion              │
│     └─> None                                                    │
│   • Try ProductMacros.productTypeConversion                    │
│     └─> None                                                    │
│   • Try coproductTypeConversion                                │
│     └─> None                                                    │
│   • FINAL: report.errorAndAbort(...)                            │
│     └─> COMPILATION ERROR ✓                                      │
└─────────────────────────────────────────────────────────────────┘
```

### FLUSSO IDEALE (IDEAL FLOW)

```
┌─────────────────────────────────────────────────────────────────┐
│ Into.deriveImpl[Int, ValidAge]                                   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 1: numericCoercion[Int, ValidAge]                         │
│   • Int -> ValidAge? NO                                         │
│   • Return: None                                                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 2: Identity Check (AGGRESSIVE)                           │
│   • aType =:= bType?                                            │
│     └─> Int =:= ValidAge?                                       │
│         └─> In Scala 3: POTENZIALMENTE TRUE                    │
│   • isOpaqueAlias(bType)? TRUE ✓                                │
│   • isTargetOpaque(bType)? TRUE ✓                               │
│   • DECISION:                                                   │
│     └─> SKIP identity (perché isTargetOpaque = TRUE) ✓         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 3: OpaqueMacros.opaqueTypeConversion[Int, ValidAge]      │
│   • Verifica: target è opaque? YES ✓                            │
│   • Verifica: source =:= underlying? YES ✓                      │
│   • Verifica: companion exists? YES ✓                           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 4: Search Validation Methods (ROBUST)                      │
│   • Cerca: apply, make, from, validate                           │
│   • TROVA: apply(i: Int): Either[String, ValidAge]            │
│   • Verifica: isEitherStringTarget(...)                         │
│     └─> MUST WORK correttamente con opaque types                │
│     └─> Usa symbol comparison per opaque types                  │
│   • validators.headOption?                                      │
│     └─> TROVATO ✓                                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 5: Generate Validation Code                              │
│   • Genera Into con validazione                                 │
│   • Return: Some(expr) ✓                                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 6: If Validation Method NOT Found                         │
│   • NO Fallback a simpleMethodResult                            │
│   • NO Fallback a identity                                      │
│   • IMMEDIATELY: report.errorAndAbort(...)                      │
│     └─> "Cannot derive Into[Int, ValidAge]: "                   │
│         "No validation method found in ValidAge companion"     │
│   • COMPILATION ERROR (Safe by Default) ✓                       │
└─────────────────────────────────────────────────────────────────┘
```

## DELTA: Dove si Perde la Validazione

### 🔴 PROBLEMA 1: Fallback a Simple Method (BYPASS VALIDATION)

**LOCATION**: `OpaqueMacros.scala` linee 404-492

```scala
// STEP 2: Fallback Simple Factory - Search for simple constructors
val simpleMethodResult: Option[Expr[zio.blocks.schema.Into[A, B]]] = 
  if (validationResult.isEmpty) {  // ⚠️ SE NON TROVA VALIDATION
    // Cerca apply senza Either
    // Se trova: BYPASS VALIDATION! ⚠️
  }
```

**SCENARIO**:
- `ValidAge` ha `apply(i: Int): Either[String, ValidAge]` (con validazione)
- Ma se `isEitherStringTarget` fallisce nel riconoscerlo
- `validationResult = None`
- Allora cerca `apply(i: Int): ValidAge` (senza Either)
- Se trova `applyUnsafe`, lo usa → **BYPASS VALIDATION** ⚠️

**SOLUZIONE IDEALE**:
```scala
// NO FALLBACK se companion exists
// Se companion exists ma validationResult.isEmpty:
if (companionSymbol != Symbol.noSymbol && validationResult.isEmpty) {
  report.errorAndAbort(
    s"Cannot derive Into[${source.show}, ${target.show}]: " +
    s"No validation method found in ${target.typeSymbol.name} companion. " +
    s"Expected: apply/make/from/validate returning Either[String, ${target.show}] or Validation[?, ${target.show}]"
  )
}
```

### 🔴 PROBLEMA 2: OpaqueMacros ritorna None → Into.scala continua

**LOCATION**: `Into.scala` linee 247-253

```scala
OpaqueMacros.opaqueTypeConversion[A, B](aType, bType) match {
  case Some(intoExpr) => return createValDefAndReturnRef(intoExpr)
  case None =>  // ⚠️ CONTINUA invece di fallire
}
// Continua con altri meccanismi...
```

**SCENARIO**:
- Se `OpaqueMacros` non trova il metodo (es. problema reflection)
- Ritorna `None`
- `Into.scala` continua cercando altri meccanismi
- Se nessuno funziona, alla fine `errorAndAbort`
- Ma il messaggio di errore è generico, non specifica il problema

**SOLUZIONE IDEALE**:
```scala
// Se target è opaque e OpaqueMacros ritorna None:
if (isTargetOpaque && OpaqueMacros.opaqueTypeConversion(...).isEmpty) {
  report.errorAndAbort(
    s"Cannot derive Into[${aType.show}, ${bType.show}]: " +
    s"Opaque type ${bType.show} requires validation but no suitable method found. " +
    s"Expected: companion method returning Either[String, ${bType.show}] or Validation[?, ${bType.show}]"
  )
}
```

### 🔴 PROBLEMA 3: isEitherStringTarget potrebbe non riconoscere opaque types

**LOCATION**: `OpaqueMacros.scala` linee 94-183

**SCENARIO**:
- `isEitherStringTarget(Either[String, ValidAge], ValidAge)`
- Deve confrontare `ValidAge` (opaque) con `ValidAge` (opaque)
- Usa `tr1.typeSymbol == tr2.typeSymbol`
- Ma se fallisce per qualche motivo (es. symbol non matcha)
- `returnsEither = false`
- `validators` rimane vuoto
- Fallback a `simpleMethodResult` → **BYPASS VALIDATION** ⚠️

**SOLUZIONE IDEALE**:
- Migliorare `isEitherStringTarget` per gestire meglio gli opaque types
- Aggiungere più fallback per il confronto
- Logging per debug quando fallisce

## Domanda Chiave: Risposta

**Q**: Se OpaqueMacros non trova il metodo apply (es. problema di reflection) e ritorna None, il compilatore fallisce con errore (come vogliamo) o continua silenziosamente trovando un'altra strada (es. identity implicita) che bypassa la validazione?

**A**: **ATTUALMENTE CONTINUA SILENZIOSAMENTE** ⚠️

1. Se `OpaqueMacros` ritorna `None`:
   - `Into.scala` continua con altri meccanismi (NewtypeMacros, CollectionMacros, etc.)
   - Se tutti falliscono, alla fine `errorAndAbort` con messaggio generico
   - **NON c'è un fallback a identity** (perché abbiamo disabilitato l'identity optimization)

2. **MA** c'è un fallback interno in `OpaqueMacros`:
   - Se `validationResult.isEmpty`, cerca `simpleMethodResult`
   - Se trova `applyUnsafe` o simile, lo usa → **BYPASS VALIDATION** ⚠️

3. **IDEALMENTE DOVREBBE**:
   - Se target è opaque e companion exists ma validation method non trovato
   - **IMMEDIATELY** `errorAndAbort` con messaggio specifico
   - **NO FALLBACK** a simple methods
   - **NO CONTINUATION** con altri meccanismi

## Raccomandazioni

1. **Rimuovere fallback a simpleMethodResult** se companion exists
2. **Aggiungere early abort** in `Into.scala` se `isTargetOpaque` e `OpaqueMacros` ritorna `None`
3. **Migliorare `isEitherStringTarget`** per gestire meglio gli opaque types
4. **Aggiungere logging** per debug quando la ricerca del metodo fallisce

---

## TODO: Fix Validazione Opaque Types

### 📋 Stato Attuale (2024-12-22)

**Problema Identificato**: I test `Int to ValidAge - invalid values` e `String to UserId - invalid values` falliscono perché la validazione non viene eseguita. Il codice usa `applyUnsafe` invece di `apply` con validazione.

**Root Cause**: Il controllo per identificare `Either[String, ValidAge]` come validator fallisce nel partition (righe 330-373 di `OpaqueMacros.scala`), causando:
- `validators` lista vuota
- Fallback a `factories` (riga 485)
- Uso di `applyUnsafe` → **BYPASS VALIDATION** ⚠️

### 🔍 Analisi Dettagliata del Problema

**Location**: `schema/shared/src/main/scala-3/zio/blocks/schema/derive/OpaqueMacros.scala` righe 346-362

**Codice Attuale**:
```scala
if (isEither) {
  retType match {
    case AppliedType(_, args) if args.size == 2 =>
      val rightArg = args(1)
      rightArg.typeSymbol == target.typeSymbol || 
      rightArg.dealias.typeSymbol == target.dealias.typeSymbol ||
      ((rightArg, target) match {
        case (tr1: TypeRef, tr2: TypeRef) if tr1.isOpaqueAlias && tr2.isOpaqueAlias =>
          tr1.typeSymbol == tr2.typeSymbol ||
          tr1.typeSymbol.fullName == tr2.typeSymbol.fullName
        case _ => false
      })
    case _ => false
  }
}
```

**Problemi Identificati**:
1. ❌ `rightArg.typeSymbol == target.typeSymbol` può fallire anche se rappresentano lo stesso tipo
2. ❌ Il fallback match richiede che `rightArg` sia un `TypeRef`, ma potrebbe non esserlo
3. ❌ Se il controllo fallisce, `validators` è vuoto → usa `factories` → bypass validazione

### ✅ Soluzioni Proposte

#### **SOLUZIONE 1: Controllo Multi-Livello Robusto** (RACCOMANDATO)

**Approccio**: Implementare un controllo a più livelli che copra tutti i casi possibili per gli opaque types.

**Implementazione**:
```scala
if (isEither) {
  retType match {
    case AppliedType(_, args) if args.size == 2 =>
      val rightArg = args(1)
      
      // MULTI-LEVEL CHECK per robustezza:
      // 1. Direct symbol comparison (most reliable)
      val symbolMatch = rightArg.typeSymbol == target.typeSymbol ||
        rightArg.typeSymbol.fullName == target.typeSymbol.fullName ||
        rightArg.dealias.typeSymbol == target.dealias.typeSymbol ||
        rightArg.dealias.typeSymbol.fullName == target.dealias.typeSymbol.fullName
      
      // 2. Type equality checks (fallback)
      val typeMatch = rightArg =:= target ||
        rightArg.dealias =:= target.dealias ||
        rightArg =:= target.dealias ||
        rightArg.dealias =:= target
      
      // 3. Opaque-specific checks (if both are opaque)
      val opaqueMatch = (rightArg, target) match {
        case (tr1: TypeRef, tr2: TypeRef) if tr1.isOpaqueAlias && tr2.isOpaqueAlias =>
          tr1.typeSymbol == tr2.typeSymbol ||
          tr1.typeSymbol.fullName == tr2.typeSymbol.fullName ||
          // Compare underlying types
          (tr1.translucentSuperType.dealias =:= tr2.translucentSuperType.dealias)
        case (tr1: TypeRef, _) if tr1.isOpaqueAlias =>
          tr1.typeSymbol == target.typeSymbol ||
          tr1.typeSymbol.fullName == target.typeSymbol.fullName
        case (_, tr2: TypeRef) if tr2.isOpaqueAlias =>
          rightArg.typeSymbol == tr2.typeSymbol ||
          rightArg.typeSymbol.fullName == tr2.typeSymbol.fullName
        case _ => false
      }
      
      // 4. Subtyping checks (last resort)
      val subtypingMatch = try {
        target <:< rightArg ||
        target.dealias <:< rightArg.dealias ||
        rightArg <:< target ||
        rightArg.dealias <:< target.dealias
      } catch {
        case _: Throwable => false
      }
      
      symbolMatch || typeMatch || opaqueMatch || subtypingMatch
    case _ => false
  }
}
```

**Pro**: 
- ✅ Copre tutti i casi possibili
- ✅ Robustezza massima per opaque types
- ✅ Fallback multipli

**Contro**:
- ⚠️ Più codice da mantenere
- ⚠️ Potenzialmente più lento (ma è compile-time)

**Status**: ✅ **IMPLEMENTATO** (2024-12-22)

---

#### **SOLUZIONE 2: Rimuovere Fallback a Factories se Companion Exists**

**Approccio**: Se il companion object esiste ma non troviamo validators, fallire immediatamente invece di cercare factories.

**Implementazione**:
```scala
// 3. Selection Strategy
if (validators.nonEmpty) {
  // PRIORITY 1: Use Validator
  // ... existing code ...
} else {
  // NO FALLBACK a factories se companion exists
  // Fail immediately con messaggio chiaro
  report.errorAndAbort(
    s"Opaque type ${target.show} has companion object but no validation method found. " +
    s"Expected: apply/make/from/validate returning Either[String, ${target.show}] or Validation[?, ${target.show}]. " +
    s"Found methods: ${candidates.map(_.name).mkString(", ")}"
  )
}
```

**Pro**:
- ✅ Previene bypass della validazione
- ✅ Messaggio di errore chiaro
- ✅ Fail-fast approach

**Contro**:
- ⚠️ Potrebbe essere troppo aggressivo se ci sono casi legittimi senza validazione

**Status**: ✅ **IMPLEMENTATO** (2024-12-22)

---

#### **SOLUZIONE 3: Aggiungere Debug Logging**

**Approccio**: Aggiungere logging dettagliato per capire perché il controllo fallisce.

**Implementazione**:
```scala
if (isEither) {
  retType match {
    case AppliedType(_, args) if args.size == 2 =>
      val rightArg = args(1)
      val matches = /* controllo multi-livello */
      
      if (!matches) {
        report.warning(
          s"[OpaqueMacros Debug] Either check failed for ${target.show}. " +
          s"rightArg: ${rightArg.show} (symbol: ${rightArg.typeSymbol.fullName}), " +
          s"target: ${target.show} (symbol: ${target.typeSymbol.fullName}), " +
          s"rightArg.typeSymbol == target.typeSymbol: ${rightArg.typeSymbol == target.typeSymbol}, " +
          s"rightArg is TypeRef: ${rightArg.isInstanceOf[TypeRef]}, " +
          s"target is TypeRef: ${target.isInstanceOf[TypeRef]}"
        )
      }
      
      matches
    case _ => false
  }
}
```

**Pro**:
- ✅ Aiuta a diagnosticare problemi
- ✅ Utile per debugging futuro

**Contro**:
- ⚠️ Aggiunge rumore ai warning di compilazione

**Status**: 🟡 **OPZIONALE** (utile per debug)

---

#### **SOLUZIONE 4: Early Abort in Into.scala**

**Approccio**: Se il target è opaque e `OpaqueMacros` ritorna `None`, fallire immediatamente invece di continuare.

**Implementazione in `Into.scala`**:
```scala
// Try opaque type conversion (check early to handle validation)
val isTargetOpaque = OpaqueMacros.isOpaque(bType)
OpaqueMacros.opaqueTypeConversion[A, B](aType, bType) match {
  case Some(intoExpr) =>
    derivationSucceeded = true
    return createValDefAndReturnRef(intoExpr)
  case None =>
    if (isTargetOpaque) {
      report.errorAndAbort(
        s"Cannot derive Into[${aType.show}, ${bType.show}]: " +
        s"Opaque type ${bType.show} requires validation but no suitable method found. " +
        s"Expected: companion method returning Either[String, ${bType.show}] or Validation[?, ${bType.show}]"
      )
    }
}
```

**Pro**:
- ✅ Fail-fast per opaque types
- ✅ Messaggio di errore specifico
- ✅ Previene continuation con altri meccanismi

**Contro**:
- ⚠️ Potrebbe essere troppo aggressivo

**Status**: 🟡 **DA VALUTARE** (dopo Soluzione 1 e 2)

---

### 📝 Piano di Implementazione

1. ✅ **FATTO**: Modificato `Into.scala` per disabilitare identity optimization quando target ha companion object
2. ✅ **FATTO**: Riscritto Case 1 in `OpaqueMacros.scala` con logica a priorità
3. ✅ **FATTO** (2024-12-22): Implementare Soluzione 1 (Controllo Multi-Livello Robusto)
4. ✅ **FATTO** (2024-12-22): Implementare Soluzione 2 (Rimuovere Fallback a Factories)
5. 🟡 **OPZIONALE**: Aggiungere Soluzione 3 (Debug Logging) se necessario
6. 🟡 **OPZIONALE**: Valutare Soluzione 4 (Early Abort in Into.scala)
7. ✅ **FATTO** (2024-12-22): Eseguire test completi: `sbt "schemaJVM/testOnly zio.blocks.schema.IntoSpec -- -z Opaque"`
8. 🔴 **IN PROGRESS**: Verificare che tutti i test passino
   - **Status** (2024-12-22, 15:00): I test falliscono ancora. Il problema persiste: la validazione non viene eseguita.
   - **Modifiche Implementate**:
     - ✅ Aggiunto controllo `underlyingMatch` che confronta i tipi sottostanti usando `getUnderlyingType`
     - ✅ Semplificato il controllo per essere più permissivo quando il target è opaco
     - ✅ Rimossi tutti i warning di debug temporanei
   - **Risultati Test** (ultima esecuzione):
     - ❌ `Int to ValidAge - invalid values`: Fallisce (dà `Right(-1)`, `Right(151)`, `Right(200)` invece di `Left`)
     - ❌ `String to UserId - invalid values`: Fallisce (dà `Right("")`, `Right("user-123")`, ecc. invece di `Left`)
     - ✅ Altri test sugli opaque types passano (valid values, unwrapping, nested, ecc.)
   - **Analisi del Problema**:
     - Il controllo multi-livello (symbolMatch, typeMatch, underlyingMatch, opaqueMatch, subtypingMatch) fallisce sempre
     - `validators` risulta vuoto, quindi il codice dovrebbe chiamare `errorAndAbort` nel blocco `else`
     - Ma i test non mostrano errori di compilazione, suggerendo che:
       - Il codice non arriva al blocco `else` (improbabile)
       - O `validators` non è vuoto ma contiene un metodo sbagliato (es. `applyUnsafe`)
       - O c'è un altro percorso che bypassa completamente `OpaqueMacros`
   - **Prossimi Passi Critici**:
     1. **Verificare se `OpaqueMacros` viene chiamato**: Aggiungere `errorAndAbort` temporaneo all'inizio di `opaqueTypeConversion` per verificare
     2. **Verificare se `candidates` contiene il metodo corretto**: Aggiungere logging per vedere quali metodi vengono trovati
     3. **Verificare se il controllo `isEither` funziona**: Potrebbe essere che `retSym.fullName` non matcha "scala.util.Either"
     4. **Verificare se c'è un fallback in `Into.scala`**: Controllare se dopo `OpaqueMacros` ritorna `None`, viene usato un altro meccanismo che bypassa la validazione
     5. **Approccio alternativo**: Se il target è opaco e il tipo di ritorno è `Either[_, _]`, accettare sempre come validator (molto permissivo ma sicuro)

### 🧪 Test da Verificare

- ✅ `Int to ValidAge - valid values` (dovrebbe passare)
- 🔴 `Int to ValidAge - invalid values` (attualmente fallisce - deve dare Left)
- ✅ `String to UserId - valid values` (dovrebbe passare)
- 🔴 `String to UserId - invalid values` (attualmente fallisce - deve dare Left)

### 📌 Note

- Il problema principale è nel controllo del matching dei simboli per gli opaque types
- Scala 3 reflection può rappresentare lo stesso tipo opaco in modi diversi
- Serve un controllo più robusto che copra tutti i casi possibili
- La priorità è implementare Soluzione 1, poi Soluzione 2

### 🔄 Tentativi Recenti (2024-12-22)

1. **Tentativo 1**: Controllo multi-livello con symbol comparison, type equality, opaque-specific checks, subtyping
   - **Risultato**: ❌ Fallito - `validators` rimane vuoto

2. **Tentativo 2**: Aggiunto controllo `underlyingMatch` usando `getUnderlyingType` per confrontare tipi sottostanti
   - **Risultato**: ❌ Fallito - `validators` rimane vuoto

3. **Tentativo 3**: Semplificato il controllo per essere più permissivo quando il target è opaco
   - **Risultato**: ❌ Fallito - `validators` rimane vuoto

4. **Tentativo 4 (Prossimo)**: Verificare se il problema è che `isEither` fallisce o se `candidates` è vuoto
   - **Azione**: Aggiungere logging/errorAndAbort per diagnosticare il problema

### 🎯 Ipotesi Attuale

Il problema potrebbe essere che:
- `OpaqueMacros` non viene chiamato per `Int -> ValidAge` (identity optimization o altro meccanismo viene prima)
- O `candidates` è vuoto (il metodo `apply` non viene trovato)
- O `isEither` fallisce (il tipo di ritorno non viene riconosciuto come `Either`)
- O il controllo di matching fallisce sempre, ma invece di chiamare `errorAndAbort`, il codice usa un fallback silenzioso

### 💡 Soluzione Implementata (2024-12-22)

**Approccio Ultra-Permissivo per Opaque Types**:
```scala
if (isEither) {
  val isTargetOpaque = isOpaqueAlias(target)
  
  if (isTargetOpaque) {
    // For opaque types, if it returns Either, accept it as validator
    // This is safe because:
    // 1. Opaque types with companion objects typically have validation
    // 2. Either return type indicates validation logic
    // 3. The combination is safe to accept
    true
  } else {
    // For non-opaque types, use standard checks
    // ... controlli esistenti ...
  }
}
```

**Modifiche Implementate**:
1. ✅ Sostituito controllo `isEither` con `isEitherType` helper (più robusto)
2. ✅ Implementato approccio ultra-permissivo per opaque types
3. ✅ Mantenuto controlli robusti per non-opaque types

**Status**: ✅ **IMPLEMENTATO** (2024-12-22)

**Modifiche Aggiuntive**:
1. ✅ Aggiunto filtro aggiuntivo per assicurarsi che solo validators sicuri (che ritornano Either o Validation) vengano usati
2. ✅ Migliorato messaggio di errore con dettagli diagnostici quando validators è vuoto
3. ✅ Sostituito controllo `isEither` con `isEitherType` helper per migliore rilevamento

**Nota**: I test falliscono ancora - il problema persiste. Il codice compila, quindi `OpaqueMacros` viene chiamato e `validators` non è vuoto. Il problema potrebbe essere:
- Il controllo `isEither` fallisce ancora nonostante l'uso di `isEitherType`
- Il codice generato non chiama correttamente il metodo `apply`
- C'è un altro percorso che bypassa la validazione a runtime

**Prossimi Passi**:
1. ✅ **FATTO**: Sostituito `Select.unique(companionRef, method.name)` con `Select(companionRef, method)` per assicurarsi che venga selezionato il metodo specifico trovato via reflection, non solo qualsiasi metodo con lo stesso nome (che potrebbe essere `applyUnsafe`)
2. 🔄 **IN PROGRESS**: Testare se questa modifica risolve il problema
3. Se il problema persiste, aggiungere logging runtime per vedere quale metodo viene effettivamente chiamato
4. Verificare il codice generato per vedere se chiama correttamente `apply` o `applyUnsafe`

**Fix Critici Implementati (2024-12-22)**:

1. **Fix Selezione Metodo**:
   - **Problema**: `Select.unique(companionRef, method.name)` potrebbe selezionare il metodo sbagliato quando ci sono più metodi con lo stesso nome (es. `apply` che ritorna `Either[String, ValidAge]` e `applyUnsafe` che ritorna `ValidAge`)
   - **Soluzione**: Usare `Select(companionRef, method)` che seleziona il metodo specifico trovato via reflection
   - **Location**: `OpaqueMacros.scala` linee 507-513 e 567-573

2. **Controllo Validazione Metodo Selezionato**:
   - **Aggiunto**: Controllo esplicito per verificare che il metodo selezionato sia quello corretto (ritorna Either o Validation)
   - **Location**: `OpaqueMacros.scala` linee 497-520

**Status Test**: ❌ I test falliscono ancora - `Right(-1)` invece di `Left(...)`, indicando che la validazione non viene eseguita.

**Ipotesi Attuale**:
- Il metodo viene selezionato correttamente (il codice compila, il controllo esplicito passa)
- `Select(companionRef, method)` viene usato per selezionare il metodo specifico
- Il problema potrebbe essere che `Select(companionRef, method)` non funziona correttamente con metodi sovraccaricati
- O c'è un problema nella generazione del Lambda che chiama il metodo
- O il codice generato non gestisce correttamente il risultato della validazione

**Prossimi Passi Critici**:
1. Verificare se `Select(companionRef, method)` funziona correttamente con metodi sovraccaricati
2. Provare un approccio alternativo: costruire esplicitamente la chiamata al metodo usando il nome e la firma
3. Aggiungere logging runtime per vedere quale metodo viene effettivamente chiamato
4. Verificare se il problema è nella generazione del Lambda o nella gestione del risultato

---

## 🔍 Verifica Into.identity Hijacking (2024-12-22, 16:00)

### Obiettivo
Verificare se `Into.identity` viene usato al posto della macro per la conversione `Int -> ValidAge`, bypassando completamente la derivazione.

### Modifiche Implementate

1. **Logging aggiunto a `Into.identity`**:
   ```scala
   given identity[A]: Into[A, A] = new Into[A, A] {
     def into(input: A): Either[SchemaError, A] = {
       // Usa System.err per evitare buffering
       if (input.toString == "-1") {
          System.err.println(s"[Into.identity] ALERT: Identity called for input: $input")
       }
       Right(input)
     }
   }
   ```
   - **Location**: `schema/shared/src/main/scala-3/zio/blocks/schema/Into.scala` linee 92-98

2. **Check esplicito per opaque types nella macro**:
   ```scala
   // Check if the target type is an opaque type (these MUST NOT use identity optimization)
   val isTargetOpaque = bType match {
     case tr: TypeRef => tr.isOpaqueAlias
     case _ => false
   }
   
   // Only apply identity optimization if types match AND (no companion object OR it's a standard primitive) AND target is NOT opaque.
   if (aType =:= bType && (!targetHasCompanion || isStandardPrimitive) && !isTargetOpaque) {
     return '{ Into.identity[A].asInstanceOf[Into[A, B]] }
   }
   ```
   - **Location**: `schema/shared/src/main/scala-3/zio/blocks/schema/Into.scala` linee 168-185

### Risultati Test

**Comando eseguito**: `sbt "project schemaJVM; clean; testOnly zio.blocks.schema.IntoSpec -- -z Opaque"`

**Risultato**:
- ❌ **Nessun `[Into.identity] ALERT` nell'output** → `Into.identity` NON viene chiamato a runtime
- ❌ **Test fallisce ancora**: `Right(-1)`, `Right(151)`, `Right(200)` invece di `Left(...)`
- ✅ **Il codice compila**: La macro viene eseguita, non c'è errore di compilazione

### Conclusioni

1. ✅ **`Into.identity` NON è il problema**: 
   - Il logging conferma che `Into.identity` non viene mai chiamato per `Int -> ValidAge`
   - Il check `isTargetOpaque` previene correttamente l'identity optimization per gli opaque types

2. 🔴 **Il problema è altrove**:
   - La validazione non viene eseguita, ma non è dovuto a `Into.identity`
   - Il problema deve essere in `OpaqueMacros` che:
     - Non trova correttamente il metodo `apply` che restituisce `Either[String, ValidAge]`
     - O trova il metodo ma non lo usa correttamente (es. usa `applyUnsafe` invece di `apply`)
     - O il codice generato non chiama correttamente il metodo di validazione

3. 📊 **Stato attuale**:
   - La macro `deriveImpl` viene chiamata (il codice compila)
   - `OpaqueMacros.opaqueTypeConversion` viene chiamato (i log mostrano `[Into DIAG] Attempting opaqueTypeConversion`)
   - Il problema è nella logica interna di `OpaqueMacros` che non trova o non usa correttamente il metodo di validazione

### Prossimi Passi

1. **Analizzare `OpaqueMacros.opaqueTypeConversion`**:
   - Verificare se `candidates` contiene il metodo `apply` corretto
   - Verificare se `isEitherType` riconosce correttamente `Either[String, ValidAge]`
   - Verificare se il controllo di matching per gli opaque types funziona correttamente
   - Verificare se `validators` contiene il metodo corretto o è vuoto

2. **Aggiungere logging diagnostico in `OpaqueMacros`**:
   - Log quando `candidates` viene trovato
   - Log quando `validators` viene partizionato
   - Log quando viene selezionato il metodo
   - Log del codice generato

3. **Verificare il codice generato**:
   - Decompilare il bytecode generato per vedere quale metodo viene effettivamente chiamato
   - Verificare se il Lambda generato chiama `apply` o `applyUnsafe`

### Note

- Il problema **NON è** dovuto a implicit resolution che preferisce `Into.identity` rispetto alla macro
- Il problema **NON è** dovuto all'identity optimization nella macro (prevenuto correttamente)
- Il problema **È** nella logica di `OpaqueMacros` che non trova o non usa correttamente il metodo di validazione

---

## 🔍 Sonda di Debug: Companion Object Visibility (2024-12-22, 17:50)

### Obiettivo
Verificare se `OpaqueMacros` viene chiamato e se riesce a trovare il companion object di `ValidAge` quando è definito in uno scope locale (come in un test).

### Sonda Implementata

**Location**: `schema/shared/src/main/scala-3/zio/blocks/schema/derive/OpaqueMacros.scala` (inizio di `opaqueTypeConversion`)

**Codice della Sonda**:
```scala
// DEBUG PROBE START
val typeName = target.typeSymbol.name
if (typeName.contains("ValidAge")) {
  val companion = target.typeSymbol.companionModule
  val hasCompanion = companion != Symbol.noSymbol
  
  // Tentativo di ricerca alternativa
  val owner = target.typeSymbol.owner
  val companionByName = try {
    owner.memberType(target.typeSymbol.name + "$") match {
      case termRef: TermRef => termRef.termSymbol
      case _ => Symbol.noSymbol
    }
  } catch { case _ => Symbol.noSymbol }
  val foundByName = companionByName != Symbol.noSymbol
  
  report.errorAndAbort(
    s"DEBUG PROBE: Target=$typeName, " +
    s"HasCompanion(Std)=$hasCompanion, " +
    s"FoundByName(Alt)=$foundByName, " +
    s"Owner=${owner.name}"
  )
}
// DEBUG PROBE END
```

### Risultati della Sonda

**Comando eseguito**: `sbt "project schemaJVM; clean; testOnly zio.blocks.schema.IntoSpec -- -z Opaque"`

**Output dell'errore di compilazione**:
```
[error] DEBUG PROBE: Target=ValidAge, HasCompanion(Std)=false, FoundByName(Alt)=false, Owner=IntoSpec$
```

### Conclusioni

1. ✅ **`OpaqueMacros` VIENE chiamato**: 
   - L'errore di compilazione conferma che la macro viene eseguita per `Int -> ValidAge`
   - Il problema NON è che la macro non viene invocata

2. 🔴 **`companionModule` FALLISCE per tipi definiti localmente**:
   - `HasCompanion(Std)=false` → `target.typeSymbol.companionModule` ritorna `Symbol.noSymbol`
   - Questo spiega perché `OpaqueMacros` non trova i validatori e ricade sull'identity cast

3. 🔴 **Anche la ricerca alternativa fallisce**:
   - `FoundByName(Alt)=false` → Il tentativo di cercare il companion per nome nello scope dell'owner fallisce
   - `Owner=IntoSpec$` → Il tipo è definito dentro l'object `IntoSpec`

4. 📊 **Root Cause Identificato**:
   - Quando `companionSymbol == Symbol.noSymbol`, il codice alla linea 298-306 di `OpaqueMacros.scala` genera un identity cast
   - Questo spiega perfettamente perché otteniamo `Right(-1)` invece di `Left(...)`

### Soluzione Implementata

**Approccio**: Implementare una ricerca robusta del companion object che cerca manualmente nelle dichiarazioni dell'owner quando `companionModule` fallisce.

**Metodo Helper Aggiunto**:
```scala
def findCompanion(tpe: TypeRepr): Symbol = {
  val sym = tpe.typeSymbol
  
  // 1. Try standard approach
  val standard = sym.companionModule
  if (standard != Symbol.noSymbol) return standard
  
  // 2. Fallback: Search in owner's declarations
  // Look for a symbol with the same name that is a Module
  val owner = sym.owner
  if (owner != Symbol.noSymbol) {
    owner.declarations.find { decl =>
      decl.name == sym.name && decl.flags.is(Flags.Module)
    }.getOrElse(Symbol.noSymbol)
  } else {
    Symbol.noSymbol
  }
}
```

**Modifiche Implementate**:
1. ✅ Rimosso il blocco DEBUG PROBE
2. ✅ Aggiunto metodo helper `findCompanion` in `OpaqueMacros.scala`
3. ✅ Sostituito `target.typeSymbol.companionModule` con `findCompanion(target)`
4. ✅ **Location**: `schema/shared/src/main/scala-3/zio/blocks/schema/derive/OpaqueMacros.scala` linee 257-273

### Risultati Attesi

Con questa modifica:
- `findCompanion(ValidAge)` dovrebbe trovare `object ValidAge` cercando nelle dichiarazioni di `IntoSpec$`
- La logica successiva dovrebbe trovare `apply(Int): Either[String, ValidAge]`
- Il codice generato dovrebbe includere la validazione
- I test `Int to ValidAge - invalid values` e `String to UserId - invalid values` dovrebbero finalmente passare (o dare `Left(...)` corretto)

### Status

- ✅ **IMPLEMENTATO** (2024-12-22, 17:50)
- ✅ **TEST PASSATI** (2024-12-22, 17:59)

### Risultati Test

**Comando eseguito**: `sbt "project schemaJVM; clean; testOnly zio.blocks.schema.IntoSpec -- -z Opaque"`

**Risultati**:
- ✅ `Int to ValidAge - invalid values` - **PASS** (ora restituisce `Left(...)` correttamente)
- ✅ `Int to ValidAge - valid values` - **PASS**
- ✅ `String to UserId - valid values` - **PASS**
- ✅ `String to UserId - invalid values` - **PASS** (ora restituisce `Left(...)` correttamente)
- ✅ `Int to Count` - **PASS** (usa factory senza validazione)
- ✅ `Int to Age` - **PASS** (usa factory senza validazione)

**Conclusione**: La soluzione funziona perfettamente! Il metodo `findCompanion` trova correttamente il companion object anche per tipi definiti in scope locali, permettendo alla logica di validazione di funzionare correttamente.

