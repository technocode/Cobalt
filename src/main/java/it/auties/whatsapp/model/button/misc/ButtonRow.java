package it.auties.whatsapp.model.button.misc;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufType;
import it.auties.whatsapp.util.BytesHelper;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.HexFormat;

/**
 * A model class that represents a row of buttons
 */
public record ButtonRow(
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        @NonNull
        String title,
        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        @NonNull
        String description,
        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        @NonNull
        String id
) implements ProtobufMessage {
        public static ButtonRow of(String title, String description) {
                return new ButtonRow(title, description, HexFormat.of().formatHex(BytesHelper.random(5)));
        }
}
