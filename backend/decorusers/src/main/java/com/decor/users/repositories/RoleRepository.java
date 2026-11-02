/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.decor.users.repositories;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.decor.users.entities.Role;

/**
 *
 * @author bruno.trinitario
 */
public interface RoleRepository extends JpaRepository<Role, Long>{
    Optional<Role> findByUser_IdAndType(Long userId, String type);
    Collection<Role> findByUser_Id(Long userId);

    // Nativa a proposito: el @SQLRestriction de Role ("deleted_at IS NULL") tambien filtra las consultas JPQL,
    // y aca hay que encontrar el rol aunque este borrado (soft delete) para reactivarlo
    @Query(value = "SELECT * FROM roles WHERE userid = :userId AND type = :type", nativeQuery = true)
    Optional<Role> existDeletedRole(@Param("userId") Long userId, @Param("type") String type);
}
