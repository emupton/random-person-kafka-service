package com.example.backendservice

import cats.effect._
import cats.implicits._
import cats.implicits.catsSyntaxApplicativeError
import com.example.backendservice.Models.{KafkaConfig, Topic}
import io.circe.Encoder
import io.circe.syntax.EncoderOps
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer
import org.typelevel.log4cats.Logger

import java.util.Properties

trait KafkaProducerService[F[_]] {

  def createProducer(): Resource[F, KafkaProducer[String, String]]

  def sendMessage[T: Encoder](
      producer: KafkaProducer[String, String],
      topic: Topic,
      key: String,
      value: T): F[Unit]

}

object KafkaProducerService {

  final class LiveKafkaProducerService[F[_]: Sync](kafkaConfig: KafkaConfig, logger: Logger[F])
      extends KafkaProducerService[F] {
    override def createProducer(): Resource[F, KafkaProducer[String, String]] = {
      Resource.make {
        Sync[F].delay {
          val properties = new Properties()
          properties.setProperty(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafkaConfig.bootstrapServers)
          properties.setProperty(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            classOf[StringSerializer].getName)
          properties.setProperty(
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            classOf[StringSerializer].getName)

          val producer = new KafkaProducer[String, String](properties)
          logger.debug("Successfully created producer"): Unit
          producer
        }
      } { producer =>
        Sync[F].delay(producer.close())
      }
    }

    override def sendMessage[T: Encoder](
        producer: KafkaProducer[String, String],
        topic: Topic,
        key: String,
        value: T): F[Unit] = for {
      _ <- Sync[F]
        .delay {
          val kafkaRecord = new ProducerRecord[String, String](topic.entryName, key, value.asJson.show)
          producer.send(kafkaRecord)
          producer.flush()
        }
        .handleErrorWith(error =>
          logger.error(error)(s"Failed to send message ${error.getMessage}"))
      _ <- (logger.debug(s"Successfully sent message ${value}"))
    } yield ()
  }
}
