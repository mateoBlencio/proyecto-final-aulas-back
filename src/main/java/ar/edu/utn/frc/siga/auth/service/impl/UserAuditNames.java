package ar.edu.utn.frc.siga.auth.service.impl;

import ar.edu.utn.frc.siga.audit.AuditUserNames;
import ar.edu.utn.frc.siga.auth.config.AuthDomainProperties;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
class UserAuditNames implements AuditUserNames {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> fullNames(Collection<String> emails) {
        Set<String> normalized = emails.stream().map(AuthDomainProperties::normalizeEmail).collect(Collectors.toSet());
        Map<String, String> namesByNormalized = new HashMap<>();
        for (User user : userRepository.findByEmailIn(normalized)) {
            String fullName = Stream.of(user.getFirstName(), user.getLastName())
                    .filter(part -> part != null && !part.isBlank())
                    .reduce((first, last) -> first + " " + last)
                    .orElse(null);
            if (fullName != null) {
                namesByNormalized.put(AuthDomainProperties.normalizeEmail(user.getEmail()), fullName);
            }
        }
        // AuditRegistryServiceImpl busca el nombre con el correo tal como lo guardó la revisión.
        Map<String, String> names = new HashMap<>();
        for (String email : emails) {
            String fullName = namesByNormalized.get(AuthDomainProperties.normalizeEmail(email));
            if (fullName != null) {
                names.put(email, fullName);
            }
        }
        return names;
    }
}
