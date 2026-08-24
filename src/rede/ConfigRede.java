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
                         int portaAzul, int portaAmarelo,
                         String interfaceDeSaida, String destinosUnicast) {

    public static ConfigRede padrao() {
        return new ConfigRede(Enderecos.VISAO_GRUPO, Enderecos.VISAO_PORTA,
                Enderecos.CONTROLE_SIM_PORTA,
                Enderecos.CONTROLE_AZUL_PORTA, Enderecos.CONTROLE_AMARELO_PORTA,
                "", "");
    }

    /** Normaliza nulo para vazio: "vazio" e "automatico" tem de ser a mesma coisa. */
    public ConfigRede {
        interfaceDeSaida = interfaceDeSaida == null ? "" : interfaceDeSaida.trim();
        destinosUnicast  = destinosUnicast  == null ? "" : destinosUnicast.trim();
    }

    public ConfigRede comGrupo(String grupo)     { return new ConfigRede(grupo, portaVisao, portaControle, portaAzul, portaAmarelo, interfaceDeSaida, destinosUnicast); }
    public ConfigRede comPortaVisao(int p)       { return new ConfigRede(grupoVisao, p, portaControle, portaAzul, portaAmarelo, interfaceDeSaida, destinosUnicast); }
    public ConfigRede comPortaControle(int p)    { return new ConfigRede(grupoVisao, portaVisao, p, portaAzul, portaAmarelo, interfaceDeSaida, destinosUnicast); }
    public ConfigRede comPortaAzul(int p)        { return new ConfigRede(grupoVisao, portaVisao, portaControle, p, portaAmarelo, interfaceDeSaida, destinosUnicast); }
    public ConfigRede comPortaAmarelo(int p)     { return new ConfigRede(grupoVisao, portaVisao, portaControle, portaAzul, p, interfaceDeSaida, destinosUnicast); }

    /**
     * Por qual interface o multicast sai. Vazio deixa o SO escolher.
     *
     * <p>Existe porque deixar o SO escolher e justamente o que falha numa maquina
     * com Wi-Fi, Ethernet e os adaptadores virtuais que Docker, VPN e VirtualBox
     * criam: a rota padrao de multicast quase nunca e a da LAN. Do lado que
     * RECEBE isto ja era tratado -- o cliente entra no grupo em todas as
     * interfaces -- e o lado que envia ficou para tras.
     */
    public ConfigRede comInterfaceDeSaida(String nome) { return new ConfigRede(grupoVisao, portaVisao, portaControle, portaAzul, portaAmarelo, nome, destinosUnicast); }

    /**
     * IPs que recebem a visao TAMBEM por unicast, separados por virgula.
     *
     * <p>Multicast e o protocolo da liga e continua saindo. Mas entre duas
     * maquinas -- uma no cabo e outra no Wi-Fi -- a ponte do roteador
     * frequentemente nao repassa multicast, e em rede de faculdade ele costuma
     * ser bloqueado por politica. Unicast nao depende de nada disso: sai como
     * qualquer pacote UDP comum.
     *
     * <p>Nao exige nada do outro lado: um socket multicast preso a uma porta
     * recebe unicast naquela porta do mesmo jeito.
     */
    public ConfigRede comDestinosUnicast(String ips) { return new ConfigRede(grupoVisao, portaVisao, portaControle, portaAzul, portaAmarelo, interfaceDeSaida, ips); }

    /** Os destinos unicast como lista, ja sem vazios. */
    public java.util.List<String> destinos() {
        if (destinosUnicast.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(destinosUnicast.split(","))
                .map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

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
