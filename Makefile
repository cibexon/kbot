# Application name and registry configuration
APP := $(shell basename $(shell git remote get-url origin))
REGISTRY := cibexon
VERSION := $(shell git describe --tags --abbrev=0)-$(shell git rev-parse --short HEAD)

# Host platform detection
HOSTOS := $(shell uname -s | tr '[:upper:]' '[:lower:]')
HOSTARCH := $(shell uname -m)
# Normalize architecture names for Docker
ifeq ($(HOSTARCH),x86_64)
    HOSTARCH := amd64
else ifeq ($(HOSTARCH),aarch64)
    HOSTARCH := arm64
endif

# Build configuration (default to host architecture if not specified)
TARGETOS ?= $(HOSTOS)
TARGETARCH ?= $(HOSTARCH)
CGO_ENABLED ?= 0

# Docker image tag
IMAGE_TAG := $(REGISTRY)/$(APP):$(VERSION)-$(TARGETARCH)

# Validate environment variables
ifeq ($(TARGETOS),)
$(error TARGETOS is not set)
endif
ifeq ($(TARGETARCH),)
$(error TARGETARCH is not set)
endif

# Phony targets
.PHONY: help format lint test get build image push clean dev release

# Help target
help:
	@echo "Available targets:"
	@echo "  help     - Show this help message"
	@echo "  format   - Format Go code"
	@echo "  lint     - Run golangci-lint"
	@echo "  test     - Run tests"
	@echo "  get      - Get dependencies"
	@echo "  build    - Build the application"
	@echo "  image    - Build Docker image for host platform ($(HOSTOS)/$(HOSTARCH))"
	@echo "  push     - Push Docker image to registry"
	@echo "  clean    - Clean build artifacts including Docker image"
	@echo "  dev      - Development build (with debug info)"
	@echo "  release  - Production build (optimized)"
	@echo ""
	@echo "Configuration:"
	@echo "  TARGETOS   - Target OS (linux, darwin, windows) [$(TARGETOS)]"
	@echo "  TARGETARCH - Target architecture (amd64, arm64) [$(TARGETARCH)]"
	@echo "  CGO_ENABLED - Enable CGO (0 or 1) [$(CGO_ENABLED)]"
	@echo "  IMAGE_TAG  - Docker image tag [$(IMAGE_TAG)]"

# Format Go code
format:
	@echo "Formatting Go code..."
	@gofmt -s -w ./

# Install golangci-lint if not present
.PHONY: install-lint
install-lint:
	@which golangci-lint >/dev/null || (echo "Installing golangci-lint..." && go install github.com/golangci/golangci-lint/cmd/golangci-lint@latest)

# Run linter
lint: install-lint
	@echo "Running linter..."
	@golangci-lint run ./...

# Run tests
test:
	@echo "Running tests..."
	@go test -v -cover ./...

# Get dependencies
get:
	@echo "Getting dependencies..."
	@go mod tidy
	@go mod download

# Development build
dev: format get
	@echo "Building development version..."
	@CGO_ENABLED=$(CGO_ENABLED) GOOS=$(TARGETOS) GOARCH=$(TARGETARCH) \
		go build -v -o kbot -ldflags "-X=github.com/cibexon/kbot/cmd.appVersion=$(VERSION)-dev"

# Production build
build: format get
	@echo "Building production version..."
	@CGO_ENABLED=$(CGO_ENABLED) GOOS=$(TARGETOS) GOARCH=$(TARGETARCH) \
		go build -v -o kbot -ldflags "-X=github.com/cibexon/kbot/cmd.appVersion=$(VERSION) -w -s"

# Build Docker image for host platform (as per assignment requirement)
image:
	@echo "Building Docker image for host platform: $(HOSTOS)/$(HOSTARCH)..."
	@docker build . -t $(IMAGE_TAG) \
		--build-arg TARGETARCH=$(HOSTARCH) \
		--build-arg VERSION=$(VERSION)
	@echo "Image built: $(IMAGE_TAG)"

# Push Docker image
push:
	@echo "Pushing Docker image..."
	@docker push $(IMAGE_TAG)

# Clean build artifacts including Docker image (as per assignment requirement)
clean:
	@echo "Cleaning build artifacts..."
	@rm -rf kbot
	@echo "Removing Docker image: $(IMAGE_TAG)"
	@docker rmi $(IMAGE_TAG) 2>/dev/null || true

# Release target
release: clean test build image push
	@echo "Release $(VERSION) completed successfully!"