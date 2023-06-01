package it.auties.whatsapp.socket;

import it.auties.whatsapp.api.DisconnectReason;
import it.auties.whatsapp.api.ErrorHandler.Location;
import it.auties.whatsapp.api.SocketEvent;
import it.auties.whatsapp.api.Whatsapp;
import it.auties.whatsapp.binary.Decoder;
import it.auties.whatsapp.binary.PatchType;
import it.auties.whatsapp.controller.Keys;
import it.auties.whatsapp.controller.Store;
import it.auties.whatsapp.crypto.AesGmc;
import it.auties.whatsapp.listener.Listener;
import it.auties.whatsapp.model.action.Action;
import it.auties.whatsapp.model.business.BusinessCategory;
import it.auties.whatsapp.model.chat.Chat;
import it.auties.whatsapp.model.chat.GroupMetadata;
import it.auties.whatsapp.model.contact.Contact;
import it.auties.whatsapp.model.contact.ContactJid;
import it.auties.whatsapp.model.contact.ContactJid.Server;
import it.auties.whatsapp.model.contact.ContactJidProvider;
import it.auties.whatsapp.model.contact.ContactStatus;
import it.auties.whatsapp.model.info.MessageIndexInfo;
import it.auties.whatsapp.model.info.MessageInfo;
import it.auties.whatsapp.model.message.model.MessageContainer;
import it.auties.whatsapp.model.message.model.MessageKey;
import it.auties.whatsapp.model.message.model.MessageStatus;
import it.auties.whatsapp.model.message.server.ProtocolMessage;
import it.auties.whatsapp.model.mobile.PhoneNumber;
import it.auties.whatsapp.model.privacy.PrivacySettingEntry;
import it.auties.whatsapp.model.request.Attributes;
import it.auties.whatsapp.model.request.MessageSendRequest;
import it.auties.whatsapp.model.request.Node;
import it.auties.whatsapp.model.request.Request;
import it.auties.whatsapp.model.response.ContactStatusResponse;
import it.auties.whatsapp.model.setting.Setting;
import it.auties.whatsapp.model.signal.auth.ClientHello;
import it.auties.whatsapp.model.signal.auth.HandshakeMessage;
import it.auties.whatsapp.model.sync.ActionValueSync;
import it.auties.whatsapp.model.sync.PatchRequest;
import it.auties.whatsapp.util.Clock;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

import static it.auties.whatsapp.api.ErrorHandler.Location.*;

@Accessors(fluent = true)
@SuppressWarnings("unused")
public class SocketHandler implements SocketListener {
    private static final Set<UUID> connectedUuids = ConcurrentHashMap.newKeySet();
    private static final Set<Long> connectedPhoneNumbers = ConcurrentHashMap.newKeySet();

    private SocketSession session;

    @NonNull
    @Getter
    private final Whatsapp whatsapp;

    @NonNull
    private final AuthHandler authHandler;

    @NonNull
    private final StreamHandler streamHandler;

    @NonNull
    private final MessageHandler messageHandler;

    @NonNull
    private final AppStateHandler appStateHandler;

    @NonNull
    @Getter
    @Setter(AccessLevel.PROTECTED)
    private SocketState state;

    @Getter
    @NonNull
    private Keys keys;

    @Getter
    @NonNull
    private Store store;

    private Thread shutdownHook;

    private CompletableFuture<Void> loginFuture;

    private CompletableFuture<Void> logoutFuture;

    private ExecutorService listenersService;

    public static boolean isConnected(@NonNull UUID uuid){
        return connectedUuids.contains(uuid);
    }

    public static boolean isConnected(long phoneNumber){
        return connectedPhoneNumbers.contains(phoneNumber);
    }

    public SocketHandler(@NonNull Whatsapp whatsapp, @NonNull Store store, @NonNull Keys keys) {
        this.whatsapp = whatsapp;
        this.store = store;
        this.keys = keys;
        this.state = SocketState.WAITING;
        this.authHandler = new AuthHandler(this);
        this.streamHandler = new StreamHandler(this);
        this.messageHandler = new MessageHandler(this);
        this.appStateHandler = new AppStateHandler(this);
    }

    private void onShutdown(boolean reconnect) {
        if (state != SocketState.LOGGED_OUT && state != SocketState.RESTORE) {
            keys.dispose();
            store.dispose();
        }
        if (!reconnect) {
            dispose();
        }
    }

