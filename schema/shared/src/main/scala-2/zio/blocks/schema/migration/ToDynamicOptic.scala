package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

trait ToDynamicOptic[S, A] {
  def apply(): DynamicOptic
}

object ToDynamicOptic {
  // কম্পাইলারকে শান্ত করার জন্য ইমপোর্টগুলো অবজেক্টের ভেতরে আনা হয়েছে
  import scala.language.experimental.macros
  import scala.language.implicitConversions

  /**
   * ল্যাম্বডা (_.field) থেকে ToDynamicOptic তৈরি করার ব্রিজ।
   */
  implicit def derive[S, A](selector: S => A): ToDynamicOptic[S, A] = 
    macro zio.blocks.schema.migration.macros.AccessorMacros.deriveImpl[S, A]
}