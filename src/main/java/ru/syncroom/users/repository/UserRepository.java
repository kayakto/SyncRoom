package ru.syncroom.users.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.syncroom.users.domain.AuthProvider;
import ru.syncroom.users.domain.User;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);
    
    Optional<User> findByEmailAndProvider(String email, AuthProvider provider);
    
    boolean existsByEmail(String email);
}
