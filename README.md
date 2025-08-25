# Chat App Backend (Java/Spring Boot)

This is the backend for the Chat App, implemented in Java using the Spring Boot framework.

## Prerequisites

- Java 17
- Maven 3.x
- Redis (running on localhost:6379)
- An OpenAI API key

## Configuration

1.  Open the `src/main/resources/application.properties` file.
2.  Set the `openai.api.key` property to your OpenAI API key.
3.  (Optional) Configure the Redis connection details (`spring.redis.host`, `spring.redis.port`) if your Redis server is not running on the default host and port.

## Build and Run

1.  Navigate to the `backend` directory.
2.  Build the project using Maven:
    ```sh
    mvn clean install
    ```
3.  Run the application:
    ```sh
    java -jar target/chat-app-0.0.1-SNAPSHOT.jar
    ```

The application will start on port 8080.
