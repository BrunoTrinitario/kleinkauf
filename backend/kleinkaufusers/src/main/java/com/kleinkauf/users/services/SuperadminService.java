package com.kleinkauf.users.services;

import com.kleinkauf.common.audit.AuditEvent;
import com.kleinkauf.common.audit.AuditLog;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.users.constants.ErrorMessages;
import com.kleinkauf.users.constants.RolesType;
import com.kleinkauf.users.dtos.platform.NewSuperadminDto;
import com.kleinkauf.users.dtos.platform.SuperadminDto;
import com.kleinkauf.users.entities.Role;
import com.kleinkauf.users.entities.User;
import com.kleinkauf.users.repositories.RoleRepository;
import com.kleinkauf.users.repositories.UserRepository;
import com.kleinkauf.users.util.CacheUtil;
import com.kleinkauf.users.util.Dates;
import com.kleinkauf.users.util.UserValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/*
Management of superadmins (public users with the SUPERADMIN role) from the platform dashboard.
- Create: with the repositories, like SuperadminInitializer (RoleUtility does not allow assigning SUPERADMIN with UserService).
  The password is temporary but it does not have to be changed on first login.
- Deactivate: deleted_at on the user and on their SUPERADMIN role. The role too, because HeaderAuthFilter looks up
  roles by id without checking the user: otherwise their token would keep working. Their roles are deleted from Redis;
  products keeps them for up to 1 minute in its local cache.
- Reactivate: clear both deleted_at.
User's and Role's @SQLRestriction hides deleted ones, which is why the list, the status change and the email check
go with plain SQL against public.
 */
@Service
public class SuperadminService {

    private static final String SUPERADMIN = RolesType.SUPERADMIN.name();

    // Manual recording of what is not a change to an @Audited entity (docs/auditoria.md)
    @Autowired
    private AuditLog auditLog;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CacheUtil cacheUtil;

    public List<SuperadminDto> list() {
        return jdbcTemplate.query(
                "SELECT u.id, u.name, u.email, u.created_at, u.lastlogin, "
                        + "(u.deleted_at IS NULL AND r.deleted_at IS NULL) AS active "
                        + "FROM public.usuarios u JOIN public.roles r ON r.userid = u.id AND r.type = ? "
                        + "ORDER BY u.created_at, u.id",
                (rs, i) -> new SuperadminDto(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getBoolean("active"),
                        Dates.withServerOffset(toLocalDateTime(rs.getTimestamp("created_at"))),
                        Dates.withServerOffset(toLocalDateTime(rs.getTimestamp("lastlogin")))),
                SUPERADMIN);
    }

    public SuperadminDto create(NewSuperadminDto dto) throws ApiException {
        if (dto == null) {
            throw new ApiException(ErrorMessages.INVALID_NAME, HttpStatus.BAD_REQUEST);
        }
        UserValidator.validateNewUser(dto.name(), dto.email(), dto.password());
        String email = UserValidator.normalizeEmail(dto.email());
        // existsByEmail does not see deleted ones, and the email is unique against them too
        if (emailInUse(email)) {
            throw new ApiException(ErrorMessages.EMAIL_ALREADY_IN_USE, HttpStatus.CONFLICT);
        }

        User user = new User();
        user.setName(dto.name().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(dto.password()));
        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email at once
            throw new ApiException(ErrorMessages.EMAIL_ALREADY_IN_USE, HttpStatus.CONFLICT);
        }

        Role role = new Role();
        role.setType(SUPERADMIN);
        role.setUser(user);
        roleRepository.save(role);

        return new SuperadminDto(user.getId(), user.getName(), user.getEmail(), true, Dates.withServerOffset(user.getCreatedAt()), null);
    }

    @Transactional
    public SuperadminDto setActive(Long id, boolean active, Long currentUserId) throws ApiException {
        SuperadminDto target = find(id);
        if (target.active() == active) {
            return target;
        }
        if (!active) {
            if (id.equals(currentUserId)) {
                throw new ApiException(ErrorMessages.CANNOT_DEACTIVATE_SELF, HttpStatus.BAD_REQUEST);
            }
            if (countActive() <= 1) {
                throw new ApiException(ErrorMessages.LAST_ACTIVE_SUPERADMIN, HttpStatus.CONFLICT);
            }
        }

        Timestamp deletedAt = active ? null : Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update("UPDATE public.usuarios SET deleted_at = ? WHERE id = ?", deletedAt, id);
        jdbcTemplate.update("UPDATE public.roles SET deleted_at = ? WHERE userid = ? AND type = ?", deletedAt, id, SUPERADMIN);
        if (!active) {
            cacheUtil.deleteUserRoles(id, null);
        }
        auditLog.record(active ? AuditEvent.ACTIVATE : AuditEvent.DEACTIVATE, "Superadmin", id,
                Map.of("active", AuditLog.change(!active, active)));
        return find(id);
    }

    private SuperadminDto find(Long id) throws ApiException {
        return list().stream()
                .filter(s -> s.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorMessages.SUPERADMIN_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private long countActive() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.usuarios u JOIN public.roles r ON r.userid = u.id AND r.type = ? "
                        + "WHERE u.deleted_at IS NULL AND r.deleted_at IS NULL",
                Long.class, SUPERADMIN);
        return count == null ? 0 : count;
    }

    private boolean emailInUse(String email) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.usuarios WHERE LOWER(email) = ?", Long.class, email);
        return count != null && count > 0;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
