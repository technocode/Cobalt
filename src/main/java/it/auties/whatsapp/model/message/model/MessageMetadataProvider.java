package it.auties.whatsapp.model.message.model;

import it.auties.whatsapp.model.chat.Chat;
import it.auties.whatsapp.model.contact.Contact;
import it.auties.whatsapp.model.contact.ContactJid;
import it.auties.whatsapp.model.info.MessageInfo;

import java.util.Optional;

/**
 * Model interface to mark classes that can provide info about a message
 */
public sealed interface MessageMetadataProvider permits MessageInfo, QuotedMessage {
    /**
     * Returns the id of the message
     *
     * @return a string
     */
    String id();


    /**
     * Returns the jid of the chat where the message was sent
     *
     * @return a jid
     */
    ContactJid chatJid();

    /**
     * Returns the chat of the message
     *
     * @return a chat
     */
    Optional<Chat> chat();

    /**
     * Returns the sender's jid
     *
     * @return a jid
     */
    ContactJid senderJid();

    /**
     * Returns the sender of the message
     *
     * @return an optional
     */
    Optional<Contact> sender();

    /**
     * Returns the message
     *
     * @return a message container
     */
    MessageContainer message();
}
