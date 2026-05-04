package com.decor.users.repositories;

import com.decor.users.entities.Tenant;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface TenantRepository extends MongoRepository<Tenant, UUID> {
}
