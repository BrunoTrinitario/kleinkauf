/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.decor.users.services;

import com.decor.common.constants.TenantConstants;
import com.decor.common.dto.RoleDto;

import java.time.LocalDateTime;
import java.util.*;

import com.decor.common.servlet.tenant.TenantContext;
import com.decor.users.constants.MailConstants;
import com.decor.users.dtos.*;
import com.decor.users.util.CacheUtil;
import com.decor.users.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.decor.users.constants.ErrorMessages;
import com.decor.users.constants.RolesType;
import com.decor.users.entities.Role;
import com.decor.users.entities.User;
import com.decor.common.servlet.exceptions.ApiException;
import com.decor.users.repositories.RoleRepository;
import com.decor.users.repositories.UserRepository;
import com.decor.users.repositories.UserSearchRepository;
import com.decor.users.util.Parser;
import com.decor.users.util.RoleUtility;
import com.decor.users.util.UserValidator;

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
    private UserSearchRepository userSearchRepository;
    @Autowired
    private CacheUtil cacheUtil;
    @Autowired
    private JwtUtil  jwtUtil;
    @Autowired
    private RolePermissionService rolePermissionService;
    @Autowired
    private MailService mailService;
    // La tabla de tenants esta en public: JPA solo ve el schema de la tienda de la request
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${platform.scheme:http}")
    private String platformScheme;

    @Value("${platform.domain:localhost:3000}")
    private String platformDomain;

    // Pagina del frontend que pide la contrasena nueva y llama a POST /api/v1/auth/forgot-password/{token}
    static final String RESET_PASSWORD_PATH = "/reset-password?token=";


    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    /*
    Listado paginado del panel, ordenado por nombre. query busca en nombre y email; role filtra por rol.
    Los roles de toda la pagina salen de una sola consulta y la mascara de cada tipo se calcula una vez.
     */
    public UserPageDto searchUsers(String query, String role, Integer page, Integer size) throws ApiException {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageNumber < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorMessages.INVALID_PAGE, HttpStatus.BAD_REQUEST);
        }
        String roleFilter = role == null || role.isBlank() ? null : role.trim().toUpperCase(Locale.ROOT);
        if (roleFilter != null && !RoleUtility.existsRole(roleFilter)) {
            throw new ApiException(ErrorMessages.INVALID_ROLE, HttpStatus.BAD_REQUEST);
        }

        UserSearchRepository.Filter filter = new UserSearchRepository.Filter(query, roleFilter);
        long total = userSearchRepository.count(filter);
        List<User> users = userSearchRepository.search(filter, pageNumber, pageSize);

        Map<Long, List<RoleDto>> rolesByUser = new HashMap<>();
        Map<String, Integer> masks = new HashMap<>();
        if (!users.isEmpty()) {
            List<Long> ids = users.stream().map(User::getId).toList();
            for (Role r : roleRepository.findByUser_IdIn(ids)) {
                int mask = masks.computeIfAbsent(r.getType(), rolePermissionService::maskFor);
                rolesByUser.computeIfAbsent(r.getUser().getId(), k -> new ArrayList<>())
                        .add(new RoleDto(r.getType(), mask));
            }
        }

        List<FullUserDto> items = users.stream()
                .map(u -> Parser.fromUserEntityToFullDto(u, rolesByUser.getOrDefault(u.getId(), List.of())))
                .toList();
        int totalPages = (int) ((total + pageSize - 1) / pageSize);
        return new UserPageDto(items, pageNumber, pageSize, total, totalPages);
    }


    public FullUserDto getUserById(Long id) throws ApiException {
        User rawUser = this.userRepository.findById(id).orElse(null);
        if (rawUser != null){
            return Parser.fromUserEntityToFullDto(rawUser,this.getUserRoles(rawUser.getId()));
        }else{
            throw new ApiException(ErrorMessages.USER_NOT_FOUND,HttpStatus.NOT_FOUND);
        }
    }

    // Una tienda sin ADMINISTRADOR no la puede administrar nadie (ni reasignar el rol): el ultimo no se borra
    // ni pierde el rol, lo haga el mismo o otro administrador. 409, como el ultimo superadmin
    private void ensureNotLastAdministrator(Long userId) throws ApiException {
        String admin = RolesType.ADMINISTRADOR.name();
        boolean isAdmin = this.roleRepository.findByUser_Id(userId).stream().anyMatch(r -> admin.equals(r.getType()));
        if (isAdmin && this.roleRepository.countActiveUsersWithType(admin) <= 1) {
            throw new ApiException(ErrorMessages.LAST_ADMINISTRATOR, HttpStatus.CONFLICT);
        }
    }

    @Transactional
    public boolean deleteUserById(Long id) throws ApiException {
        User rawUser = this.userRepository.findById(id).orElse(null);
        if (rawUser != null){
            ensureNotLastAdministrator(id);
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
        UserValidator.validateNewUser(name, email, password);
        name = name.trim();
        email = email.trim();
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

        // Si el usuario ya tuvo este rol, se reactiva: el unique (userId, type) no deja insertarlo de nuevo
        if (roleEntity != null) {
            if (roleEntity.getDeletedAt() != null) {
                roleEntity.setDeletedAt(null);
                roleRepository.saveAndFlush(roleEntity);
                CachedUser cu = new CachedUser(id, TenantContext.getCurrentTenant(),  this.getUserRoles(id));
                cacheUtil.saveOrUpdateUserCache(cu);
            }
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
            if (RolesType.ADMINISTRADOR.name().equals(role)) {
                ensureNotLastAdministrator(id);
            }
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

    // name o password en null = no se cambian; si vienen, tienen que cumplir las reglas de UserValidator
    public FullUserDto updateUser(Long id, String name, String password) throws ApiException {
        if (name != null) UserValidator.validateName(name);
        if (password != null) UserValidator.validatePassword(password);
        User rawUser = this.userRepository.findById(id).orElse(null);
        if (rawUser != null){
            if (name != null) rawUser.setName(name.trim());
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
                    rolePermissionService.maskFor(role.getType())
            ));
        }
        CachedUser cu = new CachedUser(id, TenantContext.getCurrentTenant(), list);
        cacheUtil.saveOrUpdateUserCache(cu);
        return list;
    }


    public boolean forgotPasswordRequest(EmailDto userData) throws ApiException {
        User u = this.userRepository.findByEmail(userData.email()).orElse(null);
        if (u == null) return false;
        String jwt = jwtUtil.generatePasswordToken(u.getId(),u.getEmail(), currentTenant());
        String url = resetPasswordLink(jwt);

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

    // El link del mail va al frontend de la tienda (su URL); sin tienda, al de la plataforma
    String resetPasswordLink(String jwt) {
        List<String> urls = jdbcTemplate.queryForList(
                "SELECT url FROM public.tenant WHERE schema = ? AND deleted_at IS NULL", String.class, currentTenant());
        String base = urls.isEmpty() || urls.get(0) == null ? platformScheme + "://" + platformDomain : urls.get(0);
        return base + RESET_PASSWORD_PATH + jwt;
    }

    private String currentTenant() {
        String tenant = TenantContext.getCurrentTenant();
        return tenant != null ? tenant : TenantConstants.PUBLIC;
    }

}
