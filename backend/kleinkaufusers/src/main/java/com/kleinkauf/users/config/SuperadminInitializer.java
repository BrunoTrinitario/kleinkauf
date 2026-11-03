package com.kleinkauf.users.config;

import com.kleinkauf.users.constants.RolesType;
import com.kleinkauf.users.entities.Role;
import com.kleinkauf.users.entities.User;
import com.kleinkauf.users.repositories.RoleRepository;
import com.kleinkauf.users.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/*
Crea el primer superadmin en el schema public al arrancar (al arrancar no hay tenant,
asi que los repositorios usan public). Solo si estan configurados email y contrasena
(variables SUPERADMIN_EMAIL y SUPERADMIN_PASSWORD) y ese usuario todavia no existe.
SUPERADMIN no se puede asignar con UserService (RoleUtility lo excluye), por eso se
usan los repositorios directamente.
 */
@Slf4j
@Component
public class SuperadminInitializer implements ApplicationRunner {

    @Value("${platform.superadmin.email:}")
    private String email;

    @Value("${platform.superadmin.password:}")
    private String password;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            log.info("Sin platform.superadmin.email/password: no se crea el superadmin");
            return;
        }
        if (userRepository.existsByEmail(email)) {
            return;
        }

        User superadmin = new User();
        superadmin.setName("Superadmin");
        superadmin.setEmail(email);
        superadmin.setPassword(passwordEncoder.encode(password));
        superadmin = userRepository.save(superadmin);

        Role role = new Role();
        role.setType(RolesType.SUPERADMIN.name());
        role.setUser(superadmin);
        roleRepository.save(role);
        log.info("Superadmin creado: {}", email);
    }
}
