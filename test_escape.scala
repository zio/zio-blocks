val s = "\\u003c"
println("Length: " + s.length)
println("Chars:")
for (i <- 0 until s.length) {
  println(i + ": " + s(i))
}
println("String: " + s)
