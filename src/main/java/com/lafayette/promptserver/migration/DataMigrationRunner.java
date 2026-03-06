package com.lafayette.promptserver.migration;

import com.lafayette.promptserver.model.Tenant;
import com.lafayette.promptserver.repository.TenantRepository;
import com.lafayette.promptserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataMigrationRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        migrateOrphanedData();
    }

    /**
     * Assigns all users and prompts that have no tenantId to a "Default" tenant.
     * This handles data created before multi-tenancy was introduced.
     * Safe to run repeatedly — does nothing if there is nothing to migrate.
     */
    private void migrateOrphanedData() {
        Query noTenant = Query.query(Criteria.where("tenantId").is(null));

        long orphanedUsers = mongoTemplate.count(noTenant, "users");
        long orphanedPrompts = mongoTemplate.count(noTenant, "prompts");

        // Global admins intentionally have null tenantId — don't count them as "orphaned"
        long adminCount = userRepository.findAll().stream()
                .filter(u -> u.getTenantId() == null && u.getRoles().contains("ROLE_ADMIN"))
                .count();
        long usersToMigrate = orphanedUsers - adminCount;

        if (usersToMigrate == 0 && orphanedPrompts == 0) {
            log.debug("DataMigration: nothing to migrate.");
            return;
        }

        log.info("DataMigration: found {} user(s) and {} prompt(s) without a tenant — migrating to 'Default' tenant.",
                usersToMigrate, orphanedPrompts);

        // Get or create the "Default" tenant
        Tenant defaultTenant = tenantRepository.findByName("Default")
                .orElseGet(() -> {
                    Tenant t = Tenant.builder().name("Default").build();
                    Tenant saved = tenantRepository.save(t);
                    log.info("DataMigration: created 'Default' tenant with id={}", saved.getId());
                    return saved;
                });

        String tenantId = defaultTenant.getId();

        // Migrate non-admin users with null tenantId
        userRepository.findAll().stream()
                .filter(u -> u.getTenantId() == null && !u.getRoles().contains("ROLE_ADMIN"))
                .forEach(u -> {
                    u.setTenantId(tenantId);
                    userRepository.save(u);
                    log.info("DataMigration: user '{}' → Default tenant", u.getUsername());
                });

        // Migrate all prompts with null tenantId
        if (orphanedPrompts > 0) {
            Update update = new Update().set("tenantId", tenantId);
            long updated = mongoTemplate.updateMulti(noTenant, update, "prompts").getModifiedCount();
            log.info("DataMigration: {} prompt(s) → Default tenant", updated);
        }

        log.info("DataMigration: complete.");
    }
}
