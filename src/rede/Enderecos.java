package rede;

/**
 * Valores padrao do ecossistema SSL.
 *
 * <p>Sao os mesmos que a {@code ssl-vision} e o {@code grSim} usam, de proposito:
 * com eles o software de qualquer time da liga conecta sem configurar nada. O que
 * esta em uso de fato fica na {@link ConfigRede}, que pode ser trocada em tempo
 * de execucao.
 */
public final class Enderecos {

    private Enderecos() {}

    /** Grupo multicast da visao. */
    public static final String VISAO_GRUPO = "224.5.23.2";
    public static final int VISAO_PORTA = 10006;

    /** Porta onde o simulador escuta {@code SimulatorCommand}. */
    public static final int CONTROLE_SIM_PORTA = 10300;

    /** Portas onde o simulador escuta {@code RobotControl} de cada equipe. */
    public static final int CONTROLE_AZUL_PORTA = 10301;
    public static final int CONTROLE_AMARELO_PORTA = 10302;

    /** Datagrama maximo que tratamos; um quadro de visao cabe folgado. */
    public static final int TAMANHO_BUFFER = 8192;
}
