package com.khc.controller;

import com.khc.dto.*;
import com.khc.model.OpRegistration;
import com.khc.model.Patient;
import com.khc.service.RegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/registration")
public class RegistrationController {

    @Autowired
    private RegistrationService registrationService;

    // 1. OP NEW
    @PostMapping("/op-new")
    public ResponseEntity<ApiResponse<OpRegistration>> registerOpNew(@RequestBody OpNewRegistrationReq req) {
        OpRegistration op = registrationService.registerOpNew(req);
        return ResponseEntity.ok(ApiResponse.ok("New OP Registration created successfully", op));
    }

    // 2. OP REVIEW
    @PostMapping("/op-review")
    public ResponseEntity<ApiResponse<OpRegistration>> registerOpReview(@RequestBody OpReviewReq req) {
        OpRegistration op = registrationService.registerOpReview(req);
        return ResponseEntity.ok(ApiResponse.ok("Review OP Visit created successfully", op));
    }

    // 3. PATIENT DETAIL UPDATE
    @PutMapping("/patient-update")
    public ResponseEntity<ApiResponse<Patient>> updatePatientDetail(@RequestBody PatientUpdateReq req) {
        Patient updated = registrationService.updatePatientDetail(req);
        return ResponseEntity.ok(ApiResponse.ok("Patient details updated successfully", updated));
    }

    // 4. DOCTORE NAME MODIFY - REGISTRATION
    @PutMapping("/modify-doctor")
    public ResponseEntity<ApiResponse<OpRegistration>> modifyDoctor(@RequestBody ModifyDoctorReq req) {
        OpRegistration op = registrationService.modifyRegistrationDoctor(req.getId(), req.getDoctorId(), req.getReason());
        return ResponseEntity.ok(ApiResponse.ok("Doctor modified successfully for OP: " + op.getOpNumber(), op));
    }

    // 5. PATIENT TYPE MODIFY
    @PutMapping("/modify-patient-type")
    public ResponseEntity<ApiResponse<OpRegistration>> modifyPatientType(@RequestBody ModifyPatientTypeReq req) {
        OpRegistration op = registrationService.modifyRegistrationPatientType(req.getId(), req.getPatientType(), req.getReason());
        return ResponseEntity.ok(ApiResponse.ok("Patient Type modified successfully for OP: " + op.getOpNumber(), op));
    }

    // 6. PAYMENT TYPE MODIFY
    @PutMapping("/modify-payment-type")
    public ResponseEntity<ApiResponse<OpRegistration>> modifyPaymentType(@RequestBody ModifyPaymentTypeReq req) {
        OpRegistration op = registrationService.modifyRegistrationPaymentType(req.getId(), req.getPaymentType(), req.getReason());
        return ResponseEntity.ok(ApiResponse.ok("Payment Type modified successfully for OP: " + op.getOpNumber(), op));
    }

    // Query Registrations
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<OpRegistration>>> getRegistrations(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        List<OpRegistration> list = registrationService.searchRegistrations(query, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    // Dedicated endpoint for Copy Bill (defaults to today's records)
    @GetMapping("/copy-bills")
    public ResponseEntity<ApiResponse<List<OpRegistration>>> getCopyRegistrations(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Boolean allRecords) {
        List<OpRegistration> list = registrationService.getCopyRegistrations(query, startDate, endDate, allRecords);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/{id:[0-9]+}")
    public ResponseEntity<ApiResponse<OpRegistration>> getRegistrationById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(registrationService.getRegistrationById(id)));
    }

    // Search existing patients for Review or Patient Detail Update
    @GetMapping("/patients/search")
    public ResponseEntity<ApiResponse<List<Patient>>> searchPatients(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(ApiResponse.ok(registrationService.searchPatients(query)));
    }
}
