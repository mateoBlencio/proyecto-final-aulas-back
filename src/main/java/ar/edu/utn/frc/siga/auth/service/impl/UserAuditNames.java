package ar.edu.utn.frc.siga.auth.service.impl;

import ar.edu.utn.frc.siga.audit.AuditUserNames;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
class UserAuditNames implements AuditUserNames {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> fullNames(Collection<String> emails) {
        Map<String, String> names = new HashMap<>();
        for (User user : userRepository.findByEmailIn(emails)) {
            String fullName = Stream.of(user.getFirstName(), user.getLastName())
                    .filter(part -> part != null && !part.isBlank())
                    .reduce((first, last) -> first + " " + last)
                    .orElse(null);
            if (fullName != null) {
                names.put(user.getEmail(), fullName);
            }
        }
        return names;
    }
}
