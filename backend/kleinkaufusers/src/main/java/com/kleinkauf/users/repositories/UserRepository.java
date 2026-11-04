/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */

package com.kleinkauf.users.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.kleinkauf.users.entities.User;

/**
 *
 * @author bruno.trinitario
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    /*  save(usuario)
        findById(id)
        findAll()
        deleteById(id)
        existsById(id)
    */
   Optional<User> findByName(String name);
   // IgnoreCase: los usuarios viejos pueden tener el email guardado con mayusculas
   Optional<User> findByEmailIgnoreCase(String email);
   boolean existsByEmailIgnoreCase(String email);

   // Panel de la tienda (UserDashboardService)
   long countByLastLoginGreaterThanEqual(LocalDateTime since);

   @Query("SELECT u.createdAt FROM User u WHERE u.createdAt >= :from AND u.createdAt < :to")
   List<LocalDateTime> findCreatedAtBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

}
