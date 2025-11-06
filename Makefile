.PHONY: dev build test clean install cluster-up

COMPOSE_CLUSTER_FILE ?= docker-compose.cluster.yaml
BACKEND_SCALE ?= 3
CLUSTER_ENV_FILE ?=

cluster_env_flag = $(if $(CLUSTER_ENV_FILE),--env-file $(CLUSTER_ENV_FILE),)

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

cluster-up:
	@echo "Booting multi-node cluster (scale=$(BACKEND_SCALE))..."
	docker compose -f $(COMPOSE_CLUSTER_FILE) $(cluster_env_flag) up --build --scale backend=$(BACKEND_SCALE)
