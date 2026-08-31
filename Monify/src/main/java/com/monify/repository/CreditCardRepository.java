package com.monify.repository;

import com.monify.entity.CreditCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CreditCardRepository extends JpaRepository<CreditCard, Long> {
    List<CreditCard> findByUserIdOrderByNameAsc(Long userId);
    Optional<CreditCard> findByUserIdAndNameIgnoreCase(Long userId, String name);
    Optional<CreditCard> findByIdAndUserId(Long id, Long userId);
}
