package com.example.backendservice

import cats.effect.{IO, IOApp}
import cats.implicits.toTraverseOps
import com.example.backendservice.Models.{CtRoot, KafkaConfig, Person, Topic}
import io.circe.jawn
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pureconfig.ConfigSource
import pureconfig.generic.auto._

/*
* Can be run through  sbt "runMain com.example.backendservice.PopulateData". */

object PopulateData extends IOApp.Simple {

  private def readRandomPersonsFromJsonFile(): IO[CtRoot] =
    IO.delay(scala.io.Source.fromResource("random-people-data.json").mkString).flatMap { jsonString =>
      jawn.decode[CtRoot](jsonString)(CtRoot.testDataDecoder) match {
        case Right(ctRoot) => IO.pure(ctRoot)
        case Left(error) => IO.raiseError(new Exception(s"Failed to parse JSON: $error"))
      }
    }

  override def run: IO[Unit] = {
    implicit val logger: org.typelevel.log4cats.Logger[IO] = Slf4jLogger.getLogger[IO]
    val kafkaConfig = ConfigSource.default.at("kafka").loadOrThrow[KafkaConfig]
    val kafkaProducerService =
      new KafkaProducerService.LiveKafkaProducerService[IO](kafkaConfig, logger)
    kafkaProducerService.createProducer().use { producer =>
      for {
        persons: List[Person] <- readRandomPersonsFromJsonFile().map(
          _.ctRoot)
        _ <- persons.traverse { person =>
          kafkaProducerService
            .sendMessage[Person](producer, Topic.RandomPeople, person._id, person)
        }
      } yield ()
    }
  }

}
