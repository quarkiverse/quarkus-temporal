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

counter=1

# Discover the Quarkus version whose netty.version best matches the netty
# bundled inside grpc-netty-shaded (see discover-quarkus-version.sh for details).
echo "$counter - Discovering best Quarkus version for netty compatibility"
((counter++))
QUARKUS_VERSION=$(./discover-quarkus-version.sh) || exit 1
echo "Using Quarkus version: $QUARKUS_VERSION"

# Step 1: Clone Quarkus repository
# Using sparse checkout to only get the needed directories
echo "$counter - Cloning Quarkus"
((counter++))
git clone --depth=1 --filter=blob:none --sparse --branch "$QUARKUS_VERSION" https://github.com/quarkusio/quarkus.git || { echo "Failed to clone Quarkus"; exit 1; }
cd quarkus || { echo "Failed to cd into quarkus"; exit 1; }
git sparse-checkout set extensions/grpc-common extensions/netty
cd ..

# Step 2: Clean old directories and create fresh directory structure
echo "$counter - Cleaning old and creating new netty grpc directories"
((counter++))
rm -rf "$DEST_NETTY_DEPLOYMENT" "$DEST_NETTY_RUNTIME" "$DEST_GRPC_DEPLOYMENT" "$DEST_GRPC_RUNTIME"
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
find "$DEST_NETTY_RUNTIME" "$DEST_NETTY_DEPLOYMENT" -type f -name "*.java" -exec perl -pi -e 's/io\.netty/io.grpc.netty.shaded.io.netty/g' "{}" +
find "$DEST_GRPC_RUNTIME" "$DEST_GRPC_DEPLOYMENT" -type f -name "*.java" -exec perl -pi -e 's/io\.grpc\.netty/io.grpc.netty.shaded.io.grpc.netty/g' "{}" +
find "$DEST_GRPC_RUNTIME" "$DEST_GRPC_DEPLOYMENT" -type f -name "*.java" -exec perl -pi -e 's/io\.netty/io.grpc.netty.shaded.io.netty/g' "{}" +

# Step 5: Update package declarations and imports
# Modify package names to match our extension's structure
echo "$counter - Fixing imports and packages"
((counter++))
find "$DEST_NETTY_RUNTIME" "$DEST_NETTY_DEPLOYMENT" -type f -name "*.java" -exec perl -pi -e '
    s/io\.quarkus\.netty\.deployment/io.quarkiverse.temporal.deployment.graal.netty/g;
    s/io\.quarkus\.netty\.runtime\.virtual/io.quarkiverse.temporal.graal.netty.runtime.virtual/g;
    s/io\.quarkus\.netty\.runtime\.graal/io.quarkiverse.temporal.graal.netty.runtime.graal/g;
    s/io\.quarkus\.netty\.runtime/io.quarkiverse.temporal.graal.netty.runtime/g;
    s/io\.quarkus\.netty/io.quarkiverse.temporal.graal.netty/g;
    ' "{}" +
find "$DEST_GRPC_RUNTIME" "$DEST_GRPC_DEPLOYMENT" -type f -name "*.java" -exec perl -pi -e '
    s/io\.quarkus\.grpc\.common\.deployment/io.quarkiverse.temporal.deployment.graal.grpc/g;
    s/io\.quarkus\.grpc\.common\.runtime\.graal/io.quarkiverse.temporal.graal.grpc.runtime.graal/g;
    ' "{}" +

# Step 6: Update Netty configuration
# Older Quarkus versions use class-style @ConfigRoot; newer ones use interface-style
# @ConfigMapping. Quarkus 3.27+ requires @ConfigMapping, so convert if necessary.
# Also change the config prefix from "quarkus.netty" to "quarkus.temporal.netty".
echo "$counter - Fixing Netty config"
((counter++))
NETTY_CONFIG="$DEST_NETTY_DEPLOYMENT/NettyBuildTimeConfig.java"
if grep -q 'public class NettyBuildTimeConfig' "$NETTY_CONFIG"; then
    echo "  Converting class-style config to interface-style @ConfigMapping"
    # Convert class to interface
    perl -pi -e 's/public class NettyBuildTimeConfig/public interface NettyBuildTimeConfig/' "$NETTY_CONFIG"
    # Replace @ConfigRoot(name = "netty", ...) with @ConfigRoot(phase) + @ConfigMapping
    perl -pi -e 's/\@ConfigRoot\(name\s*=\s*"netty",\s*phase\s*=\s*ConfigPhase\.BUILD_TIME\)/\@ConfigRoot(phase = ConfigPhase.BUILD_TIME)\n\@ConfigMapping(prefix = "quarkus.temporal.netty")/' "$NETTY_CONFIG"
    # Replace ConfigItem import with ConfigMapping import
    perl -pi -e 's/import io\.quarkus\.runtime\.annotations\.ConfigItem;/import io.smallrye.config.ConfigMapping;/' "$NETTY_CONFIG"
    # Remove @ConfigItem annotations
    perl -ni -e 'print unless /^\s*\@ConfigItem\s*$/' "$NETTY_CONFIG"
    # Convert public fields to interface methods (e.g. "public OptionalInt foo;" -> "OptionalInt foo();")
    perl -pi -e 's/^\s*public\s+(\S+)\s+(\w+)\s*;/    $1 $2();/' "$NETTY_CONFIG"
    # Convert field access to method calls in NettyProcessor
    perl -pi -e 's/config\.allocatorMaxOrder(?!\()/config.allocatorMaxOrder()/g' "$DEST_NETTY_DEPLOYMENT/NettyProcessor.java"
else
    echo "  Config already uses interface-style @ConfigMapping, fixing prefix only"
    perl -pi -e 's|prefix = "quarkus.netty"|prefix = "quarkus.temporal.netty"|' "$NETTY_CONFIG"
fi

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
perl -ni -e 'print unless /import io\.grpc\.netty\.shaded\.io\.netty\.resolver\.dns\.DnsServerAddressStreamProviders;/' "$DEST_NETTY_DEPLOYMENT/NettyProcessor.java"
perl -ni -e 'print unless
    /import java\.util\.Collection;/
    || /import io\.grpc\.internal\.DnsNameResolverProvider;/
    || /import io\.grpc\.internal\.PickFirstLoadBalancerProvider;/
    || /import io\.grpc\.netty\.shaded\.io\.grpc\.netty\.NettyChannelProvider;/
    || /import io\.quarkus\.deployment\.annotations\.BuildProducer;/
    || /import io\.quarkus\.deployment\.builditem\.CombinedIndexBuildItem;/
    || /import io\.quarkus\.deployment\.builditem\.nativeimage\.ReflectiveClassBuildItem;/
    || /import org\.jboss\.jandex\.ClassInfo;/
    ' "$DEST_GRPC_DEPLOYMENT/GrpcCommonProcessor.java"

# Step 9: Cleanup
# Remove the temporary Quarkus clone
echo "$counter - Deleting cloned repo"
((counter++))
rm -rf "quarkus"

echo "$counter - Done!"