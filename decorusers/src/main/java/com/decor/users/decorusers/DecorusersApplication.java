package com.decor.users.decorusers;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.decor.users.decorusers.services.UserService;

@SpringBootApplication
public class DecorusersApplication {

	public static void main(String[] args) {
		SpringApplication.run(DecorusersApplication.class, args);
	}

    @Bean
    CommandLineRunner init(UserService userService) {
        return args -> {
            
			try{
			} catch (Exception e) {
				e.printStackTrace();
			}
        };
    }

}
