/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.decor.users.services;

import com.decor.common.constants.TenantConstants;
import com.decor.common.dto.RoleDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import com.decor.common.servlet.tenant.TenantContext;
import com.decor.users.constants.MailConstants;
import com.decor.users.dtos.*;
import com.decor.users.dtos.stats.UserStatsDto;
import com.decor.users.util.CacheUtil;
import com.decor.users.util.JwtUtil;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.decor.users.constants.ErrorMessages;
import com.decor.users.entities.Role;
import com.decor.users.entities.User;
import com.decor.common.servlet.exceptions.ApiException;
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
    @Autowired
    private MailService mailService;
    @Autowired
    private EurekaClient eurekaClient;


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
            CachedUser cu = new CachedUser(id, TenantContext.getCurrentTenant(), null);
            cacheUtil.deleteUser(cu);
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
        CachedUser cu = new CachedUser(newUser.getId(), TenantContext.getCurrentTenant(), this.getUserRoles(newUser.getId()));
        cacheUtil.saveOrUpdateUserCache(cu);
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
        CachedUser cu = new CachedUser(id, TenantContext.getCurrentTenant(),  this.getUserRoles(id));
        cacheUtil.saveOrUpdateUserCache(cu);
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
                CachedUser cu = new CachedUser(id, TenantContext.getCurrentTenant(),  this.getUserRoles(id));
                cacheUtil.saveOrUpdateUserCache(cu);
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
            String token = jwtUtil.generateAccessToken(rawUser.getId(),rawUser.getEmail(),rawUser.getName(), currentTenant());
            List<RoleDto> roleList = this.getUserRoles(rawUser.getId());
            rawUser.setLastLogin(LocalDateTime.now());
            userRepository.saveAndFlush(rawUser);
            CachedUser cu = new CachedUser(rawUser.getId(), TenantContext.getCurrentTenant(), this.getUserRoles(rawUser.getId()));
            cacheUtil.saveOrUpdateUserCache(cu);
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
        CachedUser cu = new CachedUser(id, TenantContext.getCurrentTenant(), list);
        cacheUtil.saveOrUpdateUserCache(cu);
        return list;
    }

    public UserStatsDto getServiceStats(LocalDateTime startDate, LocalDateTime endDate){
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

        Map<String, Integer> periods = new LinkedHashMap<>();

        if (!allUsers.isEmpty()) {

            LocalDate oldestDate = allUsers.stream()
                    .map(user -> user.getCreatedAt().toLocalDate())
                    .min(LocalDate::compareTo)
                    .orElse(startDate.toLocalDate());

            periods = allUsers.stream()
                    .collect(Collectors.groupingBy(
                            user -> {
                                long weeks = ChronoUnit.WEEKS.between(
                                        oldestDate,
                                        user.getCreatedAt().toLocalDate()
                                );
                                return "week_" + (weeks + 1);
                            },
                            LinkedHashMap::new,
                            Collectors.summingInt(user -> 1)
                    ));
        }

        UserStatsDto dto = new UserStatsDto();
        dto.setUsersTotal(allUsers.size());
        dto.setUsersActive(activeUsers);
        dto.setPeriodRegistration(new HashMap<>(periods));

        return dto;
    }

    public boolean forgotPasswordRequest(EmailDto userData) throws ApiException {
        User u = this.userRepository.findByEmail(userData.email()).orElse(null);
        if (u == null) return false;
        String jwt = jwtUtil.generatePasswordToken(u.getId(),u.getEmail(), currentTenant());

        InstanceInfo gateway = eurekaClient
                .getNextServerFromEureka("gateway-microservice", false);
        String url = gateway.getHomePageUrl()
                + "/api/v1/auth/forgot-password/"
                + jwt;

        try{
            mailService.sendMail(
                    u.getEmail(),
                    MailConstants.FORGOT_SUBJECT,
                    MailConstants.FORGOT_MAIL_CONTENT(url)
            );
        }catch (Exception e){
            //implementar estrategia de caida para mailer
        }
        return true;
    }

    // El token tiene que ser valido y del mismo tenant (los ids de usuario se repiten entre tenants)
    public FullUserDto refreshPassword(String jwt, String newPassword) throws ApiException {
        Long id = jwtUtil.getUserId(jwt);
        if (id == null || !currentTenant().equals(jwtUtil.getTenant(jwt))) {
            throw new ApiException(ErrorMessages.INVALID_TOKEN, HttpStatus.UNAUTHORIZED);
        }
        return this.updateUser(id, null, newPassword);
    }

    private String currentTenant() {
        String tenant = TenantContext.getCurrentTenant();
        return tenant != null ? tenant : TenantConstants.PUBLIC;
    }

}
