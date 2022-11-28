package course.zio

import zio._

import java.io.IOException

/** ZIO environment is a type-indexed map that allows you to store a number of
  * objects of different types. ZIO calls these objects "services", because they
  * contain bundles of functionality consumed your application.
  */
object TypeIndeedMap extends ZIOAppDefault {
  trait Logging
  case object Logging extends Logging {
    override def toString: String = "GREG"
  }

  trait Database
  case object Database extends Database

  trait Cache
  case object Cache extends Cache

  val envLogging = ZEnvironment(Logging: Logging)

  val envDatabase = ZEnvironment(Database: Database)

  val envCache = ZEnvironment(Cache: Cache)

  /** EXERCISE
    *
    * Using the `++` operator on `ZEnvironment`, combine the three maps
    * (`envLogging`, `envDatabase`, and `envCache`) into a single map that has
    * all three objects.
    */
  val allThree: ZEnvironment[Database with Cache with Logging] =
    envLogging ++ envDatabase ++ envCache

  /** EXERCISE
    *
    * Using `ZEnvironment#get`, which can retrieve an object stored in the map,
    * retrieve the logging, database, and cache objects from `allThree`. Note
    * that you will have to specify the type parameter, as it cannot be inferred
    * (the map needs to know which of the objects you want to retrieve, and that
    * can be specified only by type).
    */
  lazy val logging  = allThree.get[Logging]
  lazy val database = allThree.get[Database]
  lazy val cache    = allThree.get[Cache]
//  lazy val boolean  = allThree.get[Boolean]

  val run = for {
    _ <- ZIO.debug(logging)
    _ <- ZIO.debug(database)
    _ <- ZIO.debug(cache)
  } yield ()
}

object SilylFunctions extends App {
  // R is like function args
  def iAmFunction(need: Int): Int = need + 10

  // provide is like function application
  iAmFunction(10)
}

object AccessEnvironment extends ZIOAppDefault {

  final case class Config(host: String, port: Int)

  /** EXERCISE
    *
    * Using `ZIO.service`, access a `Config` service from the environment, and
    * extract the `host` field from it.
    */
  val accessHost: ZIO[Config, Nothing, String] =
    for {
      config <- ZIO.service[Config]
    } yield config.host

  /** EXERCISE
    *
    * Using `ZIO.service`, access a `Config` service from the environment, and
    * extract the `port` field from it.
    */
  val accessPort: ZIO[Config, Nothing, Int] =
    ZIO.serviceWith[Config](_.port)

  val program: ZIO[Config, IOException, Unit] = for {
    host <- accessHost
    port <- accessPort
    _    <- Console.printLine(s"Configuration: $host:$port")
  } yield ()

  val config = Config("localhost", 7878)

//  val readConfigFromTypesafeConfig: Task[Config] = ???
  val elim: ZIO[Any, IOException, Unit] =
    program.provideEnvironment(ZEnvironment(config))

  val run = elim
}

object ProvideEnvironment extends ZIOAppDefault {

  final case class Config(server: String, port: Int)

  final case class DatabaseConnection() {
    def query(query: String): Task[Int] = ZIO.attempt(42)
  }

  val getServer: ZIO[Config, Nothing, String] =
    ZIO.service[Config].map(_.server)

  val useDatabaseConnection: ZIO[DatabaseConnection, Throwable, Int] =
    ZIO.serviceWithZIO[DatabaseConnection](_.query("SELECT * FROM USERS"))

  /** EXERCISE
    *
    * Compose both the `getServer` and `useDatabaseConnection` effects together
    * and run them. In order to do this successfully, you will have to use
    * `ZIO#provideEnvironment` to give them the environment that they need in
    * order to run.
    */
  val run = {
    val config     = Config("localhost", 7878)
    val connection = DatabaseConnection()
    val wholeEnv: ZEnvironment[Config with DatabaseConnection] =
      ZEnvironment(config) ++ ZEnvironment(connection)

    val zipped: ZIO[DatabaseConnection with Config, Throwable, (String, RuntimeFlags)] =
      getServer
        .zip(useDatabaseConnection)

    zipped.debug
      .provideEnvironment(wholeEnv)
  }
}

/** In ZIO, layers are values that contain construction logic for services in
  * your application. Services provide functionality like persistence or logging
  * or authentication, and they are used by business logic.
  *
  * A layer is a lot like a constructor, but may have complex initialization or
  * finalization, or may produce more than one service.
  *
  * ZIO has compile-time, type-safe wiring up of layers, which allows you to
  * optionally use ZIO for dependency-injection. The wire-up of layers is done
  * in a resource-safe, failure-aware way, with maximum parallelism to decrease
  * application startup time.
  *
  * Layers bring more power and compositionality to constructors. Although you
  * don't have to make your own layers to benefit from ZIO, layers can make it
  * easier and safer to assemble applications out of modules.
  */