    protected void onSocketEvent(SocketEvent event) {
        callListenersAsync(listener -> {
            listener.onSocketEvent(whatsapp, event);
            listener.onSocketEvent(event);
        });
    }

    private void callListenersAsync(Consumer<Listener> consumer) {
        var service = getOrCreateListenersService();
        store.listeners().forEach(listener -> service.execute(() -> invokeListenerSafe(consumer, listener)));
    }

    @Override
    public void onOpen(SocketSession session) {
        this.session = session;
        if (state == SocketState.CONNECTED) {
            return;
        }

        if(shutdownHook == null) {
            this.shutdownHook = new Thread(() -> onShutdown(false));
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        }

        markConnected();
        this.state = SocketState.WAITING;
        onSocketEvent(SocketEvent.OPEN);
        var clientHello = new ClientHello(keys.ephemeralKeyPair().publicKey());
        var handshakeMessage = new HandshakeMessage(clientHello);
        Request.of(handshakeMessage)
                .sendWithPrologue(session, keys, store)
                .exceptionallyAsync(throwable -> handleFailure(LOGIN, throwable));
    }

    protected void markConnected() {
        connectedUuids.add(store.uuid());
        store.phoneNumber()
                .map(PhoneNumber::number)
                .ifPresent(connectedPhoneNumbers::add);
    }

    @Override
    public void onMessage(byte[] message) {
        if (state != SocketState.CONNECTED && state != SocketState.RESTORE) {
            authHandler.login(session, message)
                    .thenApplyAsync(result -> result ? state(SocketState.CONNECTED) : null)
                    .exceptionallyAsync(throwable -> handleFailure(LOGIN, throwable));
            return;
        }
        if(keys.readKey() == null){
            return;
        }
        var plainText = AesGmc.decrypt(keys.readCounter(true), message, keys.readKey().toByteArray());
        var decoder = new Decoder();
        var node = decoder.readNode(plainText);
        onNodeReceived(node);
        store.resolvePendingRequest(node, false);
        streamHandler.digest(node);
    }

    private void onNodeReceived(Node deciphered) {
        callListenersAsync(listener -> {
            listener.onNodeReceived(whatsapp, deciphered);
            listener.onNodeReceived(deciphered);
        });
    }

    @Override
    public void onClose() {
        if (state == SocketState.CONNECTED) {
            disconnect(DisconnectReason.RECONNECTING);
            return;
        }
        onDisconnected(state.toReason());
        onShutdown(state == SocketState.RECONNECTING);
    }

    @Override
    public void onError(Throwable throwable) {
        if (throwable instanceof IllegalStateException stateException && stateException.getMessage()
                .equals("The connection has been closed.")) {
            onClose();
            return;
        }
        onSocketEvent(SocketEvent.ERROR);
        handleFailure(UNKNOWN, throwable);
    }

    public synchronized CompletableFuture<Void> connect() {
        if (loginFuture == null || loginFuture.isDone()) {
            this.loginFuture = new CompletableFuture<>();
        }

        if (logoutFuture == null || logoutFuture.isDone()) {
            this.logoutFuture = new CompletableFuture<>();
        }

        if(state == SocketState.CONNECTED){
            return loginFuture;
        }

        this.session = new SocketSession(store.proxy().orElse(null), store.socketExecutor());
        return session.connect(this)
                .thenCompose(ignored -> loginFuture);
    }

    public CompletableFuture<Void> loginFuture(){
        return loginFuture;
    }

    public CompletableFuture<Void> logoutFuture(){
        return logoutFuture;
    }

    public CompletableFuture<Void> disconnect(DisconnectReason reason) {
        state(SocketState.of(reason));
        keys.clearReadWriteKey();
        return switch (reason) {
            case DISCONNECTED -> {
                if(session != null) {
                    session.close();
                }
                yield CompletableFuture.completedFuture(null);
            }
            case RECONNECTING -> {
                if(session != null) {
                    session.close();
                }
                yield connect();
            }
            case LOGGED_OUT -> {
                store.deleteSession();
                store.resolveAllPendingRequests();
                if(session != null) {
                    session.close();
                }
                yield CompletableFuture.completedFuture(null);
            }
            case RESTORE -> {
                store.deleteSession();
                store.resolveAllPendingRequests();
                var qrHandler = store.qrHandler();
                var errorHandler = store.errorHandler();
                var socketExecutor = store.socketExecutor();
                var oldListeners = new ArrayList<>(store.listeners());
                if(session != null) {
                    session.close();
                }
                var uuid = UUID.randomUUID();
                var number = store.phoneNumber()
                        .map(PhoneNumber::number)
                        .orElse(null);
                this.keys = Keys.random(uuid, number, store.clientType(), store.serializer());
                this.store = Store.random(uuid, number, store.clientType(), store.serializer());
                store.qrHandler(qrHandler);
                store.errorHandler(errorHandler);
                store.socketExecutor(socketExecutor);
                store.addListeners(oldListeners);
                yield connect();
            }
        };
    }

