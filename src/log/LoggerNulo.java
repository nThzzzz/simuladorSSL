package log;

import engine.Evento;
import engine.Mundo;

import java.util.List;

/** Descarta tudo. Usado quando a gravacao esta desligada. */
public final class LoggerNulo implements Logger {

    public static final LoggerNulo INSTANCIA = new LoggerNulo();

    @Override public void inicio(Mundo mundo, double dt) {}
    @Override public void quadro(Mundo mundo) {}
    @Override public void eventos(List<Evento> eventos) {}
    @Override public long getQuadrosGravados() { return 0; }
    @Override public long getEventosGravados() { return 0; }
    @Override public void close() {}
}
