package ar.edu.utn.frc.siga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

import java.util.TimeZone;

@Modulithic(systemName = "SIGA", sharedModules = "common")
@SpringBootApplication
public class SigaApplication {

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
		SpringApplication.run(SigaApplication.class, args);
	}

}
