package io.chrisdavenport.http4s.log4cats

import munit.CatsEffectSuite
import cats.effect._
import cats.syntax.all._
import org.http4s._
import org.typelevel.log4cats.testing.StructuredTestingLogger
import org.typelevel.log4cats.extras._
import org.typelevel.log4cats.testing.StructuredTestingLogger.TRACE
import org.typelevel.log4cats.testing.StructuredTestingLogger.DEBUG
import org.typelevel.log4cats.testing.StructuredTestingLogger.INFO
import org.typelevel.log4cats.testing.StructuredTestingLogger.WARN
import org.typelevel.log4cats.testing.StructuredTestingLogger.ERROR

class MainSpec extends CatsEffectSuite {

  test("Successfully Create a Context Log") {
    val logger = StructuredTestingLogger.impl[IO]()

    val server = HttpRoutes.of[IO]{
      case _ =>  Response(Status.Ok).pure[IO]
    }.orNotFound

    val builder = ServerMiddleware.fromLogger(logger)

    val finalApp = builder.httpApp(server)

    (finalApp.run(Request[IO](Method.GET)) *> logger.logged).map{
      logged =>
      assertEquals(
        logged.map(removeDuration),
        Vector(
          INFO(
            "Http Server - GET",
            None,
            Map(

              "http.target" -> "/",
              "exit.case" -> "succeeded",
              "http.method" -> "GET",

              "http.status_code" -> "200",

              "http.host" -> "localhost",
              "http.flavor" -> "1.1",
              "http.url" -> "/"
            )
          )
        ))
    }
  }

  test("Successfully Create a Context Log with Body") {
    val logger = StructuredTestingLogger.impl[IO]()

    val server = HttpRoutes.of[IO]{
      case req => req.body.compile.drain >> Response[IO](Status.Ok).withEntity("Hello from Response!").pure[IO]
    }.orNotFound

    val builder = ServerMiddleware.fromLogger(logger)
      .withLogRequestBody(true)
      .withLogResponseBody(true)

    val finalApp = builder.httpApp(server)
    val request = Request[IO](Method.GET).withEntity("Hello from Request!")

    (finalApp.run(request).flatMap(_.body.compile.drain) *> logger.logged).map{
      logged =>
      assertEquals(
        logged.map(removeDuration),
        Vector(
          INFO(
            "Http Server - GET",
            None,
            Map(
              "http.response.header.content-length" -> "20",
              "http.target" -> "/",
              "exit.case" -> "succeeded",
              "http.method" -> "GET",
              "http.request_content_length" -> "19",
              "http.status_code" -> "200",
              "http.request.body" -> "Hello from Request!",
              "http.response.body" -> "Hello from Response!",
              "http.request.header.content-length" -> "19",
              "http.request.header.content-type" -> "text/plain; charset=UTF-8",
              "http.response.header.content-type" -> "text/plain; charset=UTF-8",
              "http.response_content_length" -> "20",
              "http.host" -> "localhost",
              "http.flavor" -> "1.1",
              "http.url" -> "/"
            )
          )
        ))
    }
  }

  test("Log Using the Body") {
    val logger = StructuredTestingLogger.impl[IO]()

    val server = HttpRoutes.of[IO]{
      case req => req.body.compile.drain >> Response[IO](Status.Ok).withEntity("Hello from Response!").pure[IO]
    }.orNotFound

    val builder = ServerMiddleware.fromLogger(logger)
      .withLogRequestBody(true)
      .withLogResponseBody(true)
      .withLogMessage{
        case (req, Outcome.Succeeded(Some(resp)), _) => s"Req Body - ${req.body.through(fs2.text.utf8.decode).compile.string}\nResp Body - ${resp.body.through(fs2.text.utf8.decode).compile.string}"
        case (_, _, _) => "Whoops!"
      }

    val finalApp = builder.httpApp(server)
    val request = Request[IO](Method.GET).withEntity("Hello from Request!")

    (finalApp.run(request).flatMap(_.body.compile.drain) *> logger.logged).map{
      logged =>
      assertEquals(
        logged.map(removeDuration),
        Vector(
          INFO(
            "Req Body - Hello from Request!\nResp Body - Hello from Response!",
            None,
            Map(
              "http.response.header.content-length" -> "20",
              "http.target" -> "/",
              "exit.case" -> "succeeded",
              "http.method" -> "GET",
              "http.request_content_length" -> "19",
              "http.status_code" -> "200",
              "http.request.body" -> "Hello from Request!",
              "http.response.body" -> "Hello from Response!",
              "http.request.header.content-length" -> "19",
              "http.request.header.content-type" -> "text/plain; charset=UTF-8",
              "http.response.header.content-type" -> "text/plain; charset=UTF-8",
              "http.response_content_length" -> "20",
              "http.host" -> "localhost",
              "http.flavor" -> "1.1",
              "http.url" -> "/"
            )
          )
        ))
    }
  }

  def removeDuration(lm: StructuredTestingLogger.LogMessage): StructuredTestingLogger.LogMessage = lm match {
    case TRACE(message, throwOpt, ctx) => TRACE(message, throwOpt, ctx - "http.duration_ms")
    case DEBUG(message, throwOpt, ctx) => DEBUG(message, throwOpt, ctx - "http.duration_ms")
    case INFO(message, throwOpt, ctx) => INFO(message, throwOpt, ctx - "http.duration_ms")
    case WARN(message, throwOpt, ctx) => WARN(message, throwOpt, ctx - "http.duration_ms")
    case ERROR(message, throwOpt, ctx) => ERROR(message, throwOpt, ctx - "http.duration_ms")
  }
  

}
