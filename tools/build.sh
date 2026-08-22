#!/usr/bin/env bash
# Compila tudo.
#
# Nao precisa de Gradle nem de Maven: o Java gerado dos .proto esta versionado em
# src/proto/ e o unico jar necessario mora em lib/, tambem versionado. Um clone
# limpo compila offline.
#
# A busca do jar so existe como rede de seguranca para quem apagou lib/ ou usa um
# checkout parcial -- no caminho normal ela nunca roda.
set -euo pipefail

PROTOBUF_VERSAO="4.34.0"

raiz="$(cd "$(dirname "$0")/.." && pwd)"
cd "$raiz"

jar="lib/protobuf-java-${PROTOBUF_VERSAO}.jar"
if [ ! -f "$jar" ]; then
  echo "$jar nao encontrado, baixando..."
  mkdir -p lib
  url="https://repo1.maven.org/maven2/com/google/protobuf/protobuf-java/${PROTOBUF_VERSAO}/protobuf-java-${PROTOBUF_VERSAO}.jar"
  if ! curl -sSfL --max-time 120 -o "$jar" "$url"; then
    rm -f "$jar"
    echo "falha ao baixar o protobuf-java." >&2
    echo "  baixe manualmente para $jar:" >&2
    echo "  $url" >&2
    exit 1
  fi
  echo "baixado."
fi

saida=out/production/SSL
rm -rf "$saida" && mkdir -p "$saida"
javac -Xlint:all,-serial -cp "lib/*" -d "$saida" $(find src -name "*.java")

echo "compilado em $saida"
echo
echo "  java -cp \"$saida:lib/*\" Main                    janela + rede"
echo "  java -cp \"$saida:lib/*\" Main --ajuda            opcoes"
echo "  java -cp \"$saida:lib/*\" teste.Autoteste         fisica"
echo "  java -cp \"$saida:lib/*\" teste.AutotesteRede     protocolo"
