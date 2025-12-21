# Istruzioni per il Demo Video

## Strumenti per Registrare

### Opzione 1: Windows (consigliato)
- **OBS Studio** (gratuito): https://obsproject.com/
- **Windows + G** (Xbox Game Bar) - built-in Windows
- **PowerPoint** (ha registrazione schermo)
- **Zoom** (puoi registrare solo te stesso)

### Opzione 2: Online
- **Loom** (gratuito): https://www.loom.com/
- **Screencastify** (estensione Chrome)

### Opzione 3: Semplice
- **Windows + Alt + R** per iniziare registrazione (se disponibile)

## Preparazione

1. **Aprire il terminale/sbt console:**
   ```bash
   cd c:\Users\Klayd\zio-blocks
   sbt
   project schemaJVM
   console
   ```

2. **Preparare lo script:**
   - Il file `demo/DemoScript.scala` contiene tutti gli esempi
   - Puoi copiare e incollare sezioni nel REPL

## Script del Video (5-10 minuti)

### 1. Introduzione (30 secondi)
- "Questo video dimostra l'implementazione di Into[A, B] e As[A, B] type classes"
- "Sono type classes per conversioni type-safe con validazione runtime"

### 2. Basic Into Conversions (1-2 minuti)
```scala
// Copia nel REPL
import zio.blocks.schema._

case class Person(name: String, age: Int)
case class User(name: String, age: Int)

val into = Into.derived[Person, User]
val person = Person("Alice", 30)
val result = into.into(person)

println(result)
// Mostra: Right(User(Alice,30))
```

**Cosa dire:**
- "Into permette conversioni one-way con validazione"
- "La macro derivation genera automaticamente l'istanza"
- "Il risultato è Either[SchemaError, B]"

### 3. Schema Evolution (2 minuti)
```scala
case class UserV1(name: String, age: Int)
case class UserV2(name: String, age: Int, email: Option[String])

val into = Into.derived[UserV1, UserV2]
val userV1 = UserV1("Bob", 25)
val result = into.into(userV1)

println(result)
// Mostra: Right(UserV2(Bob,25,None))

// Field reordering
case class Point(x: Int, y: Int)
case class Coord(y: Int, x: Int)

val into2 = Into.derived[Point, Coord]
val point = Point(10, 20)
println(into2.into(point))
// Mostra: Right(Coord(20,10)) - fields matched by name
```

**Cosa dire:**
- "Supporta schema evolution: aggiunta di campi opzionali"
- "Field mapping intelligente: per nome, tipo unico, o posizione"
- "Campi opzionali mancanti diventano None"

### 4. As Bidirectional (2 minuti)
```scala
case class Point(x: Int, y: Int)
case class Coord(x: Long, y: Long)

val as = As.derived[Point, Coord]
val point = Point(10, 20)

val coord = as.into(point)
println(coord)
// Mostra: Right(Coord(10,20))

val roundTrip = coord.flatMap(c => as.from(c))
println(roundTrip)
// Mostra: Right(Point(10,20))

println(s"Round-trip successful: ${roundTrip.map(_ == point).getOrElse(false)}")
// Mostra: Round-trip successful: true
```

**Cosa dire:**
- "As permette conversioni bidirezionali"
- "Round-trip garantito con validazione runtime"
- "Narrowing conversions validate overflow"

### 5. Error Handling (2 minuti)
```scala
case class Config(timeout: Long)
case class ConfigV2(timeout: Int)

val into = Into.derived[Config, ConfigV2]

// Valid
val valid = Config(42L)
println(into.into(valid))
// Mostra: Right(ConfigV2(42))

// Invalid (overflow)
val invalid = Config(3000000000L)
val result = into.into(invalid)
println(result)
// Mostra: Left(SchemaError(...))

result.left.foreach(err => println(err.message))
// Mostra messaggio di errore dettagliato
```

**Cosa dire:**
- "Validazione runtime per narrowing conversions"
- "Errori dettagliati con SchemaError"
- "Accumulo di errori multipli"

### 6. Collection Conversions (1-2 minuti)
```scala
val into1 = Into.derived[List[Int], Vector[Long]]
val list = List(1, 2, 3)
println(into1.into(list))
// Mostra: Right(Vector(1, 2, 3))

val into2 = Into.derived[List[Int], Set[Int]]
val listWithDups = List(1, 2, 2, 3)
println(into2.into(listWithDups))
// Mostra: Right(Set(1, 2, 3)) - duplicates removed
```

**Cosa dire:**
- "Supporta conversioni tra collection types"
- "Element coercion automatica"
- "Set rimuove duplicati"

### 7. Test Suite (30 secondi)
```bash
# Torna a sbt
sbt
project schemaJVM
test
```

**Cosa dire:**
- "Test suite completa con ~110 test"
- "Coverage ~95%"
- "Tutti i test passano"

### 8. Conclusioni (30 secondi)
- "Implementazione completa di Into e As"
- "Supporto per Scala 2.13 e 3.5"
- "Macro derivation automatica"
- "Documentazione completa in docs/"

## Suggerimenti

1. **Preparati prima:**
   - Esegui lo script una volta per verificare che funzioni
   - Prepara i comandi da copiare

2. **Parla chiaramente:**
   - Spiega cosa stai facendo
   - Evidenzia i punti chiave

3. **Mostra i risultati:**
   - Zoom sul terminale quando mostri output
   - Evidenzia i risultati importanti

4. **Mantieni il video sotto 10 minuti:**
   - Focus sulle feature principali
   - Puoi tagliare parti se troppo lungo

5. **Upload:**
   - YouTube (unlisted)
   - Loom
   - Google Drive (condividi link)
   - Qualsiasi servizio che permetta link pubblico

## Checklist Pre-Record

- [ ] Sbt compila correttamente
- [ ] Test passano
- [ ] REPL funziona
- [ ] Script di demo testato
- [ ] Software di registrazione installato
- [ ] Microfono funzionante
- [ ] Schermo pulito (chiudi app non necessarie)

## Template per la PR

Dopo aver registrato, aggiungi il link nel file `PR_TEMPLATE.md`:

```markdown
## Demo Video

[Link al tuo video qui]

Il video dimostra:
1. Basic Into conversions
2. Schema evolution patterns  
3. As bidirectional conversions
4. Error handling
5. Collection conversions
```

