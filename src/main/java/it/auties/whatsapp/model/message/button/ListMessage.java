package it.auties.whatsapp.model.message.button;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;
import it.auties.whatsapp.model.button.misc.ButtonSection;
import it.auties.whatsapp.model.info.ContextInfo;
import it.auties.whatsapp.model.info.ProductListInfo;
import it.auties.whatsapp.model.message.model.ButtonMessage;
import it.auties.whatsapp.model.message.model.ContextualMessage;
import it.auties.whatsapp.model.message.model.MessageType;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * A model class that represents a message that contains a list of buttons or a list of products
 */
public record ListMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        @NonNull
        String title,
        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        Optional<String> description,
        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        @NonNull
        String button,
        @ProtobufProperty(index = 4, type = ProtobufType.OBJECT)
        @NonNull
        ListMessageType listType,
        @ProtobufProperty(index = 5, type = ProtobufType.OBJECT, repeated = true)
        @NonNull
        List<ButtonSection> sections,
        @ProtobufProperty(index = 6, type = ProtobufType.OBJECT)
        Optional<ProductListInfo> productListInfo,
        @ProtobufProperty(index = 7, type = ProtobufType.STRING)
        Optional<String> footer,
        @ProtobufProperty(index = 8, type = ProtobufType.OBJECT)
        Optional<ContextInfo> contextInfo
) implements ContextualMessage, ButtonMessage {

    @Override
    public MessageType type() {
        return MessageType.LIST;
    }

}