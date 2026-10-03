package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class RoomRequestSuggestionStore {

    private final SettingsReader settingsReader;

    private Cache<String, RoomRequestSuggestion> cache;

    @PostConstruct
    void init() {
        // ponytail: el TTL se lee una vez al arrancar; si hace falta en caliente, escuchar SettingChangedEvent como CaffeinePreviewStore
        cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(settingsReader.getLong(SettingKey.PREVIEW_TTL_MINUTES)))
                .build();
    }

    void save(RoomRequestSuggestion suggestion) {
        cache.put(suggestion.suggestionId(), suggestion);
    }

    // asMap().remove es atómico: dos confirms concurrentes con el mismo id nunca reciben los dos la sugerencia.
    Optional<RoomRequestSuggestion> take(String suggestionId) {
        return Optional.ofNullable(cache.asMap().remove(suggestionId));
    }
}
