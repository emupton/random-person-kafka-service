package com.example.backendservice

final case class KafkaConfig(
    bootstrapServers: String,
    consumerGroupId: String,
    numberOfPartitions: 3)
