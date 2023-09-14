package it.auties.whatsapp.model.message.standard;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;
import it.auties.whatsapp.model.message.model.Message;
import it.auties.whatsapp.model.message.model.MessageCategory;
import it.auties.whatsapp.model.message.model.MessageKey;
import it.auties.whatsapp.model.message.model.MessageType;
import it.auties.whatsapp.util.Clock;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.time.ZonedDateTime;
import java.util.Optional;


/**
 * A model class that represents a message holding an emoji reaction inside
 */
public record ReactionMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.OBJECT)
        @NonNull
        MessageKey key,
        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        @NonNull
        String content,
        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        Optional<String> groupingKey,
        @ProtobufProperty(index = 4, type = ProtobufType.INT64)
        long timestampSeconds
) implements Message {
    public Optional<ZonedDateTime> timestamp() {
        return Clock.parseSeconds(timestampSeconds);
    }

    @Override
    public MessageType type() {
        return MessageType.REACTION;
    }

    @Override
    public MessageCategory category() {
        return MessageCategory.STANDARD;
    }
}
