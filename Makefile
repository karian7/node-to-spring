.PHONY: dev build test clean install

dev:
	@echo "Starting application with Testcontainers..."
	./mvnw compile spring-boot:test-run -Dspring-boot.run.profiles=dev

build:
	@echo "Building application..."
	./mvnw clean install

test:
	@echo "Running tests..."
	./mvnw test

clean:
	@echo "Cleaning build artifacts..."
	./mvnw clean

install:
	@echo "Installing dependencies..."
	./mvnw install
