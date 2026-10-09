package ar.edu.utn.frc.siga.auth.service.impl;

import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.auth.model.RoleAssignment;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository.UserEmail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Label of a role assignment: "SUBSECRETARIA de ana@frc.utn.edu.ar". Without the user, only the role. */
@Component
@RequiredArgsConstructor
class RoleAssignmentAuditLabelProvider implements AuditLabelProvider {

    private final UserRepository userRepository;

    @Override
    public Class<?> entityType() {
        return RoleAssignment.class;
    }

    @Override
    public Map<String, String> labels(List<AuditedRecord> records) {
        Set<Long> userIds = records.stream().map(record -> record.longValue("user"))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> emails = userIds.isEmpty() ? new HashMap<>()
                : userRepository.findEmailsByIdIn(userIds).stream()
                        .collect(Collectors.toMap(UserEmail::getId, UserEmail::getEmail));

        Map<String, String> labels = new HashMap<>();
        for (AuditedRecord record : records) {
            String role = record.text("role");
            if (role == null) {
                continue;
            }
            String email = emails.get(record.longValue("user"));
            labels.put(record.recordId(), email == null ? role : role + " de " + email);
        }
        return labels;
    }
}
