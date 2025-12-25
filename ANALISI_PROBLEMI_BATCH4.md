# Analisi Problemi Batch 4 - Disambiguation e Optional Injection

**Data:** 25 Dicembre 2025  
**Contesto:** Test Batch 4 (Disambiguation Priority 4 e Optional Injection) dopo isolamento errori Coproducts

---

## 📊 Riepilogo Risultati Test

- ✅ **52 test passati**
- ❌ **13 test falliti**
- ✅ **Compilazione riuscita** (dopo disabilitazione file Coproducts)

---

## ✅ Problema 1: Errore Compilazione Coproducts - RISOLTO

### Sintomo (Storico)
```
java.lang.AssertionError: assertion failed: 'new' call to non-constructor: $init$
```

### File Affetti (Storico)
- `CaseMatchingSpec.scala` - ✅ Risolto
- `NestedCoproductsSpec.scala` - ⚠️ Non ancora testato (riabilitare per verifica)
- `IntoCoproductSpec.scala` - ✅ Risolto (12/12 test passano)

### Causa Identificata

**Problema 1: Singleton Types e `New(Inferred(...))`**
Il problema era causato dall'uso di `New(Inferred(bTpe))` per singleton types (enum cases, case objects). Per questi tipi, non esiste un costruttore, quindi il compilatore generava l'errore `'new' call to non-constructor: $init$`.

**Problema 2: Ricorsione Infinita**
Quando `deriveCoproductInto` chiamava `findOrDeriveInto` per i singleton types, questi venivano erroneamente riconosciuti come coproducts, causando una ricorsione infinita (`StackOverflowError`).

**Problema 3: Estrazione Nome Simboli**
Per enum cases (singleton types), `typeSymbol.name` restituiva il nome dell'enum padre invece del nome del caso. Era necessario usare `termSymbol.name` per i singleton types.

### Soluzione Implementata

**1. Gestione Singleton Types in `generateConversionBodyReal` e `generateEitherAccumulation`:**
- Aggiunto check `if (bTpe.isSingleton)` all'inizio
- Per singleton types, usa `Ref(termSym).asExprOf[B]` invece di `New(Inferred(bTpe))`

**2. Prevenzione Ricorsione Infinita in `findOrDeriveInto`:**
- Aggiunto check `!aTpe.isSingleton && !bTpe.isSingleton` prima del controllo coproducts
- Singleton types vengono gestiti separatamente con PRIORITY 0.5 (prima dei coproducts)

**3. Estrazione Nome Corretta in `deriveCoproductInto`:**
- Implementato helper `getSubtypeName` che usa `termSymbol.name` per singleton types
- Usa `typeSymbol.name` per case classes e sealed trait subtypes

**4. Costruzione Lambda Ricorsiva:**
- `deriveCoproductInto` ora costruisce una catena ricorsiva di funzioni lambda invece di if-else annidati
- Ogni funzione nella catena controlla un sottotipo e chiama la funzione successiva se non matcha
- Risolve problemi di scope con variabili nelle macro quotes

### Risultato
- ✅ Tutti i 12 test di `IntoCoproductSpec` passano
- ✅ Enum Scala 3 (es. `Status.Active -> State.Active`) funzionano correttamente
- ✅ Sealed traits con case objects (es. `Color.Red -> Hue.Red`) funzionano correttamente
- ✅ Coproducts complessi con case classes (es. `Event.Created(1) -> Action.Created(1L)`) funzionano correttamente
- ✅ Nessun errore di compilazione
- ✅ Nessuna ricorsione infinita

### Riferimenti
- `schema/shared/src/main/scala-3/zio/blocks/schema/IntoAsVersionSpecific.scala:1095-1175` - `deriveCoproductInto` con costruzione lambda ricorsiva
- `schema/shared/src/main/scala-3/zio/blocks/schema/IntoAsVersionSpecific.scala:1214-1225` - Gestione singleton types in `findOrDeriveInto`
- `schema/shared/src/test/scala-3/zio/blocks/schema/into/IntoCoproductSpec.scala` - Test suite completa (12 test)

---

## 🟡 Problema 2: Test di Validazione Compile-Time Falliscono

### Sintomo
13 test falliscono perché **verificano che la compilazione fallisca** in caso di ambiguità, ma la macro **non rileva queste ambiguità** e genera codice che compila.

### Test Falliti

#### 1. **AmbiguousCompileErrorSpec** (7 test falliti)
- `should fail compilation for ambiguous Int fields`
- `should fail compilation for multiple ambiguous types`
- `should fail when name matches but types incompatible`
- `should provide helpful error message with available fields`
- `should fail when unique type match is ambiguous`
- `should fail when position match exists but types not unique`
- `should fail when source has more fields than target`

#### 2. **NameDisambiguationSpec** (1 test fallito)
- `should handle multiple fields with same name (should fail)`

#### 3. **UniqueTypeDisambiguationSpec** (1 test fallito)
- `should fail when unique type match is ambiguous`

