package com.example.backendservice

import cats.effect._
import cats.implicits._
import com.example.backendservice.Models.{KafkaConfig, Topic}
import io.circe
import io.circe.{Decoder, jawn}
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.typelevel.log4cats.Logger

import java.util.Properties
import scala.jdk.CollectionConverters.IterableHasAsJava

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
          consumer <- Sync[F]
            .delay {
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
              or aggregate them in memory via subsequent polls tail recursively in the consume function.
              The current solution does not scale for fetching an extremely large volume that would take >5 s to fetch
              ( though arguably such data you would not fetch from a rest api regardless)
               */
              properties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxRecords.toString)
              val consumer = new KafkaConsumer[String, String](properties)
              consumer
            }
            .onError(error => logger.error(error)(s"Failed to create kafka consumer"))
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

      for {
        numberOfPartitions <- numberOfPartitionsInTopic(topic)
        topicPartitions = Range
          .inclusive(0, numberOfPartitions - 1)
          .map(n => new TopicPartition(topic.entryName, n))
          .toList
        _ <- Sync[F].delay(consumer.assign(topicPartitions.asJava))
        _ <- topicPartitions.traverse { tp: TopicPartition =>
          Sync[F]
            .delay(consumer.seek(tp, offset))
            .flatMap(_ =>
              logger.info(s"Set offset for ${tp.topic()}-${tp.partition()}: ${offset}"))
        }
        records <- Sync[F]
          .delay(consumer.poll(java.time.Duration.ofMillis(5000)))
          .flatMap(res => logger.info(s"Polling records").map(_ => res))
        _ <- records.asScala.toList.traverse { record: ConsumerRecord[String, String] =>
          logger.info(s"Record retrieved, offset [${record.offset()}] partition [${record
              .partition()}] [${record.value()}]")
        }
        parsedRecords <- topicPartitions
          .traverse { tp => parseRecords(tp, records) }
          .map(_.toMap)
      } yield parsedRecords
    }

    private def numberOfPartitionsInTopic(topic: Topic): F[Int] = {
      // really, really boiler platey, just trying to be consistent
      Resource
        .make {
          for {
            adminClient <- Sync[F]
              .delay {
                val properties = new Properties()
                properties.put("bootstrap.servers", kafkaConfig.bootstrapServers)
                AdminClient.create(properties)
              }
              .onError(error =>
                logger.error(error)(
                  "Failed to create admin client when retrieving topic partition count"))
            _ <- logger.info("Successfully created admin client to fetch topic partitions")
          } yield adminClient
        } { adminClient =>
          Sync[F].delay(adminClient.close())
        }
        .use { adminClient =>
          Sync[F].delay {
            adminClient
              .describeTopics(List(topic.entryName).asJavaCollection)
              .topicNameValues()
              .get(topic.entryName)
              .get()
              .partitions()
              .size()
          }
        }
    }

    private def parseRecords[T](
        topicPartition: TopicPartition,
        records: ConsumerRecords[String, String])(implicit
        decoder: Decoder[T]): F[(Int, List[T])] = {
      import scala.jdk.CollectionConverters._
      for {
        decodedRecordResults: Seq[Either[circe.Error, T]] <- Sync[F].delay {
          records.records(topicPartition).asScala.toList.map { record =>
            val rawRecord = record.value()
            jawn
              .parse(rawRecord)
              .flatMap(_.as[T])
          }
        }
        _ <- decodedRecordResults.traverse {
          case Left(error) =>
            logger.error(error)(s"Failed to decode a record, ${error.toString}")
          case Right(_) =>
            /*In practice I'd never log this much data, or only at debug level, but task description placed emphasis
             on it*/
            logger.info(
              s"Successfully consumed record for  ${topicPartition.topic()}${topicPartition.partition()}")
        }
      } yield topicPartition.partition() -> decodedRecordResults.collect { case Right(value) =>
        value
      }.toList
    }
  }
}
