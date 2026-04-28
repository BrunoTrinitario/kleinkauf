/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.decor.users.decorusers.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.decor.users.decorusers.constants.ErrorMessages;
import com.decor.users.decorusers.constants.RolesType;
import com.decor.users.decorusers.dtos.FullUserDto;
import com.decor.users.decorusers.dtos.LoggedUser;
import com.decor.users.decorusers.entities.Role;
import com.decor.users.decorusers.entities.User;
import com.decor.users.decorusers.exceptions.ApiException;
import com.decor.users.decorusers.repositories.RoleRepository;
import com.decor.users.decorusers.repositories.UserRepository;
import com.decor.users.decorusers.util.JwtUtil;
import com.decor.users.decorusers.util.Parser;
import com.decor.users.decorusers.util.RoleUtility;

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
    private JwtUtil jwtUtil;

    public ArrayList<FullUserDto> getAllUsers(){
        Collection<User> rawUsers = this.userRepository.findAll();
        ArrayList<FullUserDto> userList = new ArrayList<>();
        rawUsers.forEach(u -> {
            userList.add(
                Parser.fromUserEntityToFullDto(u)
            );
        });
        return userList;
    }

    public FullUserDto getUserById(Long id) throws ApiException {
        User rawUser = this.userRepository.findById(id).orElse(null);
        if (rawUser != null){
            return Parser.fromUserEntityToFullDto(rawUser);
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
        Role newRole = new Role();
        newRole.setType(RolesType.CLIENTE.name());
        newRole.setUser(newUser);
        userRoles.add(newRole);
        roleRepository.saveAll(userRoles);
        newUser.setRoles(userRoles);
        newUser = this.userRepository.save(newUser);
        return Parser.fromUserEntityToFullDto(newUser);
    }

    public boolean userExistsByEmail(String email){
        return this.userRepository.existsByEmail(email);
    }

    public boolean userHasRole(Long id, String role) throws ApiException {
        User rawUser = this.userRepository.findById(id).orElse(null);
        if (rawUser != null){
            return rawUser.getRolesAsString().contains(role);
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
            return Parser.fromUserEntityToFullDto(user);
        }

        Role newRole = new Role();
        newRole.setType(role);
        newRole.setUser(user);
        user.getRoles().add(newRole);
        return Parser.fromUserEntityToFullDto(user);
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
            return Parser.fromUserEntityToFullDto(this.userRepository.save(rawUser));
        }else{
            throw new ApiException(ErrorMessages.USER_NOT_FOUND,HttpStatus.NOT_FOUND);
        }
    }

    public LoggedUser login(String name, String password) throws ApiException {
        User rawUser = this.userRepository.findByName(name).orElse(null);
        if (rawUser != null && passwordEncoder.matches(password, rawUser.getPassword())){
            String token = jwtUtil.generateAccessToken(rawUser.getId(), rawUser.getEmail(), rawUser.getName());
            return new LoggedUser(rawUser.getName(), rawUser.getId(), rawUser.getEmail(), token);
        }else{
            throw new ApiException(ErrorMessages.INVALID_CREDENTIALS,HttpStatus.UNAUTHORIZED);
        }
    }

}
