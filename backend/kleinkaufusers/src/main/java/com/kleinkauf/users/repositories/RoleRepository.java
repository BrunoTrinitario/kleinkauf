/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.kleinkauf.users.repositories;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kleinkauf.users.entities.Role;

/**
 *
 * @author bruno.trinitario
 */
public interface RoleRepository extends JpaRepository<Role, Long>{
    Optional<Role> findByUser_IdAndType(Long userId, String type);
    Collection<Role> findByUser_Id(Long userId);
    // Roles de varios usuarios en una sola consulta (listado paginado)
    List<Role> findByUser_IdIn(Collection<Long> userIds);

    // Nativa a proposito: el @SQLRestriction de Role ("deleted_at IS NULL") tambien filtra las consultas JPQL,
    // y aca hay que encontrar el rol aunque este borrado (soft delete) para reactivarlo
    @Query(value = "SELECT * FROM roles WHERE userid = :userId AND type = :type", nativeQuery = true)
    Optional<Role> existDeletedRole(@Param("userId") Long userId, @Param("type") String type);

    // [tipo, usuarios] de los usuarios no borrados (panel de la tienda). Un usuario con varios roles cuenta en cada uno
    @Query("SELECT r.type, COUNT(DISTINCT u.id) FROM Role r JOIN r.user u WHERE u.deletedAt IS NULL GROUP BY r.type")
    List<Object[]> countUsersByType();

    // Usuarios no borrados con ese rol (ej. que la tienda no se quede sin ADMINISTRADOR)
    @Query("SELECT COUNT(DISTINCT u.id) FROM Role r JOIN r.user u WHERE u.deletedAt IS NULL AND r.type = :type")
    long countActiveUsersWithType(@Param("type") String type);
}
