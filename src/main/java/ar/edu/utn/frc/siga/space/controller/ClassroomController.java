package ar.edu.utn.frc.siga.space.controller;

import ar.edu.utn.frc.siga.space.dto.ClassroomFilter;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${siga.api.base-path}/classrooms")
@RequiredArgsConstructor
@Tag(name = "Aulas", description = "ABM y consulta de aulas")
public class ClassroomController {

    private final ClassroomService classroomService;

    @PostMapping
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Crear aula",
               description = "400 si el roomNumber ya existe en el edificio o si la capacidad no es positiva. "
                       + "404 si el edificio o el tipo de aula no existen.")
    public ResponseEntity<ClassroomResponseDto> create(@Valid @RequestBody ClassroomRequestDto dto) {
        log.debug("POST /v1/classrooms: roomNumber={}, buildingId={}", dto.roomNumber(), dto.buildingId());
        ClassroomResponseDto response = classroomService.create(dto);
        log.info("Aula creada vía controller: id={}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
    @Operation(summary = "Buscar aula por id", description = "404 si el aula no existe.")
    public ResponseEntity<ClassroomResponseDto> findById(@PathVariable Long id) {
        log.debug("GET /v1/classrooms/{}", id);
        return ResponseEntity.ok(classroomService.findById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUBSECRETARIA','AUXILIAR_AULICO')")
    @Operation(summary = "Listar aulas", description = "Listado paginado con filtros opcionales por número, "
            + "edificio, tipo y capacidad.")
    public ResponseEntity<Page<ClassroomResponseDto>> findAll(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) Integer roomNumber,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long classroomTypeId,
            @RequestParam(required = false) Integer capacityMin,
            @RequestParam(required = false) Integer capacityMax) {

        log.debug("GET /v1/classrooms: buildingId={}, page={}", buildingId, pageable.getPageNumber());
        ClassroomFilter filter = new ClassroomFilter(roomNumber, buildingId, classroomTypeId,
                capacityMin, capacityMax);
        Page<ClassroomResponseDto> page = classroomService.findAll(filter, pageable);
        log.info("Aulas listadas: total={}", page.getTotalElements());
        return ResponseEntity.ok(page);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Actualizar aula",
               description = "404 si el aula, el edificio o el tipo de aula no existen. 400 si la capacidad no "
                       + "es positiva.")
    public ResponseEntity<ClassroomResponseDto> update(@PathVariable Long id,
                                                        @Valid @RequestBody ClassroomRequestDto dto) {
        log.debug("PUT /v1/classrooms/{}: roomNumber={}", id, dto.roomNumber());
        ClassroomResponseDto response = classroomService.update(id, dto);
        log.info("Aula actualizada vía controller: id={}", response.id());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUBSECRETARIA')")
    @Operation(summary = "Eliminar aula", description = "Soft-delete. 404 si el aula no existe.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.debug("DELETE /v1/classrooms/{}", id);
        classroomService.delete(id);
        log.info("Aula eliminada vía controller: id={}", id);
        return ResponseEntity.noContent().build();
    }

}
