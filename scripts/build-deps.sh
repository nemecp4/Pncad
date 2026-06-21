#!/usr/bin/env bash
#
# Build all native dependencies for the CGAL compute engine.
#
# This script downloads, cross-compiles, and installs:
#   - GMP  (GNU Multiple Precision Arithmetic Library)
#   - MPFR (Multiple Precision Floating-Point Reliable Library)
#   - CGAL headers (header-only, downloaded and placed)
#   - Boost headers (header-only subset, downloaded and placed)
#
# Usage:
#   ./scripts/build-deps.sh [--only gmp|mpfr|cgal|boost]
#
# Environment:
#   ANDROID_NDK_HOME  - Path to Android NDK (required)
#   API_LEVEL         - Android API level (default: 26)
#   JOBS              - Parallel make jobs (default: nproc)
#   GMP_VERSION       - GMP version to build (default: 6.3.0)
#   MPFR_VERSION      - MPFR version to build (default: 4.2.1)
#   CGAL_VERSION      - CGAL version to download (default: 6.0.1)
#   BOOST_VERSION     - Boost version to download (default: 1.86.0)
#
# Output:
#   app/vendor/gmp/include/gmp.h
#   app/vendor/gmp/lib/{arm64-v8a,armeabi-v7a,x86_64}/libgmp.a
#   app/vendor/mpfr/include/mpfr.h
#   app/vendor/mpfr/lib/{arm64-v8a,armeabi-v7a,x86_64}/libmpfr.a
#   app/vendor/cgal/include/CGAL/...
#   app/vendor/boost/include/boost/...
#

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

API_LEVEL="${API_LEVEL:-26}"
GMP_VERSION="${GMP_VERSION:-6.3.0}"
MPFR_VERSION="${MPFR_VERSION:-4.2.1}"
CGAL_VERSION="${CGAL_VERSION:-6.0.1}"
BOOST_VERSION="${BOOST_VERSION:-1.86.0}"

# Detect CPU count (cross-platform)
detect_jobs() {
    if [[ -n "${JOBS:-}" ]]; then
        echo "$JOBS"
    elif command -v nproc &>/dev/null; then
        nproc
    elif command -v sysctl &>/dev/null; then
        sysctl -n hw.ncpu
    else
        echo 4
    fi
}
JOBS="$(detect_jobs)"

# Parse --only flag
BUILD_ONLY="${2:-all}"
if [[ "${1:-}" == "--only" && -n "${2:-}" ]]; then
    BUILD_ONLY="$2"
fi

# Resolve paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
VENDOR_DIR="$PROJECT_ROOT/app/vendor"
BUILD_DIR="$PROJECT_ROOT/.build-deps"

mkdir -p "$BUILD_DIR"

# ---------------------------------------------------------------------------
# Validate NDK
# ---------------------------------------------------------------------------

validate_ndk() {
    if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
        echo "ERROR: ANDROID_NDK_HOME is not set." >&2
        echo "  export ANDROID_NDK_HOME=/path/to/ndk" >&2
        exit 1
    fi

    if [[ ! -d "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt" ]]; then
        echo "ERROR: Invalid NDK path: $ANDROID_NDK_HOME" >&2
        exit 1
    fi

    # Detect host platform
    if [[ -d "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64" ]]; then
        TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"
    elif [[ -d "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64" ]]; then
        TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64"
    elif [[ -d "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-arm64" ]]; then
        TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-arm64"
    else
        echo "ERROR: Cannot find NDK toolchain in $ANDROID_NDK_HOME" >&2
        exit 1
    fi
}

# ---------------------------------------------------------------------------
# Target ABIs
# ---------------------------------------------------------------------------

# Format: ABI | configure --host | clang target prefix
declare -a TARGETS=(
    "arm64-v8a|aarch64-linux-android|aarch64-linux-android"
    "armeabi-v7a|arm-linux-androideabi|armv7a-linux-androideabi"
    "x86_64|x86_64-linux-android|x86_64-linux-android"
)

# ---------------------------------------------------------------------------
# Download helper
# ---------------------------------------------------------------------------

download() {
    local url="$1"
    local dest="$2"

    if [[ -f "$dest" ]]; then
        echo "    (cached: $(basename "$dest"))"
        return 0
    fi

    echo "    Downloading $(basename "$dest")..."
    curl -fSL --progress-bar -o "$dest" "$url"
}

# ---------------------------------------------------------------------------
# Build GMP
# ---------------------------------------------------------------------------

