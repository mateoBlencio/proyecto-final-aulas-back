package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.service.SpecialtyService;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.CommissionOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.CommissionScheduleDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.CursadoSlotDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.SpecialtyOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.SubjectOptionDto;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestCatalogMapper;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestCatalogService;
import ar.edu.utn.frc.siga.roomrequest.validator.CursadoScheduleService;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomRequestCatalogServiceImpl implements RoomRequestCatalogService {

    private final SpecialtyService specialtyService;
    private final SubjectService subjectService;
    private final SubjectCommissionService subjectCommissionService;
    private final ClassroomService classroomService;
    private final CursadoScheduleService cursadoScheduleService;
    private final RoomRequestCatalogMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<SpecialtyOptionDto> findSpecialties() {
        return mapper.toSpecialtyOptions(specialtyService.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectOptionDto> findSubjectsBySpecialty(Integer specialtyCode) {
        return mapper.toSubjectOptions(subjectService.findBySpecialtyCode(specialtyCode));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommissionOptionDto> findCommissionsBySubject(Long subjectId) {
        subjectService.findById(subjectId);
        return subjectCommissionService.findBySubjectId(subjectId).stream()
                .map(SubjectCommissionResponseDto::commission)
                .map(mapper::toOption)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassroomOptionDto> findClassrooms() {
        return mapper.toClassroomOptions(classroomService.findAllAvailable());
    }

    @Override
    @Transactional(readOnly = true)
    public CommissionScheduleDto findCommissionSchedule(Long subjectId, Long commissionId) {
        subjectService.findById(subjectId);
        subjectCommissionService.findBySubjectAndCommission(subjectId, commissionId);

        List<CursadoSlotDto> slots = cursadoScheduleService.slots(subjectId, commissionId).stream()
                .map(slot -> new CursadoSlotDto(slot.recurringEventId(), slot.dayOfWeek(),
                        slot.startTime(), slot.endTime()))
                .toList();
        return new CommissionScheduleDto(slots,
                cursadoScheduleService.cursadoDates(subjectId, commissionId, LocalDate.now()));
    }
}
