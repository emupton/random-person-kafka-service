package com.example.backendservice

import cats.effect._
import cats.implicits._
import cats.implicits.catsSyntaxApplicativeError
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer
import org.typelevel.log4cats.Logger

import java.util.Properties

trait KafkaProducerService[F[_]] {

  def createProducer(): Resource[F, KafkaProducer[String, String]]

  def sendMessage(
      producer: KafkaProducer[String, String],
      topic: String,
      key: String,
      value: String): F[Unit]

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

    override def sendMessage(
        producer: KafkaProducer[String, String],
        topic: String,
        key: String,
        value: String): F[Unit] = for {
      _ <- Sync[F]
        .delay {
          val kafkaRecord = new ProducerRecord[String, String](topic, key, value)
          producer.send(kafkaRecord)
          producer.flush()
        }
        .handleErrorWith(error =>
          logger.error(error)(s"Failed to send message ${error.getMessage}"))
      _ <- (logger.info(s"Successfully sent message ${value}"))
    } yield ()
  }
}
