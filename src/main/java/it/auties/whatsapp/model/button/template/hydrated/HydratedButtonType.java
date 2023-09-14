package it.auties.whatsapp.model.button.template.hydrated;

import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.model.ProtobufEnum;

/**
 * The constants of this enumerated type describe the various types of buttons that a template can
 * wrap
 */
public enum HydratedButtonType implements ProtobufEnum {
    /**
     * No button
     */
    NONE(0),
    /**
     * Quick reply button
     */
    QUICK_REPLY(1),
    /**
     * Url button
     */
    URL(2),
    /**
     * Call button
     */
    CALL(3);

    final int index;
    HydratedButtonType(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }
}