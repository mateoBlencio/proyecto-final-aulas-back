package ar.edu.utn.frc.classroom_allocation.space.controller;

import ar.edu.utn.frc.classroom_allocation.space.dto.ClassroomFilter;
import ar.edu.utn.frc.classroom_allocation.space.dto.request.ClassroomRequestDTO;
import ar.edu.utn.frc.classroom_allocation.space.dto.response.ClassroomResponseDTO;
import ar.edu.utn.frc.classroom_allocation.space.service.ClassroomService;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/classrooms")
@RequiredArgsConstructor
public class ClassroomController {

    private final ClassroomService classroomService;

    @PostMapping
    public ResponseEntity<ClassroomResponseDTO> create(@Valid @RequestBody ClassroomRequestDTO dto) {
        ClassroomResponseDTO response = classroomService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClassroomResponseDTO> findById(@PathVariable Integer id) {
        ClassroomResponseDTO response = classroomService.findById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<ClassroomResponseDTO>> findAll(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String roomNumber,
            @RequestParam(required = false) Integer buildingId,
            @RequestParam(required = false) Integer classroomTypeId,
            @RequestParam(required = false) Integer capacityMin,
            @RequestParam(required = false) Integer capacityMax,
            @RequestParam(required = false) Integer floor,
            @RequestParam(required = false) Boolean available) {

        ClassroomFilter filter = new ClassroomFilter(roomNumber, buildingId, classroomTypeId,
                capacityMin, capacityMax, floor, available);
        return ResponseEntity.ok(classroomService.findAll(filter, pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClassroomResponseDTO> update(@PathVariable Integer id,
                                                        @Valid @RequestBody ClassroomRequestDTO dto) {
        ClassroomResponseDTO response = classroomService.update(id, dto);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        classroomService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
