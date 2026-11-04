package com.kleinkauf.users.services;

import com.kleinkauf.common.constants.TenantConstants;
import com.kleinkauf.common.dto.TenantDto;
import com.kleinkauf.common.events.KleinKaufEvent;
import com.kleinkauf.common.events.KleinKaufEventPublisher;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.users.constants.CacheKeys;
import com.kleinkauf.users.constants.ErrorMessages;
import com.kleinkauf.users.dtos.CachedTenant;
import com.kleinkauf.users.dtos.platform.NewTenantDto;
import com.kleinkauf.users.dtos.platform.PlatformTenantDto;
import com.kleinkauf.users.entities.Tenant;
import com.kleinkauf.users.repositories.TenantRepository;
import com.kleinkauf.users.util.CacheUtil;
import com.kleinkauf.users.util.Dates;
import com.kleinkauf.users.util.Parser;
import com.kleinkauf.users.util.UserValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class TenantService {

    // 3 a 30 caracteres, empieza con letra (Postgres no acepta schemas que empiecen con numero),
    // no termina en guion. Al ser tan restrictivo es seguro usarlo en CREATE SCHEMA.
    private static final Pattern IDENTIFIER = Pattern.compile("^[a-z][a-z0-9-]{1,28}[a-z0-9]$");

    private static final Set<String> RESERVED = Set.of(
            "www", "api", "app", "admin", "platform", "public", "mail", "static", "assets", "cdn",
            "status", "help", "support", "blog", "dev", "test", "staging", "root", "information-schema"
    );

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private CacheUtil cacheUtil;

    @Autowired
    private TenantOwnerCreator tenantOwnerCreator;

    // Altas, habilitar / deshabilitar y bajas: el panel de la plataforma se actualiza en vivo (observability)
    @Autowired
    private KleinKaufEventPublisher events;

    @Value("${platform.scheme:http}")
    private String platformScheme;

    @Value("${platform.domain:localhost:3000}")
    private String platformDomain;

    public ArrayList<TenantDto> getAllTenant(){
        Collection<Tenant> rawTenantsList = this.tenantRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc();
        ArrayList<TenantDto> tenantsList = new ArrayList<>();
        for (Tenant t: rawTenantsList){
            TenantDto dto = Parser.fromTenantEntityToFullDto(t);
            tenantsList.add(dto);
            CachedTenant ct = new CachedTenant(t.getSchema(),dto);
            cacheUtil.saveOrUpdateTenantCache(ct);
        }
        return tenantsList;
    }

    public TenantDto getTenantBySchema(String schema){
        Tenant t = tenantRepository.findBySchemaAndDeletedAtIsNull(schema).orElse(null);
        if  (t == null) return null;
        TenantDto dto = Parser.fromTenantEntityToFullDto(t);
        CachedTenant ct = new CachedTenant(t.getSchema(),dto);
        cacheUtil.saveOrUpdateTenantCache(ct);
        return dto;
    }

    public TenantDto getTenantByUrl(String url){
        Tenant t = tenantRepository.findByUrlAndDeletedAtIsNull(url).orElse(null);
        if  (t == null) return null;
        TenantDto dto = Parser.fromTenantEntityToFullDto(t);
        CachedTenant ct = new CachedTenant(t.getSchema(),dto);
        cacheUtil.saveOrUpdateTenantCache(ct);
        cacheUtil.saveTenantByUrl(url, dto);
        return dto;
    }

    public Boolean existsTenantByUrl(String url){
        boolean t = tenantRepository.existsByUrlAndDeletedAtIsNull(url);
        if(!t) return false;
        CachedTenant ct = new CachedTenant(CacheKeys.GATEWAY_CORS,url,5);
        cacheUtil.saveOrUpdateTenantCors(ct);
        return true;
    }

    /*
    Alta de un tenant con su dueno (ADMINISTRADOR y EMPLEADO), activo al instante.
    La usan el autoservicio y el superadmin.
     */
    public PlatformTenantDto createTenant(NewTenantDto dto) throws ApiException {
        String identifier = dto.identifier() != null ? dto.identifier().trim() : null;
        validateIdentifier(identifier);
        validateOwner(dto);

        String schema = toSchema(identifier);
        if (tenantRepository.existsBySchema(schema)) {
            throw new ApiException(ErrorMessages.TENANT_IDENTIFIER_IN_USE, HttpStatus.CONFLICT);
        }

        Tenant tenant = new Tenant();
        tenant.setSchema(schema);
        tenant.setUrl(platformScheme + "://" + identifier + "." + platformDomain);
        // Referencia a la configuracion propia del tenant (se agrega mas adelante)
        tenant.setConfigId(UUID.randomUUID().toString());
        tenant.setEnable(true);
        try {
            tenant = tenantRepository.save(tenant);
        } catch (DataIntegrityViolationException e) {
            // Dos altas simultaneas con el mismo identificador: la segunda choca con el unique de schema
            throw new ApiException(ErrorMessages.TENANT_IDENTIFIER_IN_USE, HttpStatus.CONFLICT);
        }

        try {
            tenantOwnerCreator.createOwner(schema, dto.ownerName().trim(), UserValidator.normalizeEmail(dto.ownerEmail()), dto.ownerPassword());
        } catch (RuntimeException e) {
            // Sin dueno nadie podria administrarlo: se deshace el alta
            tenantRepository.delete(tenant);
            throw e;
        }
        publish(KleinKaufEvent.TENANT_CREATED, tenant);
        return toPlatformDto(tenant);
    }

    public List<PlatformTenantDto> listTenants() {
        return tenantRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()
                .stream()
                .map(TenantService::toPlatformDto)
                .toList();
    }

    public PlatformTenantDto getTenant(Long id) throws ApiException {
        return toPlatformDto(findActive(id));
    }

    public PlatformTenantDto setEnabled(Long id, boolean enabled) throws ApiException {
        Tenant tenant = findActive(id);
        tenant.setEnable(enabled);
        tenantRepository.save(tenant);
        cacheUtil.deleteTenant(tenant.getSchema(), tenant.getUrl());
        publish(enabled ? KleinKaufEvent.TENANT_ENABLED : KleinKaufEvent.TENANT_DISABLED, tenant);
        return toPlatformDto(tenant);
    }

    // Soft delete: el schema y los datos quedan, el identificador no se puede volver a usar
    public void deleteTenant(Long id) throws ApiException {
        Tenant tenant = findActive(id);
        tenant.setDeletedAt(LocalDateTime.now());
        tenantRepository.save(tenant);
        cacheUtil.deleteTenant(tenant.getSchema(), tenant.getUrl());
        publish(KleinKaufEvent.TENANT_DELETED, tenant);
    }

    // Eventos de plataforma: van con tenant "public"; la tienda va en los datos
    private void publish(String type, Tenant tenant) {
        events.publish(type, TenantConstants.PUBLIC, Map.of(
                "id", tenant.getId(),
                "identifier", tenant.getSchema().replace('_', '-'),
                "schema", tenant.getSchema()));
    }

    private Tenant findActive(Long id) throws ApiException {
        return tenantRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(ErrorMessages.TENANT_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private void validateIdentifier(String identifier) throws ApiException {
        if (identifier == null || !IDENTIFIER.matcher(identifier).matches()) {
            throw new ApiException(ErrorMessages.INVALID_TENANT_IDENTIFIER, HttpStatus.BAD_REQUEST);
        }
        // pg_* son schemas reservados de Postgres
        if (RESERVED.contains(identifier) || identifier.startsWith("pg-")) {
            throw new ApiException(ErrorMessages.TENANT_IDENTIFIER_RESERVED, HttpStatus.BAD_REQUEST);
        }
    }

    private void validateOwner(NewTenantDto dto) throws ApiException {
        if (isBlank(dto.ownerName()) || isBlank(dto.ownerEmail()) || isBlank(dto.ownerPassword())) {
            throw new ApiException(ErrorMessages.OWNER_DATA_REQUIRED, HttpStatus.BAD_REQUEST);
        }
        // El dueno es un usuario mas: mismas reglas de nombre, email y contrasena
        UserValidator.validateNewUser(dto.ownerName(), dto.ownerEmail(), dto.ownerPassword());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String toSchema(String identifier) {
        return identifier.replace('-', '_');
    }

    private static PlatformTenantDto toPlatformDto(Tenant t) {
        return new PlatformTenantDto(
                t.getId(),
                t.getSchema().replace('_', '-'),
                t.getSchema(),
                t.getUrl(),
                t.getConfigId(),
                Boolean.TRUE.equals(t.getEnable()),
                Dates.withServerOffset(t.getCreatedAt())
        );
    }
}
