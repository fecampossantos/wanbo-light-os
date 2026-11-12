# ==============================================================================
# WanboLightOS Root Build & Orchestration Makefile
# Target: Wanbo T2 Max Smart Projector (armeabi-v7a, Android 9 Pie, API 28)
# ==============================================================================

.DEFAULT_GOAL := help
.PHONY: help launcher test rom clean all

# Configurable build parameters
PYTHON ?= python3
INPUT_FW ?= stock_firmware.bin
OUTPUT_FW ?= WanboLightOS_v1.0.bin
EXTRA_ARGS ?=

all: help

help:
	@echo "WanboLightOS Build System"
	@echo ""
	@echo "Available Targets:"
	@echo "  help      - Print this usage summary of available targets."
	@echo "  launcher  - Build the Android TV Launcher APK via Gradle."
	@echo "  test      - Run automated unit and pipeline verification tests."
	@echo "  rom       - Execute the firmware unpack, debloat, inject, and repack pipeline."
	@echo "  clean     - Remove temporary build artifacts and intermediate files."
	@echo ""
	@echo "Configuration Variables:"
	@echo "  INPUT_FW   - Path to stock firmware binary (default: $(INPUT_FW))"
	@echo "  OUTPUT_FW  - Path to output firmware binary (default: $(OUTPUT_FW))"
	@echo "  PYTHON     - Python interpreter command (default: $(PYTHON))"
	@echo "  EXTRA_ARGS - Additional CLI flags forwarded to build_rom.py"
	@echo ""
	@echo "Examples:"
	@echo "  make launcher"
	@echo "  make test"
	@echo "  make rom INPUT_FW=stock.bin EXTRA_ARGS=--dry-run"

launcher:
	@echo "[INFO] Building Wanbo TV Launcher APK..."
	@if [ -f launcher/gradlew ]; then \
		(cd launcher && ./gradlew assembleRelease); \
	elif command -v gradle >/dev/null 2>&1; then \
		(cd launcher && gradle assembleRelease); \
	else \
		echo "[WARN] Gradle wrapper or executable not found in PATH; skipping launcher build."; \
	fi

test:
	@echo "[INFO] Running WanboLightOS unit and pipeline verification tests..."
	@$(PYTHON) -m unittest discover -s os-pipeline/tests -p "test_*.py" -v

rom:
	@echo "[INFO] Running automated firmware unpack, debloat, injection & repack pipeline..."
	@$(PYTHON) os-pipeline/build_rom.py "$(INPUT_FW)" "$(OUTPUT_FW)" $(EXTRA_ARGS)

clean:
	@echo "[INFO] Cleaning temporary build artifacts..."
	@rm -rf build launcher/app/build launcher/build os-pipeline/__pycache__ os-pipeline/tests/__pycache__