    public CompletableFuture<Void> pushPatch(PatchRequest request) {
        return appStateHandler.push(store.jid(), List.of(request));
    }

    public CompletableFuture<Void> pushPatches(ContactJid jid, List<PatchRequest> requests) {
        return appStateHandler.push(jid, requests);
    }

    public void pullPatch(PatchType... patchTypes) {
        appStateHandler.pull(patchTypes);
    }

    protected CompletableFuture<Void> pullInitialPatches() {
        return appStateHandler.pullInitial();
    }

    public void decodeMessage(Node node) {
        messageHandler.decode(node);
    }

    public CompletableFuture<Void> sendPeerMessage(ContactJid companion, ProtocolMessage message) {
        if(message == null){
            return CompletableFuture.completedFuture(null);
        }

        var key = MessageKey.builder()
                .chatJid(companion)
                .fromMe(true)
                .senderJid(store().jid())
                .build();
        var info = MessageInfo.builder()
                .senderJid(store().jid())
                .key(key)
                .message(MessageContainer.of(message))
                .timestampSeconds(Clock.nowSeconds())
                .build();
        var request = MessageSendRequest.builder()
                .info(info)
                .peer(true)
                .build();
        return sendMessage(request);
    }

    public CompletableFuture<Void> sendMessage(MessageSendRequest request) {
        store.attribute(request.info());
        return messageHandler.encode(request);
    }

    @SuppressWarnings("UnusedReturnValue")
    public CompletableFuture<Void> sendQueryWithNoResponse(String method, String category, Node... body) {
        return sendQueryWithNoResponse(null, Server.WHATSAPP.toJid(), method, category, null, body);
    }

    public CompletableFuture<Void> sendQueryWithNoResponse(String id, ContactJid to, String method, String category, Map<String, Object> metadata, Node... body) {
        var attributes = Attributes.ofNullable(metadata)
                .put("id", id, Objects::nonNull)
                .put("type", method)
                .put("to", to)
                .put("xmlns", category, Objects::nonNull)
                .toMap();
        return sendWithNoResponse(Node.ofChildren("iq", attributes, body));
    }

    public CompletableFuture<Void> sendWithNoResponse(Node node) {
        if (state() == SocketState.RESTORE) {
            return CompletableFuture.completedFuture(null);
        }

        var request = node.toRequest(store::nextTag, null);
        return request.sendWithNoResponse(session, keys, store)
                .exceptionallyAsync(throwable -> handleFailure(STREAM, throwable))
                .thenRunAsync(() -> onNodeSent(node));
    }

    private void onNodeSent(Node node) {
        callListenersAsync(listener -> {
            listener.onNodeSent(whatsapp, node);
            listener.onNodeSent(node);
        });
    }

    public CompletableFuture<Optional<ContactStatusResponse>> queryAbout(@NonNull ContactJidProvider chat) {
        var query = Node.of("status");
        var body = Node.ofAttributes("user", Map.of("jid", chat.toJid()));
        return sendInteractiveQuery(query, body).thenApplyAsync(this::parseStatus);
    }

    public CompletableFuture<List<Node>> sendInteractiveQuery(Node queryNode, Node... queryBody) {
        var query = Node.ofChildren("query", queryNode);
        var list = Node.ofChildren("list", queryBody);
        var sync = Node.ofChildren("usync",
                Map.of("sid", store.nextTag(), "mode", "query", "last", "true", "index", "0", "context", "interactive"),
                query, list);
        return sendQuery("get", "usync", sync).thenApplyAsync(this::parseQueryResult);
    }

