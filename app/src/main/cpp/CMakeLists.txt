cmake_minimum_required(VERSION 3.22.1)
project(netprobe_bionic C)
add_library(netprobe_bionic SHARED bionic_interfaces.c)
set_target_properties(netprobe_bionic PROPERTIES C_STANDARD 11 C_STANDARD_REQUIRED YES)
target_compile_options(netprobe_bionic PRIVATE -Wall -Wextra -Werror)
# r25 LLD can extend GNU_RELRO past the final LOAD when common-page-size=16384.
# Keep 16KiB LOAD alignment, with default 4KiB RELRO layout; verify on 4KiB/16KiB Android.
target_link_options(netprobe_bionic PRIVATE "-Wl,-z,max-page-size=16384")
