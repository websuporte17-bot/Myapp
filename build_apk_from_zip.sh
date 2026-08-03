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

ANDROID_SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"

if [ -f "$EXTRACT_DIR/gradlew" ] || find "$EXTRACT_DIR" -maxdepth 3 -name 'build.gradle' | grep -q .; then
  echo "Projeto Android detectado. Tentando compilar..."

  if [ -n "$ANDROID_SDK_DIR" ] && [ ! -f "$EXTRACT_DIR/local.properties" ]; then
    echo "ANDROID_HOME/ANDROID_SDK_ROOT detectado: $ANDROID_SDK_DIR"
    echo "Criando local.properties no projeto para apontar para o SDK Android..."
    printf 'sdk.dir=%s\n' "$ANDROID_SDK_DIR" > "$EXTRACT_DIR/local.properties"
  fi

  if [ -f "$EXTRACT_DIR/gradlew" ]; then
    if ! (cd "$EXTRACT_DIR" && chmod +x gradlew && ./gradlew assembleDebug); then
      echo "A compilação falhou. Verifique o SDK Android e o arquivo local.properties."
      echo "Defina ANDROID_HOME ou ANDROID_SDK_ROOT, ou crie local.properties com sdk.dir."
      exit 1
    fi
  else
    if ! (cd "$EXTRACT_DIR" && gradle assembleDebug); then
      echo "A compilação falhou. Verifique o SDK Android e o arquivo local.properties."
      echo "Defina ANDROID_HOME ou ANDROID_SDK_ROOT, ou crie local.properties com sdk.dir."
      exit 1
    fi
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
