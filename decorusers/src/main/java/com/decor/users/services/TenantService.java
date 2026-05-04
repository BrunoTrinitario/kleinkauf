package com.decor.users.services;

import com.decor.users.dtos.ComTenantDto;
import com.decor.users.dtos.FullTenantDto;
import com.decor.users.entities.Tenant;
import com.decor.users.repositories.TenantRepository;
import com.decor.users.util.CacheUtil;
import com.decor.users.util.Parser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

@Service
public class TenantService {
    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private CacheUtil cacheUtil;

    public ArrayList<FullTenantDto> getAllTenant(){
        Collection<Tenant> rawTenantsList = this.tenantRepository.findAll();
        ArrayList<FullTenantDto> tenantsList = new ArrayList<>();
        for (Tenant t: rawTenantsList){
            tenantsList.add(Parser.fromTenantEntityToFullDto(t));
        }
        return tenantsList;
    }

    public FullTenantDto getTenantById(String id){
        Tenant t = tenantRepository.findById(UUID.fromString(id)).orElse(null);
        if  (t == null) return null;
        return Parser.fromTenantEntityToFullDto(t);
    }

    public ComTenantDto getTenantByUrl(String url){
        Tenant t = tenantRepository.findByUrl(url).orElse(null);
        if  (t == null) return null;
        ComTenantDto ctm = new ComTenantDto(
                t.getId().toString(),
                t.getName(),
                t.getDbName(),
                t.getDbUser(),
                t.getDbPassword()
        );
        cacheUtil.saveOrUpdateTenantCache(t.getUrl(),ctm);
        return ctm;
    }

    public FullTenantDto createTenant(FullTenantDto dto){
        Tenant t = Parser.formDtoToTenant(dto);
        t =  tenantRepository.save(t);
        return Parser.fromTenantEntityToFullDto(t);
    }
}
