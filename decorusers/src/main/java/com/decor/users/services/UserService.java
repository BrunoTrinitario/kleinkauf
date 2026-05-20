/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.decor.users.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import com.decor.users.config.TenantContext;
import com.decor.users.dtos.RoleDto;
import com.decor.users.dtos.stats.UserStatsDto;
import com.decor.users.util.CacheUtil;
import com.decor.users.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.decor.users.constants.ErrorMessages;
import com.decor.users.dtos.FullUserDto;
import com.decor.users.dtos.LoggedUser;
import com.decor.users.entities.Role;
import com.decor.users.entities.User;
import com.decor.users.exceptions.ApiException;
import com.decor.users.repositories.RoleRepository;
import com.decor.users.repositories.UserRepository;
import com.decor.users.util.Parser;
import com.decor.users.util.RoleUtility;

import jakarta.transaction.Transactional;


/**
 *
 * @author bruno.trinitario
 */
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private CacheUtil cacheUtil;
    @Autowired
    private JwtUtil  jwtUtil;

    public ArrayList<FullUserDto> getAllUsers(){
        Collection<User> rawUsers = this.userRepository.findAll();
        ArrayList<FullUserDto> userList = new ArrayList<>();
        rawUsers.forEach(u -> {
            userList.add(
                Parser.fromUserEntityToFullDto(u,this.getUserRoles(u.getId()))
            );
        });
        return userList;
    }


    public FullUserDto getUserById(Long id) throws ApiException {
        User rawUser = this.userRepository.findById(id).orElse(null);
        if (rawUser != null){
            return Parser.fromUserEntityToFullDto(rawUser,this.getUserRoles(rawUser.getId()));
        }else{
            throw new ApiException(ErrorMessages.USER_NOT_FOUND,HttpStatus.NOT_FOUND);
        }
    }

    @Transactional
    public boolean deleteUserById(Long id) throws ApiException {
        User rawUser = this.userRepository.findById(id).orElse(null);
        if (rawUser != null){
            for (Role r: rawUser.getRoles()){
                this.roleRepository.delete(r);
            }
            this.userRepository.deleteById(id);
            cacheUtil.deleteUser(id);
            return true;
        }else{
            throw new ApiException(ErrorMessages.USER_NOT_FOUND,HttpStatus.NOT_FOUND);
        }
    }

    public FullUserDto createUser(String name, String email, String password, Set<String> roles) throws ApiException {
        if (userExistsByEmail(email)){
            throw new ApiException(ErrorMessages.EMAIL_ALREADY_IN_USE,HttpStatus.BAD_REQUEST);
        }
        password = passwordEncoder.encode(password);
        User newUser = new User();
        newUser.setName(name);
        newUser.setEmail(email);
        newUser.setPassword(password);
        newUser = this.userRepository.save(newUser);
        Set<Role> userRoles = new HashSet<>();
        for (String rol: roles){
            if (RoleUtility.existsRole(rol)){
                Role newRole = new Role();
                newRole.setType(rol);
                newRole.setUser(newUser);
                userRoles.add(newRole);
            }
        }
        if (!userRoles.isEmpty()){
            roleRepository.saveAll(userRoles);
            newUser.setRoles(userRoles);
            newUser = this.userRepository.save(newUser);
        }
        cacheUtil.saveOrUpdateUserCache(newUser.getId(), TenantContext.getCurrentTenant(), this.getUserRoles(newUser.getId()));
        return Parser.fromUserEntityToFullDto(newUser,this.getUserRoles(newUser.getId()));
    }

    public boolean userExistsByEmail(String email){
        return this.userRepository.existsByEmail(email);
    }

    public boolean userHasRole(Long id, String role) throws ApiException {
        User rawUser = this.userRepository.findById(id).orElse(null);
        if (rawUser != null){
            return this.getUserRoles(rawUser.getId()).stream().anyMatch(r -> r.getType().equals(role));
        }else{
            throw new ApiException(ErrorMessages.USER_NOT_FOUND,HttpStatus.NOT_FOUND);
        }
    }

    @Transactional
    public FullUserDto addRoleToUser(Long id, String role) throws ApiException {
        if (!RoleUtility.existsRole(role)){
            throw new ApiException(ErrorMessages.INVALID_ROLE,HttpStatus.NOT_FOUND);
        }
        User user = userRepository.findById(id).orElseThrow(() -> new ApiException(ErrorMessages.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
        Role roleEntity = roleRepository.existDeletedRole(id, role).orElse(null);

        if (roleEntity != null) {
            if (roleEntity.getDeletedAt() != null)
                roleEntity.setDeletedAt(null);
            return Parser.fromUserEntityToFullDto(user,this.getUserRoles(user.getId()));
        }

        Role newRole = new Role();
        newRole.setType(role);
        newRole.setUser(user);
        user.getRoles().add(newRole);
        cacheUtil.saveOrUpdateUserCache(id, TenantContext.getCurrentTenant(), this.getUserRoles(id));
        return Parser.fromUserEntityToFullDto(user,this.getUserRoles(user.getId()));
    }

    public boolean deleteRoleFromUser(Long id, String role) throws ApiException {
        if (!RoleUtility.existsRole(role)){
            throw new ApiException(ErrorMessages.INVALID_ROLE,HttpStatus.NOT_FOUND);
        }
        User rawUser = this.userRepository.findById(id).orElse(null);
        if (rawUser != null && userHasRole(id, role)){
            Role roleToDelete = this.roleRepository.findByUser_IdAndType(id, role).orElse(null);
            if (roleToDelete != null){
                rawUser.getRoles().remove(roleToDelete);
                roleRepository.delete(roleToDelete);
                cacheUtil.saveOrUpdateUserCache(id,TenantContext.getCurrentTenant(), this.getUserRoles(id));
                return true;
            }else{
                throw new ApiException(ErrorMessages.USER_NOT_FOUND,HttpStatus.NOT_FOUND);
            }
        }else{
            throw new ApiException(ErrorMessages.USER_NOT_FOUND,HttpStatus.NOT_FOUND);
        }
    }

    public FullUserDto updateUser(Long id, String name, String password) throws ApiException {
        User rawUser = this.userRepository.findById(id).orElse(null);
        if (rawUser != null){
            if (name != null) rawUser.setName(name);
            if (password != null ) rawUser.setPassword(passwordEncoder.encode(password));
            rawUser = this.userRepository.save(rawUser);
            return Parser.fromUserEntityToFullDto(rawUser,this.getUserRoles(rawUser.getId()));
        }else{
            throw new ApiException(ErrorMessages.USER_NOT_FOUND,HttpStatus.NOT_FOUND);
        }
    }

    public LoggedUser login(String email, String password) throws ApiException {
        User rawUser = this.userRepository.findByEmail(email).orElse(null);
        if (rawUser != null && passwordEncoder.matches(password, rawUser.getPassword())){
            String token = jwtUtil.generateAccessToken(rawUser.getId(),rawUser.getEmail(),rawUser.getName());
            List<RoleDto> roleList = this.getUserRoles(rawUser.getId());
            rawUser.setLastLogin(LocalDateTime.now());
            userRepository.saveAndFlush(rawUser);
            cacheUtil.saveOrUpdateUserCache(rawUser.getId(), TenantContext.getCurrentTenant(),roleList);
            return new LoggedUser(rawUser.getName(), rawUser.getId(), rawUser.getEmail(), token, roleList);
        }else{
            throw new ApiException(ErrorMessages.INVALID_CREDENTIALS,HttpStatus.UNAUTHORIZED);
        }
    }

    public List<RoleDto> getUserRoles(Long id){
        Collection<Role> roleList = this.roleRepository.findByUser_Id(id);

        List<RoleDto> list = new ArrayList<>();
        for (Role role : roleList){
            list.add(new RoleDto(
                    role.getType(),
                    role.getPermission()
            ));
        }
        cacheUtil.saveOrUpdateUserCache(id, TenantContext.getCurrentTenant(),list);
        return list;
    }

    public UserStatsDto getServiceStats(LocalDate startDate, LocalDate endDate){
        /* TODO:
            Usuarios registrados
            Usuarios activos (DAU/MAU)
            Nuevos usuarios por período
         */
        List<User> allUsers = userRepository.findByCreatedAtBetween(startDate, endDate);

        LocalDateTime now = LocalDateTime.now();

        int activeUsers = (int) allUsers.stream()
                .map(User::getLastLogin)
                .filter(Objects::nonNull)
                .filter(lastLogin -> lastLogin.isAfter(now.minusHours(1)))
                .count();

        Map<String, Integer> periods = allUsers.stream()
                .collect(Collectors.groupingBy(
                        user -> {
                            long weeks = ChronoUnit.WEEKS.between(
                                    startDate,
                                    user.getCreatedAt().toLocalDate()
                            );
                            return "week_" + (weeks + 1);
                        },
                        LinkedHashMap::new,
                        Collectors.summingInt(user -> 1)
                ));

        UserStatsDto dto = new UserStatsDto();
        dto.setUsersTotal(allUsers.size());
        dto.setUsersActive(activeUsers);
        dto.setPeriodRegistration(new HashMap<>(periods));

        return dto;
    }

}
