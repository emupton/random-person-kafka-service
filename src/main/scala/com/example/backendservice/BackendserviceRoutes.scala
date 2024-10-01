package com.example.backendservice

import cats.effect.Sync
import cats.effect.kernel.Async
import cats.implicits._
import com.example.backendservice.Models.{ApiResponse, PartitionPersonRecordMappings, Person, Topic}
import org.http4s.HttpRoutes
import sttp.tapir._
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe._
import sttp.tapir.server.http4s.Http4sServerInterpreter

object BackendserviceRoutes {

  def topicRoutes[F[_]: Async](K: KafkaConsumerService[F]): HttpRoutes[F] = {
    val topicEndpoint: Endpoint[Unit, (Topic, Long, Option[Int]), String, ApiResponse, Any] =
      endpoint.get
        .in("topic" / path[Topic]("topic"))
        .in(path[Long]("offset"))
        .in(
          query[Option[Int]]("count")
            .description("Optional count parameter")
            .validateOption(Validator.max(10000)))
        .out(jsonBody[ApiResponse])
        .errorOut(stringBody)

    val logic: (Topic, Option[Int], Long) => F[Map[Int, List[Person]]] = {
      case (topic, countOpt, offset) =>
        for {
          count <- Sync[F].pure(countOpt.getOrElse(500))
          records <- K.createConsumer(topic, count).use { consumer =>
            K.consume[Person](consumer, topic, offset)
          }
        } yield records
    }

    Http4sServerInterpreter[F]().toRoutes(topicEndpoint.serverLogic {
      case (topic, countOpt, offset) =>
        logic(topic, offset, countOpt).attempt.map {
          case Right(records) =>
            Right(ApiResponse(records.map { case (i, persons) =>
              PartitionPersonRecordMappings(i, persons)
            }.toList))
          case Left(_) =>
            Left("An error occurred while consuming records.")
        }
    })
  }
}
