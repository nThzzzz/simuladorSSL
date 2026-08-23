#!/usr/bin/env bash
# Compila tudo.
#
# Nao precisa de Gradle nem de Maven: o Java gerado dos .proto esta versionado em
# src/proto/ e os jars necessarios moram em lib/, tambem versionados. Um clone
# limpo compila offline.
#
# A busca dos jars so existe como rede de seguranca para quem apagou lib/ ou usa um
# checkout parcial -- no caminho normal ela nunca roda.
set -euo pipefail

PROTOBUF_VERSAO="4.34.0"
FLATLAF_VERSAO="3.7.2"
FONTE_VERSAO="2.304"

raiz="$(cd "$(dirname "$0")/.." && pwd)"
cd "$raiz"

# jar|url, na ordem em que aparecem no classpath
dependencias=(
  "lib/protobuf-java-${PROTOBUF_VERSAO}.jar|https://repo1.maven.org/maven2/com/google/protobuf/protobuf-java/${PROTOBUF_VERSAO}/protobuf-java-${PROTOBUF_VERSAO}.jar"
  "lib/flatlaf-${FLATLAF_VERSAO}.jar|https://repo1.maven.org/maven2/com/formdev/flatlaf/${FLATLAF_VERSAO}/flatlaf-${FLATLAF_VERSAO}.jar"
  "lib/flatlaf-fonts-jetbrains-mono-${FONTE_VERSAO}.jar|https://repo1.maven.org/maven2/com/formdev/flatlaf-fonts-jetbrains-mono/${FONTE_VERSAO}/flatlaf-fonts-jetbrains-mono-${FONTE_VERSAO}.jar"
)

for dependencia in "${dependencias[@]}"; do
  jar="${dependencia%%|*}"
  url="${dependencia#*|}"
  [ -f "$jar" ] && continue
  echo "$jar nao encontrado, baixando..."
  mkdir -p lib
  if ! curl -sSfL --max-time 120 -o "$jar" "$url"; then
    rm -f "$jar"
    echo "falha ao baixar $(basename "$jar")." >&2
    echo "  baixe manualmente para $jar:" >&2
    echo "  $url" >&2
    exit 1
  fi
  echo "baixado."
done

saida=out/production/SSL
rm -rf "$saida" && mkdir -p "$saida"
javac -Xlint:all,-serial -cp "lib/*" -d "$saida" $(find src -name "*.java")

echo "compilado em $saida"
echo
echo "  java -cp \"$saida:lib/*\" Main                    janela + rede"
echo "  java -cp \"$saida:lib/*\" Main --ajuda            opcoes"
echo "  java -cp \"$saida:lib/*\" teste.Autoteste         fisica"
echo "  java -cp \"$saida:lib/*\" teste.AutotesteRede     protocolo"