    private Optional<ContactStatusResponse> parseStatus(List<Node> responses) {
        return responses.stream()
                .map(entry -> entry.findNode("status"))
                .flatMap(Optional::stream)
                .findFirst()
                .map(ContactStatusResponse::new);
    }

    public CompletableFuture<Node> sendQuery(String method, String category, Node... body) {
        return sendQuery(null, Server.WHATSAPP.toJid(), method, category, null, body);
    }

    private List<Node> parseQueryResult(Node result) {
        return result.findNodes("usync")
                .stream()
                .map(node -> node.findNode("list"))
                .flatMap(Optional::stream)
                .map(node -> node.findNodes("user"))
                .flatMap(Collection::stream)
                .toList();
    }

    public CompletableFuture<Node> sendQuery(String id, ContactJid to, String method, String category, Map<String, Object> metadata, Node... body) {
        var attributes = Attributes.ofNullable(metadata)
                .put("id", id, Objects::nonNull)
                .put("type", method)
                .put("to", to)
                .put("xmlns", category, Objects::nonNull)
                .toMap();
        return send(Node.ofChildren("iq", attributes, body));
    }

    public CompletableFuture<Node> send(Node node) {
        return send(node, null);
    }

    public CompletableFuture<Node> send(Node node, Function<Node, Boolean> filter) {
        if (state() == SocketState.RESTORE) {
            return CompletableFuture.completedFuture(node);
        }
        var request = node.toRequest(store::nextTag, filter);
        var result = request.send(session, keys, store);
        onNodeSent(node);
        return result;
    }

    public CompletableFuture<Optional<URI>> queryPicture(@NonNull ContactJidProvider chat) {
        var body = Node.ofAttributes("picture", Map.of("query", "url", "type", "image"));
        if (chat.toJid().hasServer(Server.GROUP)) {
            return queryGroupMetadata(chat.toJid())
                    .thenComposeAsync(result -> sendQuery("get", "w:profile:picture", Map.of(result.community() ? "parent_group_jid" : "target", chat.toJid()), body))
                    .thenApplyAsync(this::parseChatPicture);
        }

        return sendQuery("get", "w:profile:picture", Map.of("target", chat.toJid()), body)
                .thenApplyAsync(this::parseChatPicture);
    }

    public CompletableFuture<Node> sendQuery(String method, String category, Map<String, Object> metadata, Node... body) {
        return sendQuery(null, Server.WHATSAPP.toJid(), method, category, metadata, body);
    }

    private Optional<URI> parseChatPicture(Node result) {
        return result.findNode("picture")
                .flatMap(picture -> picture.attributes().getOptionalString("url"))
                .map(URI::create);
    }

    public CompletableFuture<List<ContactJid>> queryBlockList() {
        return sendQuery("get", "blocklist", (Node) null).thenApplyAsync(this::parseBlockList);
    }

    private List<ContactJid> parseBlockList(Node result) {
        return result.findNode("list")
                .orElseThrow(() -> new NoSuchElementException("Missing block list in response"))
                .findNodes("item")
                .stream()
                .map(item -> item.attributes().getJid("jid"))
                .flatMap(Optional::stream)
                .toList();
    }

    public CompletableFuture<Void> subscribeToPresence(ContactJidProvider jid) {
        var node = Node.ofAttributes("presence", Map.of("to", jid.toJid(), "type", "subscribe"));
        return sendWithNoResponse(node);
    }

    public CompletableFuture<GroupMetadata> queryGroupMetadata(ContactJidProvider group) {
        var body = Node.ofAttributes("query", Map.of("request", "interactive"));
        return sendQuery(group.toJid(), "get", "w:g2", body)
                .thenApplyAsync(response -> handleGroupMetadata(group, response));
    }

    private GroupMetadata handleGroupMetadata(ContactJidProvider group, Node response) {
        var metadata = response.findNode("group")
                .map(GroupMetadata::of)
                .orElseThrow(() -> new NoSuchElementException("Erroneous response: %s".formatted(response)));
        var chat = group instanceof Chat entry ? entry : store.findChatByJid(group).orElse(null);
        if(chat != null) {
            metadata.founder().ifPresent(chat::founder);
            chat.foundationTimestampSeconds(metadata.foundationTimestamp().toEpochSecond());
            metadata.description().ifPresent(chat::description);
            chat.addParticipants(metadata.participants());
        }

        return metadata;
    }

    public CompletableFuture<Node> sendQuery(ContactJid to, String method, String category, Node... body) {
        return sendQuery(null, to, method, category, null, body);
    }

