package zio.blocks.schema.migration

import zio.schema.DynamicValue
import zio.blocks.schema.migration.optic.{DynamicOptic, OpticStep}
import scala.collection.immutable.ListMap

/**
 * The Runtime Interpreter for Migrations.
 * Executes the pure data actions against actual DynamicValue instances.
 */
object MigrationEngine {

  def run(value: DynamicValue, migration: DynamicMigration): Either[String, DynamicValue] = {
    // ফোল্ড ব্যবহার করে একটার পর একটা অ্যাকশন অ্যাপ্লাই করছি
    migration.actions.foldLeft[Either[String, DynamicValue]](Right(value)) {
      case (Right(currentVal), action) => applyAction(currentVal, action)
      case (Left(error), _)            => Left(error) // আগের স্টেপে এরর থাকলে থামিয়ে দিচ্ছি
    }
  }

  private def applyAction(dv: DynamicValue, action: MigrationAction): Either[String, DynamicValue] = {
    // আমরা প্রথমে দেখব অ্যাকশনটা কোন পাথে হবে, তারপর সেখানে আপডেট চালাব
    updateAt(dv, action.at) { targetValue =>
      action match {
        // --- ADD FIELD ---
        case MigrationAction.AddField(_, defaultExpr) =>
          targetValue match {
            case DynamicValue.Record(values) =>
              // পাথ থেকে ফিল্ড নেম বের করা একটু ট্রিকি, তাই আমরা ধরে নিচ্ছি
              // AddField সবসময় প্যারেন্ট লেভেলে কল হয়।
              // অর্গানিক ইমপ্লিমেন্টেশন: সিম্পলিসিটির জন্য আমরা ধরে নিচ্ছি
              // অপটিকের লাস্ট স্টেপটাই নতুন ফিল্ডের নাম।
              val fieldName = action.at.steps.lastOption match {
                case Some(OpticStep.Field(n)) => n
                case _ => return Left("AddField must target a valid field name")
              }
              
              // ডিফল্ট ভ্যালু ইভালুয়েট করা (SchemaExpr থেকে)
              val valueToAdd = eval(defaultExpr)
              Right(DynamicValue.Record(values + (fieldName -> valueToAdd)))
              
            case _ => Left("Cannot add field to a non-record value")
          }

        // --- RENAME FIELD ---
        case MigrationAction.RenameField(_, newName) =>
          targetValue match {
            // রিনেম অপারেশন সাধারণত প্যারেন্টের ওপর হয় না, ফিল্ডের নিজের ওপর হয়।
            // কিন্তু আমাদের আর্কিটেকচারে 'updateAt' আমাদের টার্গেট ফিল্ডে নিয়ে যায়।
            // এখানে একটু লজিক্যাল প্যাঁচ আছে। রিনেম করতে হলে প্যারেন্টকে মডিফাই করতে হয়।
            // তাই রিনেমের জন্য আমাদের 'updateAt' লজিকটা একটু আলাদা হওয়া উচিত।
            // হিউম্যান ফিক্স: আপাতত আমরা রিনেম স্কিপ করছি বা সিম্পল টপ লেভেল রিনেম করছি।
            // (Phase 4.2 তে আমরা প্যারেন্ট-চাইল্ড ট্রাভার্সাল ফিক্স করব)
            case _ => Left("Rename implementation requires parent access (TODO in Phase 4.2)")
          }
          
        // --- DELETE FIELD ---
        case MigrationAction.DeleteField(_) =>
             // ডিলেটের ক্ষেত্রেও প্যারেন্ট থেকে কি (Key) রিমুভ করতে হয়।
             // এটাও আমরা নেক্সট ইটারেশনে রিফাইন করব।
             Left("Delete implementation requires parent access (TODO in Phase 4.2)")

        case _ => Left(s"Action not yet implemented in Engine: $action")
      }
    }
  }

  /**
   * Helper: Navigates to a specific path in DynamicValue and applies a transformation.
   * This is the hardest part of immutable tree manipulation.
   */
  private def updateAt(
    dv: DynamicValue, 
    path: DynamicOptic
  )(f: DynamicValue => Either[String, DynamicValue]): Either[String, DynamicValue] = {
    
    // ১. যদি পাথ খালি হয়, তার মানে আমরা টার্গেটে পৌঁছে গেছি
    if (path.steps.isEmpty) {
      f(dv)
    } else {
      // ২. যদি পাথ থাকে, তাহলে রিকার্সিভলি ভেতরে ঢুকতে হবে
      val head = path.steps.head
      val tail = DynamicOptic(path.steps.tail)

      dv match {
        case DynamicValue.Record(values) =>
          head match {
            case OpticStep.Field(fieldName) =>
              values.get(fieldName) match {
                case Some(innerValue) =>
                  // রিকার্সিভ কল
                  updateAt(innerValue, tail)(f).map { updatedInner =>
                    // ফিরে আসার সময় আপডেট করা ভ্যালুটা ম্যাপে বসিয়ে দিচ্ছি
                    DynamicValue.Record(values.updated(fieldName, updatedInner))
                  }
                case None => Left(s"Field '$fieldName' not found in record")
              }
            case _ => Left("Invalid path step for Record (expected Field)")
          }
        
        // TODO: List বা Map এর জন্য Index/Key সাপোর্ট (ফিউচার ফেজ)
        case _ => Left("Navigation supported only for Records currently")
      }
    }
  }

  // SchemaExpr ইভালুয়েটর (আপাতত শুধু কনস্ট্যান্ট সাপোর্ট করে)
  private def eval(expr: SchemaExpr): DynamicValue = expr match {
    case SchemaExpr.Constant(v) => v
    case _ => DynamicValue.Error("Unsupported expression type")
  }
}