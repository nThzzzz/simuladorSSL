package log;

import engine.Evento;
import engine.Mundo;

import java.util.List;

/**
 * Destino do log da simulacao.
 *
 * <p>Dois streams com naturezas diferentes: {@link #quadro} e denso e regular
 * (uma amostra por tick, como a SSL-Vision entrega), {@link #eventos} e esparso
 * e semantico (o que aconteceu e por que).
 */
public interface Logger extends AutoCloseable {

    /** Grava o cabecalho: geometria, parametros de fisica, equipes, dt. */
    void inicio(Mundo mundo, double dt);

    /** Amostra o estado completo do mundo neste quadro. */
    void quadro(Mundo mundo);

    /** Registra os eventos discretos drenados do mundo. */
    void eventos(List<Evento> eventos);

    /** Quantos quadros de tracking foram gravados ate agora. */
    long getQuadrosGravados();

    /** Quantos eventos foram gravados ate agora. */
    long getEventosGravados();

    @Override
    void close();
}
