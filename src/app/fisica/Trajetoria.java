package app.fisica;

import java.util.List;

/**
 * Resultado de um ensaio: o filme e o numero.
 *
 * @param amostras   quadros ja subamostrados para animacao
 * @param dtAmostra  segundos entre amostras
 * @param medida     o numero que resume o ensaio, na unidade que ele declara
 */
public record Trajetoria(List<Amostra> amostras, double dtAmostra, double medida) {

    public double duracao() { return amostras.size() * dtAmostra; }
}
