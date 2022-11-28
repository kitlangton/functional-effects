package course.zio

import zio._

import scala.concurrent.ExecutionContext
import java.io.IOException

object RuntimeRunning extends App {

  val effect = {
    ZIO.service[String] *>
      ZIO.debug("I AM AN EFFECT").as(throw new Error("HEY"))
  }

  Unsafe.unsafe { implicit seriously =>
    val exit = Runtime.default.unsafe.run(effect.provide(ZLayer.succeed("Cool")))
    println(exit)
  }

}

object CustomRuntime {
  final case class AppConfig(name: String)

  implicit val unsafe: Unsafe = Unsafe.unsafe(u => u)

  val defaultEnvironment: ZEnvironment[AppConfig with Boolean] =
    ZEnvironment(AppConfig("hello")) ++ ZEnvironment(true)
  val defaultRuntimeFlags = RuntimeFlags.default
  val defaultFiberRefs    = FiberRefs.empty

  /** EXERCISE
    *
    * Create a custom runtime that bundles a value of type `AppConfig` into the
    * environment.
    */
  lazy val customRuntime: Runtime[AppConfig with Boolean] =
    Runtime(defaultEnvironment, defaultFiberRefs, defaultRuntimeFlags)

  val layer: ZLayer[Any, Nothing, AppConfig with Boolean] =
    ZLayer.fromZIOEnvironment(ZIO.succeed(defaultEnvironment))

  val customRuntimeII: Runtime.Scoped[AppConfig with Boolean] =
    Runtime.unsafe.fromLayer(layer)

  val program: ZIO[AppConfig, IOException, Unit] =
    for {
      appConfig <- ZIO.service[AppConfig]
      _         <- Console.printLine(s"Application name is ${appConfig.name}")
      _         <- Console.printLine("What is your name?")
      name      <- Console.readLine
      _         <- Console.printLine(s"Hello, $name!")
    } yield ()

  /** EXERCISE
    *
    * Using the `unsafe.run` method of the custom runtime you created, execute
    * the `program` effect above.
    *
    * NOTE: You will have to use `Unsafe.unsafe { implicit u => ... }` or
    * `Unsafe.unsafe { ... }` (Scala 3) in order to call `run`.
    */
  def main(args: Array[String]): Unit =
    customRuntimeII.unsafe.run(program)
}

object ThreadPool extends ZIOAppDefault {

  lazy val dbPool: Executor = Executor.fromExecutionContext(ExecutionContext.global)

  /** EXERCISE
    *
    * Using `ZIO#onExecutor`, write an `onDatabase` combinator that runs the
    * specified effect on the database thread pool.
    */
  def onDatabase[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    zio.onExecutor(dbPool)

  /** EXERCISE
    *
    * Implement a combinator to print out thread information before and after
    * executing the specified effect.
    */
  def threadLogged[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] = {
    val log = ZIO.succeed {
      val thread = Thread.currentThread()

      val id        = thread.threadId()
      val name      = thread.getName()
      val groupName = thread.getThreadGroup().getName()

      println(s"Thread($id, $name, $groupName)")
    }

    log *> zio <* log
  }

  /** EXERCISE
    *
    * Use the `threadLogged` combinator around different effects below to
    * determine which threads are executing which effects.
    */
  val run =
    threadLogged(ZIO.debug("Main")) *>
      onDatabase {
        threadLogged(ZIO.debug("Database")) *>
          ZIO.blocking {
            threadLogged(ZIO.debug("Blocking"))
          } *>
          threadLogged(ZIO.debug("Database"))
      } *>
      threadLogged(ZIO.debug("Main"))
}

object CustomLogger extends ZIOAppDefault {
  // zio-logging
  // - slf4j

  /** EXERCISE
    *
    * Using `ZLogger.simple`, create a logger that dumps text strings to the
    * console using `println`.
    */
  def simpleLogger: ZLogger[String, Unit] =
    ZLogger.simple(string => println(string.toUpperCase))

  /** EXERCISE
    *
    * Create a layer that will install your simple logger using
    * Runtime.addLogger.
    */
  lazy val withCustomLogger: ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(simpleLogger)

  /** EXERCISE
    *
    * Using `ZIO#provide`, inject the custom logger into the following effect
    * and verify your logger is being used.
    */
  val run =
    ZIO
      .log("Hello World!")
      .provide(
        Runtime.addLogger(simpleLogger),
        Runtime.addLogger(simpleLogger)
      )
}

// ALSO: https://github.com/mlangc/zio-interop-log4j2
//
//object MDCInterop {
//  def withLoggingContext[A](eff: => A): ZIO[Any, Throwable, A] =
//    ZIO.logAnnotations.flatMap { ctx =>
//      ZIO.attempt {
//        import scala.jdk.CollectionConverters._
//        val previous =
//          Option(MDC.getCopyOfContextMap().asScala)
//            .getOrElse(Map.empty[String, String])
//
//        try {
//          ctx.renderContext.foreach((MDC.put _).tupled)
//          eff
//        } finally MDC.setContextMap(previous.asJava)
//      }
//    }
//}
