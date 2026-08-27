package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.roomrequest.dto.RoomRequestItemFilter;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemRowDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestComposer;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestMapper;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.roomrequest.validator.RoomRequestValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestServiceImpl")
class RoomRequestServiceImplTest {

    @Mock
    private RoomRequestRepository repository;
    @Mock
    private RoomRequestItemRepository itemRepository;
    @Mock
    private RoomRequestMapper mapper;
    @Mock
    private RoomRequestComposer composer;
    @Mock
    private RoomRequestValidator validator;

    private RoomRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoomRequestServiceImpl(repository, itemRepository, mapper, composer, validator);
    }

    @Test
    @DisplayName("traduce el sort antes de consultar el repositorio y compone la página por batch")
    void translatesSortAndComposesPage() {
        RoomRequestItemFilter filter = RoomRequestItemFilter.of(null, null, null, null, null, null, true);
        Pageable requested = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "startTime"));

        RoomRequestItem item = RoomRequestItem.builder().id(5L).build();
        RoomRequestItemRowDto row = new RoomRequestItemRowDto(
                5L,     // itemId
                null,   // status
                null,   // decidedAt
                null,   // request
                null,   // commission
                null,   // date
                null,   // startTime
                null,   // endTime
                null);  // classroomCount

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(itemRepository.findAll(ArgumentMatchers.<Specification<RoomRequestItem>>any(), pageableCaptor.capture()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(item), pageableCaptor.getValue(), 1));
        when(composer.composeRows(List.of(item))).thenReturn(List.of(row));

        Page<RoomRequestItemRowDto> result = service.findItems(filter, requested);

        assertThat(pageableCaptor.getValue().getSort().toList()).containsExactly(
                Sort.Order.desc("startTime"),
                Sort.Order.asc("id"));
        assertThat(result.getContent()).containsExactly(row);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("sort fuera de la whitelist: 400 antes de tocar el repositorio")
    void invalidSortNeverReachesRepository() {
        RoomRequestItemFilter filter = RoomRequestItemFilter.of(null, null, null, null, null, null, true);
        Pageable requested = PageRequest.of(0, 20, Sort.by("teacherEmail"));

        assertThatThrownBy(() -> service.findItems(filter, requested))
                .isInstanceOf(InvalidRoomRequestException.class);

        verifyNoInteractions(itemRepository, composer);
    }

    @Test
    @DisplayName("findItemById: delega en el repositorio y en el composer")
    void findItemByIdDelegatesToRepositoryAndComposer() {
        RoomRequestItem item = RoomRequestItem.builder().id(5L).build();
        RoomRequestItemDetailDto detail = new RoomRequestItemDetailDto(null, null);
        when(itemRepository.findWithRequestById(5L)).thenReturn(Optional.of(item));
        when(composer.composeDetail(item)).thenReturn(detail);

        RoomRequestItemDetailDto result = service.findItemById(5L);

        assertThat(result).isSameAs(detail);
    }

    @Test
    @DisplayName("findItemById: id inexistente lanza ResourceNotFoundException sin llamar al composer")
    void findItemByIdNotFoundThrows() {
        when(itemRepository.findWithRequestById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findItemById(999L))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(composer);
    }
}
