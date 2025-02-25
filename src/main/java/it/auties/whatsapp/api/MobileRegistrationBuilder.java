package it.auties.whatsapp.api;

import it.auties.whatsapp.controller.Keys;
import it.auties.whatsapp.controller.Store;
import it.auties.whatsapp.model.mobile.PhoneNumber;
import it.auties.whatsapp.model.mobile.VerificationCodeMethod;
import it.auties.whatsapp.model.mobile.VerificationCodeResponse;
import it.auties.whatsapp.util.RegistrationHelper;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A builder to specify the options for the mobile api
 */
@SuppressWarnings("unused")
public sealed class MobileRegistrationBuilder<T extends MobileRegistrationBuilder<T>> {
    final Store store;
    final Keys keys;
    final ErrorHandler errorHandler;
    final Executor socketExecutor;
    Whatsapp whatsapp;
    AsyncVerificationCodeSupplier verificationCodeSupplier;
    AsyncCaptchaCodeSupplier verificationCaptchaSupplier;

    MobileRegistrationBuilder(Store store, Keys keys, ErrorHandler errorHandler, Executor socketExecutor) {
        this.store = store;
        this.keys = keys;
        this.errorHandler = errorHandler;
        this.socketExecutor = socketExecutor;
    }

    /**
     * Sets the handler that provides the verification code when verifying an account
     *
     * @param verificationCodeSupplier the non-null supplier
     * @return the same instance
     */
    @SuppressWarnings("unchecked")
    public T verificationCodeSupplier(Supplier<String> verificationCodeSupplier) {
        this.verificationCodeSupplier = AsyncVerificationCodeSupplier.of(verificationCodeSupplier);
        return (T) this;
    }

    /**
     * Sets the handler that provides the verification code when verifying an account
     *
     * @param verificationCodeSupplier the non-null supplier
     * @return the same instance
     */
    @SuppressWarnings("unchecked")
    public T verificationCodeSupplier(AsyncVerificationCodeSupplier verificationCodeSupplier) {
        this.verificationCodeSupplier = verificationCodeSupplier;
        return (T) this;
    }

    /**
     * Sets the handler that provides the captcha newsletters when verifying an account
     * Happens only on business devices
     *
     * @param verificationCaptchaSupplier the non-null supplier
     * @return the same instance
     */
    @SuppressWarnings("unchecked")
    public T verificationCaptchaSupplier(Function<VerificationCodeResponse, String> verificationCaptchaSupplier) {
        this.verificationCaptchaSupplier = AsyncCaptchaCodeSupplier.of(verificationCaptchaSupplier);
        return (T) this;
    }

    /**
     * Sets the handler that provides the captcha newsletters when verifying an account
     * Happens only on business devices
     *
     * @param verificationCaptchaSupplier the non-null supplier
     * @return the same instance
     */
    @SuppressWarnings("unchecked")
    public T verificationCaptchaSupplier(AsyncCaptchaCodeSupplier verificationCaptchaSupplier) {
        this.verificationCaptchaSupplier = verificationCaptchaSupplier;
        return (T) this;
    }

    Whatsapp buildWhatsapp() {
        return this.whatsapp = Whatsapp.customBuilder()
                .store(store)
                .keys(keys)
                .errorHandler(errorHandler)
                .socketExecutor(socketExecutor)
                .build();
    }

    public final static class Unregistered extends MobileRegistrationBuilder<Unregistered> {
        private VerificationCodeMethod verificationCodeMethod;

        Unregistered(Store store, Keys keys, ErrorHandler errorHandler, Executor socketExecutor) {
            super(store, keys, errorHandler, socketExecutor);
            this.verificationCodeMethod = VerificationCodeMethod.SMS;
        }


        /**
         * Sets the type of method used to verify the account
         *
         * @param verificationCodeMethod the non-null method
         * @return the same instance
         */
        public Unregistered verificationCodeMethod(VerificationCodeMethod verificationCodeMethod) {
            this.verificationCodeMethod = verificationCodeMethod;
            return this;
        }

        /**
         * Registers a phone number by asking for a verification code and then sending it to Whatsapp
         *
         * @param phoneNumber a phone number(include the prefix)
         * @return a future
         */
        public CompletableFuture<Whatsapp> register(long phoneNumber) {
            if (whatsapp != null) {
                return CompletableFuture.completedFuture(whatsapp);
            }

            Objects.requireNonNull(verificationCodeSupplier, "Expected a valid verification code supplier");
            Objects.requireNonNull(verificationCodeMethod, "Expected a valid verification method");
            var number = PhoneNumber.of(phoneNumber);
            keys.setPhoneNumber(number);
            store.setPhoneNumber(number);
            if (!keys.registered()) {
                return RegistrationHelper.registerPhoneNumber(store, keys, verificationCodeSupplier, verificationCaptchaSupplier, verificationCodeMethod)
                        .thenApply(ignored -> buildWhatsapp());
            }

            return CompletableFuture.completedFuture(buildWhatsapp());
        }

        /**
         * Asks Whatsapp for a one-time-password to start the registration process
         *
         * @param phoneNumber a phone number(include the prefix)
         * @return a future
         */
        public CompletableFuture<Unverified> requestVerificationCode(long phoneNumber) {
            var number = PhoneNumber.of(phoneNumber);
            keys.setPhoneNumber(number);
            store.setPhoneNumber(number);
            if (!keys.registered()) {
                return RegistrationHelper.requestVerificationCode(store, keys, verificationCodeMethod)
                        .thenApply(ignored -> new Unverified(store, keys, errorHandler, socketExecutor));
            }

            return CompletableFuture.completedFuture(new Unverified(store, keys, errorHandler, socketExecutor));
        }
    }

    public final static class Unverified extends MobileRegistrationBuilder<Unverified> {
        Unverified(Store store, Keys keys, ErrorHandler errorHandler, Executor socketExecutor) {
            super(store, keys, errorHandler, socketExecutor);
        }

        /**
         * Sends the verification code you already requested to Whatsapp
         *
         * @return the same instance for chaining
         */
        public CompletableFuture<Whatsapp> verify(long phoneNumber) {
            var number = PhoneNumber.of(phoneNumber);
            keys.setPhoneNumber(number);
            store.setPhoneNumber(number);
            return verify();
        }


        /**
         * Sends the verification code you already requested to Whatsapp
         *
         * @return the same instance for chaining
         */
        public CompletableFuture<Whatsapp> verify() {
            Objects.requireNonNull(store.phoneNumber(), "Missing phone number: please specify it");
            Objects.requireNonNull(verificationCodeSupplier, "Expected a valid verification code supplier");
            return RegistrationHelper.sendVerificationCode(store, keys, verificationCodeSupplier, verificationCaptchaSupplier)
                    .thenApply(ignored -> buildWhatsapp());
        }
    }
}
