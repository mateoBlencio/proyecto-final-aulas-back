package ar.edu.utn.frc.siga.buildingactivity.repository;

import ar.edu.utn.frc.siga.buildingactivity.model.BuildingActivitySeen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BuildingActivitySeenRepository extends JpaRepository<BuildingActivitySeen, String> {

    /** La marca de agua nunca retrocede: un POST con un id menor al guardado no la baja. */
    @Modifying
    @Query(value = "INSERT INTO actividad_edificio_visto (usuario, id_ultima_actividad) VALUES (:user, :lastSeenId) "
            + "ON CONFLICT (usuario) DO UPDATE SET id_ultima_actividad = "
            + "GREATEST(actividad_edificio_visto.id_ultima_actividad, EXCLUDED.id_ultima_actividad)",
            nativeQuery = true)
    void advance(@Param("user") String user, @Param("lastSeenId") Long lastSeenId);
}
