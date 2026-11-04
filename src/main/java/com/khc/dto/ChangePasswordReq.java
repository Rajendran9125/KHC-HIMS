package com.khc.dto;

import lombok.Data;

@Data
public class ChangePasswordReq {
    private String username;
    private String oldPassword;
    private String newPassword;
    private String rePassword;
}
