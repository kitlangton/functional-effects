package course.zio

import zio.{test => _, _}
import zio.test._

object ShardingSpec extends ZIOSpecDefault {
  val spec =
    suite("Aspec")(
      makeTest(1),
      makeTest(2),
      makeTest(3),
      makeTest(4)
    ) @@ TestAspect.silent

  def makeTest(id: Int) =
    test(s"test $id") {
      Console.printLine(s"STARTING TEST $id!") *>
        ZIO.sleep(2.seconds) *>
        Console
          .printLine(s"DONE TEST $id!")
          .as(
            assertTrue(true)
          )
    }
}
