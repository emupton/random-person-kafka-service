# Conduktor Technical Exercise

## Development setup
### Prerequisites
SBT, Scala, Docker
### Kafka setup
The kafka image is defined in the `docker-compose.yaml` file, and the `kafka_setup.sh` initialises docker-compose,
waits for the kafka image to startup, and then will create a `random_people` topic with the appropriate configuration
(3 partitions and a replication factor of 1).

To run:
```sh
# Make shell script executable
chmod +x kafka_setup.sh
./kafka_setup.sh
```

### Running the backend API

### Running the bootstrap code