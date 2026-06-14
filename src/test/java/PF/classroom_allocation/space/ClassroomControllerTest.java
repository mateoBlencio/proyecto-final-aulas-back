package PF.classroom_allocation.space;

import PF.classroom_allocation.space.controller.ClassroomController;
import PF.classroom_allocation.space.dto.ClassroomFilter;
import PF.classroom_allocation.space.dto.ClassroomRequestDTO;
import PF.classroom_allocation.space.dto.ClassroomResponseDTO;
import PF.classroom_allocation.space.exception.GlobalExceptionHandler;
import PF.classroom_allocation.space.exception.ResourceNotFoundException;
import PF.classroom_allocation.space.exception.SpaceDomainException;
import PF.classroom_allocation.space.service.ClassroomService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClassroomController.class)
@Import(GlobalExceptionHandler.class)
class ClassroomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final ClassroomService classroomService = mock(ClassroomService.class);

    @BeforeEach
    void setUp() {
        Mockito.reset(classroomService);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        ClassroomService classroomService() {
            return classroomService;
        }
    }

    @Test
    void create_shouldReturn201() throws Exception {
        var response = ClassroomResponseDTO.builder()
                .id(1).roomNumber("101").capacity(30).floor(2).available(true)
                .buildingId(1).buildingName("Edificio A")
                .classroomTypeId(1).classroomTypeDescription("CLASSROOM")
                .build();

        when(classroomService.create(any(ClassroomRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/classrooms/v1")
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
        mockMvc.perform(post("/classrooms/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber":"","capacity":0,"classroomTypeId":null,"buildingId":null}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findById_shouldReturn200() throws Exception {
        var response = ClassroomResponseDTO.builder()
                .id(1).roomNumber("101").capacity(30).floor(2).available(true)
                .buildingId(1).buildingName("Edificio A")
                .classroomTypeId(1).classroomTypeDescription("CLASSROOM")
                .build();

        when(classroomService.findById(1)).thenReturn(response);

        mockMvc.perform(get("/classrooms/v1/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.roomNumber").value("101"));
    }

    @Test
    void findById_shouldReturn404WhenNotFound() throws Exception {
        when(classroomService.findById(999)).thenThrow(new ResourceNotFoundException("Classroom not found"));

        mockMvc.perform(get("/classrooms/v1/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Classroom not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void findAll_shouldReturn200WithPage() throws Exception {
        var response = ClassroomResponseDTO.builder()
                .id(1).roomNumber("101").capacity(30).floor(2).available(true)
                .buildingId(1).buildingName("Edificio A")
                .classroomTypeId(1).classroomTypeDescription("CLASSROOM")
                .build();
        Page<ClassroomResponseDTO> page = new PageImpl<>(
                List.of(response), PageRequest.of(0, 20, Sort.by("id")), 1);

        when(classroomService.findAll(any(ClassroomFilter.class), any())).thenReturn(page);

        mockMvc.perform(get("/classrooms/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void findAll_shouldApplyQueryParams() throws Exception {
        var response = ClassroomResponseDTO.builder()
                .id(1).roomNumber("101").capacity(30).floor(2).available(true)
                .buildingId(1).buildingName("Edificio A")
                .classroomTypeId(1).classroomTypeDescription("CLASSROOM")
                .build();
        Page<ClassroomResponseDTO> page = new PageImpl<>(
                List.of(response), PageRequest.of(0, 10), 1);

        when(classroomService.findAll(any(ClassroomFilter.class), any())).thenReturn(page);

        mockMvc.perform(get("/classrooms/v1")
                        .param("roomNumber", "10")
                        .param("buildingId", "1")
                        .param("capacityMin", "20")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void update_shouldReturn200() throws Exception {
        var response = ClassroomResponseDTO.builder()
                .id(1).roomNumber("101").capacity(40).floor(2).available(false)
                .buildingId(1).buildingName("Edificio A")
                .classroomTypeId(1).classroomTypeDescription("CLASSROOM")
                .build();

        when(classroomService.update(eq(1), any(ClassroomRequestDTO.class))).thenReturn(response);

        mockMvc.perform(put("/classrooms/v1/1")
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

        mockMvc.perform(put("/classrooms/v1/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber":"101","capacity":40,"floor":2,"classroomTypeId":1,"available":false,"buildingId":1}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_shouldReturn204() throws Exception {
        doNothing().when(classroomService).delete(1);

        mockMvc.perform(delete("/classrooms/v1/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_shouldReturn404WhenNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Classroom not found")).when(classroomService).delete(999);

        mockMvc.perform(delete("/classrooms/v1/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void handleDomainException_shouldReturn400() throws Exception {
        when(classroomService.findById(1)).thenThrow(new SpaceDomainException("Domain error"));

        mockMvc.perform(get("/classrooms/v1/1"))
                .andExpect(status().isBadRequest());
    }
}
