package com.energy.translator.controller;

import com.energy.translator.dto.ApiResponse;
import com.energy.translator.dto.PageResponse;
import com.energy.translator.dto.VppResourceDTO;
import com.energy.translator.entity.VppResource;
import com.energy.translator.security.UserPrincipal;
import com.energy.translator.service.VppResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vpp/resources")
@RequiredArgsConstructor
public class VppResourceController {

    private final VppResourceService resourceService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<VppResourceDTO>>> getResources(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        PageResponse<VppResourceDTO> response = resourceService.getResources(
                principal.getEnterpriseId(),
                principal.getRole(),
                status, type, keyword,
                page, pageSize);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VppResourceDTO>> getResourceDetail(@PathVariable String id) {
        VppResourceDTO resource = resourceService.getResourceDetail(id);
        return ResponseEntity.ok(ApiResponse.success(resource));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> registerResource(
            @RequestBody VppResource resource,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        try {
            String id = resourceService.registerResource(resource, principal.getEnterpriseId());
            return ResponseEntity.ok(ApiResponse.success(Map.of("resourceId", id), "资源登记成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updateResource(
            @PathVariable String id,
            @RequestBody VppResource resource,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        try {
            resourceService.updateResource(id, resource, principal.getEnterpriseId(), principal.getRole());
            return ResponseEntity.ok(ApiResponse.success(null, "资源更新成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteResource(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        try {
            resourceService.deleteResource(id, principal.getEnterpriseId(), principal.getRole());
            return ResponseEntity.ok(ApiResponse.success(null, "资源删除成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getResourceStatus(@PathVariable String id) {
        Map<String, Object> status = resourceService.getResourceStatus(id);
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @PostMapping("/batchStatus")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> batchGetStatus(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> resourceIds = (List<String>) body.get("resourceIds");
        List<Map<String, Object>> statuses = resourceService.batchGetStatus(resourceIds);
        return ResponseEntity.ok(ApiResponse.success(statuses));
    }
}
