package com.medichannel.repository;

import com.medichannel.entity.UserAccount;
import com.medichannel.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmailHmac(String emailHmac);

    boolean existsByEmailHmac(String emailHmac);

    boolean existsByRole(Role role);
}
