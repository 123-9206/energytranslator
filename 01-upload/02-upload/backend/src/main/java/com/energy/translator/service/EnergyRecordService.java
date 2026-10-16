package com.energy.translator.service;

import com.energy.translator.dto.PageResponse;
import com.energy.translator.entity.EnergyRecord;
import com.energy.translator.repository.EnergyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EnergyRecordService {

    private final EnergyRecordRepository energyRecordRepository;

    public EnergyRecord addRecord(EnergyRecord record) {
        if (record.getTimestamp() == null) {
            record.setTimestamp(Instant.now());
        }
        if (record.getUnit() == null) {
            record.setUnit("kWh");
        }
        if (record.getSource() == null) {
            record.setSource("manual");
        }
        return energyRecordRepository.save(record);
    }

    public PageResponse<EnergyRecord> getRecords(String enterpriseId, String startDate, String endDate, 
                                                   int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "timestamp"));
        
        Page<EnergyRecord> records;
        if (startDate != null && endDate != null) {
            Instant start = LocalDate.parse(startDate).atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant end = LocalDate.parse(endDate).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            records = energyRecordRepository.findByEnterpriseIdAndTimestampBetween(enterpriseId, start, end, pageRequest);
        } else {
            records = energyRecordRepository.findByEnterpriseId(enterpriseId, pageRequest);
        }

        return PageResponse.of(records.getContent(), records.getTotalElements(), page, pageSize);
    }

    public List<EnergyRecord> getTodayRecords(String enterpriseId) {
        LocalDate today = LocalDate.now();
        Instant start = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        return energyRecordRepository.findByTimeRange(enterpriseId, start, end);
    }
}
