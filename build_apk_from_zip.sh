#!/usr/bin/env bash
set -euo pipefail

WORKDIR="/workspaces/Myapp"
ZIP_FILE="$WORKDIR/myapp.zip"
OUTPUT_DIR="$WORKDIR/seuapkakicrlh"
EXTRACT_DIR="$OUTPUT_DIR/extracted"

mkdir -p "$OUTPUT_DIR"
rm -rf "$EXTRACT_DIR"
mkdir -p "$EXTRACT_DIR"

if [ ! -f "$ZIP_FILE" ]; then
  echo "Arquivo não encontrado: $ZIP_FILE"
  echo "Coloque o arquivo myapp.zip na pasta do projeto e execute este script novamente."
  exit 1
fi

echo "Extraindo $ZIP_FILE..."
unzip -q "$ZIP_FILE" -d "$EXTRACT_DIR"

echo "Conteúdo extraído em $EXTRACT_DIR"

find "$EXTRACT_DIR" -type f \( -name '*.apk' -o -name 'AndroidManifest.xml' -o -name 'build.gradle' -o -name 'build.gradle.kts' -o -name 'settings.gradle' -o -name 'settings.gradle.kts' \) | sort | head -200

APK_FOUND=""
if find "$EXTRACT_DIR" -type f -name '*.apk' | grep -q .; then
  APK_FOUND=$(find "$EXTRACT_DIR" -type f -name '*.apk' | head -1)
fi

if [ -n "$APK_FOUND" ]; then
  cp "$APK_FOUND" "$OUTPUT_DIR/$(basename "$APK_FOUND")"
  echo "APK copiado para $OUTPUT_DIR/$(basename "$APK_FOUND")"
  exit 0
fi

if [ -f "$EXTRACT_DIR/gradlew" ] || find "$EXTRACT_DIR" -maxdepth 3 -name 'build.gradle' | grep -q .; then
  echo "Projeto Android detectado. Tentando compilar..."
  if [ -f "$EXTRACT_DIR/gradlew" ]; then
    (cd "$EXTRACT_DIR" && chmod +x gradlew && ./gradlew assembleDebug)
  else
    (cd "$EXTRACT_DIR" && gradle assembleDebug)
  fi

  if find "$EXTRACT_DIR" -type f -name '*.apk' | grep -q .; then
    APK_FOUND=$(find "$EXTRACT_DIR" -type f -name '*.apk' | head -1)
    cp "$APK_FOUND" "$OUTPUT_DIR/$(basename "$APK_FOUND")"
    echo "APK gerado em $OUTPUT_DIR/$(basename "$APK_FOUND")"
    exit 0
  fi
fi

echo "Não foi possível localizar nem gerar um APK a partir do conteúdo do pacote."
echo "Se o conteúdo for um projeto Android, verifique se ele contém um projeto Gradle válido."
