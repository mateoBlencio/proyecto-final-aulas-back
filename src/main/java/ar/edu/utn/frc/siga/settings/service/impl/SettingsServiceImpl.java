package ar.edu.utn.frc.siga.settings.service.impl;

import ar.edu.utn.frc.siga.audit.AuditOperation;
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
import java.util.HashMap;
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
    public String getString(SettingKey key) {
        return store.getString(key);
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
    @AuditOperation(value = "Modificación de configuración", subject = "Setting")
    public SettingResponseDto update(String key, String value) {
        return apply(List.of(new SettingUpdateItemDto(key, value))).getFirst();
    }

    @Override
    @Transactional
    @AuditOperation(value = "Modificación de configuraciones", subject = "Setting")
    public List<SettingResponseDto> updateBatch(List<SettingUpdateItemDto> items) {
        return apply(items);
    }

    /** Valida todo el lote (incluida la ventana horaria) antes de escribir la primera clave. */
    private List<SettingResponseDto> apply(List<SettingUpdateItemDto> items) {
        List<Map.Entry<SettingKey, String>> validated = items.stream()
                .map(item -> {
                    SettingKey settingKey = resolveKey(item.key());
                    return Map.entry(settingKey, validator.validate(settingKey, item.value()));
                })
                .toList();
        Map<SettingKey, String> pending = new HashMap<>();
        validated.forEach(entry -> pending.put(entry.getKey(), entry.getValue()));
        validateEventHours(pending);

        return validated.stream()
                .map(entry -> {
                    store.write(entry.getKey(), entry.getValue());
                    log.info("Configuración actualizada: clave={}, valor={}", entry.getKey().getKey(), entry.getValue());
                    return mapper.toDto(entry.getKey(), entry.getValue());
                })
                .toList();
    }

    private void validateEventHours(Map<SettingKey, String> pending) {
        if (!pending.containsKey(SettingKey.EVENTS_HOURS_START) && !pending.containsKey(SettingKey.EVENTS_HOURS_END)) {
            return;
        }
        validator.validateEventHours(
                pendingOrCurrentTime(pending, SettingKey.EVENTS_HOURS_START),
                pendingOrCurrentTime(pending, SettingKey.EVENTS_HOURS_END));
    }

    private LocalTime pendingOrCurrentTime(Map<SettingKey, String> pending, SettingKey key) {
        String value = pending.get(key);
        return value != null ? LocalTime.parse(value) : store.getTime(key);
    }

    private SettingKey resolveKey(String key) {
        try {
            return SettingKey.fromKey(key);
        } catch (IllegalArgumentException ex) {
            throw new ResourceNotFoundException("No existe la configuración " + key + ".");
        }
    }
}
