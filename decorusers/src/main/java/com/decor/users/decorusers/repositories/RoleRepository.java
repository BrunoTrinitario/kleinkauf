/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.decor.users.decorusers.repositories;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.decor.users.decorusers.entities.Role;

/**
 *
 * @author bruno.trinitario
 */
public interface RoleRepository extends JpaRepository<Role, Long>{
    Optional<Role> findByUser_IdAndType(Long userId, String type);
    Collection<Role> findByUser_Id(Long userId);

    @Query("SELECT r FROM Role r WHERE r.user.id = :userId AND r.type = :type")
    Optional<Role> existDeletedRole(@Param("userId") Long userId, @Param("type") String type);
}
