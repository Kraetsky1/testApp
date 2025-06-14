# Event Status App

This is a Spring Boot application (Java 21, Spring Boot 3.3.0) that handles event status changes, schedules periodic REST calls for live events, and publishes messages to Kafka.

## Prerequisites
- Java 21
- Gradle
- Kafka

## How to Run

1. **Navigate to the project directory:**
   ```sh
   cd event-status-app
   ```
2. **Start Kafka** (if not already running):
   - You can use Docker:
     ```sh
     docker run -d --name kafka -p 9092:9092 -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 -e KAFKA_BROKER_ID=1 -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 bitnami/kafka:latest
     ```
   - Or use your local Kafka installation.
3. **Build and run the application:**
   ```sh
   ./gradlew bootRun
   ```


## Testing
Run tests with:
```sh
./gradlew test
```

## Design Decisions

### 1. ConcurrentHashMap

Chose ConcurrentHashMap for thread-safe concurrent access to event status data without external synchronization, which can later be replaced with Redis or other persistent storage solutions.

### 2. ScheduledExecutorService

Used ScheduledExecutorService to enable dynamic creation and cancellation of per-event scheduled tasks at runtime, providing better resource management, granular control, and isolation compared to @Scheduled annotation.

### 3. Unit Testing and Frameworks

Implemented testing using JUnit 5, Mockito for mocking, and Spring Boot Test with embedded Kafka, using CompletableFuture for Kafka tests to properly mock asynchronous message publishing operations.

### 4. MockExternalApiController

Added MockExternalApiController to mock external API responses within the application.

