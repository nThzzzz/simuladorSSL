package demo;

import java.util.function.Consumer;

/**
 * Uma acao do roteiro, disparada quando o cenario atinge {@code t} segundos.
 *
 * @param t      instante desde o inicio do ciclo, em segundos
 * @param rotulo nome curto que vai para o log
 * @param acao   o que fazer no mundo
 */
public record Passo(double t, String rotulo, Consumer<Contexto> acao) {}
