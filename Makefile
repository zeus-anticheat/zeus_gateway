.PHONY: all clean build build-protocol build-gateway build-legacy help

# Default target when just typing 'make'
all: build

# Show help menu
help:
	@echo "Zeus Java Plugins Makefile"
	@echo ""
	@echo "Usage:"
	@echo "  make build         - Build Protocol and Gateway (recommended)"
	@echo "  make build-all     - Build Protocol, Gateway, and Legacy Gateway"
	@echo "  make clean         - Clean all Maven build directories"
	@echo "  make protocol      - Build and install only ZeusProtocolJava"
	@echo "  make gateway       - Build only ZeusGateway (requires protocol installed)"
	@echo "  make legacy        - Build only ZeusGatewayLegacy (requires protocol installed)"

# Clean all submodules
clean:
	mvn clean

# Build everything (Protocol + modern Gateway)
build: build-protocol build-gateway
	@echo ""
	@echo "✅ Build complete! Gateway jar is located at:"
	@echo "   zeus_plugins/ZeusGateway/target/ZeusGateway-<version>.jar"

# Build absolutely everything including legacy
build-all: build-protocol build-gateway build-legacy

# Build and install the protocol (must happen before gateways)
build-protocol:
	@echo "📦 Building ZeusProtocolJava..."
	mvn clean install -pl ZeusProtocolJava -am

# Build the modern gateway
build-gateway:
	@echo "🚀 Building ZeusGateway..."
	mvn clean package -pl ZeusGateway -am

# Build the legacy gateway
build-legacy:
	@echo "🕰️ Building ZeusGatewayLegacy..."
	mvn clean package -pl ZeusGatewayLegacy -am

# Shorthand commands
protocol: build-protocol
gateway: build-gateway
legacy: build-legacy