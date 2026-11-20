cmake_minimum_required(VERSION 3.24)
project(XikeNative LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_MACOSX_BUNDLE OFF)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(protobuf_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(protobuf_BUILD_PROTOC_BINARIES ON CACHE BOOL "" FORCE)

include(FetchContent)
FetchContent_Declare(tink_cc
    GIT_REPOSITORY https://github.com/tink-crypto/tink-cc.git
    GIT_TAG v2.9.2
    GIT_SHALLOW TRUE)
FetchContent_MakeAvailable(tink_cc)

foreach(feature TEST INSTALL TAR CPIO CAT UNZIP OPENSSL LIBXML2 EXPAT BZip2 LZMA LZ4 ZSTD LZO LIBB2 ACL XATTR ICONV)
    set(ENABLE_${feature} OFF CACHE BOOL "" FORCE)
endforeach()
FetchContent_Declare(libarchive
    GIT_REPOSITORY https://github.com/libarchive/libarchive.git
    GIT_TAG v3.8.9
    GIT_SHALLOW TRUE)
FetchContent_MakeAvailable(libarchive)

FetchContent_Declare(libwebm
    GIT_REPOSITORY https://github.com/webmproject/libwebm.git
    GIT_TAG libwebm-1.0.0.32
    GIT_SHALLOW TRUE
    SOURCE_SUBDIR mkvparser)
FetchContent_MakeAvailable(libwebm)

add_library(XikeNative SHARED XikeNative.cc include/XikeNative.h
    ${libwebm_SOURCE_DIR}/mkvparser/mkvparser.cc)
target_include_directories(XikeNative PRIVATE
    include ${tink_cc_SOURCE_DIR} ${libarchive_SOURCE_DIR}/libarchive ${libwebm_SOURCE_DIR})
target_link_libraries(XikeNative PRIVATE tink::subtle::aes_gcm_hkdf_streaming
    tink::util::file_input_stream tink::util::file_output_stream
    tink::util::file_random_access_stream tink::util::input_stream_util
    archive_static crypto)
set_target_properties(XikeNative PROPERTIES
    FRAMEWORK TRUE
    MACOSX_FRAMEWORK_IDENTIFIER com.xike.native
    PUBLIC_HEADER include/XikeNative.h)

set(xike_licenses "${CMAKE_CURRENT_BINARY_DIR}/licenses")
file(MAKE_DIRECTORY "${xike_licenses}")
configure_file("${tink_cc_SOURCE_DIR}/LICENSE" "${xike_licenses}/Tink-LICENSE.txt" COPYONLY)
configure_file("${libarchive_SOURCE_DIR}/COPYING" "${xike_licenses}/libarchive-COPYING.txt" COPYONLY)
configure_file("${libwebm_SOURCE_DIR}/LICENSE.TXT" "${xike_licenses}/libwebm-LICENSE.txt" COPYONLY)
configure_file("${libwebm_SOURCE_DIR}/PATENTS.TXT" "${xike_licenses}/libwebm-PATENTS.txt" COPYONLY)
configure_file("${FETCHCONTENT_BASE_DIR}/boringssl-src/LICENSE" "${xike_licenses}/BoringSSL-LICENSE.txt" COPYONLY)
configure_file("${FETCHCONTENT_BASE_DIR}/abseil-src/LICENSE" "${xike_licenses}/Abseil-LICENSE.txt" COPYONLY)
add_custom_command(TARGET XikeNative POST_BUILD
    COMMAND "${CMAKE_COMMAND}" -E copy_directory "${xike_licenses}" "$<TARGET_BUNDLE_DIR:XikeNative>/Licenses")
