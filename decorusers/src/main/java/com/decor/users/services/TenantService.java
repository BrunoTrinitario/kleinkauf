package com.decor.users.services;

import com.decor.users.constants.CacheKeys;
import com.decor.users.dtos.CachedTenant;
import com.decor.common.dto.TenantDto;
import com.decor.users.entities.Tenant;
import com.decor.users.repositories.TenantRepository;
import com.decor.users.util.CacheUtil;
import com.decor.users.util.Parser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;

@Service
public class TenantService {
    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private CacheUtil cacheUtil;

    public ArrayList<TenantDto> getAllTenant(){
        Collection<Tenant> rawTenantsList = this.tenantRepository.findAll();
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
        Tenant t = tenantRepository.findBySchema(schema).orElse(null);
        if  (t == null) return null;
        TenantDto dto = Parser.fromTenantEntityToFullDto(t);
        CachedTenant ct = new CachedTenant(t.getSchema(),dto);
        cacheUtil.saveOrUpdateTenantCache(ct);
        return dto;
    }

    public TenantDto getTenantByUrl(String url){
        Tenant t = tenantRepository.findByUrl(url).orElse(null);
        if  (t == null) return null;
        TenantDto dto = Parser.fromTenantEntityToFullDto(t);
        CachedTenant ct = new CachedTenant(t.getSchema(),dto);
        cacheUtil.saveOrUpdateTenantCache(ct);
        return dto;
    }

    public Boolean existsTenantByUrl(String url){
        boolean t = tenantRepository.existsByUrl(url);
        if(!t) return false;
        CachedTenant ct = new CachedTenant(CacheKeys.GATEWAY_CORS,url,5);
        cacheUtil.saveOrUpdateTenantCors(ct);
        return true;
    }

}
