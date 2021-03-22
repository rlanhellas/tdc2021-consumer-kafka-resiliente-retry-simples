package br.com.tdc2021.lanhellas.consumerkafkaresiliente.consumidorkafka;

import br.com.tdc2021.lanhellas.consumerkafkaresiliente.entidade.Cliente;
import br.com.tdc2021.lanhellas.consumerkafkaresiliente.repositorio.ClienteRepositorio;
import org.hibernate.exception.JDBCConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class ConsumidorCliente {

    @Value("${app.tempo-nack-ms}")
    private long tempoNackMs;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ClienteRepositorio clienteRepositorio;

    public ConsumidorCliente(ClienteRepositorio clienteRepositorio) {
        this.clienteRepositorio = clienteRepositorio;
    }

    @KafkaListener(topics = "${app.topico-cliente}")
    public void consumir(@Payload Cliente cliente,
                         @Header(value = KafkaHeaders.RECEIVED_MESSAGE_KEY, required = false) String key,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topico,
                         @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long ts,
                         Acknowledgment ack){
        try{
            logger.info("Iniciando consumo do tópico {}, key {}, Cpf Cliente {}", topico, key, cliente.getCpf());
            clienteRepositorio.save(cliente);
            ack.acknowledge();
            logger.info("Commit realizado");
        }catch (Exception e){
            /*
            * Se ocorrer um erro de conexão com a base (ex: timeout por firewall, credenciais inválidas ..) então tentaremos
            * novamente pois não adianta passar para a próxima mensagem, já que todas precisam ser salvas na base.
            * */
            if (e.getCause() instanceof JDBCConnectionException){
                logger.error("Problemas ao comunicar com a base de dados, tentaremos novamente em 10segundos", e);
                ack.nack(tempoNackMs);
            }else {
                /*
                 * Qualquer outro erro não mapeado, estamos apenas dando commit na mensagem.
                 * Que tal um deadletter aqui ?? :D
                 * */
                logger.error("Erro desconhecido ao tentar salvar", e);
                ack.acknowledge();
            }
        }

    }

}
