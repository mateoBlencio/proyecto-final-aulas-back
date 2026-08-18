package ar.edu.utn.frc.siga.settings.repository;

import ar.edu.utn.frc.siga.settings.model.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<Setting, String> {
}
