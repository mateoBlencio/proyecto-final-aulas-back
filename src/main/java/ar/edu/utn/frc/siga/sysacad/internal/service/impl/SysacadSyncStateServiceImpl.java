package ar.edu.utn.frc.siga.sysacad.internal.service.impl;

import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSyncState;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadSyncStateRepository;

import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadSyncStateServiceImpl implements SysacadSyncStateService {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final SysacadSyncStateRepository repository;

    @PostConstruct
    void seedStates() {
        for (SysacadView view : SysacadView.values()) {
            if (repository.findByView(view).isEmpty()) {
                repository.save(SysacadSyncState.builder().view(view).build());
            }
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(SysacadView view, int rowsAffected) {
        SysacadSyncState state = findOrCreate(view);
        state.setLastSuccessAt(Instant.now());
        state.setLastRowsAffected(rowsAffected);
        state.setLastError(null);
        state.setLastErrorAt(null);
        repository.save(state);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(SysacadView view, String errorMessage) {
        SysacadSyncState state = findOrCreate(view);
        state.setLastError(truncate(errorMessage));
        state.setLastErrorAt(Instant.now());
        repository.save(state);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureExists(SysacadView view) {
        if (repository.findByView(view).isEmpty()) {
            repository.save(SysacadSyncState.builder().view(view).build());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SysacadSyncStateDto> findAll() {
        return repository.findAll().stream()
                .map(state -> new SysacadSyncStateDto(
                        state.getView(),
                        state.getLastSuccessAt(),
                        state.getLastRowsAffected(),
                        state.getLastError(),
                        state.getLastErrorAt(),
                        state.getUpdatedAt()))
                .toList();
    }

    private SysacadSyncState findOrCreate(SysacadView view) {
        return repository.findByView(view)
                .orElseGet(() -> SysacadSyncState.builder().view(view).build());
    }

    private static String truncate(String errorMessage) {
        if (errorMessage == null || errorMessage.length() <= MAX_ERROR_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_LENGTH);
    }
}
