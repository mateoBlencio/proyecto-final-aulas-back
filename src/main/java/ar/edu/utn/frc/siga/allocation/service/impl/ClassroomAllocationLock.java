package ar.edu.utn.frc.siga.allocation.service.impl;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.TreeSet;

@Component
@RequiredArgsConstructor
class ClassroomAllocationLock {

    private final EntityManager entityManager;

    // MANDATORY: sin transacción en curso el advisory lock se liberaría apenas termina la sentencia,
    // en vez de mantenerse hasta el commit del llamador.
    @Transactional(propagation = Propagation.MANDATORY)
    void lock(Collection<Long> classroomIds) {
        // Orden ascendente para que dos lotes con aulas cruzadas no se esperen en sentidos opuestos.
        for (Long classroomId : new TreeSet<>(classroomIds)) {
            entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:key)")
                    .setParameter("key", classroomId)
                    .getSingleResult();
        }
    }
}
