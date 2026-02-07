#!/bin/bash

###################################################################################
# Script: discover-quarkus-version.sh
# Purpose: Finds the Quarkus version whose netty.version best matches the netty
#          bundled inside grpc-netty-shaded (which is what Temporal uses at runtime).
#
# The project's Quarkus version often has a different netty.version than what is
# shaded inside grpc-netty-shaded, causing native build failures. This script
# searches Maven Central for a Quarkus release whose netty.version matches (or is
# closest to) the netty inside grpc-netty-shaded.
#
# Output: Prints the chosen Quarkus version to stdout (all diagnostics go to stderr).
# Exit codes: 0 = success, 1 = error
###################################################################################

# Helper: extract the patch number from a netty version string (e.g. "4.1.130.Final" -> 130)
netty_patch() {
    echo "$1" | sed -n 's/^4\.1\.\([0-9]*\).*/\1/p'
}

# Helper: fetch netty version from Quarkus BOM for a given version
# (The published BOM has explicit versions per artifact, not a <netty.version> property)
fetch_netty_version_for_quarkus() {
    local qv="$1"
    local url="https://repo1.maven.org/maven2/io/quarkus/quarkus-bom/${qv}/quarkus-bom-${qv}.pom"
    curl -sf "$url" | grep -A1 'netty-buffer' | grep '<version>' | head -1 | sed -n 's/.*<version>\(.*\)<\/version>.*/\1/p'
}

# Step A: Extract project's Quarkus version from pom.xml
PROJECT_QUARKUS_VERSION=$(sed -n 's/.*<quarkus\.version>\(.*\)<\/quarkus\.version>.*/\1/p' pom.xml)
if [[ -z "$PROJECT_QUARKUS_VERSION" ]]; then
    echo "ERROR: Could not find <quarkus.version> in pom.xml" >&2
    exit 1
fi
echo "  Project Quarkus version: $PROJECT_QUARKUS_VERSION" >&2

# Step B: Fetch grpc-netty-shaded version from Quarkus BOM on Maven Central
BOM_URL="https://repo1.maven.org/maven2/io/quarkus/quarkus-bom/${PROJECT_QUARKUS_VERSION}/quarkus-bom-${PROJECT_QUARKUS_VERSION}.pom"
GRPC_NETTY_SHADED_VERSION=$(curl -sf "$BOM_URL" | grep -A1 'grpc-netty-shaded' | grep '<version>' | head -1 | sed -n 's/.*<version>\(.*\)<\/version>.*/\1/p')
if [[ -z "$GRPC_NETTY_SHADED_VERSION" ]]; then
    echo "ERROR: Could not find grpc-netty-shaded version in Quarkus BOM at $BOM_URL" >&2
    exit 1
fi
echo "  grpc-netty-shaded version: $GRPC_NETTY_SHADED_VERSION" >&2

# Step C: Fetch target netty version from grpc-netty POM (non-shaded, same version)
GRPC_NETTY_POM_URL="https://repo1.maven.org/maven2/io/grpc/grpc-netty/${GRPC_NETTY_SHADED_VERSION}/grpc-netty-${GRPC_NETTY_SHADED_VERSION}.pom"
TARGET_NETTY_VERSION=$(curl -sf "$GRPC_NETTY_POM_URL" | grep -A1 'netty-codec-http2' | grep '<version>' | head -1 | sed -n 's/.*<version>\(.*\)<\/version>.*/\1/p')
if [[ -z "$TARGET_NETTY_VERSION" ]]; then
    echo "ERROR: Could not find netty version in grpc-netty POM at $GRPC_NETTY_POM_URL" >&2
    exit 1
fi
TARGET_PATCH=$(netty_patch "$TARGET_NETTY_VERSION")
echo "  Target netty version (bundled in grpc-netty-shaded): $TARGET_NETTY_VERSION (patch: $TARGET_PATCH)" >&2

# Step D: Search for best Quarkus version match
# Fetch all Quarkus release versions from Maven Central metadata
METADATA_URL="https://repo1.maven.org/maven2/io/quarkus/quarkus-bom/maven-metadata.xml"
ALL_VERSIONS=$(curl -sf "$METADATA_URL" | sed -n 's/.*<version>\([0-9][0-9.]*\)<\/version>.*/\1/p')
if [[ -z "$ALL_VERSIONS" ]]; then
    echo "ERROR: Could not fetch Quarkus version list from Maven Central" >&2
    exit 1
fi

# Filter to release versions <= project version, sort descending
PROJECT_MAJOR=$(echo "$PROJECT_QUARKUS_VERSION" | cut -d. -f1)
PROJECT_MINOR=$(echo "$PROJECT_QUARKUS_VERSION" | cut -d. -f2)
PROJECT_MICRO=$(echo "$PROJECT_QUARKUS_VERSION" | cut -d. -f3)

