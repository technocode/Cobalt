package it.auties.whatsapp.model.contact;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import it.auties.whatsapp.api.Whatsapp;
import it.auties.whatsapp.model.chat.Chat;
import it.auties.whatsapp.model.jid.Jid;
import it.auties.whatsapp.model.jid.JidProvider;
import it.auties.whatsapp.util.Clock;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * A model class that represents a Contact. This class is only a model, this means that changing its
 * values will have no real effect on WhatsappWeb's servers.
 */
public final class Contact implements JidProvider {
    /**
     * The non-null unique jid used to identify this contact
     */
    private final Jid jid;

    /**
     * The nullable name specified by this contact when he created a Whatsapp account. Theoretically,
     * it should not be possible for this field to be null as it's required when registering for
     * Whatsapp. Though it looks that it can be removed later, so it's nullable.
     */
    private String chosenName;

    /**
     * The nullable name associated with this contact on the phone connected with Whatsapp
     */
    private String fullName;

    /**
     * The nullable short name associated with this contact on the phone connected with Whatsapp If a
     * name is available, theoretically, also a short name should be
     */
    private String shortName;

    /**
     * The nullable last known presence of this contact. This field is associated only with the
     * presence of this contact in the corresponding conversation. If, for example, this contact is
     * composing, recording or paused in a group this field will not be affected. Instead,
     * {@link Chat#presences()} should be used. By default, Whatsapp will not send updates about a
     * contact's status unless they send a message or are in the recent contacts. To force Whatsapp to
     * send updates, use {@link Whatsapp#subscribeToPresence(JidProvider)}.
     */
    private ContactStatus lastKnownPresence;

    /**
     * The nullable last seconds this contact was seen available. Any contact can decide to hide this
     * information in their privacy settings.
     */
    private ZonedDateTime lastSeen;

    /**
     * Whether this contact is blocked
     */
    private boolean blocked;

    public Contact(Jid jid) {
        this.jid = jid;
        this.lastKnownPresence = ContactStatus.UNAVAILABLE;
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Contact(Jid jid, String chosenName, String fullName, String shortName, ContactStatus lastKnownPresence, Long lastSeenSeconds, boolean blocked) {
        this.jid = jid;
        this.chosenName = chosenName;
        this.fullName = fullName;
        this.shortName = shortName;
        this.lastKnownPresence = lastKnownPresence;
        this.lastSeen = Clock.parseSeconds(lastSeenSeconds).orElse(null);
        this.blocked = blocked;
    }

    public Jid jid() {
        return this.jid;
    }

    public String name() {
        if (shortName != null) {
            return shortName;
        }

        if (fullName != null) {
            return fullName;
        }

        if (chosenName != null) {
            return chosenName;
        }

        return jid().user();
    }

    public Optional<ZonedDateTime> lastSeen() {
        return Optional.ofNullable(lastSeen);
    }

    public Optional<String> chosenName() {
        return Optional.ofNullable(this.chosenName);
    }

    public Optional<String> fullName() {
        return Optional.ofNullable(this.fullName);
    }

    public Optional<String> shortName() {
        return Optional.ofNullable(this.shortName);
    }

    public ContactStatus lastKnownPresence() {
        return this.lastKnownPresence;
    }

    public boolean blocked() {
        return this.blocked;
    }

    public Contact setChosenName(String chosenName) {
        this.chosenName = chosenName;
        return this;
    }

    public Contact setFullName(String fullName) {
        this.fullName = fullName;
        return this;
    }

    public Contact setShortName(String shortName) {
        this.shortName = shortName;
        return this;
    }

    public Contact setLastKnownPresence(ContactStatus lastKnownPresence) {
        this.lastKnownPresence = lastKnownPresence;
        return this;
    }

    public Contact setLastSeen(ZonedDateTime lastSeen) {
        this.lastSeen = lastSeen;
        return this;
    }

    public Contact setBlocked(boolean blocked) {
        this.blocked = blocked;
        return this;
    }

    @JsonGetter("lastSeen")
    Long lastSeenValue() {
        return lastSeen != null ? lastSeen.toEpochSecond() : null;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.jid());
    }

    public boolean equals(Object other) {
        return other instanceof Contact that && Objects.equals(this.jid(), that.jid());
    }

    @Override
    public Jid toJid() {
        return jid();
    }
}
