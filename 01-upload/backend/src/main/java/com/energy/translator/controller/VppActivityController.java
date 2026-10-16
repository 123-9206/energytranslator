package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.dto.PageResponse;
import com.energy.translator.dto.VppActivityDTO;
import com.energy.translator.entity.VppActivity;
import com.energy.translator.entity.VppEnrollment;
import com.energy.translator.security.UserPrincipal;
import com.energy.translator.service.VppActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/vpp/activities")
@RequiredArgsConstructor
public class VppActivityController {

    private final VppActivityService activityService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<VppActivityDTO>>> getActivities(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean upcomingOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        String enterpriseId = principal != null ? principal.getEnterpriseId() : null;
        PageResponse<VppActivityDTO> response = activityService.getActivities(status, type, keyword, upcomingOnly, page, pageSize, enterpriseId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VppActivityDTO>> getActivityDetail(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        String enterpriseId = principal != null ? principal.getEnterpriseId() : null;
        VppActivityDTO activity = activityService.getActivityDetail(id, enterpriseId);
        return ResponseEntity.ok(ApiResponse.success(activity));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> createActivity(
            @RequestBody VppActivity activity,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        if (!"admin".equals(principal.getRole())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("只有管理员可以创建活动"));
        }
        
        try {
            String id = activityService.createActivity(activity, principal.getUserId());
            return ResponseEntity.ok(ApiResponse.success(Map.of("activityId", id)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updateActivity(
            @PathVariable String id,
            @RequestBody VppActivity activity,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        if (!"admin".equals(principal.getRole())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("只有管理员可以更新活动"));
        }
        
        try {
            activityService.updateActivity(id, activity);
            return ResponseEntity.ok(ApiResponse.success(null, "活动更新成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelActivity(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        if (!"admin".equals(principal.getRole())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("只有管理员可以取消活动"));
        }
        
        try {
            String reason = body != null ? body.get("reason") : null;
            activityService.cancelActivity(id, reason);
            return ResponseEntity.ok(ApiResponse.success(null, "活动已取消"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{id}/enroll")
    public ResponseEntity<ApiResponse<Map<String, Object>>> enrollActivity(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        try {
            @SuppressWarnings("unchecked")
            java.util.List<String> resourceIds = (java.util.List<String>) body.get("resourceIds");
            Double committedCapacity = ((Number) body.get("committedCapacity")).doubleValue();
            String contactName = (String) body.get("contactName");
            String contactPhone = (String) body.get("contactPhone");
            
            String enrollmentId = activityService.enrollActivity(
                    id, resourceIds, committedCapacity, 
                    principal.getEnterpriseId(), contactName, contactPhone);
            
            return ResponseEntity.ok(ApiResponse.success(Map.of("enrollmentId", enrollmentId), "报名成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{id}/cancelEnroll")
    public ResponseEntity<ApiResponse<Void>> cancelEnroll(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        try {
            activityService.cancelEnroll(id, principal.getEnterpriseId());
            return ResponseEntity.ok(ApiResponse.success(null, "取消报名成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/myEnrollments")
    public ResponseEntity<ApiResponse<PageResponse<VppEnrollment>>> getMyEnrollments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        PageResponse<VppEnrollment> response = activityService.getMyEnrollments(
                principal.getEnterpriseId(), page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<Void>> startActivity(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        if (!"admin".equals(principal.getRole())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("只有管理员可以开始活动"));
        }
        
        try {
            activityService.startActivity(id);
            return ResponseEntity.ok(ApiResponse.success(null, "活动已开始"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<ApiResponse<Void>> endActivity(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        if (!"admin".equals(principal.getRole())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("只有管理员可以结束活动"));
        }
        
        try {
            activityService.endActivity(id);
            return ResponseEntity.ok(ApiResponse.success(null, "活动已结束"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
