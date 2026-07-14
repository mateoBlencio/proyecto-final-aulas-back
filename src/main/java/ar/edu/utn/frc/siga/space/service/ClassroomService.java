package ar.edu.utn.frc.siga.space.service;

import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.space.dto.ClassroomFilter;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.NamedInterface;

import java.util.Collection;
import java.util.List;

/**
 * Fachada pública del módulo {@code space} para el CRUD y la consulta de aulas.
 */
@NamedInterface("api")
public interface ClassroomService {

    /**
     * Crea un aula validando que el número de aula no esté en uso, que el piso no exceda
     * la cantidad de pisos del edificio y que la capacidad sea positiva.
     */
    ClassroomResponseDto create(ClassroomRequestDto dto);

    ClassroomResponseDto findById(Integer id);

    /** Todas las aulas disponibles (no eliminadas) para la asignación automática. */
    List<ClassroomResponseDto> findAllAvailable();

    /**
     * Busca aulas por lote de IDs para componer DTOs de datos ajenos sin N+1 (p. ej.
     * desde {@code AllocationComposer}). A diferencia de {@link #findById(Integer)}, NO
     * filtra aulas eliminadas: una asignación histórica puede referenciar un aula ya
     * borrada y su dato debe poder seguir componiéndose (no lanza 404).
     */
    List<ClassroomResponseDto> findByIds(Collection<Integer> ids);

    Page<ClassroomResponseDto> findAll(ClassroomFilter filter, Pageable pageable);

    /** Actualiza un aula existente, revalidando piso y capacidad igual que {@link #create}. */
    ClassroomResponseDto update(Integer id, ClassroomRequestDto dto);

    /** Elimina el aula (soft-delete: marca {@code deleted}, no borra el registro). */
    void delete(Integer id);

    /**
     * Busca un aula por número dentro de un edificio; si no existe, la crea con datos
     * provisionales (piso 0, capacidad igual a {@code enrolledCount} o 1, tipo por defecto).
     * Usado por flujos que reciben aulas como texto libre (p. ej. importación de Excel).
     */
    FindOrCreateResult<ClassroomResponseDto> findOrCreate(String roomNumber, Integer buildingId, Integer enrolledCount);
}
