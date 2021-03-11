package it.auties.whatsapp4j.response.impl.json;

import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.Nullable;

/**
 * A json model that contains information about the features available to the user linked with this session
 * This record should only be used by {@link UserInformationResponse}
 *
 * @param url a flag used to determine whether URLs can be shown???
 * @param flags unknown
 */
@Jacksonized
public record FeaturesInformation(boolean url, @Nullable String flags) {

}
