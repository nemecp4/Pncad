# Vendor Dependencies for CGAL Compute Engine

This directory contains third-party headers and pre-compiled static libraries required
by the native CGAL compute engine. These files are not included in version control due
to their size — you must obtain and place them manually before the native build will succeed.

## Directory Layout

```
vendor/
├── cgal/
│   └── include/          ← CGAL headers (header-only library)
│       └── CGAL/
│           ├── Exact_predicates_exact_constructions_kernel.h
│           ├── Nef_polyhedron_3.h
│           ├── Polyhedron_3.h
│           └── ...
├── boost/
│   └── include/          ← Boost headers (header-only subset)
│       └── boost/
│           ├── multiprecision/
│           ├── iterator/
│           └── ...
├── gmp/
│   ├── include/          ← GMP header
│   │   └── gmp.h
│   └── lib/
│       ├── arm64-v8a/    ← Static library for 64-bit ARM
│       │   └── libgmp.a
│       ├── armeabi-v7a/  ← Static library for 32-bit ARM
│       │   └── libgmp.a
│       └── x86_64/       ← Static library for x86_64 emulators
│           └── libgmp.a
└── mpfr/
    ├── include/          ← MPFR header
    │   └── mpfr.h
    └── lib/
        ├── arm64-v8a/
        │   └── libmpfr.a
        ├── armeabi-v7a/
        │   └── libmpfr.a
        └── x86_64/
            └── libmpfr.a
```

## Obtaining CGAL Headers

CGAL is header-only since version 5.0. No compilation is needed.

1. Download the latest release from https://github.com/CGAL/cgal/releases
2. Extract the archive
3. Copy the `include/CGAL/` directory into `vendor/cgal/include/`

Only the headers are needed — no library files, no CMake config.

Minimum required version: **CGAL 5.6** (for stable Nef polyhedron support with C++17).

## Obtaining Boost Headers

CGAL depends on a subset of Boost (header-only portions only). No compiled Boost
libraries are needed.

1. Download from https://www.boost.org/users/download/
2. Extract the archive
3. Copy the `boost/` directory into `vendor/boost/include/`

You only need the headers — the full Boost source tree is ~800 MB but the headers
alone are sufficient. Alternatively, use the BCP tool to extract only the subset
CGAL requires:

```bash
# From the Boost source tree:
./dist/bin/bcp \
  boost/multiprecision \
  boost/iterator \
  boost/property_map \
  boost/graph \
  boost/random \
  ../Pncad/app/vendor/boost/include
```

Minimum required version: **Boost 1.72** or later.

## Cross-Compiling GMP for Android

GMP (GNU Multiple Precision Arithmetic Library) must be compiled as a static library
for each target ABI using the Android NDK toolchain.

### Prerequisites

- Android NDK r25+ (set `ANDROID_NDK_HOME` environment variable)
- GMP source: https://gmplib.org/download/gmp/

### Build Steps

```bash
export NDK=$ANDROID_NDK_HOME
export TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64
export API=24

# --- arm64-v8a ---
export TARGET=aarch64-linux-android
export CC=$TOOLCHAIN/bin/$TARGET$API-clang
export CXX=$TOOLCHAIN/bin/$TARGET$API-clang++

cd gmp-6.3.0
./configure \
  --host=$TARGET \
  --prefix=$(pwd)/install-arm64 \
  --disable-shared \
  --enable-static \
  CC="$CC" CXX="$CXX"
make -j$(nproc)
make install

# Copy outputs:
# install-arm64/include/gmp.h      → vendor/gmp/include/gmp.h
# install-arm64/lib/libgmp.a       → vendor/gmp/lib/arm64-v8a/libgmp.a

# --- armeabi-v7a ---
export TARGET=armv7a-linux-androideabi
export CC=$TOOLCHAIN/bin/$TARGET$API-clang
export CXX=$TOOLCHAIN/bin/$TARGET$API-clang++

make distclean
./configure \
  --host=arm-linux-androideabi \
  --prefix=$(pwd)/install-armv7a \
  --disable-shared \
  --enable-static \
  CC="$CC" CXX="$CXX"
make -j$(nproc)
make install

# Copy: install-armv7a/lib/libgmp.a → vendor/gmp/lib/armeabi-v7a/libgmp.a

# --- x86_64 ---
export TARGET=x86_64-linux-android
export CC=$TOOLCHAIN/bin/$TARGET$API-clang
export CXX=$TOOLCHAIN/bin/$TARGET$API-clang++

make distclean
./configure \
  --host=$TARGET \
  --prefix=$(pwd)/install-x86_64 \
  --disable-shared \
  --enable-static \
  CC="$CC" CXX="$CXX"
make -j$(nproc)
make install

# Copy: install-x86_64/lib/libgmp.a → vendor/gmp/lib/x86_64/libgmp.a
```

