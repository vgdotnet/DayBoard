cmake_minimum_required(VERSION 3.22.1)
project(dayboardminer C)

add_library(dayboardminer SHARED sha256_arm.c)

# The kernel is for arm64 with the ARMv8 crypto extensions (sha2). Other ABIs get a stub that
# reports "unsupported" and the app falls back to the Kotlin kernel.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(dayboardminer PRIVATE -O3 -march=armv8-a+crypto -DHAVE_ARM_SHA2=1)
else()
    target_compile_options(dayboardminer PRIVATE -O2)
endif()

target_link_libraries(dayboardminer log)
