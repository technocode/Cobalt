package it.auties.whatsapp.model.signal.message;

import it.auties.curve25519.Curve25519;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;
import it.auties.whatsapp.util.BytesHelper;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Arrays;

import static it.auties.whatsapp.util.Spec.Signal.SIGNATURE_LENGTH;

public final class SenderKeyMessage extends SignalProtocolMessage<SenderKeyMessage> {
    @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
    private final Integer id;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT32)
    private final Integer iteration;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    private final byte @NonNull [] cipherText;

    private byte[] signingKey;

    public SenderKeyMessage(int id, int iteration, byte @NonNull [] cipherText) {
        this.id = id;
        this.iteration = iteration;
        this.cipherText = cipherText;
    }

    public SenderKeyMessage(Integer id, Integer iteration, byte @NonNull [] cipherText, byte[] signingKey) {
        this.id = id;
        this.iteration = iteration;
        this.cipherText = cipherText;
        this.signingKey = signingKey;
    }

    public static SenderKeyMessage ofSerialized(byte[] serialized) {
        var data = Arrays.copyOfRange(serialized, 1, serialized.length - SIGNATURE_LENGTH);
        return SenderKeyMessageSpec.decode(data)
                .setVersion(BytesHelper.bytesToVersion(serialized[0]))
                .setSerialized(serialized);
    }

    @Override
    public byte[] serialized() {
        if(serialized == null) {
            var serialized = BytesHelper.concat(serializedVersion(), SenderKeyMessageSpec.encode(this));
            var signature = Curve25519.sign(signingKey, serialized, true);
            this.serialized = BytesHelper.concat(serialized, signature);
        }

        return serialized;
    }

    public Integer id() {
        return id;
    }

    public Integer iteration() {
        return iteration;
    }

    public byte[] cipherText() {
        return cipherText;
    }

    public byte[] signingKey() {
        return signingKey;
    }
}
