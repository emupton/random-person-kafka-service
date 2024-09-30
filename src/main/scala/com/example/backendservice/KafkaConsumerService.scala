package com.example.backendservice

import cats.effect._
import cats.implicits._
import com.example.backendservice.Models.{KafkaConfig, Topic}
import io.circe.{Decoder, jawn}
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.typelevel.log4cats.Logger

import java.util.Properties

trait KafkaConsumerService[F[_]] {
  def createConsumer(topic: Topic, maxRecords: Int): Resource[F, KafkaConsumer[String, String]]

  def consume[T: Decoder](
      consumer: KafkaConsumer[String, String],
      topic: Topic,
      offset: Long): F[Map[Int, List[T]]]

}

object KafkaConsumerService {

  final class LiveConsumerService[F[_]: Sync](kafkaConfig: KafkaConfig, logger: Logger[F])
      extends KafkaConsumerService[F] {

    override def createConsumer(
        topic: Topic,
        maxRecords: Int): Resource[F, KafkaConsumer[String, String]] = {
      Resource.make {
        for {
          consumer <- Sync[F].delay {
            val properties = new Properties()
            properties.setProperty(
              ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
              kafkaConfig.bootstrapServers)
            properties.setProperty(
              ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
              classOf[StringDeserializer].getName)
            properties.setProperty(
              ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
              classOf[StringDeserializer].getName)
            properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, kafkaConfig.clientId)
            properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
            /* INFO: Not sure if it's best to control the number of records polled this way,
              or aggregate them in memory via subsequent polls tail recursively in the consume function
             */
            properties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxRecords.toString)
            val consumer = new KafkaConsumer[String, String](properties)
            consumer
          }
          _ <- logger.info(s"Successfully created consumer for ${topic}")
        } yield consumer
      } { consumer =>
        Sync[F].delay {
          consumer.close()
        }
      }
    }

    override def consume[T](consumer: KafkaConsumer[String, String], topic: Topic, offset: Long)(
        implicit decoder: Decoder[T]): F[Map[Int, List[T]]] = {
      import scala.jdk.CollectionConverters._
      // Retrieve from admin client number of partitions
      val topicPartitions =
        Range
          .inclusive(1, kafkaConfig.numberOfPartitions)
          .map(n => new TopicPartition(topic.entryName, n))
          .toList

      for {
        // Assign partitions and set offset
        _ <- Sync[F].delay(consumer.assign(topicPartitions.asJava))
        _ <- topicPartitions.traverse { tp: TopicPartition =>
          Sync[F].delay(consumer.seek(tp, offset))
        }

        // Poll for records
        records <- Sync[F].delay(consumer.poll(java.time.Duration.ofMillis(5000)))

        // Parse records to type T
        parsedRecords <- topicPartitions
          .traverse { tp: TopicPartition =>
            Sync[F].delay {
              val decoded: List[Option[T]] = records.records(tp).asScala.toList.map { record =>
                val rawRecord = record.value()
                jawn
                  .parse(rawRecord)
                  .flatMap(_.as[T]) match {
                  case Left(error) =>
                    logger.error(
                      s"Error decoding record: ${error.getMessage}, raw record: ${rawRecord}"): Unit
                    None
                  case Right(value) =>
                    logger.info(s"Successfully decoded record: ${record.value()}"): Unit
                    Some(value)
                }
              }

              tp.partition() -> decoded.flatten
            }
          }
          .map(_.toMap)
      } yield parsedRecords
    }
  }
}
