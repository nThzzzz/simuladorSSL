package rede;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Enderecos e portas usados pelo simulador.
 *
 * @param grupoVisao     grupo multicast onde a visao e publicada
 * @param portaVisao     porta da visao
 * @param portaControle  porta de {@code SimulatorCommand}
 * @param portaAzul      porta de {@code RobotControl} da equipe azul
 * @param portaAmarelo   porta de {@code RobotControl} da equipe amarela
 */
public record ConfigRede(String grupoVisao, int portaVisao, int portaControle,
                         int portaAzul, int portaAmarelo) {

    public static ConfigRede padrao() {
        return new ConfigRede(Enderecos.VISAO_GRUPO, Enderecos.VISAO_PORTA,
                Enderecos.CONTROLE_SIM_PORTA,
                Enderecos.CONTROLE_AZUL_PORTA, Enderecos.CONTROLE_AMARELO_PORTA);
    }

    public ConfigRede comGrupo(String grupo)     { return new ConfigRede(grupo, portaVisao, portaControle, portaAzul, portaAmarelo); }
    public ConfigRede comPortaVisao(int p)       { return new ConfigRede(grupoVisao, p, portaControle, portaAzul, portaAmarelo); }
    public ConfigRede comPortaControle(int p)    { return new ConfigRede(grupoVisao, portaVisao, p, portaAzul, portaAmarelo); }
    public ConfigRede comPortaAzul(int p)        { return new ConfigRede(grupoVisao, portaVisao, portaControle, p, portaAmarelo); }
    public ConfigRede comPortaAmarelo(int p)     { return new ConfigRede(grupoVisao, portaVisao, portaControle, portaAzul, p); }

    /**
     * Explica por que a configuracao e invalida, ou {@code null} se estiver boa.
     *
     * <p>Vale validar antes de tentar abrir socket: um endereco que nao e
     * multicast nao da erro ao enviar, so faz o pacote nunca chegar em ninguem --
     * e as tres portas de escuta precisam ser distintas porque duas ligacoes na
     * mesma porta falham no bind.
     */
    public String problema() {
        if (grupoVisao == null || grupoVisao.isBlank()) {
            return "informe o grupo multicast da visao";
        }
        try {
            if (!InetAddress.getByName(grupoVisao.trim()).isMulticastAddress()) {
                return grupoVisao + " nao e um endereco multicast (faixa 224.0.0.0 a 239.255.255.255)";
            }
        } catch (UnknownHostException e) {
            return "endereco invalido: " + grupoVisao;
        }

        String[] nomes = {"visao", "controle", "robos azul", "robos amarelo"};
        int[] portas = {portaVisao, portaControle, portaAzul, portaAmarelo};
        for (int i = 0; i < portas.length; i++) {
            if (portas[i] < 1024 || portas[i] > 65535) {
                return "porta de " + nomes[i] + " fora da faixa 1024-65535: " + portas[i];
            }
        }

        // A visao so envia, entao pode repetir; as tres de escuta nao podem.
        for (int i = 1; i < portas.length; i++) {
            for (int j = i + 1; j < portas.length; j++) {
                if (portas[i] == portas[j]) {
                    return "portas de " + nomes[i] + " e " + nomes[j] + " sao iguais ("
                            + portas[i] + "); cada uma precisa da sua";
                }
            }
        }
        return null;
    }
}
