package it.auties.whatsapp.model.interactive;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufType;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Optional;

/**
 * A model class that represents a native flow button
 */
public record InteractiveButton(
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        @NonNull
        String name,
        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        Optional<String> parameters
) implements ProtobufMessage {
        public InteractiveButton(@NonNull String name) {
                this(name, Optional.empty());
        }
}
