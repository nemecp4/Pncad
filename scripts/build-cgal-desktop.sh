#!/bin/bash
#
# Build libcgal_engine for desktop (Linux x86_64 or macOS with Homebrew)
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

# Detect OS
OS="$(uname -s)"
case "$OS" in
    Linux)  PLATFORM="linux" ;;
    Darwin) PLATFORM="macos" ;;
    *)
        echo "ERROR: Unsupported OS: $OS"
        echo "This script supports Linux and macOS only."
        exit 1
        ;;
esac
echo "Platform: $PLATFORM"

# --- macOS-specific setup ---
if [ "$PLATFORM" = "macos" ]; then
    # Check for Homebrew
    if ! command -v brew &>/dev/null; then
        echo "ERROR: Homebrew not found."
        echo "Install it from https://brew.sh and re-run this script."
        exit 1
    fi

    # Check for required brew packages
    MISSING_PKGS=()
    for pkg in gmp mpfr; do
        if ! brew --prefix "$pkg" &>/dev/null 2>&1; then
            MISSING_PKGS+=("$pkg")
        fi
    done

    if [ ${#MISSING_PKGS[@]} -ne 0 ]; then
        echo "ERROR: Required Homebrew packages not installed: ${MISSING_PKGS[*]}"
        echo "Install with: brew install ${MISSING_PKGS[*]}"
        exit 1
    fi

    # Check for compiler
    if ! command -v clang++ &>/dev/null; then
        echo "ERROR: clang++ not found. Install Xcode Command Line Tools:"
        echo "  xcode-select --install"
        exit 1
    fi
    CXX="clang++"

    # Find JDK include path on macOS
    JDK_INCLUDE=""
    if command -v /usr/libexec/java_home &>/dev/null; then
        JAVA_HOME_DIR="$(/usr/libexec/java_home 2>/dev/null || true)"
        if [ -n "$JAVA_HOME_DIR" ] && [ -f "$JAVA_HOME_DIR/include/jni.h" ]; then
            JDK_INCLUDE="$JAVA_HOME_DIR/include"
        fi
    fi
    # Fallback: check brew openjdk
    if [ -z "$JDK_INCLUDE" ]; then
        BREW_JDK="$(brew --prefix openjdk 2>/dev/null || true)"
        if [ -n "$BREW_JDK" ] && [ -f "$BREW_JDK/include/jni.h" ]; then
            JDK_INCLUDE="$BREW_JDK/include"
        fi
    fi
    if [ -z "$JDK_INCLUDE" ]; then
        echo "ERROR: jni.h not found. Install a JDK:"
        echo "  brew install openjdk"
        exit 1
    fi

    JDK_PLATFORM_INCLUDE="$JDK_INCLUDE/darwin"
    GMP_PREFIX="$(brew --prefix gmp)"
    MPFR_PREFIX="$(brew --prefix mpfr)"
    LIB_EXT="dylib"
    LIB_NAME="libcgal_engine.dylib"

# --- Linux-specific setup ---
else
    # Check prerequisites
    for cmd in g++ pkg-config; do
        if ! command -v $cmd &>/dev/null; then
            echo "ERROR: $cmd not found. Install with: sudo apt install build-essential pkg-config"
            exit 1
        fi
    done
    CXX="g++"

    # Find JDK include path on Linux
    # Override with: JAVA_HOME=/path/to/jdk ./scripts/build-cgal-desktop.sh
    JDK_INCLUDE=""
    if [ -n "$JAVA_HOME" ] && [ -f "$JAVA_HOME/include/jni.h" ]; then
        JDK_INCLUDE="$JAVA_HOME/include"
    else
        for jdk in /usr/lib/jvm/java-*-openjdk-amd64 /usr/lib/jvm/java-*-openjdk /usr/lib/jvm/default-java; do
            if [ -f "$jdk/include/jni.h" ]; then
                JDK_INCLUDE="$jdk/include"
                break
            fi
        done
    fi
    if [ -z "$JDK_INCLUDE" ]; then
        echo "ERROR: jni.h not found. Install a JDK (sudo apt install default-jdk) or set JAVA_HOME."
        exit 1
    fi

    JDK_PLATFORM_INCLUDE="$JDK_INCLUDE/linux"

    # Check for required libraries
    for lib in gmp mpfr; do
        if ! ldconfig -p | grep -q "lib${lib}.so"; then
            echo "ERROR: lib${lib} not found. Install with: sudo apt install lib${lib}-dev"
            exit 1
        fi
    done

    GMP_PREFIX=""
    MPFR_PREFIX=""
    LIB_EXT="so"
    LIB_NAME="libcgal_engine.so"
fi

echo "JDK include: $JDK_INCLUDE"

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
    if command -v wget &>/dev/null; then
        wget -q -O "$CPP_DIR/nlohmann/json.hpp" \
            "https://github.com/nlohmann/json/releases/download/v3.11.3/json.hpp"
    elif command -v curl &>/dev/null; then
        curl -sL -o "$CPP_DIR/nlohmann/json.hpp" \
            "https://github.com/nlohmann/json/releases/download/v3.11.3/json.hpp"
    else
        echo "ERROR: Neither wget nor curl found. Cannot download nlohmann/json.hpp"
        exit 1
    fi
    echo "Downloaded nlohmann/json.hpp"
fi

# Compile
echo ""
cd "$BUILD_DIR"

# Build platform-specific flags
INCLUDE_FLAGS=(
    -I"$BUILD_DIR"
    -I"$CPP_DIR"
    -I"$CPP_DIR/include"
    -I"$JDK_INCLUDE"
    -I"$JDK_PLATFORM_INCLUDE"
)
LINK_FLAGS=(-lgmp -lmpfr)

if [ "$PLATFORM" = "macos" ]; then
    INCLUDE_FLAGS+=(
        -I"$GMP_PREFIX/include"
        -I"$MPFR_PREFIX/include"
    )
    LINK_FLAGS+=(
        -L"$GMP_PREFIX/lib"
        -L"$MPFR_PREFIX/lib"
    )
    SHARED_FLAG="-dynamiclib"
else
    INCLUDE_FLAGS+=(-I/usr/include)
    SHARED_FLAG="-shared"
fi

# Compile ttf2mesh as a separate object first (C library, compiled with C compiler)
echo "Compiling ttf2mesh..."
if [ "$PLATFORM" = "macos" ]; then
    CC_CMD="clang"
else
    CC_CMD="gcc"
fi
$CC_CMD -c -fPIC -O2 \
    "$CPP_DIR/ttf2mesh/ttf2mesh.c" \
    -o "$BUILD_DIR/ttf2mesh.o" \
    2>&1

echo ""
echo "Compiling cgal_engine with $CXX..."

$CXX $SHARED_FLAG -fPIC -O2 \
    "$CPP_DIR/cgal_engine.cpp" \
    "$CPP_DIR/cgal_compute.cpp" \
    "$CPP_DIR/scene_builder.cpp" \
    "$CPP_DIR/mesh_extractor.cpp" \
    "$CPP_DIR/text_renderer.cpp" \
    "$BUILD_DIR/ttf2mesh.o" \
    -o "$LIB_NAME" \
    "${INCLUDE_FLAGS[@]}" \
    "${LINK_FLAGS[@]}" \
    2>&1

if [ -f "$BUILD_DIR/$LIB_NAME" ]; then
    echo ""
    echo "=== Build successful ==="
    echo "Output: $BUILD_DIR/$LIB_NAME"
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