#### 4. **PositionDisambiguationSpec** (3 test falliti)
- `should fail when position match but one type is ambiguous`
- `should work when position match with one unique and one ambiguous`
- `should fail when positional match exists but types not unique`

#### 5. **RemoveOptionalFieldSpec** (1 test fallito)
- `should fail when trying to remove non-optional field`

### Ipotesi sulla Causa

**Problema Principale: Mancanza di Validazione Pre-Generazione**

La funzione `findMatchingField` in `IntoAsVersionSpecific.scala` (righe 750-840) implementa le priorità di disambiguazione ma **non valida se ci sono ambiguità** prima di generare il codice.

**Analisi del Codice:**

```scala
private def findMatchingField(...): Option[FieldInfo] = {
  // Priority 1: Exact Name Match
  val nameMatches = aFields.filter(_.name == bField.name)
  nameMatches.find(_.tpeRepr(using q) =:= bField.tpeRepr(using q)) match {
    case Some(exact) => return Some(exact)  // ✅ OK: match unico
    case None => // continue
  }
  
  // Priority 2: Name Match with Coercion
  if (nameMatches.size == 1) return Some(nameMatches.head)  // ⚠️ PROBLEMA: non verifica compatibilità tipo
  
  // Priority 3: Unique Type Match
  val typeMatches = aFields.filter(_.tpeRepr(using q) =:= bField.tpeRepr(using q))
  // ... restituisce il primo match senza validare ambiguità
  
  // Priority 4: Position + Compatible Type
  // ... restituisce match posizionale senza validare se altri campi sono ambigui
}
```

**Problemi Specifici:**

1. **Priority 2: Name Match con Tipo Incompatibile**
   - Il codice restituisce il match se `nameMatches.size == 1`
   - **NON verifica** se il tipo è compatibile (coercible)
   - Risultato: `Int -> String` compila invece di fallire

2. **Priority 3: Unique Type Match Ambiguo**
   - Verifica se il tipo è unico in A o in B
   - **NON verifica** se ci sono più campi con lo stesso tipo in A quando B ha più campi con lo stesso tipo
   - Risultato: `V1(width: Int, height: Int) -> V2(first: Int, second: Int)` compila invece di fallire

3. **Priority 4: Position Match con Tipi Non Unici**
   - Usa la posizione come disambiguatore quando i tipi sono compatibili
   - **NON valida** se altri campi nella stessa posizione hanno lo stesso tipo
   - Risultato: match posizionali ambigui compilano invece di fallire

4. **Mancanza di Validazione Globale**
   - La macro non ha una fase di validazione che verifica **tutti i campi** prima di generare il codice
   - Ogni campo viene processato indipendentemente
   - Non c'è un controllo che verifica se ci sono ambiguità tra campi diversi

**Esempio Concreto:**

```scala
case class V1(width: Int, height: Int)
case class V2(first: Int, second: Int)

Into.derived[V1, V2]  // Dovrebbe fallire, ma compila
```

**Cosa succede:**
1. Per `first: Int` in V2:
   - Priority 1: nessun match per nome
   - Priority 2: nessun match per nome
   - Priority 3: trova `width: Int` (tipo match, unico in V2)
   - **Restituisce `width`** ✅

2. Per `second: Int` in V2:
   - Priority 1: nessun match per nome
   - Priority 2: nessun match per nome
   - Priority 3: trova `height: Int` (tipo match, unico in V2)
   - **Restituisce `height`** ✅

3. **Problema:** Entrambi i campi hanno trovato un match, ma **non c'è validazione che verifica se ci sono ambiguità**. Se `first` e `second` fossero entrambi `Int` e ci fossero 3 campi `Int` in V1, la macro genererebbe comunque codice.

**Cosa manca:**

```scala
// Fase di validazione mancante:
def validateNoAmbiguities(
  aFields: List[FieldInfo],
  bFields: List[FieldInfo],
  mappings: Map[FieldInfo, FieldInfo]
): Either[String, Unit] = {
  // Verifica che ogni campo in B abbia un mapping unico
  // Verifica che non ci siano conflitti (stesso campo A mappato a più campi B)
  // Verifica che i tipi siano compatibili
  // Verifica che non ci siano ambiguità quando più campi hanno lo stesso tipo
}
```

---

## 🔍 Analisi Dettagliata: Perché Non Rileva Errori

### 1. Design "Fail-Fast" vs "Best-Effort"

La macro attualmente usa un approccio **"best-effort"**:
- Cerca di trovare un match per ogni campo
- Se trova un match (anche ambiguo), lo usa
- Se non trova match, fallisce solo se il campo è required (non Option)

**Dovrebbe usare un approccio "fail-fast"**:
- Valida tutti i match prima di generare codice
- Verifica ambiguità e incompatibilità
- Fallisce a compile-time se ci sono problemi

### 2. Mancanza di Fase di Validazione