build_gmp() {
    echo ""
    echo "================================================================"
    echo " Building GMP ${GMP_VERSION}"
    echo "================================================================"

    local gmp_tar="$BUILD_DIR/gmp-${GMP_VERSION}.tar.xz"
    local gmp_src="$BUILD_DIR/gmp-${GMP_VERSION}"

    download "https://gmplib.org/download/gmp/gmp-${GMP_VERSION}.tar.xz" "$gmp_tar"

    if [[ ! -d "$gmp_src" ]]; then
        echo "    Extracting..."
        tar xf "$gmp_tar" -C "$BUILD_DIR"
    fi

    mkdir -p "$VENDOR_DIR/gmp/include"

    for target_spec in "${TARGETS[@]}"; do
        IFS='|' read -r abi configure_host clang_prefix <<< "$target_spec"

        echo ""
        echo "--- GMP: $abi ---"

        local install_dir="$BUILD_DIR/gmp-install-$abi"
        local build_dir="$BUILD_DIR/gmp-build-$abi"

        rm -rf "$build_dir" "$install_dir"
        mkdir -p "$build_dir"

        local cc="$TOOLCHAIN/bin/${clang_prefix}${API_LEVEL}-clang"
        local cxx="$TOOLCHAIN/bin/${clang_prefix}${API_LEVEL}-clang++"

        if [[ ! -x "$cc" ]]; then
            echo "ERROR: Compiler not found: $cc" >&2
            exit 1
        fi

        pushd "$build_dir" > /dev/null

        if ! "$gmp_src/configure" \
            --host="$configure_host" \
            --prefix="$install_dir" \
            --disable-shared \
            --enable-static \
            --disable-assembly \
            --with-pic \
            CC="$cc" \
            CXX="$cxx" \
            AR="$TOOLCHAIN/bin/llvm-ar" \
            RANLIB="$TOOLCHAIN/bin/llvm-ranlib" \
            NM="$TOOLCHAIN/bin/llvm-nm" \
            STRIP="$TOOLCHAIN/bin/llvm-strip" \
            CFLAGS="-fPIC" \
            CXXFLAGS="-fPIC" \
            > configure.log 2>&1; then
            echo "ERROR: GMP configure failed for $abi. Log:" >&2
            tail -20 configure.log >&2
            exit 1
        fi

        if ! make -j"$JOBS" > make.log 2>&1; then
            echo "ERROR: GMP make failed for $abi. Log:" >&2
            tail -30 make.log >&2
            exit 1
        fi

        if ! make install > install.log 2>&1; then
            echo "ERROR: GMP install failed for $abi. Log:" >&2
            tail -20 install.log >&2
            exit 1
        fi

        popd > /dev/null

        if [[ ! -f "$install_dir/lib/libgmp.a" ]]; then
            echo "ERROR: libgmp.a was not produced for $abi" >&2
            echo "  Check logs in: $build_dir/" >&2
            exit 1
        fi

        # Sanity check: a real libgmp.a should be at least 100KB
        local lib_size
        lib_size=$(wc -c < "$install_dir/lib/libgmp.a")
        if [[ "$lib_size" -lt 100000 ]]; then
            echo "ERROR: libgmp.a is suspiciously small (${lib_size} bytes) for $abi" >&2
            echo "  GMP build likely failed. Check: $build_dir/make.log" >&2
            exit 1
        fi

        mkdir -p "$VENDOR_DIR/gmp/lib/$abi"
        cp "$install_dir/include/gmp.h" "$VENDOR_DIR/gmp/include/gmp.h"
        cp "$install_dir/lib/libgmp.a" "$VENDOR_DIR/gmp/lib/$abi/libgmp.a"

        echo "    Installed: app/vendor/gmp/lib/$abi/libgmp.a"
    done

    echo ""
    echo "==> GMP ${GMP_VERSION} complete."
}

# ---------------------------------------------------------------------------
# Build MPFR (depends on GMP)
# ---------------------------------------------------------------------------

