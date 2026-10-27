package generatescalatypesfromopenapi

/**
 * Generate Scala Types from an OpenAPI Spec — shared spec
 *
 * The bookstore's OpenAPI document as a string, shared by the section examples.
 * `CompleteExample` reads the same text from `src/main/openapi/bookstore.json`.
 */
object BookstoreSpec {
  val bookstoreJson: String =
    """{
      |  "openapi": "3.1.0",
      |  "info": { "title": "Bookstore", "version": "1.0.0" },
      |  "paths": {
      |    "/orders/{orderId}": {
      |      "get": {
      |        "operationId": "getOrder",
      |        "parameters": [
      |          { "name": "orderId", "in": "path", "required": true, "schema": { "type": "string", "format": "uuid" } }
      |        ],
      |        "responses": {
      |          "200": {
      |            "description": "The order",
      |            "content": { "application/json": { "schema": { "$ref": "#/components/schemas/Order" } } }
      |          }
      |        }
      |      }
      |    }
      |  },
      |  "components": {
      |    "schemas": {
      |      "Book": {
      |        "type": "object",
      |        "description": "A book in the catalog.",
      |        "required": ["id", "title", "authors", "format", "price"],
      |        "properties": {
      |          "id": { "type": "string" },
      |          "title": { "type": "string" },
      |          "authors": { "type": "array", "items": { "$ref": "#/components/schemas/Author" } },
      |          "format": { "$ref": "#/components/schemas/BookFormat" },
      |          "price": { "type": "number" },
      |          "pages": { "type": "integer", "format": "int32" },
      |          "publishedOn": { "type": "string", "format": "date" },
      |          "subtitle": { "type": ["string", "null"] },
      |          "tags": { "type": "array", "items": { "type": "string" } }
      |        },
      |        "example": {
      |          "id": "zionomicon",
      |          "title": "Zionomicon",
      |          "authors": [{ "name": "John A. De Goes" }, { "name": "Adam Fraser" }, { "name": "Milad Khajavi" }],
      |          "format": "Ebook",
      |          "price": 59.99,
      |          "tags": ["zio", "functional-programming"]
      |        }
      |      },
      |      "Author": {
      |        "type": "object",
      |        "required": ["name"],
      |        "properties": {
      |          "name": { "type": "string" },
      |          "website": { "type": "string", "format": "uri" }
      |        }
      |      },
      |      "BookFormat": {
      |        "type": "string",
      |        "description": "How the book is delivered.",
      |        "enum": ["Hardcover", "Paperback", "Ebook"]
      |      },
      |      "Order": {
      |        "type": "object",
      |        "description": "A customer's order.",
      |        "required": ["id", "customerId", "placedAt", "lines", "payment", "status"],
      |        "properties": {
      |          "id": { "type": "string", "format": "uuid" },
      |          "customerId": { "type": "integer", "format": "int64" },
      |          "placedAt": { "type": "string", "format": "date-time" },
      |          "lines": { "type": "array", "items": { "$ref": "#/components/schemas/OrderLine" } },
      |          "payment": { "$ref": "#/components/schemas/Payment" },
      |          "status": { "$ref": "#/components/schemas/OrderStatus" },
      |          "giftMessage": { "type": "string" },
      |          "metadata": { "type": "object", "additionalProperties": { "type": "string" } }
      |        },
      |        "example": {
      |          "id": "3f0c1c5e-8a59-4d55-9b8e-2b2f0b6a4c11",
      |          "customerId": 1001,
      |          "placedAt": "2026-10-27T09:30:00Z",
      |          "lines": [
      |            { "bookId": "zionomicon", "quantity": 1, "unitPrice": 59.99 },
      |            { "bookId": "programming-in-scala-5", "quantity": 2, "unitPrice": 44.95 }
      |          ],
      |          "payment": { "method": "card", "last4": "4242" },
      |          "status": "Placed",
      |          "metadata": { "channel": "web" }
      |        }
      |      },
      |      "OrderLine": {
      |        "type": "object",
      |        "required": ["bookId", "quantity", "unitPrice"],
      |        "properties": {
      |          "bookId": { "type": "string" },
      |          "quantity": { "type": "integer", "format": "int32" },
      |          "unitPrice": { "type": "number" }
      |        }
      |      },
      |      "OrderStatus": {
      |        "type": "string",
      |        "enum": ["Placed", "Shipped", "Delivered", "Cancelled"]
      |      },
      |      "Payment": {
      |        "description": "How the customer paid.",
      |        "oneOf": [
      |          { "$ref": "#/components/schemas/CardPayment" },
      |          { "$ref": "#/components/schemas/GiftCardPayment" }
      |        ],
      |        "discriminator": {
      |          "propertyName": "method",
      |          "mapping": {
      |            "card": "#/components/schemas/CardPayment",
      |            "gift_card": "#/components/schemas/GiftCardPayment"
      |          }
      |        }
      |      },
      |      "CardPayment": {
      |        "type": "object",
      |        "required": ["method", "last4"],
      |        "properties": {
      |          "method": { "type": "string" },
      |          "last4": { "type": "string" }
      |        }
      |      },
      |      "GiftCardPayment": {
      |        "type": "object",
      |        "required": ["method", "code"],
      |        "properties": {
      |          "method": { "type": "string" },
      |          "code": { "type": "string" }
      |        }
      |      }
      |    }
      |  }
      |}""".stripMargin
}