CANDIDATES=$(echo "$ALL_VERSIONS" | while read -r v; do
    M=$(echo "$v" | cut -d. -f1)
    m=$(echo "$v" | cut -d. -f2)
    p=$(echo "$v" | cut -d. -f3)
    if [[ "$M" -lt "$PROJECT_MAJOR" ]] ||
       [[ "$M" -eq "$PROJECT_MAJOR" && "$m" -lt "$PROJECT_MINOR" ]] ||
       [[ "$M" -eq "$PROJECT_MAJOR" && "$m" -eq "$PROJECT_MINOR" && "$p" -le "$PROJECT_MICRO" ]]; then
        echo "$v"
    fi
done | sort -t. -k1,1nr -k2,2nr -k3,3nr)

BEST_VERSION=""
BEST_GAP=999
BEST_NETTY=""

# Optimization: check .0 releases first to find the right minor, then check patches
MINOR_VERSIONS=$(echo "$CANDIDATES" | sed -n 's/^\([0-9]*\.[0-9]*\)\.[0-9]*$/\1/p' | awk '!seen[$0]++')

echo "  Searching for best match (target netty patch: $TARGET_PATCH)..." >&2

FOUND_MINOR=""
for minor in $MINOR_VERSIONS; do
    check_version="${minor}.0"
    if ! echo "$CANDIDATES" | grep -qx "$check_version"; then
        check_version=$(echo "$CANDIDATES" | grep "^${minor}\." | tail -1)
        if [[ -z "$check_version" ]]; then
            continue
        fi
    fi

    nv=$(fetch_netty_version_for_quarkus "$check_version")
    if [[ -z "$nv" ]]; then
        continue
    fi

    np=$(netty_patch "$nv")
    if [[ -z "$np" ]]; then
        continue
    fi

    gap=$(( TARGET_PATCH - np ))
    if [[ "$gap" -lt 0 ]]; then
        gap=$(( -gap ))
    fi

    echo "    ${check_version}: netty ${nv} (patch: ${np}, gap: ${gap})" >&2

    if [[ "$gap" -lt "$BEST_GAP" ]]; then
        BEST_GAP=$gap
        BEST_VERSION=$check_version
        BEST_NETTY=$nv
        FOUND_MINOR=$minor
    fi

    # Exact match at minor level - no need to check further minors
    if [[ "$gap" -eq 0 ]]; then
        break
    fi

    # If gap is growing (netty versions decrease as we go to older Quarkus), stop
    if [[ "$np" -lt "$TARGET_PATCH" && "$gap" -gt "$BEST_GAP" ]]; then
        break
    fi
done

# If we found a matching minor, scan its patches for the latest with the best gap
if [[ -n "$FOUND_MINOR" ]]; then
    MINOR_PATCHES=$(echo "$CANDIDATES" | grep "^${FOUND_MINOR}\." | sort -t. -k3,3nr)

    for pv in $MINOR_PATCHES; do
        if [[ "$pv" == "$BEST_VERSION" ]]; then
            continue
        fi

        nv=$(fetch_netty_version_for_quarkus "$pv")
        if [[ -z "$nv" ]]; then
            continue
        fi

        np=$(netty_patch "$nv")
        if [[ -z "$np" ]]; then
            continue
        fi

        gap=$(( TARGET_PATCH - np ))
        if [[ "$gap" -lt 0 ]]; then
            gap=$(( -gap ))
        fi

        echo "    ${pv}: netty ${nv} (patch: ${np}, gap: ${gap})" >&2

        if [[ "$gap" -lt "$BEST_GAP" ]] || { [[ "$gap" -eq "$BEST_GAP" ]] && [[ "$pv" > "$BEST_VERSION" ]]; }; then
            BEST_GAP=$gap
            BEST_VERSION=$pv
            BEST_NETTY=$nv
        fi

        if [[ "$gap" -eq 0 ]]; then
            break
        fi
    done
fi

# Step E: Report result
if [[ -z "$BEST_VERSION" ]]; then
    echo "ERROR: Could not find any suitable Quarkus version" >&2
    exit 1
fi

if [[ "$BEST_GAP" -eq 0 ]]; then
    echo "  Found exact netty version match: Quarkus $BEST_VERSION (netty $BEST_NETTY)" >&2
elif [[ "$BEST_GAP" -le 10 ]]; then
    echo "  WARNING: No exact netty version match found." >&2
    echo "  Using Quarkus $BEST_VERSION (netty $BEST_NETTY) — closest to target $TARGET_NETTY_VERSION (gap: $BEST_GAP patches)." >&2
    echo "  Native build may have substitution mismatches." >&2
else
    echo "ERROR: No reasonable netty version match found (best gap: $BEST_GAP patches)." >&2
    echo "  Best candidate: Quarkus $BEST_VERSION (netty $BEST_NETTY), target: $TARGET_NETTY_VERSION" >&2
    echo "  Please investigate manually and hardcode QUARKUS_VERSION in update-netty-substitutions.sh." >&2
    exit 1
fi

# Print the result to stdout for the caller to capture
echo "$BEST_VERSION"
