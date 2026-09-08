package ar.edu.utn.frc.siga.space.service;

import ar.edu.utn.frc.siga.space.dto.ClassroomFilter;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomDetailsUpdateDto;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomListItemDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomSubjectPermissionDto;
import ar.edu.utn.frc.siga.common.service.ActivationService;
import ar.edu.utn.frc.siga.space.service.command.ClassroomSyncCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.NamedInterface;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@NamedInterface("api")
public interface ClassroomService extends ActivationService<Long> {

    String DEFAULT_CLASSROOM_TYPE = "Aula común";

    ClassroomResponseDto create(ClassroomRequestDto dto);

    ClassroomResponseDto findById(Long id);

    List<ClassroomResponseDto> findAllAvailable();

    List<ClassroomResponseDto> findByIds(Collection<Long> ids);

    /**
     * Resuelve, para cada aula pedida, si habilita el uso por materia: {@code openToAll} cuando el
     * modo de permiso es {@code ALL}, o el conjunto de materias habilitadas cuando es {@code SUBSET}
     * ({@code NONE} devuelve conjunto vacío y {@code openToAll = false}). La semántica
     * {@code ALL}/{@code NONE}/{@code SUBSET} vive acá; los consumidores solo consultan el resultado.
     */
    Map<Long, ClassroomSubjectPermissionDto> findSubjectPermissions(Collection<Long> classroomIds);

    Page<ClassroomListItemDto> findAll(ClassroomFilter filter, Pageable pageable, boolean includeDeactivated);

    ClassroomResponseDto update(Long id, ClassroomRequestDto dto);

    ClassroomListItemDto updateDetails(Long id, ClassroomDetailsUpdateDto dto);

    void delete(Long id);

    ClassroomResponseDto findByRoomNumberAndBuilding(Integer roomNumber, Long buildingId);

    Optional<ClassroomResponseDto> findByRoomNumberAndBuildingCode(Integer roomNumber, Integer buildingCode);

    /**
     * Sincroniza el lote de aulas provenientes de SysAcad: crea/actualiza por (edificio, número)
     * comparando hash (sin pisar el tipo de aula, local-owned) y marca como no vigentes las ausentes
     * en la corrida. Devuelve la cantidad de filas afectadas.
     */
    int syncClassrooms(List<ClassroomSyncCommand> commands);
}