    public void sendReceipt(ContactJid jid, ContactJid participant, List<String> messages, String type) {
        if (messages.isEmpty()) {
            return;
        }
        var attributes = Attributes.of()
                .put("id", messages.get(0))
                .put("t", Clock.nowMilliseconds(), () -> Objects.equals(type, "read") || Objects.equals(type, "read-self"))
                .put("to", jid)
                .put("type", type, Objects::nonNull);
        if(Objects.equals(type, "sender") && jid.hasServer(Server.WHATSAPP)){
            attributes.put("recipient", jid);
            attributes.put("to", participant);
        }else {
            attributes.put("to", jid);
            attributes.put("participant", participant, Objects::nonNull);
        }
        var receipt = Node.ofChildren("receipt", attributes.toMap(), toMessagesNode(messages));
        sendWithNoResponse(receipt);
    }

    private List<Node> toMessagesNode(List<String> messages) {
        if (messages.size() <= 1) {
            return null;
        }
        return messages.subList(1, messages.size())
                .stream()
                .map(id -> Node.ofAttributes("item", Map.of("id", id)))
                .toList();
    }

    protected void sendMessageAck(Node node, Map<String, Object> metadata) {
        var to = node.attributes()
                .getJid("from")
                .orElseThrow(() -> new NoSuchElementException("Missing from in message ack"));
        var participant = node.attributes().getNullableString("participant");
        var recipient = node.attributes().getNullableString("recipient");
        var type = node.attributes()
                .getOptionalString("type")
                .orElse(null);
        var attributes = Attributes.of()
                .put("id", node.id())
                .put("to", to)
                .put("class", node.description())
                .put("participant", participant, Objects::nonNull)
                .put("recipient", recipient, Objects::nonNull)
                .put("type", type, Objects::nonNull)
                .toMap();
        sendWithNoResponse(Node.ofAttributes("ack", attributes));
    }

    protected void onRegistrationCode(long code) {
        callListenersAsync(listener -> {
            listener.onRegistrationCode(whatsapp, code);
            listener.onRegistrationCode(code);
        });
    }

    protected void onMetadata(Map<String, String> properties) {
        callListenersAsync(listener -> {
            listener.onMetadata(whatsapp, properties);
            listener.onMetadata(properties);
        });
    }

    protected void onMessageStatus(MessageStatus status, Contact participant, MessageInfo message, Chat chat) {
        callListenersAsync(listener -> {
            if (participant == null) {
                listener.onConversationMessageStatus(whatsapp, message, status);
                listener.onConversationMessageStatus(message, status);
            }
            listener.onAnyMessageStatus(whatsapp, chat, participant, message, status);
            listener.onAnyMessageStatus(chat, participant, message, status);
        });
    }

    protected void onUpdateChatPresence(ContactStatus status, ContactJid contactJid, Chat chat) {
        var contact = store.findContactByJid(contactJid);
        if(contact.isPresent()) {
            contact.get().lastKnownPresence(status);
            if (status == contact.get().lastKnownPresence()) {
                return;
            }

            contact.get().lastSeen(ZonedDateTime.now());
        }

        chat.presences().put(contactJid, status);
        callListenersAsync(listener -> {
            listener.onContactPresence(whatsapp, chat, contactJid, status);
            listener.onContactPresence(chat, contactJid, status);
        });
    }

    protected void onNewMessage(MessageInfo info, boolean offline) {
        callListenersAsync(listener -> {
            listener.onNewMessage(whatsapp, info);
            listener.onNewMessage(info);
            listener.onNewMessage(whatsapp, info, offline);
            listener.onNewMessage(info, offline);
        });
    }

    protected void onNewStatus(MessageInfo info) {
        callListenersAsync(listener -> {
            listener.onNewStatus(whatsapp, info);
            listener.onNewStatus(info);
        });
    }

    protected void onChatRecentMessages(Chat chat, boolean last) {
        callListenersAsync(listener -> {
            listener.onChatMessagesSync(whatsapp, chat, last);
            listener.onChatMessagesSync(chat, last);
        });
    }

    protected void onFeatures(ActionValueSync.PrimaryFeature features) {
        callListenersAsync(listener -> {
            listener.onFeatures(whatsapp, features.flags());
            listener.onFeatures(features.flags());
        });
    }

