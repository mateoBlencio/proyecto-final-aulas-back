package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.roomrequest.dto.RoomRequestItemFilter;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateFinalExamDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateOtherDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.FreeFormItemDto;
import ar.edu.utn.frc.siga.roomrequest.dto.request.RequesterInfo;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemRowDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemStatusCountDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestResponseDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.handler.RoomRequestHandlers;
import ar.edu.utn.frc.siga.roomrequest.handler.RoomRequestTypeHandler;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestComposer;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    private RoomRequestComposer composer;
    @Mock
    private RoomRequestHandlers handlers;

    private RoomRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoomRequestServiceImpl(repository, itemRepository, composer, handlers);
    }

    @Test
    @DisplayName("create: resuelve el handler del tipo, valida, arma, persiste y compone — en ese orden")
    void createDelegatesToHandler() {
        CreateRoomRequestDto dto = finalExamDto();
        RoomRequestTypeHandler handler = org.mockito.Mockito.mock(RoomRequestTypeHandler.class);
        RoomRequest assembled = RoomRequest.builder().type(RoomRequestType.FINAL_EXAM).build();
        RoomRequest saved = RoomRequest.builder().id(9L).type(RoomRequestType.FINAL_EXAM).build();
        RoomRequestResponseDto response = new RoomRequestResponseDto(
                9L, RoomRequestType.FINAL_EXAM, null, null, null, null, null, null, List.of());

        when(handlers.forType(RoomRequestType.FINAL_EXAM)).thenReturn(handler);
        when(handler.assemble(dto)).thenReturn(assembled);
        when(repository.save(assembled)).thenReturn(saved);
        when(composer.compose(saved)).thenReturn(response);

        RoomRequestResponseDto result = service.create(dto);

        assertThat(result).isSameAs(response);
        var order = inOrder(handler, repository, composer);
        order.verify(handler).validate(dto);
        order.verify(handler).assemble(dto);
        order.verify(repository).save(assembled);
        order.verify(composer).compose(saved);
    }

    @Test
    @DisplayName("create: si el handler rechaza la validación, no persiste ni compone")
    void createRejectedByHandlerDoesNotPersist() {
        CreateRoomRequestDto dto = otherDto();
        RoomRequestTypeHandler handler = org.mockito.Mockito.mock(RoomRequestTypeHandler.class);
        when(handlers.forType(RoomRequestType.OTHER)).thenReturn(handler);
        org.mockito.Mockito.doThrow(new InvalidRoomRequestException("mal armado")).when(handler).validate(dto);

        assertThatThrownBy(() -> service.create(dto)).isInstanceOf(InvalidRoomRequestException.class);

        verifyNoInteractions(repository, composer);
    }

    @Test
    @DisplayName("traduce el sort antes de consultar el repositorio y compone la página por batch")
    void translatesSortAndComposesPage() {
        RoomRequestItemFilter filter = RoomRequestItemFilter.of(null, null, null, null, null, null, true);
        Pageable requested = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "startTime"));

        RoomRequestItem item = RoomRequestItem.builder().id(5L).build();
        RoomRequestItemRowDto row = new RoomRequestItemRowDto(
                5L, null, null, null, null, null, null, null, null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(itemRepository.findAll(ArgumentMatchers.<Specification<RoomRequestItem>>any(), pageableCaptor.capture()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(item), pageableCaptor.getValue(), 1));
        when(composer.composeRows(List.of(item))).thenReturn(List.of(row));

        Page<RoomRequestItemRowDto> result = service.findItems(filter, requested);

        assertThat(pageableCaptor.getValue().getSort().toList())
                .extracting(Sort.Order::getProperty)
                .containsExactly("startTime", "id");
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
    @DisplayName("countItemsByStatus: un conteo por cada estado del enum, en orden, sin tocar el composer")
    void countItemsByStatusReturnsOneEntryPerStatus() {
        when(itemRepository.count(ArgumentMatchers.<Specification<RoomRequestItem>>any()))
                .thenReturn(30L, 10L, 0L);

        List<RoomRequestItemStatusCountDto> result = service.countItemsByStatus(true);

        assertThat(result).containsExactly(
                new RoomRequestItemStatusCountDto(RoomRequestStatus.PENDING, 30L),
                new RoomRequestItemStatusCountDto(RoomRequestStatus.PRE_APPROVED, 10L),
                new RoomRequestItemStatusCountDto(RoomRequestStatus.CANCELLED, 0L));
        verify(itemRepository, times(3)).count(ArgumentMatchers.<Specification<RoomRequestItem>>any());
        verifyNoInteractions(composer);
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

    private static RequesterInfo requester() {
        return new RequesterInfo(AcademicScope.GRADO, "Ada Lovelace", "ada@frc.utn.edu.ar", "351-1234567");
    }

    private static FreeFormItemDto freeFormItem() {
        return new FreeFormItemDto(null, java.time.LocalDate.now().plusDays(7),
                java.time.LocalTime.of(10, 0), java.time.LocalTime.of(12, 0), 30, 1,
                false, false, null, null, null, null, List.of());
    }

    private static CreateFinalExamDto finalExamDto() {
        return new CreateFinalExamDto(RoomRequestType.FINAL_EXAM, requester(), 1L, List.of(freeFormItem()));
    }

    private static CreateOtherDto otherDto() {
        return new CreateOtherDto(RoomRequestType.OTHER, requester(), null, List.of(freeFormItem()));
    }
}
