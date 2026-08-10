cmake_minimum_required(VERSION 3.22.1)

project(draarl_rnnoise)

set(CMAKE_C_STANDARD 99)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(RNNOISE_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/third_party/rnnoise)
set(RNNOISE_SRC ${RNNOISE_ROOT}/src)

add_library(
    draarl_rnnoise
    SHARED
    rnnoise_jni.cpp
    ${RNNOISE_SRC}/celt_lpc.c
    ${RNNOISE_SRC}/denoise.c
    ${RNNOISE_SRC}/kiss_fft.c
    ${RNNOISE_SRC}/nnet.c
    ${RNNOISE_SRC}/nnet_default.c
    ${RNNOISE_SRC}/parse_lpcnet_weights.c
    ${RNNOISE_SRC}/pitch.c
    ${RNNOISE_SRC}/rnn.c
    ${RNNOISE_SRC}/rnnoise_data.c
    ${RNNOISE_SRC}/rnnoise_tables.c
)

target_compile_definitions(draarl_rnnoise PRIVATE RNNOISE_BUILD)
target_compile_options(draarl_rnnoise PRIVATE -O3)
target_link_options(
    draarl_rnnoise
    PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
target_include_directories(
    draarl_rnnoise
    PRIVATE
    ${RNNOISE_ROOT}/include
    ${RNNOISE_SRC}
)

find_library(log-lib log)

target_link_libraries(
    draarl_rnnoise
    ${log-lib}
    m
)