    protected void onSetting(Setting setting) {
        callListenersAsync(listener -> {
            listener.onSetting(whatsapp, setting);
            listener.onSetting(setting);
        });
    }

    protected void onMessageDeleted(MessageInfo message, boolean everyone) {
        callListenersAsync(listener -> {
            listener.onMessageDeleted(whatsapp, message, everyone);
            listener.onMessageDeleted(message, everyone);
        });
    }

    protected void onAction(Action action, MessageIndexInfo indexInfo) {
        callListenersAsync(listener -> {
            listener.onAction(whatsapp, action, indexInfo);
            listener.onAction(action, indexInfo);
        });
    }

    protected void onDisconnected(DisconnectReason loggedOut) {
        if(loggedOut != DisconnectReason.RECONNECTING) {
            connectedUuids.remove(store.uuid());
            store.phoneNumber()
                    .map(PhoneNumber::number)
                    .ifPresent(connectedPhoneNumbers::remove);
            if(shutdownHook != null){
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            }
            if(loginFuture != null && !loginFuture.isDone()){
                loginFuture.complete(null);
            }
            if(logoutFuture != null && !logoutFuture.isDone()) {
                logoutFuture.complete(null);
            }
        }
        callListenersSync(listener -> {
            listener.onDisconnected(whatsapp, loggedOut);
            listener.onDisconnected(loggedOut);
        });
    }

    protected void onLoggedIn() {
        if(!loginFuture.isDone()) {
            loginFuture.complete(null);
        }
        callListenersSync(listener -> {
            listener.onLoggedIn(whatsapp);
            listener.onLoggedIn();
        });
    }

    public void callListenersSync(Consumer<Listener> consumer) {
        var service = getOrCreateListenersService();
        var futures = store.listeners()
                .stream()
                .map(listener -> CompletableFuture.runAsync(() -> invokeListenerSafe(consumer, listener), service))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();
    }

    private void invokeListenerSafe(Consumer<Listener> consumer, Listener listener) {
        try {
            consumer.accept(listener);
        }catch (Throwable throwable){
            handleFailure(UNKNOWN, throwable);
        }
    }

    protected void onChats() {
        callListenersSync(listener -> {
            listener.onChats(whatsapp, store().chats());
            listener.onChats(store().chats());
        });
    }

    protected void onStatus() {
        callListenersAsync(listener -> {
            listener.onStatus(whatsapp, store().status());
            listener.onStatus(store().status());
        });
    }

    protected void onContacts() {
        callListenersSync(listener -> {
            listener.onContacts(whatsapp, store().contacts());
            listener.onContacts(store().contacts());
        });
    }

    protected void onHistorySyncProgress(Integer progress, boolean recent) {
        callListenersAsync(listener -> {
            listener.onHistorySyncProgress(whatsapp, progress, recent);
            listener.onHistorySyncProgress(progress, recent);
        });
    }

    protected void onReply(MessageInfo info) {
        var quoted = info.quotedMessage().orElse(null);
        if (quoted == null) {
            return;
        }
        store.resolvePendingReply(info);
        callListenersAsync(listener -> {
            listener.onMessageReply(whatsapp, info, quoted);
            listener.onMessageReply(info, quoted);
        });
    }

    protected void onGroupPictureChange(Chat fromChat) {
        callListenersAsync(listener -> {
            listener.onGroupPictureChange(whatsapp, fromChat);
            listener.onGroupPictureChange(fromChat);
        });
    }

    protected void onContactPictureChange(Contact fromContact) {
        callListenersAsync(listener -> {
            listener.onContactPictureChange(whatsapp, fromContact);
            listener.onContactPictureChange(fromContact);
        });
    }

    protected void onUserAboutChange(String newAbout, String oldAbout) {
        callListenersAsync(listener -> {
            listener.onUserAboutChange(whatsapp, oldAbout, newAbout);
            listener.onUserAboutChange(oldAbout, newAbout);
        });
    }

    public void onUserPictureChange(URI newPicture, URI oldPicture) {
        callListenersAsync(listener -> {
            listener.onUserPictureChange(whatsapp, oldPicture, newPicture);
            listener.onUserPictureChange(oldPicture, newPicture);
        });
    }

