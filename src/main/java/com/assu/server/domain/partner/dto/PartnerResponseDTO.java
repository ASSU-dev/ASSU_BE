package com.assu.server.domain.partner.dto;

import com.assu.server.domain.admin.entity.Admin;

import java.util.List;

public record PartnerResponseDTO (
        List<AdminLiteDTO> admins
){

    public record AdminLiteDTO(
            Long adminId,
            String adminAddress,
            String adminDetailAddress,
            String adminName,
            String adminUrl,
            String adminPhone
    ) {
        public static AdminLiteDTO from(Admin admin) {
            return new AdminLiteDTO(
                    admin.getId(),
                    admin.getOfficeAddress(),
                    admin.getDetailAddress(),
                    admin.getName(),
                    admin.getMember() != null ? admin.getMember().getProfileUrl() : null,
                    admin.getMember() != null ? admin.getMember().getPhoneNum() : null
            );
        }
    }
}
