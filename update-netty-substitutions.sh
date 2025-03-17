#!/bin/bash

###################################################################################
# Script: update-netty-substitutions.sh
# Purpose: Updates and manages Netty and gRPC substitutions for Temporal extension
#          by copying and modifying relevant files from Quarkus codebase.
#
# This script performs the following main tasks:
# 1. Clones specific version of Quarkus repository
# 2. Copies Netty and gRPC related files to appropriate directories
# 3. Updates namespace references for shaded Netty
# 4. Fixes imports and package declarations
# 5. Updates configuration handling for compatibility
###################################################################################

# Directory structure for source files in Quarkus repository
# These paths are relative to the Quarkus root directory
SRC_NETTY_RUNTIME="quarkus/extensions/netty/runtime/src/main/java/io/quarkus/netty"
SRC_GRPC_RUNTIME="quarkus/extensions/grpc-common/runtime/src/main/java/io/quarkus/grpc/common"

# Directory structure for destination files in our extension
# Where the modified files will be placed
DEST_NETTY_RUNTIME="extension/runtime/src/main/java/io/quarkiverse/temporal/graal/netty"
DEST_GRPC_RUNTIME="extension/runtime/src/main/java/io/quarkiverse/temporal/graal/grpc"

# Deployment source directories in Quarkus
SRC_NETTY_DEPLOYMENT="quarkus/extensions/netty/deployment/src/main/java/io/quarkus/netty/deployment"
SRC_GRPC_DEPLOYMENT="quarkus/extensions/grpc-common/deployment/src/main/java/io/quarkus/grpc/common/deployment"

# Deployment destination directories in our extension
DEST_NETTY_DEPLOYMENT="extension/deployment/src/main/java/io/quarkiverse/temporal/deployment/graal/netty"
DEST_GRPC_DEPLOYMENT="extension/deployment/src/main/java/io/quarkiverse/temporal/deployment/graal/grpc"

# Quarkus version to use - this should match the GRPC version compatible with Temporal SDK
QUARKUS_VERSION=3.16.3

echo "Using Quarkus version: $QUARKUS_VERSION"

counter=1

# Step 1: Clone Quarkus repository
# Using sparse checkout to only get the needed directories
echo "$counter - Cloning Quarkus"
((counter++))
git clone --depth=1 --filter=blob:none --sparse --branch "$QUARKUS_VERSION" git@github.com:quarkusio/quarkus.git
cd quarkus
git sparse-checkout set extensions/grpc-common extensions/netty bom/application
cd ..

# Extract Netty version from Quarkus BOM file
BOM_FILE="quarkus/bom/application/pom.xml"
NETTY_VERSION=$(sed -n 's/.*<netty.version>\(.*\)<\/netty.version>.*/\1/p' "$BOM_FILE")

if [[ -z "$NETTY_VERSION" ]]; then
    echo "Could not find <netty.version> in the BOM file."
else
    echo "The Netty target version is: $NETTY_VERSION"
fi

# Step 2: Create directory structure for the modified files
echo "$counter - Creating netty grpc directories"
((counter++))
mkdir -p "$DEST_NETTY_DEPLOYMENT"
mkdir -p "$DEST_NETTY_RUNTIME"
mkdir -p "$DEST_GRPC_DEPLOYMENT"
mkdir -p "$DEST_GRPC_RUNTIME"

