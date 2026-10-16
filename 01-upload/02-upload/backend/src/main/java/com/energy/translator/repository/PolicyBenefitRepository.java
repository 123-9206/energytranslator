package com.energy.translator.repository;

import com.energy.translator.entity.PolicyBenefit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PolicyBenefitRepository extends MongoRepository<PolicyBenefit, String> {

    List<PolicyBenefit> findByStatus(String status);

    List<PolicyBenefit> findByPolicyTypeAndStatus(String policyType, String status);

    Page<PolicyBenefit> findByStatusAndTitleContainingIgnoreCase(String status, String keyword, Pageable pageable);

    Page<PolicyBenefit> findByStatus(String status, Pageable pageable);

    Page<PolicyBenefit> findByPolicyType(String policyType, Pageable pageable);
}
