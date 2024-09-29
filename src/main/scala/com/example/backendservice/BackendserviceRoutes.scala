package com.example.backendservice

import cats.effect.Sync
import cats.implicits._
import com.example.backendservice.Models.Person
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.dsl.Http4sDsl

object BackendserviceRoutes {

  def topicRoutes[F[_]: Sync](K: KafkaConsumerService[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    object CountQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("count")

    HttpRoutes.of[F] {
      case GET -> Root / "topic" / topicName / offset :? CountQueryParamMatcher(count) =>
        // Handle the request with topicName, optional offset, and optional count
        for {
          records <- K.createConsumer(topicName, count.getOrElse(500)).use { consumer =>
            K.consume[Person](consumer, topicName, offset.toLong)
          }
          resp <- Ok(records)
        } yield resp
    }
  }
}
