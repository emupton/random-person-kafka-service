package com.example.backendservice

import cats.effect.IO
import cats.syntax.all._
import com.comcast.ip4s._
import org.http4s.Response
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object BackendserviceServer {

  def run: IO[Nothing] = {
    implicit val logger: org.typelevel.log4cats.Logger[IO] = Slf4jLogger.getLogger[IO]
    val kafkaConfig = KafkaConfig("127.0.0.1:9092", "my-application", 3)
    val kafkaConsumerService =
      new KafkaConsumerService.LiveConsumerService[IO](kafkaConfig, logger)
    val httpApp =
      BackendserviceRoutes.topicRoutes[IO](kafkaConsumerService).orNotFound
    val finalHttpApp = Logger.httpApp(true, true)(httpApp)

    val errorHandler: PartialFunction[Throwable, IO[Response[IO]]] = (error) => {
      error.printStackTrace()
      import org.http4s.dsl.io._
      // TODO: Fix, better error matching, etc. Use logger
      logger.error(error)(s"Exception throw: ${error.getMessage}"): Unit
      InternalServerError("sad".pure[IO])
    }

    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(finalHttpApp)
      .withErrorHandler(errorHandler)
      .build
  }.useForever
}
