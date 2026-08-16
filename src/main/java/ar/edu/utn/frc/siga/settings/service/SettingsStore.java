package ar.edu.utn.frc.siga.settings.service;

import ar.edu.utn.frc.siga.settings.api.SettingChangedEvent;
import ar.edu.utn.frc.siga.settings.config.SettingsCatalogProperties;
import ar.edu.utn.frc.siga.settings.model.Setting;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.settings.repository.SettingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Component
@RequiredArgsConstructor
public class SettingsStore {

    private final SettingRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final SettingsCatalogProperties catalog;
    private final Map<SettingKey, String> cache = new ConcurrentHashMap<>();

    @PostConstruct
    void loadFromDatabase() {
        repository.findAll().forEach(this::cacheRow);
        log.info("Cache de configuración cargado desde la base: {} entradas", cache.size());
    }

    public String getRaw(SettingKey key) {
        return cache.getOrDefault(key, catalog.defaultValue(key));
    }

    public int getInt(SettingKey key) {
        return Integer.parseInt(getRaw(key));
    }

    public long getLong(SettingKey key) {
        return Long.parseLong(getRaw(key));
    }

    public boolean getBoolean(SettingKey key) {
        return Boolean.parseBoolean(getRaw(key));
    }

    public LocalTime getTime(SettingKey key) {
        return LocalTime.parse(getRaw(key));
    }


    public void write(SettingKey key, String value) {
        Setting setting = repository.findById(key.getKey())
                .orElseGet(() -> new Setting(key.getKey(), value));
        setting.setValue(value);
        repository.save(setting);
        eventPublisher.publishEvent(new SettingChangedEvent(key));
        updateCacheAfterCommit(key, value);
    }

    private void updateCacheAfterCommit(SettingKey key, String value) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.put(key, value);
                }
            });
        } else {
            cache.put(key, value);
        }
    }

    private void cacheRow(Setting setting) {
        try {
            cache.put(SettingKey.fromKey(setting.getKey()), setting.getValue());
        } catch (IllegalArgumentException ex) {
            log.warn("Fila de configuración con clave desconocida, se ignora: {}", setting.getKey());
        }
    }
}