## Cross-Compiling MPFR for Android

MPFR depends on GMP, so build GMP first.

### Prerequisites

- GMP already compiled for each ABI (see above)
- MPFR source: https://www.mpfr.org/mpfr-current/

### Build Steps

```bash
export NDK=$ANDROID_NDK_HOME
export TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64
export API=24

# --- arm64-v8a ---
export TARGET=aarch64-linux-android
export CC=$TOOLCHAIN/bin/$TARGET$API-clang
export CXX=$TOOLCHAIN/bin/$TARGET$API-clang++

cd mpfr-4.2.1
./configure \
  --host=$TARGET \
  --prefix=$(pwd)/install-arm64 \
  --disable-shared \
  --enable-static \
  --with-gmp=$(pwd)/../gmp-6.3.0/install-arm64 \
  CC="$CC" CXX="$CXX"
make -j$(nproc)
make install

# Copy outputs:
# install-arm64/include/mpfr.h     → vendor/mpfr/include/mpfr.h
# install-arm64/lib/libmpfr.a      → vendor/mpfr/lib/arm64-v8a/libmpfr.a

# --- armeabi-v7a ---
export TARGET=armv7a-linux-androideabi
export CC=$TOOLCHAIN/bin/$TARGET$API-clang
export CXX=$TOOLCHAIN/bin/$TARGET$API-clang++

make distclean
./configure \
  --host=arm-linux-androideabi \
  --prefix=$(pwd)/install-armv7a \
  --disable-shared \
  --enable-static \
  --with-gmp=$(pwd)/../gmp-6.3.0/install-armv7a \
  CC="$CC" CXX="$CXX"
make -j$(nproc)
make install

# Copy: install-armv7a/lib/libmpfr.a → vendor/mpfr/lib/armeabi-v7a/libmpfr.a

# --- x86_64 ---
export TARGET=x86_64-linux-android
export CC=$TOOLCHAIN/bin/$TARGET$API-clang
export CXX=$TOOLCHAIN/bin/$TARGET$API-clang++

make distclean
./configure \
  --host=$TARGET \
  --prefix=$(pwd)/install-x86_64 \
  --disable-shared \
  --enable-static \
  --with-gmp=$(pwd)/../gmp-6.3.0/install-x86_64 \
  CC="$CC" CXX="$CXX"
make -j$(nproc)
make install

# Copy: install-x86_64/lib/libmpfr.a → vendor/mpfr/lib/x86_64/libmpfr.a
```

## Verification

After placing all files, verify the structure matches the layout above:

```bash
# Check that all required files exist
find app/vendor -name "*.a" -o -name "*.h" | sort
```

Expected output (minimum):
```
app/vendor/gmp/include/gmp.h
app/vendor/gmp/lib/arm64-v8a/libgmp.a
app/vendor/gmp/lib/armeabi-v7a/libgmp.a
app/vendor/gmp/lib/x86_64/libgmp.a
app/vendor/mpfr/include/mpfr.h
app/vendor/mpfr/lib/arm64-v8a/libmpfr.a
app/vendor/mpfr/lib/armeabi-v7a/libmpfr.a
app/vendor/mpfr/lib/x86_64/libmpfr.a
```

And the CGAL/Boost header directories should contain their respective header trees.

## Notes

- The `.gitkeep` files in empty directories preserve the directory structure in git.
  They can be removed once actual library/header files are placed.
- Only static libraries (`.a`) are used — no shared libraries (`.so`) for GMP/MPFR.
  This avoids runtime library-loading issues on Android.
- The `gmp.h` header is the same across all ABIs — only one copy is needed in
  `vendor/gmp/include/`. Same for `mpfr.h`.
- CGAL and Boost are header-only — no per-ABI compilation needed for them.
