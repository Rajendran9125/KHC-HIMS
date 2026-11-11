package com.khc.controller;

import com.khc.dto.ApiResponse;
import com.khc.dto.ModifyDoctorReq;
import com.khc.dto.ModifyPaymentTypeReq;
import com.khc.dto.OpBillCreateReq;
import com.khc.dto.RefundReq;
import com.khc.model.OpBill;
import com.khc.service.BillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    @Autowired
    private BillingService billingService;

    // 1. OP SERVICE BILLING
    @PostMapping("/op-service")
    public ResponseEntity<ApiResponse<OpBill>> createOpServiceBill(@RequestBody OpBillCreateReq req) {
        OpBill bill = billingService.createOpServiceBill(req);
        return ResponseEntity.ok(ApiResponse.ok("OP Service Bill created successfully", bill));
    }

    // 2. DOCTOR NAME MODIFY - OP BILL
    @PutMapping("/modify-doctor")
    public ResponseEntity<ApiResponse<OpBill>> modifyDoctor(@RequestBody ModifyDoctorReq req) {
        OpBill bill = billingService.modifyBillDoctor(req.getId(), req.getDoctorId(), req.getReason());
        return ResponseEntity.ok(ApiResponse.ok("Doctor modified successfully on Bill: " + bill.getBillNumber(), bill));
    }

    // 3. PAYMENT TYPE MODIFY
    @PutMapping("/modify-payment-type")
    public ResponseEntity<ApiResponse<OpBill>> modifyPaymentType(@RequestBody ModifyPaymentTypeReq req) {
        OpBill bill = billingService.modifyBillPaymentType(req.getId(), req.getPaymentType(), req.getReason());
        return ResponseEntity.ok(ApiResponse.ok("Payment type modified successfully on Bill: " + bill.getBillNumber(), bill));
    }

    // 4. REFUND / REVERT SPECIFIC SERVICE ITEM
    @PostMapping("/refund-item")
    public ResponseEntity<ApiResponse<OpBill>> refundBillItem(@RequestBody RefundReq req) {
        OpBill bill = billingService.refundBillItem(req.getBillId(), req.getItemId(), req.getReason());
        return ResponseEntity.ok(ApiResponse.ok("Service item refunded and reverted successfully", bill));
    }

    // 5. REFUND FULL BILL
    @PostMapping("/refund-full")
    public ResponseEntity<ApiResponse<OpBill>> refundFullBill(@RequestBody RefundReq req) {
        OpBill bill = billingService.refundFullBill(req.getBillId(), req.getReason());
        return ResponseEntity.ok(ApiResponse.ok("Full bill refunded successfully", bill));
    }

    // List and details
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<OpBill>>> getBills(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        List<OpBill> list = billingService.searchBills(query, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    // Dedicated endpoint for Copy Bill (defaults to today's records)
    @GetMapping("/copy-bills")
    public ResponseEntity<ApiResponse<List<OpBill>>> getCopyBills(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Boolean allRecords) {
        List<OpBill> list = billingService.getCopyBills(query, startDate, endDate, allRecords);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/{id:[0-9]+}")
    public ResponseEntity<ApiResponse<OpBill>> getBillById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(billingService.getBillById(id)));
    }
}
