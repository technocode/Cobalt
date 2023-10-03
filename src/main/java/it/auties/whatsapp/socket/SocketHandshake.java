package it.auties.whatsapp.socket;

import it.auties.whatsapp.controller.Keys;
import it.auties.whatsapp.crypto.AesGcm;
import it.auties.whatsapp.crypto.Hkdf;
import it.auties.whatsapp.crypto.Sha256;
import it.auties.whatsapp.util.BytesHelper;
import it.auties.whatsapp.util.Spec;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Arrays;

public class SocketHandshake {
    private final Keys keys;
    private byte[] hash;
    private byte[] salt;
    private byte[] cryptoKey;
    private long counter;

    public SocketHandshake(Keys keys) {
        this.keys = keys;
        this.hash = Spec.Whatsapp.PROTOCOL;
        this.salt = Spec.Whatsapp.PROTOCOL;
        this.cryptoKey = Spec.Whatsapp.PROTOCOL;
        this.counter = 0;
        updateHash(keys.prologue());
    }

    public void updateHash(byte @NonNull [] data) {
        var input = BytesHelper.concat(hash, data);
        this.hash = Sha256.calculate(input);
    }

    public byte[] cipher(byte @NonNull [] bytes, boolean encrypt) {
        var cyphered = encrypt ? AesGcm.encrypt(counter++, bytes, cryptoKey, hash) : AesGcm.decrypt(counter++, bytes, cryptoKey, hash);
        if (!encrypt) {
            updateHash(bytes);
            return cyphered;
        }
        updateHash(cyphered);
        return cyphered;
    }

    public void finish() {
        var expanded = Hkdf.extractAndExpand(new byte[0], salt, null, 64);
        keys.setWriteKey(Arrays.copyOfRange(expanded, 0, 32));
        keys.setReadKey(Arrays.copyOfRange(expanded, 32, 64));
        dispose();
    }

    private void dispose() {
        this.hash = null;
        this.salt = null;
        this.cryptoKey = null;
        this.counter = 0;
    }

    public void mixIntoKey(byte @NonNull [] bytes) {
        var expanded = Hkdf.extractAndExpand(bytes, salt, null, 64);
        this.salt = Arrays.copyOfRange(expanded, 0, 32);
        this.cryptoKey = Arrays.copyOfRange(expanded, 32, 64);
        this.counter = 0;
    }
}