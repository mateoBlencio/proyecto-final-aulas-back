package ar.edu.utn.frc.siga.auth.config;

import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoleCatalogSeeder implements ApplicationRunner {

    private final RoleRepository repository;

    @Override
    @Transactional
    public void run(@NonNull ApplicationArguments args) {
        int seeded = 0;
        for (SystemRole systemRole : SystemRole.values()) {
            if (!repository.existsByName(systemRole.name())) {
                repository.save(Role.builder()
                        .name(systemRole.name())
                        .systemRole(true)
                        .permissions(systemRole.defaultPermissions())
                        .build());
                seeded++;
            }
        }
        log.info("Seed de roles de sistema completado: {} roles sembrados, {} ya existentes",
                seeded, SystemRole.values().length - seeded);
    }
}
