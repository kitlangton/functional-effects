//package course.zio
//
//import course.zio.LayerEnvironment._
//import zio.test._
//import zio._
//import zio.macros.accessible
//
//final case class LoggingTest(ref: Ref[List[String]]) extends Logging {
//  override def log(line: String): UIO[Unit] =
//    ref.update(line :: _)
//
//  def logged: UIO[List[String]] = ref.get
//}
//
//object LoggingTest {
//  def logged: ZIO[LoggingTest, Nothing, List[String]] =
//    ZIO.serviceWithZIO[LoggingTest](_.logged)
//
//  val layer: ZLayer[Any, Nothing, LoggingTest] =
//    ZLayer {
//      for {
//        ref <- Ref.make(List.empty[String])
//      } yield LoggingTest(ref)
//    }
//}
//
////object DatabaseSpec extends ZIOSpecDefault {
////  val spec =
////    suite("DB")(
////      LayerEnvironmentSpec.spec,
////      LayerEnvironmentSpec.spec,
////      LayerEnvironmentSpec.spec,
////    ).provideShared(".....")
////}
//
//object LayerEnvironmentSpec {
//
//  val spec =
//    suite("ApplicationSpec")(
//      test("Application works great") {
//        for {
//          app    <- ZIO.service[Application]
//          _      <- FilesStub.write("build.sbt", "design")
//          _      <- app.run
//          logged <- LoggingTest.logged
//        } yield assertTrue(
//          logged.head == "design",
//          logged.length == 1
//        )
//      },
//      test("Application works horribly") {
//        for {
//          app    <- ZIO.service[Application]
//          result <- app.run.flip
//        } yield assertTrue(
//          result.getMessage == "File build.sbt not found"
//        )
//      }
//    )
//      .provide(
//        Application.layer,
//        Files.stub,
//        LoggingTest.layer
//      )
//}
