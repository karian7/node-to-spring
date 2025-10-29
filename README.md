# Chat App Backend (Java/Spring Boot)

This is the backend for the Chat App, implemented in Java using the Spring Boot framework.

## Prerequisites

- Java 21
- Maven 3.x
- MongoDB (running locally or configured)
- Redis (optional - will use in-memory mock if not available)

## Configuration

### Environment Variables

Create a `.env` file or set environment variables for sensitive data:

```bash
# Required
ENCRYPTION_KEY=your-64-character-hex-string
JWT_SECRET=your-secure-jwt-secret-key
OPENAI_API_KEY=your-openai-api-key

# Optional (defaults provided)
PORT=5001
SERVER_ADDRESS=0.0.0.0
SOCKETIO_PORT=5002
FILE_UPLOAD_DIR=./uploads
```

See `.env.example` for a complete template.

### application.properties

The `application.properties` file is pre-configured with sensible defaults. All sensitive data is loaded from environment variables.

## Build and Run

1. Set up environment variables (copy `.env.example` to `.env` and fill in values)
2. Build the project:
   ```sh
   mvn clean install
   ```
3. Run the application:
   ```sh
   mvn spring-boot:run
   # or
   java -jar target/chat-app-0.0.1-SNAPSHOT.jar
   ```

The application will start on:
- REST API: `http://0.0.0.0:5001`
- Socket.IO: `http://0.0.0.0:5002`

## Development Mode

Set logging level for development:
```bash
LOGGING_LEVEL_SPRING=DEBUG
LOGGING_LEVEL_APP=DEBUG
```

Request logging will automatically activate in development mode.

## Security Notes

⚠️ **Never commit `.env` file or hardcode sensitive data in `application.properties`**

- Use environment variables for all secrets
- Generate a secure 64-character hex string for `ENCRYPTION_KEY`
- Use a strong secret key (256+ bits) for `JWT_SECRET`

## Migration from Node.js

This Java implementation is compatible with the Node.js backend:
- Same encryption format (AES-256-CBC with random IV)
- Same JWT token structure
- Same API endpoints and responses
- Same Socket.IO CORS policies

⚠️ **Important**: Existing encrypted data from old Java implementation needs re-encryption due to IV format change.
