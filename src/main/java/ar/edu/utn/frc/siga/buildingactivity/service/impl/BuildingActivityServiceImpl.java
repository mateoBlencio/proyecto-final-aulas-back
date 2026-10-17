package ar.edu.utn.frc.siga.buildingactivity.service.impl;

import ar.edu.utn.frc.siga.buildingactivity.dto.response.BuildingActivityDto;
import ar.edu.utn.frc.siga.buildingactivity.model.ActivityLine;
import ar.edu.utn.frc.siga.buildingactivity.model.BuildingActivity;
import ar.edu.utn.frc.siga.buildingactivity.model.BuildingActivitySeen;
import ar.edu.utn.frc.siga.buildingactivity.repository.BuildingActivityRepository;
import ar.edu.utn.frc.siga.buildingactivity.repository.BuildingActivitySeenRepository;
import ar.edu.utn.frc.siga.buildingactivity.service.BuildingActivityService;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.BuildingScopedSpecifications;
import ar.edu.utn.frc.siga.common.security.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
class BuildingActivityServiceImpl implements BuildingActivityService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final TypeReference<List<ActivityLine>> LINES_TYPE = new TypeReference<>() {};

    private final BuildingActivityRepository repository;
    private final BuildingActivitySeenRepository seenRepository;
    private final BuildingScopeResolver buildingScopeResolver;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<BuildingActivityDto> find(String user, Long buildingId, LocalDate from, LocalDate to, Pageable pageable) {
        if (buildingId != null) {
            buildingScopeResolver.requireAccess(Permission.BUILDING_ACTIVITY_READ, buildingId);
        }
        Specification<BuildingActivity> spec = inScope();
        if (buildingId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("buildingId"), buildingId));
        }
        if (from != null) {
            Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), start));
        }
        if (to != null) {
            Instant end = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.<Instant>get("occurredAt"), end));
        }
        Pageable page = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "id"));
        long watermark = watermark(user);
        return repository.findAll(spec, page).map(activity -> toDto(activity, user, watermark));
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnseen(String user) {
        long watermark = watermark(user);
        return repository.count(inScope()
                .and((root, query, cb) -> cb.greaterThan(root.<Long>get("id"), watermark))
                .and((root, query, cb) -> cb.notEqual(root.get("actor"), user)));
    }

    @Override
    @Transactional
    public void markSeen(String user, Long lastSeenId) {
        seenRepository.advance(user, lastSeenId);
    }

    private Specification<BuildingActivity> inScope() {
        return BuildingScopedSpecifications.withinScope(
                buildingScopeResolver.scopeFor(Permission.BUILDING_ACTIVITY_READ), "buildingId");
    }

    private long watermark(String user) {
        return seenRepository.findById(user).map(BuildingActivitySeen::getLastSeenActivityId).orElse(0L);
    }

    private BuildingActivityDto toDto(BuildingActivity activity, String user, long watermark) {
        return new BuildingActivityDto(
                activity.getId(),
                new BuildingActivityDto.BuildingRef(activity.getBuildingId(), activity.getBuildingName()),
                activity.getActor(),
                activity.getSource(),
                activity.getReason(),
                activity.getOccurredAt(),
                activity.getId() <= watermark || activity.getActor().equals(user),
                objectMapper.readValue(activity.getLines(), LINES_TYPE));
    }
}
