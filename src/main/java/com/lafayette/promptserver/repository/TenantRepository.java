package com.lafayette.promptserver.repository;

import com.lafayette.promptserver.model.Tenant;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends MongoRepository<Tenant, String> {
    boolean existsByName(String name);
    List<Tenant> findAllByOrderByNameAsc();
    Optional<Tenant> findByName(String name);
}
