#!/usr/bin/env bash
#
# Generates Elixir and Erlang client/server snapshots for every model under
# codegen/codegen-test/src/test/resources/model/, writing results into
# codegen/codegen-test/src/test/resources/snapshots/<modelname>/.
#
# Usage:
#   ./generate_snapshots.sh
#
# Prerequisites:
#   - smithy CLI on PATH
#   - JDK 17+
#

# set -euo pipefail

SCRIPT_DIR=.
MODEL_DIR="${SCRIPT_DIR}/model"
SNAPSHOT_DIR="${SCRIPT_DIR}/snapshots"
VERSION="0.1.0-SNAPSHOT"

for model_file in "${MODEL_DIR}"/*.smithy; do
    model_name="$(basename "${model_file}" .smithy)"
    namespace="$(grep -m1 '^namespace ' "${model_file}" | awk '{print $2}')"
    service_name="$(grep -m1 '^service ' "${model_file}" | awk '{print $2}')"
    service_id="${namespace}#${service_name}"

    echo "Generating: ${model_name} (${service_id})"

    tmp_dir="$(mktemp -d)"
    mkdir -p "${tmp_dir}/model"
    cp "${model_file}" "${tmp_dir}/model/"

    cat > "${tmp_dir}/smithy-build.json" <<EOF
{
  "version": "1.0",
  "sources": ["model"],
  "maven": {
    "repositories": [
      { "url": "file://~/.m2/repository" }
    ],
    "dependencies": [
      "io.smithy.beam:codegen-erlang:${VERSION}",
      "io.smithy.beam:codegen-elixir:${VERSION}"
    ]
  },
  "plugins": {
    "erlang-client-codegen": {
      "service": "${service_id}",
      "module":  "${model_name}",
      "edition": "2025",
      "outputDir": "erlang/client"
    },
    "erlang-server-codegen": {
      "service": "${service_id}",
      "module":  "${model_name}",
      "edition": "2025",
      "outputDir": "erlang/server"
    },
    "elixir-client-codegen": {
      "service":   "${service_id}",
      "namespace": "${service_name}",
      "edition":   "2025",
      "outputDir": "elixir/client"
    },
    "elixir-server-codegen": {
      "service":   "${service_id}",
      "namespace": "${service_name}",
      "edition":   "2025",
      "outputDir": "elixir/server"
    }
  }
}
EOF

    (cd "${tmp_dir}" && smithy build > build.log 2>&1)

    dest="${SNAPSHOT_DIR}/${model_name}"
    rm -rf "${dest}"
    mkdir -p "${dest}"
    cp "${tmp_dir}/build.log" "${dest}/build.log"
    cp -r "${tmp_dir}/elixir" "${dest}/elixir"
    cp -r "${tmp_dir}/erlang" "${dest}/erlang"
    rm -rf "${tmp_dir}"
done

echo "Done. Snapshots written to ${SNAPSHOT_DIR}"

tree snapshots
