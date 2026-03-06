package com.lafayette.promptserver.dto;

import com.lafayette.promptserver.model.Tenant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateTenantResponse {
    private Tenant tenant;
    private String adminUsername;
    /** Plain-text temp password — only returned once, never stored. */
    private String adminTempPassword;
}
