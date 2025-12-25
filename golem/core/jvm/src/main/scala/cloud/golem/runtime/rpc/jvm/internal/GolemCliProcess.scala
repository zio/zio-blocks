package cloud.golem.runtime.rpc.jvm.internal

import scala.sys.process.Process

private[rpc] object GolemCliProcess {
  def run(cwd: java.io.File, cmd: Seq[String]): Either[String, String] = {
    val out = new StringBuilder
    val exit =
      Process(cmd, cwd).!(
        scala.sys.process.ProcessLogger(
          line => out.append(line).append('\n'),
          line => out.append(line).append('\n')
        )
      )
    if (exit == 0) Right(out.result())
    else Left(s"Command failed (exit=$exit): ${cmd.mkString(" ")}\n$out")
  }
}