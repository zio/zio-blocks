# Cosa Copiare e Incollare - Guida Passo Passo

## Passo 1: Aprire sbt Console

Nel terminale, esegui:
```bash
sbt
```

Poi dentro sbt:
```bash
project schemaJVM
console
```

Aspetta che il REPL si carichi (vedrai `scala>`).

---

## Passo 2: Import (copiare UNA volta all'inizio)

Copia e incolla questo nel REPL:

```scala
import zio.blocks.schema._
```

Premi INVIO.

---

## Passo 3: Demo 1 - Basic Into (copiare tutto insieme)

Copia e incolla tutto questo blocco:

```scala
println("=== 1. Basic Into Conversion ===")
case class Person(name: String, age: Int)
case class User(name: String, age: Int)
val into = Into.derived[Person, User]
val person = Person("Alice", 30)
val result = into.into(person)
println(s"Person: $person")
println(s"Converted: $result")
```

Premi INVIO e guarda l'output.

---

## Passo 4: Demo 2 - Schema Evolution (copiare tutto insieme)

Copia e incolla tutto questo blocco:

```scala
println("\n=== 2. Schema Evolution ===")
case class UserV1(name: String, age: Int)
case class UserV2(name: String, age: Int, email: Option[String])
val into2 = Into.derived[UserV1, UserV2]
val userV1 = UserV1("Bob", 25)
val result2 = into2.into(userV1)
println(s"UserV1: $userV1")
println(s"Converted to UserV2: $result2")
```

Premi INVIO e guarda l'output.

---

## Passo 5: Demo 3 - Field Reordering (copiare tutto insieme)

Copia e incolla tutto questo blocco:

```scala
println("\n=== 3. Field Reordering ===")
case class Point(x: Int, y: Int)
case class Coord(y: Int, x: Int)
val into3 = Into.derived[Point, Coord]
val point = Point(10, 20)
val result3 = into3.into(point)
println(s"Point: $point")
println(s"Converted to Coord (fields reordered): $result3")
```

Premi INVIO e guarda l'output.

---

## Passo 6: Demo 4 - As Round-trip (copiare tutto insieme)

Copia e incolla tutto questo blocco:

```scala
println("\n=== 4. As Bidirectional Conversion ===")
case class Point2(x: Int, y: Int)
case class Coord2(x: Long, y: Long)
val as = As.derived[Point2, Coord2]
val point2 = Point2(10, 20)
val coord = as.into(point2)
println(s"Point -> Coord: $coord")
val back = coord.flatMap(c => as.from(c))
println(s"Coord -> Point (round-trip): $back")
println(s"Round-trip successful: ${back.map(_ == point2).getOrElse(false)}")
```

Premi INVIO e guarda l'output.

---

## Passo 7: Demo 5 - Error Handling (copiare tutto insieme)

Copia e incolla tutto questo blocco:

```scala
println("\n=== 5. Error Handling (Overflow) ===")
case class Config(timeout: Long)
case class ConfigV2(timeout: Int)
val into4 = Into.derived[Config, ConfigV2]
val valid = Config(42L)
println(s"Valid conversion: ${into4.into(valid)}")
val invalid = Config(3000000000L)
val errorResult = into4.into(invalid)
println(s"Invalid conversion (overflow): $errorResult")
errorResult.left.foreach(err => println(s"Error: ${err.message}"))
```

Premi INVIO e guarda l'output.

---

## Passo 8: Demo 6 - Collection Conversions (copiare tutto insieme)

Copia e incolla tutto questo blocco:

```scala
println("\n=== 6. Collection Conversions ===")
val into5 = Into.derived[List[Int], Vector[Long]]
val list = List(1, 2, 3)
println(s"List[Int] -> Vector[Long]: ${into5.into(list)}")
val into6 = Into.derived[List[Int], Set[Int]]
val listWithDups = List(1, 2, 2, 3)
println(s"List[Int] with duplicates -> Set[Int]: ${into6.into(listWithDups)}")
```

Premi INVIO e guarda l'output.

---

## Passo 9: Uscire dal REPL

Quando hai finito, scrivi:
```scala
:q
```

Poi esci da sbt:
```bash
exit
```

---

## ⚠️ IMPORTANTE per il Video

1. **Prima di registrare**: Esegui tutti i passi una volta per verificare che funzioni
2. **Durante la registrazione**: 
   - Copia e incolla un blocco alla volta
   - Aspetta che l'output appaia prima di passare al prossimo
   - Spiega cosa stai facendo mentre esegui
3. **Parla chiaramente**:
   - "Ora mostro una conversione base con Into"
   - "Vediamo come funziona lo schema evolution"
   - "Ecco un esempio di errore con overflow"
   - etc.

---

## Versione TUTTO IN UNO (se preferisci)

Se vuoi copiare tutto insieme, usa questo (ma è meglio farlo passo passo per il video):

```scala
import zio.blocks.schema._

println("=== 1. Basic Into Conversion ===")
case class Person(name: String, age: Int)
case class User(name: String, age: Int)
val into = Into.derived[Person, User]
println(into.into(Person("Alice", 30)))

println("\n=== 2. Schema Evolution ===")
case class UserV1(name: String, age: Int)
case class UserV2(name: String, age: Int, email: Option[String])
val into2 = Into.derived[UserV1, UserV2]
println(into2.into(UserV1("Bob", 25)))

println("\n=== 3. Field Reordering ===")
case class Point(x: Int, y: Int)
case class Coord(y: Int, x: Int)
val into3 = Into.derived[Point, Coord]
println(into3.into(Point(10, 20)))

println("\n=== 4. As Round-trip ===")
case class Point2(x: Int, y: Int)
case class Coord2(x: Long, y: Long)
val as = As.derived[Point2, Coord2]
val point2 = Point2(10, 20)
val coord = as.into(point2)
println(s"Point -> Coord: $coord")
val back = coord.flatMap(c => as.from(c))
println(s"Round-trip: $back")

println("\n=== 5. Error Handling ===")
case class Config(timeout: Long)
case class ConfigV2(timeout: Int)
val into4 = Into.derived[Config, ConfigV2]
println(s"Valid: ${into4.into(Config(42L))}")
println(s"Invalid: ${into4.into(Config(3000000000L))}")

println("\n=== 6. Collections ===")
val into5 = Into.derived[List[Int], Vector[Long]]
println(into5.into(List(1, 2, 3)))
val into6 = Into.derived[List[Int], Set[Int]]
println(into6.into(List(1, 2, 2, 3)))

println("\n=== Demo Complete ===")
```

