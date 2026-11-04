package com.khc.controller;

import com.khc.dto.ApiResponse;
import com.khc.dto.ChangePasswordReq;
import com.khc.dto.LoginReq;
import com.khc.dto.LoginResp;
import com.khc.dto.ResetPasswordReq;
import com.khc.service.MasterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private MasterService masterService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResp>> login(@RequestBody LoginReq req) {
        try {
            LoginResp resp = masterService.login(req.getUsername(), req.getPassword());
            return ResponseEntity.ok(ApiResponse.ok("Login successful", resp));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@RequestBody ResetPasswordReq req) {
        try {
            masterService.resetPassword(req.getUsername(), req.getNewPassword(), req.getEmailOrPhone());
            return ResponseEntity.ok(ApiResponse.ok("Password updated successfully. You can now login with your new password.", "SUCCESS"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(@RequestBody ChangePasswordReq req) {
        try {
            masterService.changePassword(req);
            return ResponseEntity.ok(ApiResponse.ok("Password changed successfully", "SUCCESS"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
