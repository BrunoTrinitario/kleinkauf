/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.kleinkauf.users.services;

import com.kleinkauf.common.constants.TenantConstants;
import com.kleinkauf.common.constants.TokenConstants;
import com.kleinkauf.common.dto.RoleDto;

import java.time.LocalDateTime;
import java.util.*;

import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.users.constants.MailConstants;
import com.kleinkauf.users.dtos.*;
import com.kleinkauf.users.util.CacheUtil;
import com.kleinkauf.users.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.kleinkauf.users.constants.ErrorMessages;
import com.kleinkauf.users.constants.RolesType;
import com.kleinkauf.users.entities.Role;
import com.kleinkauf.users.entities.User;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.users.repositories.RoleRepository;
import com.kleinkauf.users.repositories.UserRepository;
import com.kleinkauf.users.repositories.UserSearchRepository;
import com.kleinkauf.users.util.Parser;
import com.kleinkauf.users.util.RoleUtility;
import com.kleinkauf.users.util.UserValidator;

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
        email = UserValidator.normalizeEmail(email);
        if (userExistsByEmail(email)){
            // 409, como en el alta de superadmins
            throw new ApiException(ErrorMessages.EMAIL_ALREADY_IN_USE,HttpStatus.CONFLICT);
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
        return this.userRepository.existsByEmailIgnoreCase(UserValidator.normalizeEmail(email));
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
        // Rol que viene como parametro (?roleName=): 400, como en el listado. 404 queda para lo que va en la ruta
        if (!RoleUtility.existsRole(role)){
            throw new ApiException(ErrorMessages.INVALID_ROLE,HttpStatus.BAD_REQUEST);
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
        // Rol que viene como parametro (?roleName=): 400, como en el listado. 404 queda para lo que va en la ruta
        if (!RoleUtility.existsRole(role)){
            throw new ApiException(ErrorMessages.INVALID_ROLE,HttpStatus.BAD_REQUEST);
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
        User rawUser = this.userRepository.findByEmailIgnoreCase(UserValidator.normalizeEmail(email)).orElse(null);
        if (rawUser != null && passwordEncoder.matches(password, rawUser.getPassword())){
            rawUser.setLastLogin(LocalDateTime.now());
            userRepository.saveAndFlush(rawUser);
            return session(rawUser);
        }else{
            throw new ApiException(ErrorMessages.INVALID_CREDENTIALS,HttpStatus.UNAUTHORIZED);
        }
    }

    /*
    Renueva la sesion con el refresh token: devuelve tokens nuevos (el refresh tambien) y los roles al dia.
    No sirve si es de otro tenant, si el usuario se borro o si la contrasena cambio despues de entrar.
     */
    public LoggedUser refresh(String refreshToken) throws ApiException {
        Long id = refreshToken == null ? null : jwtUtil.getUserId(refreshToken);
        if (id == null || !currentTenant().equals(jwtUtil.getTenant(refreshToken))
                || !TokenConstants.REFRESH.equals(jwtUtil.getType(refreshToken))) {
            throw new ApiException(ErrorMessages.INVALID_TOKEN, HttpStatus.UNAUTHORIZED);
        }
        User user = this.userRepository.findById(id).orElse(null);
        if (user == null || !JwtUtil.passwordFingerprint(user.getPassword()).equals(jwtUtil.getPasswordFingerprint(refreshToken))) {
            throw new ApiException(ErrorMessages.INVALID_TOKEN, HttpStatus.UNAUTHORIZED);
        }
        return session(user);
    }

    // Tokens nuevos y roles del usuario (tambien quedan en la cache de Redis que leen los otros servicios)
    private LoggedUser session(User user) {
        String token = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), user.getName(), currentTenant());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), currentTenant(), user.getPassword());
        List<RoleDto> roleList = this.getUserRoles(user.getId());
        CachedUser cu = new CachedUser(user.getId(), TenantContext.getCurrentTenant(), roleList);
        cacheUtil.saveOrUpdateUserCache(cu);
        return new LoggedUser(user.getName(), user.getId(), user.getEmail(), token, refreshToken, roleList);
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
        User u = this.userRepository.findByEmailIgnoreCase(UserValidator.normalizeEmail(userData.email())).orElse(null);
        if (u == null) return false;
        String jwt = jwtUtil.generatePasswordToken(u.getId(),u.getEmail(), currentTenant(), u.getPassword());
        String url = resetPasswordLink(jwt);

        // Si el mail falla queda en el log (MailService); la respuesta es la misma, para no decir si el email existe
        mailService.sendMail(
                u.getEmail(),
                MailConstants.FORGOT_SUBJECT,
                MailConstants.FORGOT_MAIL_CONTENT(url)
        );
        return true;
    }

    /*
    El token tiene que ser valido, del mismo tenant (los ids de usuario se repiten entre tenants) y de recuperacion:
    uno de sesion no sirve para cambiar la contrasena sin saber la actual. Si la contrasena ya cambio desde que se
    mando el mail (el link ya se uso), la huella no coincide y el token no sirve.
     */
    public FullUserDto refreshPassword(String jwt, String newPassword) throws ApiException {
        Long id = jwtUtil.getUserId(jwt);
        if (id == null || !currentTenant().equals(jwtUtil.getTenant(jwt))
                || !TokenConstants.PASSWORD_RESET.equals(jwtUtil.getType(jwt))) {
            throw new ApiException(ErrorMessages.INVALID_TOKEN, HttpStatus.UNAUTHORIZED);
        }
        User user = this.userRepository.findById(id).orElse(null);
        if (user == null || !JwtUtil.passwordFingerprint(user.getPassword()).equals(jwtUtil.getPasswordFingerprint(jwt))) {
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
