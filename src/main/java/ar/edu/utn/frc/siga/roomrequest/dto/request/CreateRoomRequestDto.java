package ar.edu.utn.frc.siga.roomrequest.dto.request;

import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateRoomRequestDto(
        @NotNull RoomRequestType type,
        @NotNull AcademicScope scope,
        @NotBlank @Size(max = 150) String teacherName,
        @NotBlank @Email @Size(max = 150) String teacherEmail,
        @NotBlank @Size(max = 40) String teacherPhone,
        Long subjectId,
        @NotEmpty @Valid List<CreateRoomRequestItemDto> items
) {}
