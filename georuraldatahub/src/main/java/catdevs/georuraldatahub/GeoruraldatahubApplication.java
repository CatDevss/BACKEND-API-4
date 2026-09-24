package catdevs.georuraldatahub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GeoruraldatahubApplication {

	public static void main(String[] args) {
		SpringApplication.run(GeoruraldatahubApplication.class, args);
	}

}
