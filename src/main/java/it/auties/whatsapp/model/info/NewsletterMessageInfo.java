package it.auties.whatsapp.model.info;

import com.fasterxml.jackson.annotation.JsonBackReference;
import it.auties.whatsapp.model.jid.Jid;
import it.auties.whatsapp.model.message.model.MessageContainer;
import it.auties.whatsapp.model.message.model.MessageStatus;
import it.auties.whatsapp.model.newsletter.Newsletter;
import it.auties.whatsapp.model.newsletter.NewsletterReaction;
import it.auties.whatsapp.util.Clock;

import java.time.ZonedDateTime;
import java.util.*;

public final class NewsletterMessageInfo implements MessageInfo, MessageStatusInfo<NewsletterMessageInfo> {
    @JsonBackReference
    private Newsletter newsletter;
    private final String id;
    private final int serverId;
    private final Long timestampSeconds;
    private final Long views;
    private final Map<String, NewsletterReaction> reactions;
    private final MessageContainer message;
    private MessageStatus status;

    public NewsletterMessageInfo(Newsletter newsletter, String id, int serverId, Long timestampSeconds, Long views, Map<String, NewsletterReaction> reactions, MessageContainer message, MessageStatus status) {
        this.newsletter = newsletter;
        this.id = id;
        this.serverId = serverId;
        this.timestampSeconds = timestampSeconds;
        this.views = views;
        this.reactions = reactions;
        this.message = message;
        this.status = status;
    }

    public NewsletterMessageInfo setNewsletter(Newsletter newsletter) {
        this.newsletter = newsletter;
        return this;
    }

    public Jid newsletterJid() {
        return newsletter.jid();
    }

    @Override
    public Jid parentJid() {
        return newsletterJid();
    }

    @Override
    public Jid senderJid() {
        return newsletterJid();
    }

    public Newsletter newsletter() {
        return newsletter;
    }

    public String id() {
        return id;
    }

    public int serverId() {
        return serverId;
    }

    @Override
    public OptionalLong timestampSeconds() {
        return timestampSeconds == null ? OptionalLong.empty() : OptionalLong.of(timestampSeconds);
    }

    public OptionalLong views() {
        return views == null ? OptionalLong.empty() : OptionalLong.of(views);
    }

    public MessageContainer message() {
        return message;
    }

    public Optional<ZonedDateTime> timestamp() {
        return Clock.parseSeconds(timestampSeconds);
    }

    @Override
    public MessageStatus status() {
        return status;
    }

    @Override
    public NewsletterMessageInfo setStatus(MessageStatus status) {
        this.status = status;
        return this;
    }

    public Collection<NewsletterReaction> reactions() {
        return Collections.unmodifiableCollection(reactions.values());
    }

    public Optional<NewsletterReaction> findReaction(String value) {
        return Optional.ofNullable(reactions.get(value));
    }

    public Optional<NewsletterReaction> addReaction(NewsletterReaction reaction) {
        return Optional.ofNullable(reactions.put(reaction.content(), reaction));
    }

    public Optional<NewsletterReaction> removeReaction(String code) {
        return Optional.ofNullable(reactions.remove(code));
    }

    public void incrementReaction(String code, boolean fromMe) {
        findReaction(code).ifPresentOrElse(reaction -> {
            reaction.setCount(reaction.count() + 1);
            reaction.setFromMe(fromMe);
        }, () -> {
            var reaction = new NewsletterReaction(code, 1, fromMe);
            addReaction(reaction);
        });
    }

    public void decrementReaction(String code) {
        findReaction(code).ifPresent(reaction -> {
            if(reaction.count() <= 1) {
                removeReaction(reaction.content());
                return;
            }

            reaction.setCount(reaction.count() - 1);
            reaction.setFromMe(false);
        });
    }


    @Override
    public boolean equals(Object obj) {
        return obj instanceof NewsletterMessageInfo that && Objects.equals(this.id(), that.id());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}
