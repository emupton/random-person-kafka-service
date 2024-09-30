docker-compose up -d

check_kafka_running() {
    while ! docker ps --filter "name=kafka" --filter "status=running" --format "{{.Names}}" | grep -q kafka; do
        echo "Waiting for Kafka container to start..."
        sleep 5
    done
}

check_kafka_running

docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --topic RandomPeople \
  --create \
  --partitions 3 \
  --replication-factor 1 \
  --config cleanup.policy=compact

echo "Topic 'RandomPeople' created successfully."
