package ar.edu.utn.frc.siga.buildingactivity.service.impl;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.buildingactivity.dto.response.BuildingActivityDto;
import ar.edu.utn.frc.siga.buildingactivity.model.ActivityLine;
import ar.edu.utn.frc.siga.buildingactivity.model.BuildingActivity;
import ar.edu.utn.frc.siga.buildingactivity.model.BuildingActivitySeen;
import ar.edu.utn.frc.siga.buildingactivity.repository.BuildingActivityRepository;
import ar.edu.utn.frc.siga.buildingactivity.repository.BuildingActivitySeenRepository;
import ar.edu.utn.frc.siga.buildingactivity.service.BuildingActivityService;
import ar.edu.utn.frc.siga.common.exception.InvalidSortPropertyException;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.BuildingScopedSpecifications;
import ar.edu.utn.frc.siga.common.search.LikePatterns;
import ar.edu.utn.frc.siga.common.security.Permission;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
class BuildingActivityServiceImpl implements BuildingActivityService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String LINE_COUNT = "lineCount";
    private static final Set<String> SORTABLE = new LinkedHashSet<>(List.of("occurredAt", "buildingName", "actor", LINE_COUNT));
    private static final TypeReference<List<ActivityLine>> LINES_TYPE = new TypeReference<>() {};

    private final BuildingActivityRepository repository;
    private final BuildingActivitySeenRepository seenRepository;
    private final BuildingScopeResolver buildingScopeResolver;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<BuildingActivityDto> find(String user, Long buildingId, LocalDate from, LocalDate to,
                                          AllocationSource source, Boolean seen, String actor, Pageable pageable) {
        Specification<BuildingActivity> ordering = orderedBy(pageable.getSort());
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
        long watermark = watermark(user);
        if (source != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("source"), source));
        }
        if (seen != null) {
            spec = spec.and((root, query, cb) -> {
                Predicate isSeen = cb.or(cb.lessThanOrEqualTo(root.<Long>get("id"), watermark),
                        cb.equal(root.get("actor"), user));
                return seen ? isSeen : cb.not(isSeen);
            });
        }
        if (actor != null && !actor.isBlank()) {
            String pattern = LikePatterns.containing(actor.trim().toLowerCase());
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.<String>get("actor")), pattern, '\\'));
        }
        Pageable page = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
        spec = spec.and(ordering);
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

    private static Specification<BuildingActivity> orderedBy(Sort sort) {
        List<Sort.Order> requested = sort.isSorted() ? sort.toList() : List.of(Sort.Order.desc("occurredAt"));
        requested.forEach(order -> {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new InvalidSortPropertyException(order.getProperty(), SORTABLE);
            }
        });
        return (root, query, cb) -> {
            if (!Long.class.equals(query.getResultType())) {
                List<Order> orders = new ArrayList<>();
                for (Sort.Order order : requested) {
                    Expression<?> expression = LINE_COUNT.equals(order.getProperty())
                            ? cb.function("jsonb_array_length", Integer.class, root.get("lines"))
                            : root.get(order.getProperty());
                    orders.add(order.isAscending() ? cb.asc(expression) : cb.desc(expression));
                }
                orders.add(cb.desc(root.get("id")));
                query.orderBy(orders);
            }
            return null;
        };
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
