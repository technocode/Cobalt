package it.auties.whatsapp.model.sync;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;

import static it.auties.protobuf.model.ProtobufType.BYTES;

public record AppStateSyncKeyId(@ProtobufProperty(index = 1, type = BYTES) byte[] keyId) implements ProtobufMessage {
}
