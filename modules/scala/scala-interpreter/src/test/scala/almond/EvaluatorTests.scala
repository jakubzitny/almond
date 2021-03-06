package almond

import almond.TestUtil.SessionRunner
import almond.amm.AlmondPreprocessor
import almond.kernel.KernelThreads
import almond.util.ThreadUtil.{attemptShutdownExecutionContext, singleThreadedExecutionContext}
import utest._

object EvaluatorTests extends TestSuite {

  val interpreterEc = singleThreadedExecutionContext("test-interpreter")
  val bgVarEc = singleThreadedExecutionContext("test-bg-var")

  val threads = KernelThreads.create("test")

  override def utestAfterAll() = {
    threads.attemptShutdown()
    if (!attemptShutdownExecutionContext(interpreterEc))
      println(s"Don't know how to shutdown $interpreterEc")
  }

  val runner = new SessionRunner(interpreterEc, bgVarEc, threads)

  def ifVarUpdates(s: String): String =
    if (AlmondPreprocessor.isAtLeast_2_12_7) s
    else ""

  def ifNotVarUpdates(s: String): String =
    if (AlmondPreprocessor.isAtLeast_2_12_7) ""
    else s


  val tests = Tests {

    "from Ammonite" - {

      // These sessions were copy-pasted from ammonite.session.EvaluatorTests
      // Running them here to test our custom preprocessor.

      "multistatement" - {
        runner.run(
          Seq(
            ";1; 2L; '3';" ->
              """res0_0: Int = 1
                |res0_1: Long = 2L
                |res0_2: Char = '3'""".stripMargin,
            "val x = 1; x;" ->
              """x: Int = 1
                |res1_1: Int = 1""".stripMargin,
            "var x = 1; x = 2; x" ->
              """x: Int = 2
                |res2_2: Int = 2""".stripMargin,
            "var y = 1; case class C(i: Int = 0){ def foo = x + y }; new C().foo" ->
              """y: Int = 1
                |defined class C
                |res3_2: Int = 3""".stripMargin,
            "C()" -> "res4: C = C(0)"
          )
        )
      }

      "lazy vals" - {
        runner.run(
          Seq(
            "lazy val x = 'h'" -> "",
            "x" -> "res1: Char = 'h'",
            "var w = 'l'" -> ifNotVarUpdates("w: Char = 'l'"),
            "lazy val y = {w = 'a'; 'A'}" -> "",
            "lazy val z = {w = 'b'; 'B'}" -> "",
            "z" -> "res5: Char = 'B'",
            "y" -> "res6: Char = 'A'",
            "w" -> "res7: Char = 'a'"
          ),
          Seq(
            "x: Char = [lazy]",
            "x: Char = 'h'",
            ifVarUpdates("w: Char = 'l'"),
            "y: Char = [lazy]",
            "z: Char = [lazy]",
            ifVarUpdates("w: Char = 'b'"),
            "z: Char = 'B'",
            ifVarUpdates("w: Char = 'a'"),
            "y: Char = 'A'"
          ).filter(_.nonEmpty)
        )
      }

      "vars" - {
        runner.run(
          Seq(
            "var x: Int = 10" -> ifNotVarUpdates("x: Int = 10"),
            "x" -> "res1: Int = 10",
            "x = 1" -> "",
            "x" -> "res3: Int = 1"
          ),
          Seq(
            ifVarUpdates("x: Int = 10"),
            ifVarUpdates("x: Int = 1")
          ).filter(_.nonEmpty)
        )
      }
    }

    "type annotation" - {
      if (AlmondPreprocessor.isAtLeast_2_12_7)
        runner.run(
          Seq(
            "var x: Any = 2" -> "",
            "x = 'a'" -> ""
          ),
          Seq(
            "x: Any = 2",
            ifVarUpdates("x: Any = 'a'")
          )
        )
    }

    "pattern match still compile" - {
      // no updates for var-s defined via pattern matching
      runner.run(
        Seq(
          "var (a, b) = (1, 'a')" ->
            """a: Int = 1
              |b: Char = 'a'""".stripMargin,
          "a = 2" -> "",
          "b = 'c'" -> ""
        )
      )
    }
  }

}
