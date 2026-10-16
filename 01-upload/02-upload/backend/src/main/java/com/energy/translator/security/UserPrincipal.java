package com.energy.translator.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal {
    private String userId;
    private String username;
    private String password;
    private String role;
    private String enterpriseId;
    private List<String> permissions;
}
