package it.auties.whatsapp.model.interactive;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufType;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Optional;

/**
 * A model class that represents the footer of a product
 */
public record InteractiveFooter(
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        @NonNull
        String content
) implements ProtobufMessage {

        public static Optional<InteractiveFooter> ofNullable(@Nullable String content) {
                return Optional.ofNullable(content)
                        .map(InteractiveFooter::new);
        }
}