package com.decor.users.services;

import com.decor.users.constants.RolesType;
import com.decor.users.entities.Role;
import com.decor.users.entities.User;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;

/*
Crea al dueno de un tenant nuevo dentro de su schema.
La request de alta corre en el schema public y Hibernate fija el tenant al abrir la sesion,
por eso se abre una sesion propia con el schema del tenant nuevo. Al pedir la conexion,
TenantDataSource crea el schema y sus tablas.
 */
@Component
public class TenantOwnerCreator {

    private static final List<RolesType> OWNER_ROLES = List.of(RolesType.ADMINISTRADOR, RolesType.EMPLEADO);

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public void createOwner(String schema, String name, String email, String password) {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        try (Session session = sessionFactory.withOptions().tenantIdentifier((Object) schema).openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                User owner = new User();
                owner.setName(name);
                owner.setEmail(email);
                owner.setPassword(passwordEncoder.encode(password));
                owner.setRoles(new HashSet<>());
                session.persist(owner);

                for (RolesType type : OWNER_ROLES) {
                    Role role = new Role();
                    role.setType(type.name());
                    role.setUser(owner);
                    owner.getRoles().add(role);
                    session.persist(role);
                }
                tx.commit();
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }
}
