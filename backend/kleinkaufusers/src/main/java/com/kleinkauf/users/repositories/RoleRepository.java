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
    // Roles of several users in a single query (paginated listing)
    List<Role> findByUser_IdIn(Collection<Long> userIds);

    // Native on purpose: Role's @SQLRestriction ("deleted_at IS NULL") also filters JPQL queries,
    // and here the role has to be found even if it is deleted (soft delete) to reactivate it
    @Query(value = "SELECT * FROM roles WHERE userid = :userId AND type = :type", nativeQuery = true)
    Optional<Role> existDeletedRole(@Param("userId") Long userId, @Param("type") String type);

    // [type, users] of the non-deleted users (store dashboard). A user with several roles counts in each one
    @Query("SELECT r.type, COUNT(DISTINCT u.id) FROM Role r JOIN r.user u WHERE u.deletedAt IS NULL GROUP BY r.type")
    List<Object[]> countUsersByType();

    // Non-deleted users with that role (e.g. so the store is not left without an ADMINISTRADOR)
    @Query("SELECT COUNT(DISTINCT u.id) FROM Role r JOIN r.user u WHERE u.deletedAt IS NULL AND r.type = :type")
    long countActiveUsersWithType(@Param("type") String type);
}
