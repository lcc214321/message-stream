package tech.guyi.component.message.stream.kafka;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
import tech.guyi.component.message.stream.api.stream.MessageStream;
import tech.guyi.component.message.stream.api.stream.entry.Message;
import tech.guyi.component.message.stream.api.worker.MessageStreamWorker;
import tech.guyi.component.message.stream.kafka.configuration.ConfigurationType;
import tech.guyi.component.message.stream.kafka.configuration.KafkaConfiguration;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author guyi
 * @date 2021/1/19 16:08
 */
@Slf4j
public class KafkaMessageStream implements MessageStream {

    @Resource
    private MessageStreamWorker worker;
    @Resource
    private KafkaConfiguration configuration;

    // 生产者
    private KafkaProducer<String,byte[]> producer;
    // 消费者
    private KafkaConsumer<String,byte[]> consumer;
    // 消费线程
    private Future<?> future;
    // 是否消息信息
    private boolean run;

    // 消息接收者
    private Consumer<Message> receiver;

    @Override
    public @NonNull String getName() {
        return "kafka";
    }

    // 打开生产者连接
    private void openProducer(){
        Properties props = new Properties();
        props.put("bootstrap.servers", configuration.getBootstrapServers(ConfigurationType.PRODUCER));
        props.put("acks", configuration.getProducer().getAcks());
        props.put("retries", configuration.getProducer().getRetries());
        props.put("batch.size", configuration.getProducer().getBatchSize());
        props.put("key.serializer", configuration.getProducer().getKeySerializer());
        props.put("value.serializer", configuration.getProducer().getValueSerializer());
        this.producer = new KafkaProducer<>(props);
    }

    // 创建消费者连接
    private void openConsumer(){
        Properties props = new Properties();
        props.put("bootstrap.servers", configuration.getBootstrapServers(ConfigurationType.CONSUMER));
        props.put("group.id", configuration.getConsumer().getGroupId());
        props.put("enable.auto.commit", configuration.getConsumer().isAutoCommit());
        props.put("auto.commit.interval.ms",configuration.getConsumer().getInterval());
        props.put("session.timeout.ms", configuration.getConsumer().getTimeout());
        props.put("heartbeat.interval.ms", configuration.getConsumer().getHeartbeat());
        props.put("max.poll.records", configuration.getConsumer().getMaxRecords());
        props.put("auto.offset.reset", configuration.getConsumer().getReset());
        props.put("key.deserializer", configuration.getConsumer().getKeyDeserializer());
        props.put("value.deserializer", configuration.getConsumer().getValueDeserializer());
        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(configuration.getConsumer().getTopic());
        this.run = true;
        this.future = this.worker.submit(this::pullMessage);
    }

    // 拉取消息
    // 线程池中执行
    private void pullMessage(){
        while (this.run){
            try{
                try{
                    this.consumer.poll(Duration.ofMillis(configuration.getConsumer().getInterval()))
                            .forEach(record -> this.receiver.accept(new Message(record.key(),record.value())));
                }catch (WakeupException wakeupException){}
            }catch (Exception e) {
                e.printStackTrace();
                log.error("Kafka消息拉取异常", e);
            }
        }
    }

    @Override
    public void close() {
        // 让消费者线程跳出死循环, 关闭消费者
        this.run = false;
        this.consumer.close();
        this.future.cancel(true);

        this.producer.close();
    }

    @Override
    public void register(String topic) {
    }

    @Override
    public void unregister(String topic) {
    }

    @Override
    public void open(Consumer<Message> receiver) {
        // 打开生产者连接
        this.openProducer();
        // 打开消费者连接
        this.openConsumer();
        // 缓存消息接收者
        this.receiver = receiver;
    }

    @Override
    public void publish(Message message) {
        this.producer.send(new ProducerRecord<>(configuration.getProducer().getTopic(), message.getTopic(), message.getBytes()));
    }

}
