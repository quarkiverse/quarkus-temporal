#!/bin/bash

# Set directories as variables
SRC_NETTY_RUNTIME="quarkus/extensions/netty/runtime/src/main/java/io/quarkus/netty"
SRC_GRPC_RUNTIME="quarkus/extensions/grpc-common/runtime/src/main/java/io/quarkus/grpc/common"

DEST_NETTY_RUNTIME="extension/runtime/src/main/java/io/quarkiverse/temporal/nettyhandling"
DEST_GRPC_RUNTIME="extension/runtime/src/main/java/io/quarkiverse/temporal/grpchandling"

SRC_NETTY_DEPLOYMENT="quarkus/extensions/netty/deployment/src/main/java/io/quarkus/netty/deployment"
SRC_GRPC_DEPLOYMENT="quarkus/extensions/grpc-common/deployment/src/main/java/io/quarkus/grpc/common/deployment"

DEST_NETTY_DEPLOYMENT="extension/deployment/src/main/java/io/quarkiverse/temporal/deployment/nettyhandling"
DEST_GRPC_DEPLOYMENT="extension/deployment/src/main/java/io/quarkiverse/temporal/deployment/grpchandling"

# When bumping temporal we need to check if netty was updated and match with Quarkus version
QUARKUS_VERSION=3.16.3

echo "Using Quarkus version: $QUARKUS_VERSION"

counter=1

# Clone repo
echo "$counter - Cloning Quarkus"
((counter++))
git clone --depth=1 --filter=blob:none --sparse --branch "$QUARKUS_VERSION" git@github.com:quarkusio/quarkus.git
cd quarkus
git sparse-checkout set extensions/grpc-common extensions/netty bom/application
cd ..

BOM_FILE="quarkus/bom/application/pom.xml"
NETTY_VERSION=$(sed -n 's/.*<netty.version>\(.*\)<\/netty.version>.*/\1/p' "$BOM_FILE")

if [[ -z "$NETTY_VERSION" ]]; then
    echo "Could not find <netty.version> in the BOM file."
else
    echo "The Netty target version is: $NETTY_VERSION"
fi

# Creating "nettyhandling" and "grpchandling" directories to keep pulled files separate from our extension's
echo "$counter - Creating nettyhandling grpchandling directories"
((counter++))
mkdir -p "$DEST_NETTY_DEPLOYMENT"
mkdir -p "$DEST_NETTY_RUNTIME"
mkdir -p "$DEST_GRPC_DEPLOYMENT"
mkdir -p "$DEST_GRPC_RUNTIME"

