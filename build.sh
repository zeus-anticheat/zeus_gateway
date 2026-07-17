#!/bin/bash

# ============================================
#  Zeus Plugins Build Script
#  Supports: ZeusProtocolJava, ZeusGateway, ZeusPhysicsLab, ZeusFabric
# ============================================

set -e
export PYTHONDONTWRITEBYTECODE=1

# Optional: set JAVA_HOME if needed
# export JAVA_HOME="/usr/lib/jvm/java-25-openjdk"
# export PATH="$JAVA_HOME/bin:$PATH"

echo "============================================"
echo " Zeus Plugins Build Script"
echo " Supports: ZeusProtocolJava, ZeusGateway, ZeusPhysicsLab, ZeusFabric"
echo "============================================"

echo "[1/4] Checking Java version and release contracts..."
java -version
python3 -m unittest scripts/test_support_profiles.py

echo "============================================"
echo " Building Maven modules (ZeusProtocolJava + ZeusGatewayLegacy + ZeusGateway)"
echo "============================================"

mvn -v

if ! mvn clean install -pl ZeusProtocolJava -am; then
    echo "[ERROR] ZeusProtocolJava build failed!"
    exit 1
fi
echo "[OK] ZeusProtocolJava built successfully."

if ! mvn clean package -pl ZeusGateway -am; then
    echo "[ERROR] ZeusGateway build failed!"
    exit 1
fi
echo "[OK] ZeusGateway unified artifact built successfully."

if ! mvn clean package -f ZeusPhysicsLab/pom.xml; then
    echo "[ERROR] ZeusPhysicsLab build failed!"
    exit 1
fi
echo "[OK] ZeusPhysicsLab built successfully."

echo "============================================"
echo " Building Gradle module (ZeusFabric)"
echo "============================================"

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

echo "[gate] Certifying newly built artifacts..."
python3 scripts/write_release_evidence.py
python3 scripts/render_support_matrix.py --write
python3 scripts/render_support_readiness.py --write
python3 scripts/verify_support_matrix.py --require-artifacts
echo "[OK] Newly built artifacts and support claims verified."

echo "============================================"
echo " Build Summary"
echo "============================================"

if [ -f "ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar" ]; then
    echo "[OK] ZeusProtocolJava : ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar"
else
    echo "[--] ZeusProtocolJava : not found"
fi

if [ -f "ZeusGateway/target/ZeusGateway-1.0-SNAPSHOT.jar" ]; then
    echo "[OK] ZeusGateway: ZeusGateway/target/ZeusGateway-1.0-SNAPSHOT.jar"
else
    echo "[--] ZeusGateway: not found"
fi

if [ -f "ZeusPhysicsLab/target/zeus_physics_lab-1.0-SNAPSHOT.jar" ]; then
    echo "[OK] ZeusPhysicsLab    : ZeusPhysicsLab/target/zeus_physics_lab-1.0-SNAPSHOT.jar"
else
    echo "[--] ZeusPhysicsLab    : not found"
fi


for target in "${FABRIC_TARGETS[@]}"; do
    if [ -f "ZeusFabric/build/libs/ZeusFabric-${target}-1.0-SNAPSHOT.jar" ]; then
        echo "[OK] ZeusFabric-${target}: ZeusFabric/build/libs/ZeusFabric-${target}-1.0-SNAPSHOT.jar"
    else
        echo "[--] ZeusFabric-${target}: not built (requires Gradle)"
    fi
done

echo "============================================"
echo " Verification Surface:"
echo "   ZeusGateway -> one Java 8 Bukkit-family artifact; consult support-matrix.json"
echo "   ZeusPhysicsLab     -> physics coverage lab; standalone Maven build"
for target in "${FABRIC_TARGETS[@]}"; do
    echo "   ZeusFabric-${target} -> exact target build; consult support-matrix.json"
done
echo "============================================"
