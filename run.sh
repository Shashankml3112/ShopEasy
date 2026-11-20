#!/bin/sh
# Build and start ShopEasy on Mac/Linux (needs JDK 17+). Usage: ./run.sh [port]
cd "$(dirname "$0")" || exit 1
mkdir -p bin
javac -encoding UTF-8 -d bin $(find src -name '*.java') || exit 1
java -cp bin com.shop.ShopApp "$@"
