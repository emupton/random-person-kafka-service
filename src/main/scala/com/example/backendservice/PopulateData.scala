package com.example.backendservice

import cats.effect.{IO, IOApp}
import cats.implicits.{toShow, toTraverseOps}
import com.example.backendservice.Models.{CtRoot, Person}
import io.circe.{Codec, Decoder, HCursor, jawn}
import io.circe.generic.semiauto._
import io.circe.syntax.EncoderOps
import org.typelevel.log4cats.slf4j.Slf4jLogger

object PopulateData extends IOApp.Simple {

  // Compile the stream into a single string
  def readRandomPersonsFromJsonFile(filePath: String): IO[CtRoot] =
    IO.delay(scala.io.Source.fromResource(filePath).mkString).flatMap { jsonString =>
      // Decode the JSON string into CtRoot
      jawn.decode[CtRoot](jsonString)(CtRoot.testDataDecoder) match {
        case Right(ctRoot) => IO.pure(ctRoot)
        case Left(error) => IO.raiseError(new Exception(s"Failed to parse JSON: $error"))
      }
    }

  override def run: IO[Unit] = {
    implicit val logger: org.typelevel.log4cats.Logger[IO] = Slf4jLogger.getLogger[IO]
    val kafkaConfig = KafkaConfig("127.0.0.1:9092", "my-application", 3)
    val kafkaProducerService =
      new KafkaProducerService.LiveKafkaProducerService[IO](kafkaConfig, logger)
    kafkaProducerService.createProducer().use { producer =>
      for {
        persons: List[Person] <- readRandomPersonsFromJsonFile("random-people-data.json").map(
          _.ctRoot)
        _ <- persons.traverse { person =>
          kafkaProducerService
            .sendMessage(producer, "random_people", person._id, person.asJson.show)
        }
      } yield ()
    }
  }

}
