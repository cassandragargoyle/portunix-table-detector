# Table Detector Plugin Makefile

.PHONY: build clean test install dist help all

# Plugin info
PLUGIN_NAME := table-detector
PLUGIN_VERSION := $(shell grep -o '"version": *"[^"]*"' plugin.json | head -1 | cut -d'"' -f4)
MAVEN_VERSION := $(shell grep -o '<version>[^<]*</version>' pom.xml | head -1 | sed 's/<[^>]*>//g')
JAR_NAME := table-detector-$(PLUGIN_VERSION).jar
BUILD_JAR := table-detector-core/target/table-detector-core-$(MAVEN_VERSION)-standalone.jar

# Default target
all: build

## build: Build plugin JAR (without tests)
build:
	@echo "Building table-detector..."
	mvn clean package -DskipTests
	@echo "Built: $(BUILD_JAR)"

## test: Run tests
test:
	mvn test

## clean: Clean build artifacts
clean:
	mvn clean
	rm -rf dist/

## install: Install to local Maven repository
install:
	mvn install -DskipTests

## dist: Create distribution package for deployment
dist: build
	@echo "Creating distribution package..."
	@rm -rf dist/
	@mkdir -p dist/
	@cp $(BUILD_JAR) dist/$(JAR_NAME)
	@cp plugin.json dist/
	@cp README.md dist/ 2>/dev/null || true
	@echo "Distribution created in dist/"
	@ls -la dist/

## help: Show this help message
help:
	@echo "Table Detector Plugin - Build Commands"
	@echo ""
	@sed -n 's/^##//p' $(MAKEFILE_LIST) | column -t -s ':' | sed -e 's/^/  /'
