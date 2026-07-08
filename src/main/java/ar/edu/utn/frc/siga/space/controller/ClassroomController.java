package ar.edu.utn.frc.siga.space.controller;

import ar.edu.utn.frc.siga.space.dto.ClassroomFilter;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDTO;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDTO;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
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
@RequestMapping("/v1/classrooms")
@RequiredArgsConstructor
public class ClassroomController {

    private final ClassroomService classroomService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClassroomResponseDTO> create(@Valid @RequestBody ClassroomRequestDTO dto) {
        log.debug("POST /v1/classrooms: roomNumber={}, buildingId={}", dto.roomNumber(), dto.buildingId());
        ClassroomResponseDTO response = classroomService.create(dto);
        log.info("Classroom created via controller: id={}", response.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR_AULICO')")
    public ResponseEntity<ClassroomResponseDTO> findById(@PathVariable Integer id) {
        log.debug("GET /v1/classrooms/{}", id);
        return ResponseEntity.ok(classroomService.findById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR_AULICO')")
    public ResponseEntity<Page<ClassroomResponseDTO>> findAll(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String roomNumber,
            @RequestParam(required = false) Integer buildingId,
            @RequestParam(required = false) Integer classroomTypeId,
            @RequestParam(required = false) Integer capacityMin,
            @RequestParam(required = false) Integer capacityMax,
            @RequestParam(required = false) Integer floor,
            @RequestParam(required = false) Boolean available) {

        log.debug("GET /v1/classrooms: buildingId={}, page={}", buildingId, pageable.getPageNumber());
        ClassroomFilter filter = new ClassroomFilter(roomNumber, buildingId, classroomTypeId,
                capacityMin, capacityMax, floor, available);
        Page<ClassroomResponseDTO> page = classroomService.findAll(filter, pageable);
        log.info("Classrooms listed: total={}", page.getTotalElements());
        return ResponseEntity.ok(page);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClassroomResponseDTO> update(@PathVariable Integer id,
                                                        @Valid @RequestBody ClassroomRequestDTO dto) {
        log.debug("PUT /v1/classrooms/{}: roomNumber={}", id, dto.roomNumber());
        ClassroomResponseDTO response = classroomService.update(id, dto);
        log.info("Classroom updated via controller: id={}", response.getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        log.debug("DELETE /v1/classrooms/{}", id);
        classroomService.delete(id);
        log.info("Classroom deleted via controller: id={}", id);
        return ResponseEntity.noContent().build();
    }

}
