package ar.edu.utn.frc.siga.settings.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.settings.dto.request.SettingUpdateItemDto;
import ar.edu.utn.frc.siga.settings.dto.response.SettingResponseDto;
import ar.edu.utn.frc.siga.settings.mapper.SettingResponseMapper;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.settings.service.SettingsService;
import ar.edu.utn.frc.siga.settings.service.SettingsStore;
import ar.edu.utn.frc.siga.settings.validator.SettingValueValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsServiceImpl implements SettingsService {

    private final SettingsStore store;
    private final SettingValueValidator validator;
    private final SettingResponseMapper mapper;

    @Override
    public int getInt(SettingKey key) {
        return store.getInt(key);
    }

    @Override
    public long getLong(SettingKey key) {
        return store.getLong(key);
    }

    @Override
    public boolean getBoolean(SettingKey key) {
        return store.getBoolean(key);
    }

    @Override
    public LocalTime getTime(SettingKey key) {
        return store.getTime(key);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<SettingResponseDto>> findAllGroupedByCategory() {
        Map<String, List<SettingResponseDto>> grouped = new LinkedHashMap<>();
        for (SettingKey key : SettingKey.values()) {
            grouped.computeIfAbsent(key.getCategory(), category -> new ArrayList<>())
                    .add(mapper.toDto(key, store.getRaw(key)));
        }
        return grouped;
    }

    @Override
    @Transactional(readOnly = true)
    public SettingResponseDto findByKey(String key) {
        SettingKey settingKey = resolveKey(key);
        return mapper.toDto(settingKey, store.getRaw(settingKey));
    }

    @Override
    @Transactional
    public SettingResponseDto update(String key, String value) {
        SettingKey settingKey = resolveKey(key);
        String normalized = validator.validate(settingKey, value);
        store.write(settingKey, normalized);
        log.info("Configuración actualizada: clave={}, valor={}", settingKey.getKey(), normalized);
        return mapper.toDto(settingKey, normalized);
    }

    @Override
    @Transactional
    public List<SettingResponseDto> updateBatch(List<SettingUpdateItemDto> items) {
        return items.stream()
                .map(item -> update(item.key(), item.value()))
                .toList();
    }

    private SettingKey resolveKey(String key) {
        try {
            return SettingKey.fromKey(key);
        } catch (IllegalArgumentException ex) {
            throw new ResourceNotFoundException("Setting not found with id: " + key);
        }
    }
}
