package com.example.backendservice

import io.circe.{Codec, Decoder, HCursor}
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder, deriveEncoder}
import enumeratum._
import sttp.tapir.codec.enumeratum.TapirCodecEnumeratum

/* In practice, with less time constraints, these would be in their own package
 * as separate files. I've also had mixed experiences with this library:
 * https://github.com/fthomas/refined
 * I think it's good for a greenfield project, but if you're working with pre-existing data
 * it can encourage engineers to be overzealous in the constraints they place on data.
 */
object Models {
  final case class Address(street: String, town: String, postcode: String)

  final case class Person(
      _id: String,
      name: String,
      dob: String,
      address: Address,
      telephone: String,
      pets: List[String],
      score: Double,
      email: String,
      url: String,
      description: String,
      verified: Boolean,
      salary: Int)

  final case class CtRoot(ctRoot: List[Person])

  object Address {
    val testDataDecoder: Decoder[Address] = new Decoder[Address] {
      // INFO: `post-c-ode` typo in test data
      final def apply(c: HCursor): Decoder.Result[Address] = for {
        street <- c.downField("street").as[String]
        town <- c.downField("town").as[String]
        postcode <- c.downField("postode").as[String]
      } yield {
        Address(street, town, postcode)
      }
    }

    implicit val decoder: io.circe.Decoder[Address] = deriveDecoder[Address]

    implicit val encoder: io.circe.Encoder[Address] = deriveEncoder[Address]
  }

  object Person {
    implicit val codec: Codec[Person] = deriveCodec[Person]
  }

  object CtRoot {
    implicit val codec: Codec[CtRoot] = deriveCodec[CtRoot]

    val testDataDecoder: Decoder[CtRoot] = new Decoder[CtRoot] {
      final def apply(c: HCursor): Decoder.Result[CtRoot] = {
        implicit val personDecoder: Decoder[Person] = new Decoder[Person] {
          final def apply(c: HCursor): Decoder.Result[Person] = for {
            _id <- c.downField("_id").as[String]
            name <- c.downField("name").as[String]
            dob <- c.downField("dob").as[String]
            address <- c.downField("address").as[Address](Address.testDataDecoder)
            telephone <- c.downField("telephone").as[String]
            pets <- c.downField("pets").as[List[String]]
            score <- c.downField("score").as[Double]
            email <- c.downField("email").as[String]
            url <- c.downField("url").as[String]
            description <- c.downField("description").as[String]
            verified <- c.downField("verified").as[Boolean]
            salary <- c.downField("salary").as[Int]
          } yield Person(
            _id,
            name,
            dob,
            address,
            telephone,
            pets,
            score,
            email,
            url,
            description,
            verified,
            salary)
        }

        // Decode CtRoot using the custom Person decoder
        deriveDecoder[CtRoot].apply(c)
      }
    }
  }

  sealed trait Topic extends EnumEntry

  object Topic extends Enum[Topic] with CirceEnum[Topic] with TapirCodecEnumeratum {
    val values = findValues

    case object RandomPeople extends Topic

  }

  final case class KafkaConfig(
      bootstrapServers: String,
      clientId: String,
      // todo: Try to retrieve number of partitions from AdminClient rather than pulling from config
      numberOfPartitions: Int)

  final case class PartitionPersonRecordMappings(partition: Int, persons: List[Person])

  object PartitionPersonRecordMappings {
    implicit val codec: Codec[PartitionPersonRecordMappings] =
      deriveCodec[PartitionPersonRecordMappings]
  }

  final case class ApiResponse(records: List[PartitionPersonRecordMappings])

  object ApiResponse {
    implicit val codec: Codec[ApiResponse] = deriveCodec[ApiResponse]
  }
}
