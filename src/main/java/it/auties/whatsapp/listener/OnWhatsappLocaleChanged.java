package it.auties.whatsapp.listener;

import it.auties.whatsapp.api.Whatsapp;

public interface OnWhatsappLocaleChanged extends Listener {
    /**
     * Called when the companion's locale changes
     *
     * @param whatsapp  an instance to the calling api
     * @param oldLocale the non-null old locale
     * @param newLocale the non-null new picture
     */
    @Override
    void onLocaleChanged(Whatsapp whatsapp, String oldLocale, String newLocale);
}
