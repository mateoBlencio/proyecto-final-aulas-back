package ar.edu.utn.frc.siga.realtime.internal;

import ar.edu.utn.frc.siga.common.realtime.LiveChange;
import ar.edu.utn.frc.siga.common.realtime.LiveChangeMapper;
import ar.edu.utn.frc.siga.common.realtime.LiveChangeMapper.PreviousState;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostCommitDeleteEventListener;
import org.hibernate.event.spi.PostCommitInsertEventListener;
import org.hibernate.event.spi.PostCommitUpdateEventListener;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Escucha altas, modificaciones y bajas de las entidades que algún módulo mapeó a un aviso, recién después del
 * commit. Así cubre todos los caminos que escriben esas tablas (servicios, schedulers, sync de SysAcad, ingest) sin
 * que cada uno se acuerde de avisar. No ve los UPDATE/DELETE masivos por JPQL ni el SQL nativo: hoy ninguno toca
 * esas tablas.
 */
@Slf4j
@Component
class LiveHibernateListener implements PostCommitInsertEventListener, PostCommitUpdateEventListener,
        PostCommitDeleteEventListener {

    private final EntityManagerFactory entityManagerFactory;
    private final LiveDispatcher dispatcher;
    private final Map<Class<?>, LiveChangeMapper<?>> mappersByType = new HashMap<>();

    LiveHibernateListener(EntityManagerFactory entityManagerFactory, LiveDispatcher dispatcher,
                          List<LiveChangeMapper<?>> mappers) {
        this.entityManagerFactory = entityManagerFactory;
        this.dispatcher = dispatcher;
        mappers.forEach(mapper -> mappersByType.put(mapper.entityType(), mapper));
    }

    @PostConstruct
    void register() {
        EventListenerRegistry registry = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getServiceRegistry().requireService(EventListenerRegistry.class);
        registry.appendListeners(EventType.POST_COMMIT_INSERT, this);
        registry.appendListeners(EventType.POST_COMMIT_UPDATE, this);
        registry.appendListeners(EventType.POST_COMMIT_DELETE, this);
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return mapperFor(persister.getMappedClass()) != null;
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        publish(event.getEntity(), PreviousState.NONE);
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        Object[] oldState = event.getOldState();
        String[] properties = event.getPersister().getPropertyNames();
        publish(event.getEntity(), oldState == null ? null : property -> valueOf(properties, oldState, property));
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        publish(event.getEntity(), PreviousState.NONE);
    }

    @Override
    public void onPostInsertCommitFailed(PostInsertEvent event) {
    }

    @Override
    public void onPostUpdateCommitFailed(PostUpdateEvent event) {
    }

    @Override
    public void onPostDeleteCommitFailed(PostDeleteEvent event) {
    }

    /** Corre después de un commit exitoso: una falla acá no puede llegar al que hizo el cambio como un error. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void publish(Object entity, PreviousState previous) {
        LiveChangeMapper mapper = mapperFor(entity.getClass());
        if (mapper == null) return;
        try {
            LiveChange change = mapper.map(entity, previous);
            if (change != null) dispatcher.accept(change);
        } catch (RuntimeException e) {
            log.warn("No se pudo armar el aviso en tiempo real para {}", entity.getClass().getSimpleName(), e);
        }
    }

    private LiveChangeMapper<?> mapperFor(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            LiveChangeMapper<?> mapper = mappersByType.get(current);
            if (mapper != null) return mapper;
        }
        return null;
    }

    private static Object valueOf(String[] properties, Object[] state, String property) {
        for (int i = 0; i < properties.length; i++) {
            if (properties[i].equals(property)) return state[i];
        }
        return null;
    }
}
