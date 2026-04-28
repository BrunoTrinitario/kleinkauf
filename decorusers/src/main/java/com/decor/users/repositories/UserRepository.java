/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */

package com.decor.users.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.decor.users.entities.User;

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

   boolean existsByEmail(String email);

}
