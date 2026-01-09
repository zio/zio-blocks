package zio.schema

// এটি একটি প্লেসহোল্ডার। যখন আমরা আসল zio-schema লাইব্রেরি যোগ করব,
// তখন এই ফাইলটি ডিলিট করে দেব। আপাতত কম্পাইল করার জন্য এটি দরকার।
trait DynamicValue

object DynamicValue {
  // টেস্টের সুবিধার জন্য কিছু ডামি ইমপ্লিমেন্টেশন
  case class Primitive[A](value: A) extends DynamicValue
  case object Error extends DynamicValue
}