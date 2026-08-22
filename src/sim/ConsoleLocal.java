package sim;

import log.ConfigLog;

import java.nio.file.Path;

/**
 * Controle da gravacao, oferecido pelo simulador a sua propria interface.
 *
 * <p>Nao ha mensagem no protocolo da SSL para "comece a gravar" -- gravar log e
 * assunto de quem opera o simulador, nao de quem se conecta a ele. Por isso vive
 * numa interface separada do {@link visao.CanalDeControle}, que e a porta que a
 * rede tambem usa.
 */
public interface ConsoleLocal {

    void iniciarGravacao(Path diretorio, ConfigLog config);

    void pararGravacao();

    boolean estaGravando();

    long getQuadrosGravados();

    long getEventosGravados();
}
