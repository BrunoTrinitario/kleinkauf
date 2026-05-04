package com.decor.users.services;

import com.decor.users.dtos.FullTenantDto;
import com.decor.users.entities.Tenant;
import com.decor.users.repositories.TenantRepository;
import com.decor.users.util.Parser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;

@Service
public class TenantService {
    @Autowired
    private TenantRepository tenantRepository;

    public ArrayList<FullTenantDto> getAllTenant(){
        Collection<Tenant> rawTenantsList = this.tenantRepository.findAll();
        ArrayList<FullTenantDto> tenantsList = new ArrayList<>();
        for (Tenant t: rawTenantsList){
            tenantsList.add(Parser.fromTenantEntityToFullDto(t));
        }
        return tenantsList;
    }
}
