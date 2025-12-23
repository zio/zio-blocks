# Advanced Examples - Into & As

This document provides advanced examples of using `Into[A, B]` and `As[A, B]` for complex conversion scenarios.

## Table of Contents

1. [Deeply Nested Structures](#deeply-nested-structures)
2. [Large Products](#large-products)
3. [Complex Coproduct Matching](#complex-coproduct-matching)
4. [Collection Transformations](#collection-transformations)
5. [Schema Evolution Patterns](#schema-evolution-patterns)
6. [Performance-Critical Conversions](#performance-critical-conversions)
7. [Custom Validation](#custom-validation)

---

## Deeply Nested Structures

### 5+ Levels of Nesting

```scala
// Level 1: Leaf
case class LeafV1(value: Int)
case class LeafV2(value: Long)

// Level 2: Branch
case class BranchV1(left: LeafV1, right: LeafV1)
case class BranchV2(left: LeafV2, right: LeafV2)

// Level 3: Tree
case class TreeV1(root: BranchV1, depth: Int)
case class TreeV2(root: BranchV2, depth: Long)

// Level 4: Forest
case class ForestV1(trees: List[TreeV1], name: String)
case class ForestV2(trees: Vector[TreeV2], name: String)

// Level 5: Ecosystem
case class EcosystemV1(forests: Map[String, ForestV1], climate: String)
case class EcosystemV2(forests: Map[String, ForestV2], climate: String)

// Automatic conversion handles all 5 levels
val into = Into.derived[EcosystemV1, EcosystemV2]
val ecosystem = EcosystemV1(
  Map("north" -> ForestV1(
    List(TreeV1(BranchV1(LeafV1(1), LeafV1(2)), 3)),
    "Pine Forest"
  )),
  "Temperate"
)

val converted = into.into(ecosystem)
// Automatically converts:
// - EcosystemV1 → EcosystemV2
// - Map values: ForestV1 → ForestV2
// - Vector: List → Vector
// - TreeV1 → TreeV2
// - BranchV1 → BranchV2
// - LeafV1 → LeafV2 (Int → Long)
```

---

## Large Products

### 20+ Fields with Complex Mapping

```scala
case class UserProfileV1(
  id: Int,
  firstName: String,
  lastName: String,
  email: String,
  age: Int,
  height: Int,
  weight: Int,
  city: String,
  country: String,
  zipCode: String,
  phone: String,
  createdAt: Long,
  updatedAt: Long,
  lastLogin: Long,
  status: String,
  role: String,
  department: String,
  salary: Int,
  managerId: Int,
  teamSize: Int
)

case class UserProfileV2(
  userId: Long,           // Widened, renamed
  fullName: String,        // Combined firstName + lastName
  email: Option[String],   // Made optional
  age: Long,               // Widened
  heightCm: Long,          // Widened, renamed
  weightKg: Long,           // Widened, renamed
  address: Address,         // Nested structure
  phone: Option[String],   // Made optional
  timestamps: Timestamps,  // Nested structure
  metadata: UserMetadata   // Nested structure
)

case class Address(
  city: String,
  country: String,
  zipCode: String
)

case class Timestamps(
  createdAt: Long,
  updatedAt: Long,
  lastLogin: Option[Long]
)

case class UserMetadata(
  status: String,
  role: String,
  department: String,
  salary: Long,      // Widened
  managerId: Long,   // Widened
  teamSize: Long     // Widened
)

// For complex transformations like combining firstName + lastName,
// you may need a custom instance:
implicit val profileInto: Into[UserProfileV1, UserProfileV2] = 
  new Into[UserProfileV1, UserProfileV2] {
    def into(input: UserProfileV1): Either[SchemaError, UserProfileV2] = {
      Right(UserProfileV2(
        userId = input.id.toLong,
        fullName = s"${input.firstName} ${input.lastName}",
        email = Some(input.email),
        age = input.age.toLong,
        heightCm = input.height.toLong,
        weightKg = input.weight.toLong,
        address = Address(
          city = input.city,
          country = input.country,
          zipCode = input.zipCode
        ),
        phone = Some(input.phone),
        timestamps = Timestamps(
          createdAt = input.createdAt,
          updatedAt = input.updatedAt,
          lastLogin = Some(input.lastLogin)
        ),
        metadata = UserMetadata(
          status = input.status,
          role = input.role,
          department = input.department,
          salary = input.salary.toLong,
          managerId = input.managerId.toLong,
          teamSize = input.teamSize.toLong
        )
      ))
    }
  }
```

---

## Complex Coproduct Matching

### Large Coproducts with Signature Matching

```scala
sealed trait EventV1
object EventV1 {
  case class UserCreated(id: Int, name: String, timestamp: Long) extends EventV1
  case class UserUpdated(id: Int, name: String, timestamp: Long) extends EventV1
  case class UserDeleted(id: Int, timestamp: Long) extends EventV1
  case class OrderPlaced(orderId: Int, userId: Int, amount: Double, timestamp: Long) extends EventV1
  case class OrderCancelled(orderId: Int, reason: String, timestamp: Long) extends EventV1
  case class PaymentProcessed(paymentId: Int, orderId: Int, amount: Double, timestamp: Long) extends EventV1
  case class RefundIssued(refundId: Int, paymentId: Int, amount: Double, timestamp: Long) extends EventV1
  // ... 15+ more cases
}

sealed trait EventV2
object EventV2 {
  // Renamed cases, but signatures match
  case class UserSpawned(id: Long, name: String, timestamp: Long) extends EventV2
  case class UserModified(id: Long, name: String, timestamp: Long) extends EventV2
  case class UserRemoved(id: Long, timestamp: Long) extends EventV2
  case class OrderCreated(orderId: Long, userId: Long, amount: Double, timestamp: Long) extends EventV2
  case class OrderAborted(orderId: Long, reason: String, timestamp: Long) extends EventV2
  case class PaymentCompleted(paymentId: Long, orderId: Long, amount: Double, timestamp: Long) extends EventV2
  case class RefundCompleted(refundId: Long, paymentId: Long, amount: Double, timestamp: Long) extends EventV2
  // ... matching cases
}

// Automatic matching by signature when names differ
val into = Into.derived[EventV1, EventV2]

val event = EventV1.UserCreated(1, "Alice", 1234567890L)
val converted = into.into(event)
// Right(EventV2.UserSpawned(1L, "Alice", 1234567890L))
// - Matched by signature: (Int, String, Long)
// - Converted types: Int → Long
```

---

## Collection Transformations

### Complex Collection Conversions

```scala
// Nested collections with type conversions
case class DataV1(
  users: List[UserV1],
  orders: Map[Int, OrderV1],
  tags: Set[String],
  metadata: Option[MetadataV1]
)

case class DataV2(
  users: Vector[UserV2],
  orders: Map[Long, OrderV2],
  tags: List[String],
  metadata: Option[MetadataV2]
)

// Handles:
// - List[UserV1] → Vector[UserV2] (collection + element conversion)
// - Map[Int, OrderV1] → Map[Long, OrderV2] (key + value conversion)
// - Set[String] → List[String] (collection type change)
// - Option[MetadataV1] → Option[MetadataV2] (nested conversion)

val into = Into.derived[DataV1, DataV2]
```

### Large Collection Performance

```scala
// For large collections, consider batching
def migrateLargeDataset[T1, T2](
  items: List[T1]
)(using into: Into[T1, T2]): Either[SchemaError, Vector[T2]] = {
  // Convert in batches to avoid memory issues
  items.grouped(1000).foldLeft(Right(Vector.empty[T2]): Either[SchemaError, Vector[T2]]) {
    case (Right(acc), batch) =>
      val batchInto = Into.derived[List[T1], Vector[T2]]
      batchInto.into(batch).map(acc ++ _)
    case (left, _) => left
  }
}
```

---

## Schema Evolution Patterns

### Real-World Migration Scenario

```scala
// Initial schema (V1)
case class ProductV1(
  id: Int,
  name: String,
  price: Double,
  category: String,
  inStock: Boolean
)

// Evolved schema (V2) - multiple changes
case class ProductV2(
  productId: Long,                    // Widened, renamed
  name: String,                       // Same
  pricing: PricingInfo,               // Nested structure
  category: ProductCategory,          // Enum/sealed trait
  inventory: InventoryStatus,         // Nested structure
  metadata: Option[ProductMetadata]   // New optional field
)

case class PricingInfo(
  basePrice: Double,
  currency: String,
  discount: Option[Double]
)

sealed trait ProductCategory
object ProductCategory {
  case object Electronics extends ProductCategory
  case object Clothing extends ProductCategory
  case object Food extends ProductCategory
  case object Other extends ProductCategory
}

case class InventoryStatus(
  inStock: Boolean,
  quantity: Option[Int],
  lastRestocked: Option[Long]
)

case class ProductMetadata(
  tags: List[String],
  description: Option[String],
  images: List[String]
)

// Custom migration with business logic
implicit val productInto: Into[ProductV1, ProductV2] = 
  new Into[ProductV1, ProductV2] {
    def into(input: ProductV1): Either[SchemaError, ProductV2] = {
      // Convert category string to enum
      val category = input.category.toLowerCase match {
        case "electronics" => ProductCategory.Electronics
        case "clothing" => ProductCategory.Clothing
        case "food" => ProductCategory.Food
        case _ => ProductCategory.Other
      }
      
      Right(ProductV2(
        productId = input.id.toLong,
        name = input.name,
        pricing = PricingInfo(
          basePrice = input.price,
          currency = "USD",
          discount = None
        ),
        category = category,
        inventory = InventoryStatus(
          inStock = input.inStock,
          quantity = None,
          lastRestocked = None
        ),
        metadata = None
      ))
    }
  }
```

---

## Performance-Critical Conversions

### Caching Instances

```scala
// ❌ Slow - derives on every call
def processUser(user: UserV1): UserV2 = {
  Into.derived[UserV1, UserV2].intoOrThrow(user)
}

// ✅ Fast - derive once, cache
object Conversions {
  val userInto = Into.derived[UserV1, UserV2]
  val productInto = Into.derived[ProductV1, ProductV2]
  // ... cache all frequently used conversions
}

def processUser(user: UserV1): UserV2 = {
  Conversions.userInto.intoOrThrow(user)
}
```

### Batch Processing

```scala
// Process multiple items efficiently
def migrateBatch[T1, T2](
  items: List[T1]
)(using into: Into[T1, T2]): Either[SchemaError, Vector[T2]] = {
  // Use Vector for better append performance
  val result = Vector.newBuilder[T2]
  
  items.foldLeft[Either[SchemaError, Unit]](Right(())) { (acc, item) =>
    acc.flatMap(_ => into.into(item).map(result += _))
  }.map(_ => result.result())
}
```

---

## Custom Validation

### Opaque Types with Complex Validation

```scala
// Scala 3
opaque type Email = String
object Email {
  def apply(s: String): Either[String, Email] = {
    val emailRegex = """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""".r
    if (emailRegex.matches(s)) Right(s)
    else Left(s"Invalid email format: $s")
  }
  
  def applyUnsafe(s: String): Email = s
}

opaque type UserId = Long
object UserId {
  def apply(l: Long): Either[String, UserId] = {
    if (l > 0) Right(l)
    else Left(s"UserId must be positive: $l")
  }
  
  def applyUnsafe(l: Long): UserId = l
}

// Automatic validation during conversion
val into = Into.derived[String, Email]
into.into("user@example.com") // Right(Email(...))
into.into("invalid") // Left(SchemaError) - validation failed

// Multiple validations accumulate errors
case class UserInput(email: String, userId: Long)
case class ValidatedUser(email: Email, userId: UserId)

val userInto = Into.derived[UserInput, ValidatedUser]
userInto.into(UserInput("invalid", -1))
// Left(SchemaError) - contains both validation errors
```

---

## Error Accumulation

### Handling Multiple Validation Errors

```scala
case class FormData(
  name: String,
  age: String,      // Should be Int
  email: String,    // Should be Email
  phone: String     // Should be Phone
)

case class ValidatedForm(
  name: String,
  age: Int,
  email: Email,
  phone: Phone
)

val into = Into.derived[FormData, ValidatedForm]

// All validation errors are accumulated
into.into(FormData("", "abc", "invalid", "123")) match {
  case Right(valid) => // Success
  case Left(error) =>
    // error contains all validation failures:
    // - name validation (if any)
    // - age parsing error
    // - email validation error
    // - phone validation error
    error.errors.foreach { singleError =>
      println(s"Error at ${singleError.trace}: ${singleError.message}")
    }
}
```

---

## Best Practices for Advanced Use Cases

1. **Cache frequently used conversions** - Derive once, reuse many times
2. **Use custom instances for complex logic** - Don't force automatic derivation
3. **Handle errors gracefully** - Always check `Either` results
4. **Test edge cases** - Large products, deeply nested, empty collections
5. **Profile performance** - Use benchmarks for critical paths
6. **Document custom instances** - Explain why manual conversion is needed
7. **Consider batch processing** - For large datasets, process in chunks

---

## Additional Resources

- [Into Usage Guide](INTO_USAGE.md) - Basic usage examples
- [As Usage Guide](AS_USAGE.md) - Bidirectional conversions
- [Migration Guide](MIGRATION_GUIDE.md) - Schema evolution patterns
- [Performance Guide](PERFORMANCE.md) - Performance optimization tips
- [API Reference](API.md) - Complete API documentation




