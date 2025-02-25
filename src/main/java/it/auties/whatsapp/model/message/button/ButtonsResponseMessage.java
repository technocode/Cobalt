package it.auties.whatsapp.model.message.button;

import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessageName;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufEnum;
import it.auties.protobuf.model.ProtobufType;
import it.auties.whatsapp.model.button.base.Button;
import it.auties.whatsapp.model.button.base.ButtonBody;
import it.auties.whatsapp.model.button.base.ButtonText;
import it.auties.whatsapp.model.info.ChatMessageInfo;
import it.auties.whatsapp.model.info.ContextInfo;
import it.auties.whatsapp.model.message.model.ButtonReplyMessage;
import it.auties.whatsapp.model.message.model.MessageType;

import java.util.Optional;

/**
 * A model class that represents a message that contains a newsletters to a previous
 * {@link ButtonsMessage}
 */
@ProtobufMessageName("Message.ButtonsResponseMessage")
public record ButtonsResponseMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String buttonId,
        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        Optional<String> buttonText,
        @ProtobufProperty(index = 3, type = ProtobufType.OBJECT)
        Optional<ContextInfo> contextInfo,
        @ProtobufProperty(index = 4, type = ProtobufType.OBJECT)
        ResponseType responseType
) implements ButtonReplyMessage {
    public static ButtonsResponseMessage of(ChatMessageInfo quoted, Button button) {
        return new ButtonsResponseMessageBuilder()
                .buttonId(button.id())
                .buttonText(button.bodyText().map(ButtonText::content))
                .contextInfo(ContextInfo.of(quoted))
                .responseType(button.bodyType() == ButtonBody.Type.TEXT ? ResponseType.SELECTED_DISPLAY_TEXT : ResponseType.UNKNOWN)
                .build();
    }

    @Override
    public MessageType type() {
        return MessageType.BUTTONS_RESPONSE;
    }

    public enum ResponseType implements ProtobufEnum {
        UNKNOWN(0),
        SELECTED_DISPLAY_TEXT(1);

        final int index;

        ResponseType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        public int index() {
            return index;
        }
    }
}