La macro non ha una fase di validazione separata. Il flusso attuale è:

```
1. Per ogni campo B:
   a. Trova match in A (findMatchingField)
   b. Se trovato, genera conversione
   c. Se non trovato, verifica se è Option
2. Genera codice
```

**Dovrebbe essere:**

```
1. Per ogni campo B:
   a. Trova TUTTI i possibili match in A
   b. Valida che ci sia un match unico
2. VALIDAZIONE GLOBALE:
   a. Verifica che non ci siano ambiguità
   b. Verifica che tutti i campi required abbiano match
   c. Verifica compatibilità tipi
3. Se validazione OK, genera codice
4. Altrimenti, fallisce con errore descrittivo
```

### 3. Logica di Disambiguazione Incompleta

La logica di disambiguazione implementa le priorità ma **non gestisce i casi edge**:

- ✅ Priority 1: Exact Name + Type → OK
- ⚠️ Priority 2: Name + Coercible Type → **NON verifica se tipo è coercible**
- ⚠️ Priority 3: Unique Type → **NON verifica ambiguità quando stesso tipo appare più volte**
- ⚠️ Priority 4: Position + Compatible → **NON verifica se altri campi sono ambigui**

---

## 💡 Soluzioni Proposte

### Soluzione 1: Aggiungere Fase di Validazione

**Implementazione:**
1. Modificare `findMatchingField` per restituire `List[FieldInfo]` (tutti i possibili match) invece di `Option[FieldInfo]`
2. Aggiungere funzione `validateMappings` che verifica:
   - Unicità dei match
   - Compatibilità tipi
   - Assenza di ambiguità
3. Chiamare `validateMappings` prima di generare codice

**Vantaggi:**
- Rileva tutti gli errori a compile-time
- Messaggi di errore più chiari
- Mantiene la logica di disambiguazione esistente

**Svantaggi:**
- Richiede refactoring significativo
- Potrebbe rallentare la compilazione (validazione aggiuntiva)

### Soluzione 2: Migliorare Logica di Disambiguazione

**Implementazione:**
1. In `findMatchingField`, aggiungere validazione per ogni priorità:
   - Priority 2: Verificare che il tipo sia coercible prima di restituire match
   - Priority 3: Verificare che non ci siano ambiguità quando stesso tipo appare più volte
   - Priority 4: Verificare che altri campi non siano ambigui
2. Restituire `None` se ci sono ambiguità (forzare fallimento)

**Vantaggi:**
- Meno invasivo (modifiche localizzate)
- Mantiene struttura esistente

**Svantaggi:**
- Potrebbe non catturare tutti i casi edge
- Logica più complessa in `findMatchingField`

### Soluzione 3: Validazione Post-Match

**Implementazione:**
1. Dopo aver trovato tutti i match, validare:
   - Che ogni campo B abbia un match unico
   - Che non ci siano conflitti (stesso campo A -> più campi B)
   - Che i tipi siano compatibili
2. Se validazione fallisce, chiamare `fail()` con messaggio descrittivo

**Vantaggi:**
- Separazione delle responsabilità (match vs validazione)
- Facile da testare
- Messaggi di errore centralizzati

**Svantaggi:**
- Richiede refactoring del flusso principale

---

## 📝 Raccomandazioni

### Priorità Alta
1. **Implementare validazione per Priority 2** (Name Match con Coercion)
   - Verificare che il tipo sia coercible prima di restituire match
   - Fallire se tipo non è coercible

2. **Implementare validazione per Priority 3** (Unique Type)
   - Verificare che non ci siano ambiguità quando stesso tipo appare più volte
   - Fallire se ci sono più match possibili

3. **Implementare validazione per Priority 4** (Position)
   - Verificare che altri campi non siano ambigui
   - Fallire se match posizionale crea ambiguità

### Priorità Media
4. **Aggiungere validazione globale post-match**
   - Verificare che tutti i campi required abbiano match
   - Verificare che non ci siano conflitti

5. **Migliorare messaggi di errore**
   - Includere informazioni sui campi disponibili
   - Suggerire possibili fix

### Priorità Bassa
6. ~~**Risolvere bug Coproducts**~~ ✅ **RISOLTO**
   - ✅ Singleton types gestiti correttamente
   - ✅ Ricorsione infinita prevenuta
   - ✅ Estrazione nomi corretta per enum cases
   - ✅ Costruzione lambda ricorsiva implementata

---

## 🔗 Riferimenti

- `schema/shared/src/main/scala-3/zio/blocks/schema/IntoAsVersionSpecific.scala:750-840` - `findMatchingField`
- `schema/shared/src/main/scala-3/zio/blocks/schema/IntoAsVersionSpecific.scala:1113-1148` - Case class derivation
- `schema/shared/src/test/scala-3/zio/blocks/schema/into/disambiguation/AmbiguousCompileErrorSpec.scala` - Test falliti
- `KNOWN_ISSUES.md` - Issue tracker esistente