# Copy files from runtime and deployment directories and overwrite existing
echo "$counter - Copying files"
((counter++))
cp -r "$SRC_NETTY_RUNTIME"/* "$DEST_NETTY_RUNTIME"
cp -r "$SRC_NETTY_DEPLOYMENT"/* "$DEST_NETTY_DEPLOYMENT"

cp -r "$SRC_GRPC_RUNTIME"/* "$DEST_GRPC_RUNTIME"
cp -r "$SRC_GRPC_DEPLOYMENT"/* "$DEST_GRPC_DEPLOYMENT"

# Prepend "io.grpc.netty.shaded." to all "io.netty" occurrences in the copied files
echo "$counter - Replacing shaded netty namespace"
((counter++))
find "$DEST_NETTY_RUNTIME" "$DEST_NETTY_DEPLOYMENT" -type f -name "*.java" -exec sed -i '' 's/io\.netty/io.grpc.netty.shaded.io.netty/g' "{}" +
find "$DEST_GRPC_RUNTIME" "$DEST_GRPC_DEPLOYMENT" -type f -name "*.java" -exec sed -i '' 's/io\.grpc\.netty/io.grpc.netty.shaded.io.grpc.netty/g' "{}" +
find "$DEST_GRPC_RUNTIME" "$DEST_GRPC_DEPLOYMENT" -type f -name "*.java" -exec sed -i '' 's/io\.netty/io.grpc.netty.shaded.io.netty/g' "{}" +


# Fix the imports and packages
echo "$counter - Fixing imports and packages"
((counter++))
find "$DEST_NETTY_RUNTIME" "$DEST_NETTY_DEPLOYMENT" -type f -name "*.java" -exec sed -i '' \
    -e 's/io\.quarkus\.netty\.deployment/io.quarkiverse.temporal.deployment.nettyhandling/g' \
    -e 's/io\.quarkus\.netty/io.quarkiverse.temporal.nettyhandling/g' \
    -e 's/io\.quarkus\.netty\.runtime/io.quarkiverse.temporal.nettyhandling.runtime/g' \
    -e 's/io\.quarkus\.netty\.runtime\.virtual/io.quarkiverse.temporal.nettyhandling.runtime.virtual/g' \
    -e 's/io\.quarkus\.netty\.runtime\.graal/io.quarkiverse.temporal.nettyhandling.runtime.graal/g' \
    "{}" +
find "$DEST_GRPC_RUNTIME" "$DEST_GRPC_DEPLOYMENT" -type f -name "*.java" -exec sed -i '' \
    -e 's/io\.quarkus\.grpc\.common\.deployment/io.quarkiverse.temporal.deployment.grpchandling/g' \
    -e 's/io\.quarkus\.grpc\.common\.runtime\.graal/io.quarkiverse.temporal.grpchandling.runtime.graal/g' \
    "{}" +

# Fix netty config as we need to make it compatible with newer Quarkus versions
# Also setting the config prefix to quarkus.temporal.netty
echo "$counter - Fixing Netty config"
((counter++))
sed -i '' '/import io.quarkus.runtime.annotations.ConfigItem;/d' "$DEST_NETTY_DEPLOYMENT/NettyBuildTimeConfig.java"
sed -i '' 's/import io.quarkus.runtime.annotations.ConfigRoot;/import io.quarkus.runtime.annotations.ConfigRoot;\nimport io.smallrye.config.ConfigMapping;/' "$DEST_NETTY_DEPLOYMENT/NettyBuildTimeConfig.java"
sed -i '' 's/@ConfigRoot(name = "netty", phase = ConfigPhase.BUILD_TIME)/@ConfigRoot(phase = ConfigPhase.BUILD_TIME)\n@ConfigMapping(prefix = "grpc.netty")/' "$DEST_NETTY_DEPLOYMENT/NettyBuildTimeConfig.java"
sed -i '' 's/@ConfigRoot(name = "netty", phase = ConfigPhase.BUILD_TIME)/import io.smallrye.config.ConfigMapping;\n\n@ConfigRoot(phase = ConfigPhase.BUILD_TIME)\n@ConfigMapping(prefix = "quarkus.temporal.netty")/' "$DEST_NETTY_DEPLOYMENT/NettyBuildTimeConfig.java"
sed -i '' 's/public class NettyBuildTimeConfig/public interface NettyBuildTimeConfig/' "$DEST_NETTY_DEPLOYMENT/NettyBuildTimeConfig.java"
sed -i '' '/ConfigItem/d' "$DEST_NETTY_DEPLOYMENT/NettyBuildTimeConfig.java"
sed -i '' 's/public OptionalInt allocatorMaxOrder;/OptionalInt allocatorMaxOrder();/' "$DEST_NETTY_DEPLOYMENT/NettyBuildTimeConfig.java"
sed -i '' 's/config.allocatorMaxOrder/config.allocatorMaxOrder()/' "$DEST_NETTY_DEPLOYMENT/NettyProcessor.java"

#Delete two methods in NettyProcessor which we don't want to use
echo "$counter - Deleting code we don't want in NettyProcessor and GrpcCommonProcessor"
((counter++))
./deleteMethod.sh "LogCleanupFilterBuildItem cleanupMacDNSInLog" "$DEST_NETTY_DEPLOYMENT/NettyProcessor.java"
./deleteMethod.sh "public void configureNativeExecutable" "$DEST_GRPC_DEPLOYMENT/GrpcCommonProcessor.java"
rm ${DEST_GRPC_RUNTIME}/runtime/graal/GrpcSubstitutions.java
rm ${DEST_GRPC_DEPLOYMENT}/GrpcDotNames.java


# This isn't absolutely necessary, as Quarkus will optimise imports and remove unused/missing ones during compilation.
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

# Delete the cloned repo
echo "$counter - Deleting cloned repo"
((counter++))
rm -rf "quarkus"

echo "$counter - Done!"