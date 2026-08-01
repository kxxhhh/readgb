#!/bin/bash

set -e

echo "Setting up global Codex config..."

mkdir -p ~/.codex

cp .codex/config.toml ~/.codex/config.toml

echo "Codex global config installed:"
cat ~/.codex/config.toml

echo "Done."