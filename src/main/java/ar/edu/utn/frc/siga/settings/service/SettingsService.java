package ar.edu.utn.frc.siga.settings.service;

import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.dto.request.SettingUpdateItemDto;
import ar.edu.utn.frc.siga.settings.dto.response.SettingResponseDto;

import java.util.List;
import java.util.Map;

public interface SettingsService extends SettingsReader {

    Map<String, List<SettingResponseDto>> findAllGroupedByCategory();

    SettingResponseDto findByKey(String key);

    SettingResponseDto update(String key, String value);

    List<SettingResponseDto> updateBatch(List<SettingUpdateItemDto> items);
}