# Step 3: Copy files from Quarkus to our extension
echo "$counter - Copying files"
((counter++))
cp -r "$SRC_NETTY_RUNTIME"/* "$DEST_NETTY_RUNTIME"
cp -r "$SRC_NETTY_DEPLOYMENT"/* "$DEST_NETTY_DEPLOYMENT"
cp -r "$SRC_GRPC_RUNTIME"/* "$DEST_GRPC_RUNTIME"
cp -r "$SRC_GRPC_DEPLOYMENT"/* "$DEST_GRPC_DEPLOYMENT"

# Step 4: Update namespace references
# Prepend "io.grpc.netty.shaded." to all "io.netty" occurrences in the copied files
echo "$counter - Replacing shaded netty namespace"
((counter++))
find "$DEST_NETTY_RUNTIME" "$DEST_NETTY_DEPLOYMENT" -type f -name "*.java" -exec sed -i '' 's/io\.netty/io.grpc.netty.shaded.io.netty/g' "{}" +
find "$DEST_GRPC_RUNTIME" "$DEST_GRPC_DEPLOYMENT" -type f -name "*.java" -exec sed -i '' 's/io\.grpc\.netty/io.grpc.netty.shaded.io.grpc.netty/g' "{}" +
find "$DEST_GRPC_RUNTIME" "$DEST_GRPC_DEPLOYMENT" -type f -name "*.java" -exec sed -i '' 's/io\.netty/io.grpc.netty.shaded.io.netty/g' "{}" +

# Step 5: Update package declarations and imports
# Modify package names to match our extension's structure
echo "$counter - Fixing imports and packages"
((counter++))
find "$DEST_NETTY_RUNTIME" "$DEST_NETTY_DEPLOYMENT" -type f -name "*.java" -exec sed -i '' \
    -e 's/io\.quarkus\.netty\.deployment/io.quarkiverse.temporal.deployment.graal.netty/g' \
    -e 's/io\.quarkus\.netty/io.quarkiverse.temporal.graal.netty/g' \
    -e 's/io\.quarkus\.netty\.runtime/io.quarkiverse.temporal.graal.netty.runtime/g' \
    -e 's/io\.quarkus\.netty\.runtime\.virtual/io.quarkiverse.temporal.graal.netty.runtime.virtual/g' \
    -e 's/io\.quarkus\.netty\.runtime\.graal/io.quarkiverse.temporal.graal.netty.runtime.graal/g' \
    "{}" +
find "$DEST_GRPC_RUNTIME" "$DEST_GRPC_DEPLOYMENT" -type f -name "*.java" -exec sed -i '' \
    -e 's/io\.quarkus\.grpc\.common\.deployment/io.quarkiverse.temporal.deployment.graal.grpc/g' \
    -e 's/io\.quarkus\.grpc\.common\.runtime\.graal/io.quarkiverse.temporal.graal.grpc.runtime.graal/g' \
    "{}" +

# Step 6: Update Netty configuration
# Modify configuration to use newer Quarkus style and set custom prefix
echo "$counter - Fixing Netty config"
((counter++))
sed -i '' '/import io.quarkus.runtime.annotations.ConfigItem;/d' "$DEST_NETTY_DEPLOYMENT/NettyBuildTimeConfig.java"
sed -i '' 's/import io.quarkus.runtime.annotations.ConfigRoot;/import io.quarkus.runtime.annotations.ConfigRoot;\nimport io.smallrye.config.ConfigMapping;/' "$DEST_NETTY_DEPLOYMENT/NettyBuildTimeConfig.java"
sed -i '' 's/@ConfigRoot(name = "netty", phase = ConfigPhase.BUILD_TIME)/@ConfigRoot(phase = ConfigPhase.BUILD_TIME)\n@ConfigMapping(prefix = "quarkus.temporal.netty")/' "$DEST_NETTY_DEPLOYMENT/NettyBuildTimeConfig.java"
sed -i '' 's/public class NettyBuildTimeConfig/public interface NettyBuildTimeConfig/' "$DEST_NETTY_DEPLOYMENT/NettyBuildTimeConfig.java"
sed -i '' '/ConfigItem/d' "$DEST_NETTY_DEPLOYMENT/NettyBuildTimeConfig.java"
sed -i '' 's/public OptionalInt allocatorMaxOrder;/OptionalInt allocatorMaxOrder();/' "$DEST_NETTY_DEPLOYMENT/NettyBuildTimeConfig.java"
sed -i '' 's/config.allocatorMaxOrder/config.allocatorMaxOrder()/' "$DEST_NETTY_DEPLOYMENT/NettyProcessor.java"

# Step 7: Remove unwanted methods and files
echo "$counter - Deleting code we don't want in NettyProcessor and GrpcCommonProcessor"
((counter++))
./deleteMethod.sh "LogCleanupFilterBuildItem cleanupMacDNSInLog" "$DEST_NETTY_DEPLOYMENT/NettyProcessor.java"
./deleteMethod.sh "public void configureNativeExecutable" "$DEST_GRPC_DEPLOYMENT/GrpcCommonProcessor.java"
rm ${DEST_GRPC_RUNTIME}/runtime/graal/GrpcSubstitutions.java
rm ${DEST_GRPC_DEPLOYMENT}/GrpcDotNames.java

# Step 8: Clean up imports
# Remove unused imports that might cause compilation issues
echo "$counter - Deleting missing import"
((counter++))
sed -i '' '/import io.grpc.netty.shaded.io.netty.resolver.dns.DnsServerAddressStreamProviders;/d' "$DEST_NETTY_DEPLOYMENT/NettyProcessor.java"
sed -i '' '/import java.util.Collection;/d' "$DEST_GRPC_DEPLOYMENT/GrpcCommonProcessor.java"
sed -i '' '/import io.grpc.internal.DnsNameResolverProvider;/d' "$DEST_GRPC_DEPLOYMENT/GrpcCommonProcessor.java"
sed -i '' '/import io.grpc.internal.PickFirstLoadBalancerProvider;/d' "$DEST_GRPC_DEPLOYMENT/GrpcCommonProcessor.java"
sed -i '' '/import io.grpc.netty.shaded.io.grpc.netty.NettyChannelProvider;/d' "$DEST_GRPC_DEPLOYMENT/GrpcCommonProcessor.java"
sed -i '' '/import io.quarkus.deployment.annotations.BuildProducer;/d' "$DEST_GRPC_DEPLOYMENT/GrpcCommonProcessor.java"
sed -i '' '/import io.quarkus.deployment.builditem.CombinedIndexBuildItem;/d' "$DEST_GRPC_DEPLOYMENT/GrpcCommonProcessor.java"
sed -i '' '/import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;/d' "$DEST_GRPC_DEPLOYMENT/GrpcCommonProcessor.java"
sed -i '' '/import org.jboss.jandex.ClassInfo;/d' "$DEST_GRPC_DEPLOYMENT/GrpcCommonProcessor.java"

# Step 9: Cleanup
# Remove the temporary Quarkus clone
echo "$counter - Deleting cloned repo"
((counter++))
rm -rf "quarkus"

echo "$counter - Done!"