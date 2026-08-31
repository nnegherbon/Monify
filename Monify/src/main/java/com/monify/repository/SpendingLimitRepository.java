package com.monify.repository;

import com.monify.entity.SpendingLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpendingLimitRepository extends JpaRepository<SpendingLimit, Long> {
    List<SpendingLimit> findByUserIdOrderByCategoryNameAsc(Long userId);
    List<SpendingLimit> findByUserIdAndMonthOrderByCategoryNameAsc(Long userId, String month);
    List<SpendingLimit> findByUserIdAndMonthAndLimitTypeOrderByCategoryNameAsc(Long userId, String month, SpendingLimit.LimitType limitType);
    Optional<SpendingLimit> findByUserIdAndCategoryKeyAndMonthAndLimitType(Long userId, String categoryKey, String month, SpendingLimit.LimitType limitType);
}
