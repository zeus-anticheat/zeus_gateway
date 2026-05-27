#!/bin/bash

# ============================================
#  Zeus Plugins Build Script
#  Supports: ZeusProtocolJava, ZeusGateway, ZeusPhysicsLab, ZeusFabric
# ============================================

set -e

# Optional: set JAVA_HOME if needed
# export JAVA_HOME="/usr/lib/jvm/java-25-openjdk"
# export PATH="$JAVA_HOME/bin:$PATH"

echo "============================================"
echo " Zeus Plugins Build Script"
echo " Supports: ZeusProtocolJava, ZeusGateway, ZeusPhysicsLab, ZeusFabric"
echo "============================================"
echo ""

echo "[1/4] Checking Java version..."
java -version
echo ""

echo "[gate] Checking generated support matrix..."
python3 scripts/render_support_matrix.py
python3 scripts/render_support_readiness.py
echo "[OK] Support matrix documentation is current."
echo ""

echo "[gate] Checking support claims and build metadata..."
python3 scripts/verify_support_matrix.py
echo "[OK] Support claims match available verification evidence."
echo ""

echo "============================================"
echo " Building Maven modules (ZeusProtocolJava + ZeusGatewayLegacy + ZeusGateway + ZeusPhysicsLab)"
echo "============================================"
echo ""

mvn -v
echo ""

if ! mvn clean install -pl ZeusProtocolJava -am; then
    echo ""
    echo "[ERROR] ZeusProtocolJava build failed!"
    exit 1
fi
echo ""
echo "[OK] ZeusProtocolJava built successfully."
echo ""

if ! mvn clean package -pl ZeusGateway -am; then
    echo ""
    echo "[ERROR] ZeusGateway build failed!"
    exit 1
fi
echo ""
echo "[OK] ZeusGateway-modern built successfully."
echo ""
if ! mvn clean package -pl ZeusGatewayLegacy -am; then
    echo ""
    echo "[ERROR] ZeusGatewayLegacy build failed!"
    exit 1
fi
echo ""
echo "[OK] ZeusGateway-legacy built successfully."
echo ""
if ! mvn clean package -pl ZeusPhysicsLab -am; then
    echo ""
    echo "[ERROR] ZeusPhysicsLab build failed!"
    exit 1
fi
echo ""
echo "[OK] ZeusPhysicsLab built successfully."
echo ""

echo "============================================"
echo " Building Gradle module (ZeusFabric)"
echo "============================================"
echo ""

mapfile -t FABRIC_TARGETS < <(python3 scripts/list_fabric_build_targets.py)

if [ -f "ZeusFabric/gradlew" ]; then
    echo "Using Gradle wrapper..."
    cd ZeusFabric
    chmod +x gradlew
    for target in "${FABRIC_TARGETS[@]}"; do
        echo "Building ZeusFabric target ${target}..."
        ./gradlew build -PmcTarget="${target}"
    done
    cd ..
elif [ -f "ZeusFabric/build.gradle" ]; then
    echo "Gradle wrapper not found, attempting system Gradle..."
    cd ZeusFabric
    for target in "${FABRIC_TARGETS[@]}"; do
        echo "Building ZeusFabric target ${target}..."
        gradle build -PmcTarget="${target}"
    done
    cd ..
else
    echo "[SKIP] ZeusFabric build.gradle not found or Gradle not configured."
    echo "       To build ZeusFabric, run 'gradle build' inside the ZeusFabric directory."
    echo "       Make sure to install ZeusProtocolJava to local Maven repo first:"
    echo "         mvn install -pl ZeusProtocolJava"
fi

echo ""
echo "============================================"
echo " Build Summary"
echo "============================================"
echo ""

if [ -f "ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar" ]; then
    echo "[OK] ZeusProtocolJava : ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar"
else
    echo "[--] ZeusProtocolJava : not found"
fi

if [ -f "ZeusGateway/target/ZeusGateway-modern-1.0-SNAPSHOT.jar" ]; then
    echo "[OK] ZeusGateway-modern: ZeusGateway/target/ZeusGateway-modern-1.0-SNAPSHOT.jar"
else
    echo "[--] ZeusGateway      : not found"
fi

if [ -f "ZeusGatewayLegacy/target/ZeusGateway-legacy-1.0-SNAPSHOT.jar" ]; then
    echo "[OK] ZeusGateway-legacy: ZeusGatewayLegacy/target/ZeusGateway-legacy-1.0-SNAPSHOT.jar"
else
    echo "[--] ZeusGatewayLegacy: not found"
fi

if [ -f "ZeusPhysicsLab/target/zeus_physics_lab-1.0-SNAPSHOT.jar" ]; then
    echo "[OK] ZeusPhysicsLab  : ZeusPhysicsLab/target/zeus_physics_lab-1.0-SNAPSHOT.jar"
else
    echo "[--] ZeusPhysicsLab  : not found"
fi

for target in "${FABRIC_TARGETS[@]}"; do
    if [ -f "ZeusFabric/build/libs/ZeusFabric-${target}-1.0-SNAPSHOT.jar" ]; then
        echo "[OK] ZeusFabric-${target}: ZeusFabric/build/libs/ZeusFabric-${target}-1.0-SNAPSHOT.jar"
    else
        echo "[--] ZeusFabric-${target}: not built (requires Gradle)"
    fi
done

echo ""
echo "============================================"
echo " Verification Surface:"
echo "   ZeusGateway-modern -> build/unit verification; consult support-matrix.json"
echo "   ZeusGateway-legacy -> Java 8 build verification; consult support-matrix.json"
for target in "${FABRIC_TARGETS[@]}"; do
    echo "   ZeusFabric-${target} -> exact target build; consult support-matrix.json"
done
echo "   ZeusPhysicsLab  -> Paper lab generator for replay physics coverage"
echo "============================================"
echo ""