build_mpfr() {
    echo ""
    echo "================================================================"
    echo " Building MPFR ${MPFR_VERSION}"
    echo "================================================================"

    # Verify GMP was built
    if [[ ! -f "$VENDOR_DIR/gmp/include/gmp.h" ]]; then
        echo "ERROR: GMP must be built before MPFR." >&2
        echo "  Run: $0  (without --only) or build GMP first." >&2
        exit 1
    fi

    local mpfr_tar="$BUILD_DIR/mpfr-${MPFR_VERSION}.tar.xz"
    local mpfr_src="$BUILD_DIR/mpfr-${MPFR_VERSION}"

    download "https://www.mpfr.org/mpfr-${MPFR_VERSION}/mpfr-${MPFR_VERSION}.tar.xz" "$mpfr_tar"

    if [[ ! -d "$mpfr_src" ]]; then
        echo "    Extracting..."
        tar xf "$mpfr_tar" -C "$BUILD_DIR"
    fi

    mkdir -p "$VENDOR_DIR/mpfr/include"

    for target_spec in "${TARGETS[@]}"; do
        IFS='|' read -r abi configure_host clang_prefix <<< "$target_spec"

        echo ""
        echo "--- MPFR: $abi ---"

        local install_dir="$BUILD_DIR/mpfr-install-$abi"
        local build_dir="$BUILD_DIR/mpfr-build-$abi"
        local gmp_install="$BUILD_DIR/gmp-install-$abi"

        # If the per-ABI GMP install doesn't exist, create a temporary one
        # from the vendor directory with the layout configure expects
        if [[ ! -f "$gmp_install/lib/libgmp.a" ]]; then
            echo "    (creating GMP staging dir from vendor for $abi)"
            gmp_install="$BUILD_DIR/gmp-staging-$abi"
            rm -rf "$gmp_install"
            mkdir -p "$gmp_install/include" "$gmp_install/lib"
            if [[ ! -f "$VENDOR_DIR/gmp/lib/$abi/libgmp.a" ]]; then
                echo "ERROR: Cannot find libgmp.a for $abi in vendor dir either." >&2
                echo "  Expected: $VENDOR_DIR/gmp/lib/$abi/libgmp.a" >&2
                echo "  Run: $0 --only gmp first." >&2
                exit 1
            fi
            cp "$VENDOR_DIR/gmp/include/gmp.h" "$gmp_install/include/"
            cp "$VENDOR_DIR/gmp/lib/$abi/libgmp.a" "$gmp_install/lib/"
        fi

        echo "    Using GMP from: $gmp_install"
        echo "    libgmp.a exists: $(ls -la "$gmp_install/lib/libgmp.a" 2>&1)"

        rm -rf "$build_dir" "$install_dir"
        mkdir -p "$build_dir"

        local cc="$TOOLCHAIN/bin/${clang_prefix}${API_LEVEL}-clang"
        local cxx="$TOOLCHAIN/bin/${clang_prefix}${API_LEVEL}-clang++"

        pushd "$build_dir" > /dev/null

        if ! "$mpfr_src/configure" \
            --host="$configure_host" \
            --prefix="$install_dir" \
            --disable-shared \
            --enable-static \
            --with-gmp-include="$gmp_install/include" \
            --with-gmp-lib="$gmp_install/lib" \
            --with-pic \
            CC="$cc" \
            CXX="$cxx" \
            AR="$TOOLCHAIN/bin/llvm-ar" \
            RANLIB="$TOOLCHAIN/bin/llvm-ranlib" \
            NM="$TOOLCHAIN/bin/llvm-nm" \
            STRIP="$TOOLCHAIN/bin/llvm-strip" \
            CFLAGS="-fPIC -I$gmp_install/include" \
            CXXFLAGS="-fPIC -I$gmp_install/include" \
            LDFLAGS="-L$gmp_install/lib -lgmp" \
            > configure.log 2>&1; then
            echo "ERROR: MPFR configure failed for $abi. Log:" >&2
            tail -20 configure.log >&2
            exit 1
        fi

        if ! make -j"$JOBS" > make.log 2>&1; then
            echo "ERROR: MPFR make failed for $abi. Log:" >&2
            tail -20 make.log >&2
            exit 1
        fi

        if ! make install > install.log 2>&1; then
            echo "ERROR: MPFR install failed for $abi. Log:" >&2
            tail -20 install.log >&2
            exit 1
        fi

        popd > /dev/null

        if [[ ! -f "$install_dir/lib/libmpfr.a" ]]; then
            echo "ERROR: libmpfr.a was not produced for $abi" >&2
            echo "  Check logs in: $build_dir/" >&2
            exit 1
        fi

        mkdir -p "$VENDOR_DIR/mpfr/lib/$abi"
        cp "$install_dir/include/mpfr.h" "$VENDOR_DIR/mpfr/include/mpfr.h"
        cp "$install_dir/include/mpf2mpfr.h" "$VENDOR_DIR/mpfr/include/mpf2mpfr.h" 2>/dev/null || true
        cp "$install_dir/lib/libmpfr.a" "$VENDOR_DIR/mpfr/lib/$abi/libmpfr.a"

        echo "    Installed: app/vendor/mpfr/lib/$abi/libmpfr.a"
    done

    echo ""
    echo "==> MPFR ${MPFR_VERSION} complete."
}

