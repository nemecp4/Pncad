#!/bin/bash
#
# Build libcgal_engine.so for desktop Linux (x86_64)
# Used to run benchmark tests with the CGAL engine locally.
#
# Usage: ./scripts/build-cgal-desktop.sh
#
set -e

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="$PROJECT_ROOT/build/cgal-native"
CPP_DIR="$PROJECT_ROOT/app/src/main/cpp"

echo "=== Building CGAL engine for desktop ==="
echo "Project root: $PROJECT_ROOT"
echo "Build dir:    $BUILD_DIR"
echo "Source dir:   $CPP_DIR"
echo ""

# Check prerequisites
for cmd in g++ pkg-config; do
    if ! command -v $cmd &>/dev/null; then
        echo "ERROR: $cmd not found. Install with: sudo apt install build-essential pkg-config"
        exit 1
    fi
done

# Find JDK include path
JDK_INCLUDE=""
for jdk in /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/java-17-openjdk /usr/lib/jvm/default-java; do
    if [ -f "$jdk/include/jni.h" ]; then
        JDK_INCLUDE="$jdk/include"
        break
    fi
done

if [ -z "$JDK_INCLUDE" ]; then
    echo "ERROR: jni.h not found. Install JDK 17: sudo apt install openjdk-17-jdk"
    exit 1
fi
echo "JDK include: $JDK_INCLUDE"

# Check for required libraries
for lib in gmp mpfr; do
    if ! ldconfig -p | grep -q "lib${lib}.so"; then
        echo "ERROR: lib${lib} not found. Install with: sudo apt install lib${lib}-dev"
        exit 1
    fi
done

# Create build directory
mkdir -p "$BUILD_DIR/android"

# Create Android logger stub
cat > "$BUILD_DIR/android/log.h" << 'EOF'
#ifndef ANDROID_LOG_H_STUB
#define ANDROID_LOG_H_STUB
#define ANDROID_LOG_ERROR 6
#define __android_log_print(prio, tag, fmt, ...) ((void)0)
#endif
EOF

echo "Created android/log.h stub"

# Download nlohmann/json.hpp if not present in source tree
if [ ! -f "$CPP_DIR/nlohmann/json.hpp" ]; then
    echo "Downloading nlohmann/json.hpp..."
    mkdir -p "$CPP_DIR/nlohmann"
    wget -q -O "$CPP_DIR/nlohmann/json.hpp" \
        "https://github.com/nlohmann/json/releases/download/v3.11.3/json.hpp"
    echo "Downloaded nlohmann/json.hpp"
fi

# Compile
echo ""
echo "Compiling cgal_engine.cpp..."
cd "$BUILD_DIR"

g++ -shared -fPIC -O2 \
    "$CPP_DIR/cgal_engine.cpp" \
    "$CPP_DIR/cgal_compute.cpp" \
    "$CPP_DIR/scene_builder.cpp" \
    "$CPP_DIR/mesh_extractor.cpp" \
    -o libcgal_engine.so \
    -lgmp -lmpfr \
    -I"$BUILD_DIR" \
    -I"$CPP_DIR" \
    -I"$CPP_DIR/include" \
    -I/usr/include \
    -I"$JDK_INCLUDE" \
    -I"$JDK_INCLUDE/linux" \
    2>&1

if [ -f "$BUILD_DIR/libcgal_engine.so" ]; then
    echo ""
    echo "=== Build successful ==="
    echo "Output: $BUILD_DIR/libcgal_engine.so"
    echo ""
    echo "Run benchmarks with CGAL:"
    echo "  ./gradlew :benchmark:cleanTest :benchmark:test --tests '*BenchmarkTest*' \\"
    echo "      -Pcgal.library.path=$BUILD_DIR"
else
    echo ""
    echo "=== Build failed ==="
    echo ""
    echo "The CGAL native code uses project-internal headers that may need"
    echo "additional stubs. Check the error messages above."
    echo ""
    echo "Missing headers to look for in: $CPP_DIR"
    echo "  - nlohmann/json.hpp  (download from https://github.com/nlohmann/json)"
    echo "  - cgal_compute.h     (project internal)"
    echo "  - scene_builder.h    (project internal)"
    exit 1
fi
