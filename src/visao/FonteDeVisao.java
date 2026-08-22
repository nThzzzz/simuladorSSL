package visao;

/**
 * De onde vem o quadro que o cliente desenha.
 *
 * <p>Implementacoes previstas: {@code VisaoLocal} (le o simulador no mesmo
 * processo) e, na fase de rede, um receptor UDP do {@code SSL_WrapperPacket}.
 * O cliente nao distingue as duas.
 */
public interface FonteDeVisao {

    /** Ultimo quadro disponivel. Nunca nulo -- antes do primeiro, um quadro vazio. */
    EstadoMundo ultimoQuadro();
}
