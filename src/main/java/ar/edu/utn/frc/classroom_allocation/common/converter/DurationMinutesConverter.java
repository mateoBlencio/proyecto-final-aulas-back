package ar.edu.utn.frc.classroom_allocation.common.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Duration;

@Converter(autoApply = true)
public class DurationMinutesConverter implements AttributeConverter<Duration, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Duration duration) {
        return duration == null ? null : (int) duration.toMinutes();
    }

    @Override
    public Duration convertToEntityAttribute(Integer minutes) {
        return minutes == null ? null : Duration.ofMinutes(minutes);
    }
}
