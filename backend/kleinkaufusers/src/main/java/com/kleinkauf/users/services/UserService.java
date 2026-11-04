/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.kleinkauf.users.services;

import com.kleinkauf.common.constants.TenantConstants;
import com.kleinkauf.common.constants.TokenConstants;
import com.kleinkauf.common.dto.RoleDto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import com.kleinkauf.common.servlet.tenant.TenantContext;
import com.kleinkauf.users.constants.MailConstants;
import com.kleinkauf.users.dtos.*;
import com.kleinkauf.common.files.ImageFiles;
import com.kleinkauf.users.util.AvatarStorage;
import com.kleinkauf.users.util.CacheUtil;
import java.io.IOException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
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
    // How long a refresh token keeps working after rotating it (another tab may have renewed with the same one at once)
    private static final Duration REFRESH_GRACE = Duration.ofSeconds(30);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AvatarStorage avatarStorage;
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
    // The tenants table is in public: JPA only sees the schema of the request's store
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${platform.scheme:http}")
    private String platformScheme;

    @Value("${platform.domain:localhost:3000}")
    private String platformDomain;

    // Frontend page that asks for the new password and calls POST /api/v1/auth/forgot-password/{token}
    static final String RESET_PASSWORD_PATH = "/reset-password?token=";


    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    /*
    Paginated dashboard listing, ordered by name. query searches name and email; role filters by role.
    The roles of the whole page come from a single query and each type's mask is computed once.
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

    // A store without an ADMINISTRADOR cannot be managed by anyone (nor the role reassigned): the last one is not deleted
    // nor does it lose the role, whether done by themselves or another administrator. 409, like the last superadmin
    private void ensureNotLastAdministrator(Long userId) throws ApiException {
        String admin = RolesType.ADMINISTRADOR.name();
        boolean isAdmin = this.roleRepository.findByUser_Id(userId).stream().anyMatch(r -> admin.equals(r.getType()));
        if (isAdmin && this.roleRepository.countActiveUsersWithType(admin) <= 1) {
            throw new ApiException(ErrorMessages.LAST_ADMINISTRATOR, HttpStatus.CONFLICT);
        }
    }

    public static final long MAX_AVATAR_BYTES = 1024 * 1024; // 1 MB

    // Sets the user's own photo (the previous file is deleted). Up to 1 MB, PNG, JPG, WEBP or GIF by content
    @Transactional
    public FullUserDto setAvatar(Long userId, MultipartFile file) throws ApiException {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorMessages.AVATAR_REQUIRED, HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new ApiException(ErrorMessages.AVATAR_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorMessages.INVALID_AVATAR, HttpStatus.BAD_REQUEST);
        }
        String extension = ImageFiles.extension(bytes);
        if (extension == null) {
            throw new ApiException(ErrorMessages.INVALID_AVATAR, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        User user = this.userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorMessages.USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        String previous = user.getAvatar();
        String saved = avatarStorage.save(bytes, extension);
        user.setAvatar(saved);
        this.userRepository.save(user);
        deleteFilesAfterCompletion(previous, saved);
        return Parser.fromUserEntityToFullDto(user, getUserRoles(userId));
    }

    @Transactional
    public FullUserDto deleteAvatar(Long userId) throws ApiException {
        User user = this.userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorMessages.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
        String previous = user.getAvatar();
        if (previous != null) {
            user.setAvatar(null);
            this.userRepository.save(user);
            deleteFilesAfterCompletion(previous, null);
        }
        return Parser.fromUserEntityToFullDto(user, getUserRoles(userId));
    }

    /*
    Files are only touched once the database change is final: on commit the replaced photo is deleted, on rollback
    the one just saved (so neither an orphan file nor a user pointing to a missing file is left behind)
     */
    private void deleteFilesAfterCompletion(String onCommit, String onRollback) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            avatarStorage.delete(onCommit);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                avatarStorage.delete(status == STATUS_COMMITTED ? onCommit : onRollback);
            }
        });
    }

    @Transactional
    public boolean deleteUserById(Long id) throws ApiException {
        User rawUser = this.userRepository.findById(id).orElse(null);
        if (rawUser != null){
            ensureNotLastAdministrator(id);
            for (Role r: rawUser.getRoles()){
                this.roleRepository.delete(r);
            }
            // The photo is personal data: it goes with the account
            String avatar = rawUser.getAvatar();
            rawUser.setAvatar(null);
            this.userRepository.deleteById(id);
            deleteFilesAfterCompletion(avatar, null);
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
            // 409, as in the superadmin sign-up
            throw new ApiException(ErrorMessages.EMAIL_ALREADY_IN_USE,HttpStatus.CONFLICT);
        }
        password = passwordEncoder.encode(password);
        User newUser = new User();
        newUser.setName(name);
        newUser.setEmail(email);
        newUser.setPassword(password);
        newUser = this.userRepository.save(newUser);
        Set<Role> userRoles = new HashSet<>();
        for (String roleName: roles){
            if (RoleUtility.existsRole(roleName)){
                Role newRole = new Role();
                newRole.setType(roleName);
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
        // Role coming as a parameter (?roleName=): 400, as in the listing. 404 is kept for what goes in the path
        if (!RoleUtility.existsRole(role)){
            throw new ApiException(ErrorMessages.INVALID_ROLE,HttpStatus.BAD_REQUEST);
        }
        User user = userRepository.findById(id).orElseThrow(() -> new ApiException(ErrorMessages.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
        Role roleEntity = roleRepository.existDeletedRole(id, role).orElse(null);

        // If the user already had this role, it is reactivated: the unique (userId, type) does not allow inserting it again
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
        // Role coming as a parameter (?roleName=): 400, as in the listing. 404 is kept for what goes in the path
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

    // name or password null = not changed; if present, they must follow UserValidator's rules
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
    Renews the session with the refresh token: returns new tokens (the refresh one too) and up-to-date roles.
    It does not work if it belongs to another tenant, if the user was deleted, if the password changed after logging in, if that
    session was closed or if it was already used (each refresh is used once; the old one lasts REFRESH_GRACE in case another tab renews at the same time).
     */
    public LoggedUser refresh(String refreshToken) throws ApiException {
        Long id = refreshUserId(refreshToken);
        String tokenId = id == null ? null : jwtUtil.getTokenId(refreshToken);
        if (tokenId == null || !cacheUtil.isRefreshTokenActive(currentTenant(), id, tokenId)) {
            throw new ApiException(ErrorMessages.INVALID_TOKEN, HttpStatus.UNAUTHORIZED);
        }
        User user = this.userRepository.findById(id).orElse(null);
        if (user == null || !JwtUtil.passwordFingerprint(user.getPassword()).equals(jwtUtil.getPasswordFingerprint(refreshToken))) {
            throw new ApiException(ErrorMessages.INVALID_TOKEN, HttpStatus.UNAUTHORIZED);
        }
        cacheUtil.retireRefreshToken(currentTenant(), id, tokenId, REFRESH_GRACE);
        return session(user);
    }

    // Logout: revokes that session's refresh token. Does not fail if it no longer worked (the frontend logs out anyway)
    public void logout(String refreshToken) {
        Long id = refreshUserId(refreshToken);
        String tokenId = id == null ? null : jwtUtil.getTokenId(refreshToken);
        if (tokenId != null) {
            cacheUtil.deleteRefreshToken(currentTenant(), id, tokenId);
        }
    }

    // User id if the token is a refresh token signed for this tenant; null otherwise
    private Long refreshUserId(String refreshToken) {
        Long id = refreshToken == null ? null : jwtUtil.getUserId(refreshToken);
        if (id == null || !currentTenant().equals(jwtUtil.getTenant(refreshToken))
                || !TokenConstants.REFRESH.equals(jwtUtil.getType(refreshToken))) {
            return null;
        }
        return id;
    }

    // New tokens and the user's roles (they also end up in the Redis cache the other services read)
    private LoggedUser session(User user) {
        String token = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), user.getName(), currentTenant());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), currentTenant(), user.getPassword());
        cacheUtil.saveRefreshToken(currentTenant(), user.getId(), jwtUtil.getTokenId(refreshToken),
                Duration.ofMillis(JwtUtil.REFRESH_TOKEN_MILLIS));
        List<RoleDto> roleList = this.getUserRoles(user.getId());
        CachedUser cu = new CachedUser(user.getId(), TenantContext.getCurrentTenant(), roleList);
        cacheUtil.saveOrUpdateUserCache(cu);
        return new LoggedUser(user.getName(), user.getId(), user.getEmail(), token, refreshToken, roleList, user.getAvatar());
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

        // If the email fails it stays in the log (MailService); the response is the same, so as not to reveal whether the email exists
        mailService.sendMail(
                u.getEmail(),
                MailConstants.FORGOT_SUBJECT,
                MailConstants.FORGOT_MAIL_CONTENT(url)
        );
        return true;
    }

    /*
    The token must be valid, from the same tenant (user ids repeat across tenants) and a recovery one:
    a session token does not work to change the password without knowing the current one. If the password already changed since the
    email was sent (the link was already used), the fingerprint does not match and the token does not work.
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

    // The email link goes to the store's frontend (its URL); without a store, to the platform's
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
