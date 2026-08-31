package ar.edu.utn.frc.siga.roomrequest.handler;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registro de handlers por tipo. Falla al arrancar si un tipo del enum no tiene handler o si hay dos
 * para el mismo tipo: agregar un {@link RoomRequestType} sin su handler no compila un despliegue.
 */
@Component
public class RoomRequestHandlers {

    private final Map<RoomRequestType, RoomRequestTypeHandler> byType;

    public RoomRequestHandlers(List<RoomRequestTypeHandler> handlers) {
        Map<RoomRequestType, RoomRequestTypeHandler> map = new EnumMap<>(RoomRequestType.class);
        for (RoomRequestTypeHandler handler : handlers) {
            if (map.put(handler.type(), handler) != null) {
                throw new IllegalStateException("Más de un handler para el tipo " + handler.type());
            }
        }
        for (RoomRequestType type : RoomRequestType.values()) {
            if (!map.containsKey(type)) {
                throw new IllegalStateException("Falta el handler para el tipo " + type);
            }
        }
        this.byType = map;
    }

    public RoomRequestTypeHandler forType(RoomRequestType type) {
        return byType.get(type);
    }
}
