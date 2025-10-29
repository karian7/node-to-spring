## Project Overview

This is a chat application backend that exists in a dual architecture:
- **Legacy Node.js backend** in `/backend/` directory (Express.js + Socket.IO + MongoDB + Redis)
- **New Java Spring Boot backend** in `/src/main/java/` (Spring Boot + Spring Security + MongoDB + Redis + SocketIO)

The project is currently in **migration phase** from Node.js to Java Spring Boot. The Migration.md file contains detailed analysis of differences and completed/pending tasks.

## Development Commands

### Java Spring Boot (Primary)
```bash
# Build and test
mvn clean install

# Run application (starts on port 5001)
mvn spring-boot:run
# or
java -jar target/chat-app-0.0.1-SNAPSHOT.jar

# Run tests
mvn test
```

### Node.js (Legacy - being phased out)
```bash
cd backend/
npm start          # Production
npm run dev        # Development with nodemon
```

## Architecture

### Core Technologies
- **Backend**: Spring Boot 3.5.4 with Java 21
- **Database**: MongoDB (bootcamp-chat database)
- **Cache**: Redis (session management, rate limiting)
- **Real-time**: SocketIO (netty-socketio library)
- **Security**: Spring Security + JWT tokens
- **File Handling**: MultipartFile uploads to `/uploads` directory

### Key Components

#### Authentication & Session Management
- JWT tokens with custom headers (`x-auth-token`, `x-session-id`)
- Redis-based session storage with metadata (IP, User-Agent, device info)
- Rate limiting (60 requests/minute per IP)
- Custom validation annotations (`@ValidEmail`, `@ValidPassword`, `@ValidName`)

#### Real-time Communication
- SocketIO server on port 5002 (separate from REST API on 5001)
- Event-driven architecture for chat messages, typing indicators, room notifications
- Connection state management with user presence tracking
- Message read status tracking with real-time updates

#### File System
- Secure file uploads with path traversal protection
- File metadata storage in MongoDB
- Integration with RAG system for AI processing
- Automatic cleanup on upload failures

```

### Package Structure
- `controller/` - REST API endpoints
- `service/` - Business logic layer
- `repository/` - MongoDB data access
- `dto/` - Request/response objects
- `model/` - Entity classes
- `config/` - Configuration classes
- `security/` - JWT and authentication
- `websocket/` - SocketIO handlers
- `util/` - Helper utilities
- `validation/` - Custom validators

## Configuration

### Required Environment Setup
1. MongoDB running locally (default connection)
2. Redis server on localhost:6379
3. Set OpenAI API key in `application.properties`
4. Java 21 installed
5. Maven 3.x installed

### Important Configuration Files
- `src/main/resources/application.properties` - Main configuration
- `Migration.md` - Detailed migration status and task tracking

## Migration Status

The codebase is transitioning from Node.js to Spring Boot. Major completed features:
- ✅ Authentication system with session management
- ✅ Message system with read status tracking  
- ✅ File upload with security hardening
- ✅ Rate limiting and security enhancements
- ✅ Real-time WebSocket communication
- ✅ Room management with pagination

Remaining work focuses on AI service enhancement and user profile features (see Migration.md for details).

## Development Notes

- The `/backend` directory contains the legacy Node.js code that is being replaced
- New features should be implemented in the Java Spring Boot codebase
- Follow existing patterns for DTO classes, service layers, and error handling
- All API endpoints should return standardized `ApiResponse` format
- WebSocket events should be handled through the SocketIO configuration
- File uploads must use the security utilities in `FileSecurityUtil`