# ---------------------------------------------------------------------------
# Download CGAL headers
# ---------------------------------------------------------------------------

install_cgal() {
    echo ""
    echo "================================================================"
    echo " Installing CGAL ${CGAL_VERSION} headers"
    echo "================================================================"

    local cgal_tar="$BUILD_DIR/CGAL-${CGAL_VERSION}-library.tar.xz"
    local cgal_src="$BUILD_DIR/CGAL-${CGAL_VERSION}"

    download \
        "https://github.com/CGAL/cgal/releases/download/v${CGAL_VERSION}/CGAL-${CGAL_VERSION}-library.tar.xz" \
        "$cgal_tar"

    if [[ ! -d "$cgal_src" ]]; then
        echo "    Extracting..."
        tar xf "$cgal_tar" -C "$BUILD_DIR"
    fi

    mkdir -p "$VENDOR_DIR/cgal/include"
    rm -rf "$VENDOR_DIR/cgal/include/CGAL"
    cp -r "$cgal_src/include/CGAL" "$VENDOR_DIR/cgal/include/"

    local header_count
    header_count=$(find "$VENDOR_DIR/cgal/include/CGAL" -name "*.h" | wc -l)
    echo "    Installed $header_count CGAL headers."
    echo ""
    echo "==> CGAL ${CGAL_VERSION} complete."
}

# ---------------------------------------------------------------------------
# Download Boost headers
# ---------------------------------------------------------------------------

install_boost() {
    echo ""
    echo "================================================================"
    echo " Installing Boost ${BOOST_VERSION} headers"
    echo "================================================================"

    local boost_underscore="${BOOST_VERSION//./_}"
    local boost_tar="$BUILD_DIR/boost_${boost_underscore}.tar.gz"
    local boost_src="$BUILD_DIR/boost_${boost_underscore}"

    download \
        "https://archives.boost.io/release/${BOOST_VERSION}/source/boost_${boost_underscore}.tar.gz" \
        "$boost_tar"

    if [[ ! -d "$boost_src" ]]; then
        echo "    Extracting (this may take a moment)..."
        tar xf "$boost_tar" -C "$BUILD_DIR"
    fi

    mkdir -p "$VENDOR_DIR/boost/include"
    rm -rf "$VENDOR_DIR/boost/include/boost"
    cp -r "$boost_src/boost" "$VENDOR_DIR/boost/include/"

    local header_count
    header_count=$(find "$VENDOR_DIR/boost/include/boost" -name "*.hpp" | wc -l)
    echo "    Installed $header_count Boost headers."
    echo ""
    echo "==> Boost ${BOOST_VERSION} complete."
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

echo "========================================"
echo " Pncad Native Dependency Builder"
echo "========================================"
echo ""
echo " GMP:   ${GMP_VERSION}"
echo " MPFR:  ${MPFR_VERSION}"
echo " CGAL:  ${CGAL_VERSION}"
echo " Boost: ${BOOST_VERSION}"
echo " API:   ${API_LEVEL}"
echo " Jobs:  ${JOBS}"
echo ""

case "$BUILD_ONLY" in
    gmp)
        validate_ndk
        build_gmp
        ;;
    mpfr)
        validate_ndk
        build_mpfr
        ;;
    cgal)
        install_cgal
        ;;
    boost)
        install_boost
        ;;
    all)
        validate_ndk
        build_gmp
        build_mpfr
        install_cgal
        install_boost
        ;;
    *)
        echo "ERROR: Unknown component '$BUILD_ONLY'" >&2
        echo "  Valid: gmp, mpfr, cgal, boost, all" >&2
        exit 1
        ;;
esac

echo ""
echo "========================================"
echo " Done! Verify with:"
echo "   find app/vendor -name '*.a' -o -name '*.h' | head -20"
echo "   ls app/vendor/cgal/include/CGAL/ | head -5"
echo "   ls app/vendor/boost/include/boost/ | head -5"
echo "========================================"
