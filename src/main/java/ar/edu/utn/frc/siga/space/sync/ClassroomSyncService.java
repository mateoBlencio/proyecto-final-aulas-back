package ar.edu.utn.frc.siga.space.sync;

import ar.edu.utn.frc.siga.common.model.RecordSource;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomTypeRepository;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadClassroomDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.api.SysacadViewSyncer;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class ClassroomSyncService implements SysacadViewSyncer {

    private static final String DEFAULT_CLASSROOM_TYPE = "Normal";

    private final SysacadCatalogReader catalogReader;
    private final ClassroomRepository classroomRepository;
    private final BuildingRepository buildingRepository;
    private final ClassroomTypeRepository classroomTypeRepository;
    private final SysacadSyncStateService syncStateService;

    @Override
    public SysacadView view() {
        return SysacadView.AULAS;
    }

    @Override
    @Transactional
    public void sync() {
        try {
            int affected = upsert(catalogReader.findClassrooms());
            syncStateService.recordSuccess(SysacadView.AULAS, affected);
            log.info("Sync de Aulas finalizado: {} filas afectadas", affected);
        } catch (RuntimeException e) {
            syncStateService.recordFailure(SysacadView.AULAS, e.getMessage());
            throw e;
        }
    }

    private int upsert(List<SysacadClassroomDto> rows) {
        Instant syncedAt = Instant.now();
        ClassroomType defaultType = null;
        Map<Integer, Building> buildingsByCode = buildingRepository.findAll().stream()
                .filter(building -> building.getBuildingCode() != null)
                .collect(Collectors.toMap(Building::getBuildingCode, Function.identity()));
        Map<ClassroomKey, Classroom> existing = classroomRepository.findAll().stream()
                .collect(Collectors.toMap(ClassroomSyncService::keyOf, Function.identity()));
        Set<ClassroomKey> incoming = new HashSet<>();
        int affected = 0;

        for (SysacadClassroomDto row : rows) {
            if (row.roomNumber() == null || row.buildingCode() == null) {
                log.warn("Aula de SysAcad ignorada por clave incompleta: aula={}, edificio={}",
                        row.roomNumber(), row.buildingCode());
                continue;
            }
            Building building = buildingsByCode.get(row.buildingCode());
            if (building == null) {
                log.warn("Aula de SysAcad ignorada: no existe el edificio con codigo={}", row.buildingCode());
                continue;
            }

            ClassroomKey key = new ClassroomKey(building.getId(), String.valueOf(row.roomNumber()));
            incoming.add(key);
            String hash = Hashes.sha256Hex(row.capacity(), row.isEnabled());
            Classroom classroom = existing.get(key);

            if (classroom == null) {
                if (defaultType == null) {
                    defaultType = resolveDefaultClassroomType();
                }
                Classroom saved = classroomRepository.save(Classroom.builder()
                        .roomNumber(key.roomNumber())
                        .building(building)
                        .classroomType(defaultType)
                        .capacity(row.capacity())
                        .available(row.isEnabled())
                        .source(RecordSource.SYSACAD)
                        .syncedAt(syncedAt)
                        .sysacadHash(hash)
                        .presentInSysacad(true)
                        .build());
                existing.put(key, saved);
                affected++;
                continue;
            }
            if (isUpToDate(classroom, hash)) {
                continue;
            }
            // `piso` y `classroomType` son local-owned: el sync nunca los pisa en un update (§4.3).
            // Al crear sí se asigna un default (§ constraint NOT NULL), ver resolveDefaultClassroomType().
            classroom.setCapacity(row.capacity());
            classroom.setAvailable(row.isEnabled());
            classroom.setSource(RecordSource.SYSACAD);
            classroom.setSyncedAt(syncedAt);
            classroom.setSysacadHash(hash);
            classroom.setPresentInSysacad(true);
            classroomRepository.save(classroom);
            affected++;
        }

        return affected + markAbsent(existing.values(), incoming, syncedAt);
    }

    private int markAbsent(Iterable<Classroom> existing, Set<ClassroomKey> incoming, Instant syncedAt) {
        int affected = 0;
        for (Classroom classroom : existing) {
            if (incoming.contains(keyOf(classroom))
                    || classroom.getSource() != RecordSource.SYSACAD
                    || Boolean.FALSE.equals(classroom.getPresentInSysacad())) {
                continue;
            }
            classroom.setPresentInSysacad(false);
            classroom.setAvailable(false);
            classroom.setSyncedAt(syncedAt);
            classroomRepository.save(classroom);
            affected++;
            log.info("Aula marcada como no vigente en SysAcad: id={}", classroom.getId());
        }
        return affected;
    }

    private ClassroomType resolveDefaultClassroomType() {
        return classroomTypeRepository.findByDescriptionIgnoreCase(DEFAULT_CLASSROOM_TYPE)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el tipo de aula por defecto '" + DEFAULT_CLASSROOM_TYPE + "' (seed de data.sql)"));
    }

    private static ClassroomKey keyOf(Classroom classroom) {
        return new ClassroomKey(classroom.getBuilding().getId(), classroom.getRoomNumber());
    }

    private static boolean isUpToDate(Classroom classroom, String hash) {
        return hash.equals(classroom.getSysacadHash())
                && Boolean.TRUE.equals(classroom.getPresentInSysacad())
                && classroom.getSource() == RecordSource.SYSACAD;
    }

    private record ClassroomKey(Integer buildingId, String roomNumber) {}
}
