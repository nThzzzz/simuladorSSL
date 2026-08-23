package model;

import core.Caixa;

/**
 * Uma das tres paredes de um gol: os dois postes e o fundo.
 *
 * <p>Leva o nome junto com a caixa porque a parede atingida vira dado de log, e
 * deduzir "qual das seis foi" a partir das coordenadas na hora de registrar o
 * evento seria reconstruir uma informacao que a geometria ja tinha.
 *
 * @param nome  identificador estavel para o log, ex. {@code gol_x_max_fundo}
 * @param caixa a regiao ocupada pela parede, vista de cima
 */
public record ParedeDoGol(String nome, Caixa caixa) {}
