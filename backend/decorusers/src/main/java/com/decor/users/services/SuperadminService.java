package com.decor.users.services;

import com.decor.common.audit.AuditEvent;
import com.decor.common.audit.AuditLog;
import com.decor.common.servlet.exceptions.ApiException;
import com.decor.users.constants.ErrorMessages;
import com.decor.users.constants.RolesType;
import com.decor.users.dtos.platform.NewSuperadminDto;
import com.decor.users.dtos.platform.SuperadminDto;
import com.decor.users.entities.Role;
import com.decor.users.entities.User;
import com.decor.users.repositories.RoleRepository;
import com.decor.users.repositories.UserRepository;
import com.decor.users.util.CacheUtil;
import com.decor.users.util.Dates;
import com.decor.users.util.UserValidator;
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
Gestion de superadmins (usuarios de public con rol SUPERADMIN) desde el panel de la plataforma.
- Alta: con los repositorios, como SuperadminInitializer (RoleUtility no deja asignar SUPERADMIN con UserService).
  La contrasena es temporal pero no hay que cambiarla en el primer login.
- Desactivar: deleted_at en el usuario y en su rol SUPERADMIN. El rol tambien, porque el HeaderAuthFilter busca
  los roles por id sin mirar el usuario: si no, su token seguiria sirviendo. Se borran sus roles de Redis;
  products los tiene hasta 1 minuto en su cache local.
- Reactivar: limpiar los dos deleted_at.
El @SQLRestriction de User y Role oculta los borrados, por eso la lista, el cambio de estado y el chequeo de email
van con SQL directo sobre public.
 */
@Service
public class SuperadminService {

    private static final String SUPERADMIN = RolesType.SUPERADMIN.name();

    // Registro a mano de lo que no es un cambio de una entidad @Audited (docs/auditoria.md)
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
        String email = dto.email().trim();
        // existsByEmail no ve los borrados, y el email es unico tambien contra ellos
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
            // Dos altas con el mismo email a la vez
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
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.usuarios WHERE email = ?", Long.class, email);
        return count != null && count > 0;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
