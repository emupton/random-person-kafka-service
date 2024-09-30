package com.example.backendservice

import cats.effect.IO
import cats.syntax.all._
import com.comcast.ip4s._
import com.example.backendservice.Models.KafkaConfig
import org.http4s.Response
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pureconfig.ConfigSource
import pureconfig.generic.auto._

object BackendserviceServer {

  def run: IO[Nothing] = {
    val logger: org.typelevel.log4cats.Logger[IO] = Slf4jLogger.getLogger
    val kafkaConfig = ConfigSource.default.at("kafka").loadOrThrow[KafkaConfig]
    val kafkaConsumerService =
      new KafkaConsumerService.LiveConsumerService[IO](kafkaConfig, logger)
    val httpApp =
      BackendserviceRoutes.topicRoutes[IO](kafkaConsumerService).orNotFound
    val finalHttpApp = Logger.httpApp(true, true)(httpApp)

    val errorHandler: PartialFunction[Throwable, IO[Response[IO]]] = (error) => {
      import org.http4s.dsl.io._
      // TODO: Fix, better error matching & error handling all around
      logger.error(error)(s"Exception throw: ${error.getMessage}"): Unit
      // todo: cleanup/JSON response
      InternalServerError("Unknown error".pure[IO])
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
