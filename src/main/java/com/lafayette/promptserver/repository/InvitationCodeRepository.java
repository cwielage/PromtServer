package com.lafayette.promptserver.repository;

import com.lafayette.promptserver.model.InvitationCode;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface InvitationCodeRepository extends MongoRepository<InvitationCode, String> {

    Optional<InvitationCode> findByCode(String code);
}
