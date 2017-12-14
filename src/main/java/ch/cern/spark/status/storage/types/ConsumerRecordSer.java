package ch.cern.spark.status.storage.types;

import java.io.Serializable;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.utils.Bytes;

import ch.cern.spark.ByteArray;

public class ConsumerRecordSer implements Serializable{

    private static final long serialVersionUID = -8815018248610333648L;
    
    private long offset;
    private ByteArray key;
    private ByteArray value;

    public ConsumerRecordSer(ConsumerRecord<Bytes, Bytes> consumerRecord) {
        this.offset = consumerRecord.offset();
        this.key = new ByteArray(consumerRecord.key().get());
        this.value = new ByteArray(consumerRecord.value().get());
    }

    public long offset() {
        return offset;
    }

    public ByteArray key() {
        return key;
    }
    
    public ByteArray value() {
        return value;
    }

}
