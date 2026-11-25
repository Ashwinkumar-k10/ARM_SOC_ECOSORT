#!/usr/bin/env bash
# arm_submission/setup_pi5_llama.sh
# Builds ExecuTorch's native C++ `llama_main` runner on Raspberry Pi 5 (Arm Cortex-A76)
# with XNNPACK (NEON/SDOT) + custom LLM ops (update_cache, custom_sdpa, 4bit embedding).

set -euo pipefail

EXECUTORCH_DIR="${HOME}/executorch"

echo "=========================================================================="
echo " [PI 5 SETUP] Building ExecuTorch C++ llama_main (Arm Cortex-A76 XNNPACK)"
echo "=========================================================================="

sudo apt-get update
sudo apt-get install -y git cmake ninja-build build-essential python3-pip

if [ ! -d "${EXECUTORCH_DIR}" ]; then
  git clone --depth 1 https://github.com/pytorch/executorch.git "${EXECUTORCH_DIR}"
fi

cd "${EXECUTORCH_DIR}"
git submodule sync && git submodule update --init --recursive

cmake -B cmake-out -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX=cmake-out \
  -DEXECUTORCH_BUILD_XNNPACK=ON \
  -DEXECUTORCH_BUILD_EXTENSION_MODULE=ON \
  -DEXECUTORCH_BUILD_EXTENSION_TENSOR=ON \
  -DEXECUTORCH_BUILD_EXTENSION_LLM=ON \
  -DEXECUTORCH_BUILD_EXTENSION_LLM_RUNNER=ON \
  -DEXECUTORCH_BUILD_KERNELS_QUANTIZED=ON \
  -DEXECUTORCH_BUILD_KERNELS_OPTIMIZED=ON \
  -DEXECUTORCH_BUILD_KERNELS_LLM=ON

cmake --build cmake-out -j"$(nproc)" --target install

cmake -B cmake-out/examples/models/llama -S examples/models/llama -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_PREFIX_PATH="${PWD}/cmake-out"

cmake --build cmake-out/examples/models/llama --target llama_main -j"$(nproc)"

LLAMA_BIN="${EXECUTORCH_DIR}/cmake-out/examples/models/llama/llama_main"
echo ""
echo "[✓] Build complete: ${LLAMA_BIN}"
echo "[*] Export before running pi5_edge_server.py:"
echo "    export EXECUTORCH_LLAMA_MAIN=${LLAMA_BIN}"
