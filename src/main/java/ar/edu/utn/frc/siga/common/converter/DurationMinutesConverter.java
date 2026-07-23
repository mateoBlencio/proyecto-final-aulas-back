package ar.edu.utn.frc.siga.common.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Duration;

/**
 * Convierte {@link Duration} a un entero de minutos para persistirlo (y viceversa), ya que
 * la columna de base de datos guarda la duración como un número simple de minutos.
 * Aplica automáticamente a todo atributo {@code Duration} de las entidades ({@code autoApply}).
 */
@Converter(autoApply = true)
public class DurationMinutesConverter implements AttributeConverter<Duration, Integer> {

    /**
     * Redondea la duración al minuto más cercano para la columna numérica.
     */
    @Override
    public Integer convertToDatabaseColumn(Duration duration) {
        return duration == null ? null : (int) Math.round(duration.toSeconds() / 60.0);
    }

    /**
     * Reconstruye la duración a partir de los minutos almacenados.
     */
    @Override
    public Duration convertToEntityAttribute(Integer minutes) {
        return minutes == null ? null : Duration.ofMinutes(minutes);
    }
}
