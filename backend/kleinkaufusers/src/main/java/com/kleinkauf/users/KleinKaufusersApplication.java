package com.kleinkauf.users;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.kleinkauf.users.services.UserService;


@SpringBootApplication
public class KleinKaufusersApplication {

	public static void main(String[] args) {
		SpringApplication.run(KleinKaufusersApplication.class, args);
	}

    @Bean
    CommandLineRunner init(UserService userService) {
        return args -> {
//            boolean exist = userService.userExistsByEmail("admin@admin");
//            if (!exist){
//                Set<String> roles = new HashSet<>();
//                roles.add("EMPLEADO");
//                roles.add("ADMINISTRADOR");
//                roles.add("CLIENTE");
//                userService.createUser("admin", "admin@admin", "admin", roles);
//            }
        };
    }

}
