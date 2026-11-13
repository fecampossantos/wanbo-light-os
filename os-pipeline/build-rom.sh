#!/usr/bin/env bash
# ==============================================================================
# WanboLightOS Firmware Build Orchestration Wrapper
# Target: Wanbo T2 Max Smart Projector (armeabi-v7a, Android 9 Pie, API 28)
# ==============================================================================
set -euo pipefail

# Script directory resolution
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BUILD_PY="${SCRIPT_DIR}/build_rom.py"

# /**
#  * Displays command usage instructions and exits.
#  *
#  * @param {string} program_name - The executable invocation name.
#  * @returns {void}
#  */
show_help() {
    echo "Usage: $0 <input_fw.bin> [output_fw.bin] [options]"
    echo ""
    echo "WanboLightOS Automated Firmware Unpack, Debloat, Injection & Repack Pipeline."
    echo ""
    echo "Positional Arguments:"
    echo "  input_fw.bin       Path to stock firmware binary (required)."
    echo "  output_fw.bin      Path to final flashable output binary (default: WanboLightOS_v1.0.bin)."
    echo ""
    echo "Options:"
    echo "  --dry-run          Simulate pipeline without modifying files or repacking."
    echo "  --launcher-apk     Explicit path to compiled WanboLauncher APK."
    echo "  --settings-apk     Explicit path to compiled WanboSettings APK."
    echo "  --material-files-apk Explicit path to MaterialFiles APK."
    echo "  --aptoide-apk      Explicit path to AptoideTV APK."
    echo "  --debloat-list     Explicit path to debloat-list.txt."
    echo "  --build-prop-patch Explicit path to build.prop.patch."
    echo "  -v, --verbose      Enable verbose debug output."
    echo "  -h, --help         Show this help message and exit."
    echo ""
    echo "Examples:"
    echo "  $0 stock_firmware.bin WanboLightOS_v1.0.bin"
    echo "  $0 stock_firmware.bin --dry-run"
}

# /**
#  * Validates Python 3 interpreter availability.
#  *
#  * @returns {string} Path to Python 3 interpreter.
#  */
detect_python() {
    if command -v python3 >/dev/null 2>&1; then
        echo "python3"
    elif command -v python >/dev/null 2>&1; then
        echo "python"
    else
        echo "[ERROR] Python interpreter not found in PATH. Please install Python 3.8+." >&2
        exit 1
    fi
}

# /**
#  * Main execution entry point.
#  *
#  * @param {...string} args - Command-line arguments.
#  * @returns {int} Exit code from python build pipeline.
#  */
main() {
    if [[ $# -eq 0 ]]; then
        show_help
        exit 1
    fi

    if [[ "$1" == "-h" || "$1" == "--help" ]]; then
        show_help
        exit 0
    fi

    local py_bin
    py_bin="$(detect_python)"

    echo "[INFO] Starting WanboLightOS Firmware Build Wrapper..."
    echo "[INFO] Using Python interpreter: ${py_bin}"

    # Forward all arguments directly to build_rom.py
    exec "${py_bin}" "${BUILD_PY}" "$@"
}

main "$@"
