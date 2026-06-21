cmake_minimum_required(VERSION 3.22)
project(cgal_engine CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Vendor directory (app/vendor relative to app/src/main/cpp)
set(VENDOR_DIR ${CMAKE_SOURCE_DIR}/../../../vendor)

# CGAL headers (mandatory)
if(NOT EXISTS "${VENDOR_DIR}/cgal/include/CGAL")
    message(FATAL_ERROR
        "CGAL headers not found at ${VENDOR_DIR}/cgal/include/CGAL\n"
        "Please place CGAL headers in app/vendor/cgal/include/")
endif()

# Boost headers (mandatory)
if(NOT EXISTS "${VENDOR_DIR}/boost/include/boost")
    message(FATAL_ERROR
        "Boost headers not found at ${VENDOR_DIR}/boost/include/boost\n"
        "Please place Boost headers in app/vendor/boost/include/")
endif()

# GMP static library (mandatory)
set(GMP_LIB "${VENDOR_DIR}/gmp/lib/${ANDROID_ABI}/libgmp.a")
if(NOT EXISTS "${GMP_LIB}")
    message(FATAL_ERROR
        "GMP static library not found at ${GMP_LIB}\n"
        "Please place libgmp.a in app/vendor/gmp/lib/${ANDROID_ABI}/")
endif()

# MPFR static library (mandatory)
set(MPFR_LIB "${VENDOR_DIR}/mpfr/lib/${ANDROID_ABI}/libmpfr.a")
if(NOT EXISTS "${MPFR_LIB}")
    message(FATAL_ERROR
        "MPFR static library not found at ${MPFR_LIB}\n"
        "Please place libmpfr.a in app/vendor/mpfr/lib/${ANDROID_ABI}/")
endif()

# CGAL header-only mode
add_definitions(-DCGAL_HEADER_ONLY)

# Vendor headers as SYSTEM to suppress warnings from third-party code
include_directories(SYSTEM
    ${VENDOR_DIR}/cgal/include
    ${VENDOR_DIR}/boost/include
    ${VENDOR_DIR}/gmp/include
    ${VENDOR_DIR}/mpfr/include
)

# Project headers (not SYSTEM — warnings are useful here)
include_directories(
    ${CMAKE_SOURCE_DIR}/include
)

# Native shared library
add_library(cgal_engine SHARED
    cgal_engine.cpp
    cgal_compute.cpp
    scene_builder.cpp
    mesh_extractor.cpp
)

target_link_libraries(cgal_engine
    ${GMP_LIB}
    ${MPFR_LIB}
    log
)
