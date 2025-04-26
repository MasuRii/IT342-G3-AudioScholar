package edu.cit.audioscholar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class AudioscholarApplication {

	public static void main(String[] args) {
		SpringApplication.run(AudioscholarApplication.class, args);
	}

}