    public void updateUserName(String newName, String oldName) {
        if (oldName != null && !Objects.equals(newName, oldName)) {
            sendWithNoResponse(Node.ofAttributes("presence", Map.of("name", oldName, "type", "unavailable")));
            sendWithNoResponse(Node.ofAttributes("presence", Map.of("name", newName, "type", "available")));
            onUserNameChange(newName, oldName);
        }
        var self = store().jid().toWhatsappJid();
        store().findContactByJid(self).orElseGet(() -> store().addContact(self)).chosenName(newName);
        store().name(newName);
    }


    private void onUserNameChange(String newName, String oldName) {
        callListenersAsync(listener -> {
            listener.onUserNameChange(whatsapp, oldName, newName);
            listener.onUserNameChange(oldName, newName);
        });
    }

    public void updateLocale(String newLocale, String oldLocale) {
        if (!Objects.equals(newLocale, oldLocale)) {
            return;
        }
        if (oldLocale != null) {
            onUserLocaleChange(newLocale, oldLocale);
        }
        store().locale(newLocale);
    }

    private void onUserLocaleChange(String newLocale, String oldLocale) {
        callListenersAsync(listener -> {
            listener.onUserLocaleChange(whatsapp, oldLocale, newLocale);
            listener.onUserLocaleChange(oldLocale, newLocale);
        });
    }

    protected void onContactBlocked(Contact contact) {
        callListenersAsync(listener -> {
            listener.onContactBlocked(whatsapp, contact);
            listener.onContactBlocked(contact);
        });
    }

    protected void onNewContact(Contact contact) {
        callListenersAsync(listener -> {
            listener.onNewContact(whatsapp, contact);
            listener.onNewContact(contact);
        });
    }

    protected void onDevices(LinkedHashMap<ContactJid, Integer> devices) {
        callListenersAsync(listener -> {
            listener.onLinkedDevices(whatsapp, devices.keySet());
            listener.onLinkedDevices(devices.keySet());
        });
    }

    public void onPrivacySettingChanged(PrivacySettingEntry oldEntry, PrivacySettingEntry newEntry) {
        callListenersAsync(listener -> {
            listener.onPrivacySettingChanged(whatsapp, oldEntry, newEntry);
            listener.onPrivacySettingChanged(oldEntry, newEntry);
        });
    }

    protected void querySessionsForcefully(ContactJid contactJid) {
        messageHandler.querySessions(List.of(contactJid), true);
    }

    private void dispose() {
        onSocketEvent(SocketEvent.CLOSE);
        streamHandler.dispose();
        messageHandler.dispose();
        appStateHandler.dispose();
        if(listenersService != null){
            listenersService.shutdownNow();
        }
    }

    private synchronized ExecutorService getOrCreateListenersService(){
        if(listenersService == null || listenersService.isShutdown()){
            listenersService = Executors.newCachedThreadPool();
        }

        return listenersService;
    }

    protected <T> T handleFailure(Location location, Throwable throwable) {
        if (state() == SocketState.RESTORE || state() == SocketState.LOGGED_OUT) {
            return null;
        }
        var result = store.errorHandler().handleError(store.clientType(), location, throwable);
        switch (result) {
            case RESTORE -> disconnect(DisconnectReason.RESTORE);
            case LOG_OUT -> disconnect(DisconnectReason.LOGGED_OUT);
            case DISCONNECT -> disconnect(DisconnectReason.DISCONNECTED);
            case RECONNECT -> disconnect(DisconnectReason.RECONNECTING);
        }
        return null;
    }

    public CompletableFuture<Void> queryCompanionDevices() {
        return messageHandler.getDevices(List.of(store.jid().toWhatsappJid()), true, false)
                .thenCompose(values -> messageHandler.querySessions(values, false));
    }

    public void parseSessions(Node result) {
        messageHandler.parseSessions(result);
    }

    public CompletableFuture<List<BusinessCategory>> queryBusinessCategories() {
            return sendQuery("get", "fb:thrift_iq", Node.ofChildren("request", Map.of("op", "profile_typeahead", "type", "catkit", "v", "1"), Node.ofChildren("query", List.of())))
                    .thenApplyAsync(this::parseBusinessCategories);
    }

    private List<BusinessCategory> parseBusinessCategories(Node result) {
        return result.findNode("response")
                .flatMap(entry -> entry.findNode("categories"))
                .stream()
                .map(entry -> entry.findNodes("category"))
                .flatMap(Collection::stream)
                .map(BusinessCategory::of)
                .toList();
    }
}
