package it.auties.whatsapp.model.message.payment;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;
import it.auties.whatsapp.model.contact.ContactJid;
import it.auties.whatsapp.model.message.model.MessageContainer;
import it.auties.whatsapp.model.message.model.MessageType;
import it.auties.whatsapp.model.message.model.PaymentMessage;
import it.auties.whatsapp.model.payment.PaymentBackground;
import it.auties.whatsapp.model.payment.PaymentMoney;
import it.auties.whatsapp.util.Clock;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.time.ZonedDateTime;
import java.util.Optional;


/**
 * A model class that represents a message to try to place a {@link PaymentMessage}.
 */
public record RequestPaymentMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        @NonNull
        String currency,
        @ProtobufProperty(index = 2, type = ProtobufType.UINT64)
        long amount1000,
        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        ContactJid requestFrom,
        @ProtobufProperty(index = 4, type = ProtobufType.OBJECT)
        Optional<MessageContainer> noteMessage,
        @ProtobufProperty(index = 5, type = ProtobufType.UINT64)
        long expiryTimestampSeconds,
        @ProtobufProperty(index = 6, type = ProtobufType.OBJECT)
        @NonNull
        PaymentMoney amount,
        @ProtobufProperty(index = 7, type = ProtobufType.OBJECT)
        Optional<PaymentBackground> background
) implements PaymentMessage {
        /**
         * Returns when the transaction expires
         *
         * @return an optional
         */
        public Optional<ZonedDateTime> expiryTimestamp() {
                return Clock.parseSeconds(expiryTimestampSeconds);
        }

        @Override
        public MessageType type() {
                return MessageType.REQUEST_PAYMENT;
        }
}
