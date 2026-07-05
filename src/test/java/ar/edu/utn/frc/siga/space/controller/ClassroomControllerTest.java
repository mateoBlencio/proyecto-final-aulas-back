package ar.edu.utn.frc.siga.space.controller;

import ar.edu.utn.frc.siga.space.dto.ClassroomFilter;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDTO;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDTO;
import ar.edu.utn.frc.siga.common.exception.GlobalExceptionHandler;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.space.exception.SpaceDomainException;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ClassroomControllerTest {

    @Mock
    private ClassroomService classroomService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Mockito.reset(classroomService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ClassroomController(classroomService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private ClassroomResponseDTO response() {
        return ClassroomResponseDTO.builder()
                .id(1).roomNumber("101").capacity(30).floor(2).available(true)
                .buildingId(1).buildingName("Edificio A")
                .classroomTypeId(1).classroomTypeDescription("CLASSROOM")
                .build();
    }

    @Test
    void create_shouldReturn201() throws Exception {
        when(classroomService.create(any(ClassroomRequestDTO.class))).thenReturn(response());

        mockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber":"101","capacity":30,"floor":2,"classroomTypeId":1,"available":true,"buildingId":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.roomNumber").value("101"));
    }

    @Test
    void create_shouldReturn400WhenInvalid() throws Exception {
        mockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber":"","capacity":0,"classroomTypeId":null,"buildingId":null}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findById_shouldReturn200() throws Exception {
        when(classroomService.findById(1)).thenReturn(response());

        mockMvc.perform(get("/v1/classrooms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.roomNumber").value("101"));
    }

    @Test
    void findById_shouldReturn404WhenNotFound() throws Exception {
        when(classroomService.findById(999)).thenThrow(new ResourceNotFoundException("Classroom not found"));

        mockMvc.perform(get("/v1/classrooms/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Classroom not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void findAll_shouldReturn200WithPage() throws Exception {
        Page<ClassroomResponseDTO> page = new PageImpl<>(
                List.of(response()), PageRequest.of(0, 20), 1);

        when(classroomService.findAll(any(ClassroomFilter.class), any())).thenReturn(page);

        mockMvc.perform(get("/v1/classrooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void findAll_shouldApplyQueryParams() throws Exception {
        Page<ClassroomResponseDTO> page = new PageImpl<>(
                List.of(response()), PageRequest.of(0, 10), 1);

        when(classroomService.findAll(any(ClassroomFilter.class), any())).thenReturn(page);

        mockMvc.perform(get("/v1/classrooms")
                        .param("roomNumber", "10")
                        .param("buildingId", "1")
                        .param("capacityMin", "20")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void update_shouldReturn200() throws Exception {
        ClassroomResponseDTO updated = ClassroomResponseDTO.builder()
                .id(1).roomNumber("101").capacity(40).floor(2).available(false)
                .buildingId(1).buildingName("Edificio A")
                .classroomTypeId(1).classroomTypeDescription("CLASSROOM")
                .build();

        when(classroomService.update(eq(1), any(ClassroomRequestDTO.class))).thenReturn(updated);

        mockMvc.perform(put("/v1/classrooms/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber":"101","capacity":40,"floor":2,"classroomTypeId":1,"available":false,"buildingId":1}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void update_shouldReturn404WhenNotFound() throws Exception {
        when(classroomService.update(eq(999), any(ClassroomRequestDTO.class)))
                .thenThrow(new ResourceNotFoundException("Classroom not found"));

        mockMvc.perform(put("/v1/classrooms/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber":"101","capacity":40,"floor":2,"classroomTypeId":1,"available":false,"buildingId":1}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_shouldReturn204() throws Exception {
        doNothing().when(classroomService).delete(1);

        mockMvc.perform(delete("/v1/classrooms/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_shouldReturn404WhenNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Classroom not found")).when(classroomService).delete(999);

        mockMvc.perform(delete("/v1/classrooms/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void handleDomainException_shouldReturn400() throws Exception {
        when(classroomService.findById(1)).thenThrow(new SpaceDomainException("Domain error"));

        mockMvc.perform(get("/v1/classrooms/1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_shouldReturn400WhenRoomNumberBlank() throws Exception {
        mockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber":"","capacity":30,"floor":2,"classroomTypeId":1,"available":true,"buildingId":1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_shouldReturn400WhenCapacityNull() throws Exception {
        mockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber":"101","capacity":null,"floor":2,"classroomTypeId":1,"available":true,"buildingId":1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_shouldReturn400WhenBuildingIdNull() throws Exception {
        mockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber":"101","capacity":30,"floor":2,"classroomTypeId":1,"available":true,"buildingId":null}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_shouldReturn201WithFullResponseCheck() throws Exception {
        when(classroomService.create(any(ClassroomRequestDTO.class))).thenReturn(response());

        mockMvc.perform(post("/v1/classrooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber":"101","capacity":30,"floor":2,"classroomTypeId":1,"available":true,"buildingId":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.roomNumber").value("101"))
                .andExpect(jsonPath("$.capacity").value(30))
                .andExpect(jsonPath("$.floor").value(2))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.buildingId").value(1))
                .andExpect(jsonPath("$.buildingName").value("Edificio A"))
                .andExpect(jsonPath("$.classroomTypeId").value(1))
                .andExpect(jsonPath("$.classroomTypeDescription").value("CLASSROOM"));
    }

    @Test
    void findAll_shouldReturn200WithDefaultPageable() throws Exception {
        Page<ClassroomResponseDTO> page = new PageImpl<>(
                List.of(response()), PageRequest.of(0, 20), 1);

        when(classroomService.findAll(any(ClassroomFilter.class), any())).thenReturn(page);

        mockMvc.perform(get("/v1/classrooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void delete_shouldReturn400WhenDomainException() throws Exception {
        doThrow(new SpaceDomainException("Cannot delete")).when(classroomService).delete(1);

        mockMvc.perform(delete("/v1/classrooms/1"))
                .andExpect(status().isBadRequest());
    }
}