object LayerEnvironment extends ZIOAppDefault {

  import java.io.IOException

  final case class Config(specialNumber: Int)

  object Config {
    // zio-config
    val live: ZLayer[Any, Nothing, Config] = ZLayer.scoped {
      for {
        config <-
          Console
            .readLine("Gimme a special number")
            .orDie
            .map(_.toInt)
            .map(Config(_))
        _ <- ZIO.addFinalizer(ZIO.debug(s"I am shutting down $config"))
      } yield config
    }
  }

  trait Files {
    def read(file: String): IO[IOException, String]
  }

  final case class FilesStub(files: Ref[Map[String, String]]) extends Files {

    def write(fileName: String, content: String) =
      files.update(_ + (fileName -> content))

    def clearAll =
      files.set(Map.empty)

    override def read(file: String): IO[IOException, String] =
      files.get.flatMap { files =>
        ZIO
          .fromOption(files.get(file))
          .orElseFail(new IOException(s"File $file not found"))
      }

  }

  object FilesStub {
    val clearAll =
      ZIO.serviceWithZIO[FilesStub](_.clearAll)

    def write(fileName: String, content: String) =
      ZIO.serviceWithZIO[FilesStub](_.write(fileName, content))
  }

  final case class FilesLive(
      config: Config,
      logging: Logging,
      ref: Ref[Int],
      connectionPool: ConnectionPool[DatabaseBaseType.PostgresType]
  ) extends Files {
    println(s"Files I have a connection pool: $connectionPool")

    override def read(file: String): IO[IOException, String] =
      ref.update(_ + 1) *>
        ZIO.readFile(file)
  }

  object Files {
    val live: ZLayer[ConnectionPool[DatabaseBaseType.PostgresType] with Logging with Config, Nothing, FilesLive] =
      ZLayer(Ref.make(0)) >>> ZLayer.fromFunction(FilesLive.apply _)

    val stub =
      ZLayer {
        Ref.make(Map.empty[String, String]).map(FilesStub(_))
      }
  }

  sealed trait DatabaseBaseType extends Product with Serializable

  object DatabaseBaseType {
    trait PostgresType extends DatabaseBaseType
    trait MySqlType    extends DatabaseBaseType
  }

  final case class ConnectionPool[T <: DatabaseBaseType](name: String)

  object ConnectionPool {

    val postgresPool: ZLayer[Any, Nothing, ConnectionPool[DatabaseBaseType.PostgresType]] = ZLayer.scoped {
      ZIO.acquireRelease {
        ZIO.succeed(ConnectionPool[DatabaseBaseType.PostgresType]("postgres")).debug("CREATING POOL")
      } { _ =>
        ZIO.debug("CLOSING MY POOL")
      }
    }

    val mySqlPool: ZLayer[Any, Nothing, ConnectionPool[DatabaseBaseType.MySqlType]] = ZLayer.scoped {
      ZIO.acquireRelease {
        ZIO.succeed(ConnectionPool[DatabaseBaseType.MySqlType]("mysql")).debug("CREATING POOL")
      } { _ =>
        ZIO.debug("CLOSING MY POOL")
      }
    }

  }

  trait Logging {
    def log(line: String): UIO[Unit]
  }

  final case class LoggingLive(connectionPool: ConnectionPool[DatabaseBaseType.MySqlType]) extends Logging {
    println(s"Logging has a connection pool: $connectionPool")

    override def log(line: String): UIO[Unit] =
      Console.printLine(line).orDie
  }

  final case class Application(files: Files, logging: Logging) {
    def run: IO[IOException, Unit] =
      for {
        file <- files.read("build.sbt")
        _    <- logging.log(file)
      } yield ()
  }

  object Application {
    val layer: ZLayer[Files with Logging, Nothing, Application] =
      ZLayer.fromFunction(Application.apply _)
  }

  object Logging {

    /** EXERCISE
      *
      * Using `ZLayer.fromFunction`, create a layer that requires `Console` and
      * uses the console to provide a logging service.
      */
    val live =
      ZLayer.fromFunction(LoggingLive.apply _)
  }

  /** EXERCISE
    *
    * Discover the inferred type of `effect`, and write it out explicitly.
    */
//  val effect: ZIO[Logging with Files, IOException, Unit] =
//    for {
//      files   <- ZIO.service[Files]
//      logging <- ZIO.service[Logging]
//      file    <- files.read("build.sbt")
//      _       <- logging.log(file)
//    } yield ()

  val booleanLayer = ZLayer.succeed(true)

  val run =
    ZIO
      .serviceWithZIO[Application](_.run)
      .provide(
        ZLayer.Debug.mermaid,
        Application.layer,
        Files.live,
        Logging.live,
        Config.live,
        ConnectionPool.postgresPool,
        ConnectionPool.mySqlPool
      )

}
