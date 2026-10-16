package com.energy.translator.repository;

import com.energy.translator.entity.BillCheckReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillCheckReportRepository extends MongoRepository<BillCheckReport, String> {

    Page<BillCheckReport> findByStatusOrderByCreatedAtDesc(BillCheckReport.Status status, Pageable pageable);

    Page<BillCheckReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(BillCheckReport.Status status);

    java.util.List<BillCheckReport> findByStatusIn(java.util.Collection<BillCheckReport.Status> statuses);
}
