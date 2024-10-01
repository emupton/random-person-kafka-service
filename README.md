# Conduktor Technical Exercise

## Development setup
### Prerequisites
SBT, Scala, Docker
### Kafka setup
The kafka image is defined in the `docker-compose.yaml` file, and the `kafka_setup.sh` initialises docker-compose,
waits for the kafka image to startup, and then will create a `RandomPeople` topic with the appropriate configuration
(3 partitions and a replication factor of 1).

To run:
```sh
# Make shell script executable
chmod +x kafka_setup.sh
./kafka_setup.sh
```

### Running the backend API
```
docker-compose up
sbt run
```

It runs on port 8080 by default.

An example API request: http://localhost/topic/RandomPeople/0?count=5000
### Running the bootstrap code
```sh
sbt "runMain com.example.backendservice.PopulateData"
```