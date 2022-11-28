package course.zio

import zio._
import zio.metrics.Metric

object SimpleLogging extends ZIOAppDefault {

  /** EXERCISE
    *
    * Add logging using `ZIO.log` around each update of the ref.
    */
  val program =
    for {
      ref <- Ref.make(0)
      _ <- ZIO.foreachParDiscard(0 to 4) { _ =>
             ZIO.log("About to increment") *>
               ref.update(_ + 1)
           }
      value <- ref.get
    } yield value

  /** EXERCISE
    *
    * Surround `program` in `LogLevel.Error` to change its log level.
    */
  val program2: ZIO[Any, Nothing, Int] =
    LogLevel.Error {
      program
    }

  val run = program *> program2

}

object LogSpan extends ZIOAppDefault {

  final case class User(id: Int, name: String, passHash: String)

  /** EXERCISE
    *
    * Add a log span of "createUser" to the whole function.
    */
  def createUser(userName: String, passHash: String): ZIO[Any, Nothing, User] =
    ZIO.logSpan("createUser") {
      for {
        _  <- ZIO.sleep(1.second)
        _  <- FiberRef.currentLogSpan.get.debug("SPANS") // TODO: A Public Method
        _  <- ZIO.log(s"Creating user $userName")
        id <- Random.nextIntBounded(100)
      } yield User(id, userName, passHash)
    }

  /** EXERCISE
    *
    * Add a log span of "run" to the for comprehension
    */
  val run =
    ZIO.logSpan("run") {
      ZIO.logAnnotate("MAIN", "YES") {
        for {
          _ <- ZIO.logAnnotations.debug("ANNS")
          _ <- ZIO.log(s"Starting App")
          _ <- ZIO.sleep(1.second)
          _ <-
            ZIO.logAnnotate("correlation-id", "GREG") {
              createUser("sherlockholmes", "jkdf67sf6")
            } zipPar ZIO.logAnnotate("correlation-id", "KYLE") {
              createUser("sherlockholmes", "jkdf67sf6")
            }
          _ <- ZIO.log(s"Closing App")
        } yield ()
      }
    }
}

object CounterExample extends ZIOAppDefault {
  final case class Request(body: String)
  final case class Response(body: String)

  /** EXERCISE
    *
    * Use the constructors in `Metric` to make a counter metric that accepts
    * integers as input.
    */
  lazy val requestCounter =
    Metric.counter("rides").fromConst(1)

  /** EXERCISE
    *
    * Use methods on the counter to increment the counter on every request.
    */
  def processRequest(request: Request): Task[Response] =
    ZIO.debug(s"Processing ride request: $request") *>
      ZIO.succeed(Response("OK")) @@ requestCounter

  /** EXERCISE
    *
    * Use methods on the counter to print out its value.
    *
    * NOTE: In real applications you don't need to poll metrics because they
    * will be exported to monitoring systems.
    */
  lazy val printCounter: ZIO[Any, Nothing, Unit] =
    requestCounter.value.debug("COUNT").unit

  lazy val run = {
    val processor = processRequest(Request("input")).repeat(Schedule.spaced(400.millis).jittered)
    val printer   = printCounter.schedule(Schedule.fixed(1.second))

    processor.race(printer)
  }
}
