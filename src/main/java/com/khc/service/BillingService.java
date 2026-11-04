package com.khc.service;

import com.khc.dto.OpBillCreateReq;
import com.khc.dto.OpBillItemReq;
import com.khc.model.*;
import com.khc.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class BillingService {

    @Autowired
    private OpBillRepo billRepo;

    @Autowired
    private OpBillItemRepo billItemRepo;

    @Autowired
    private PatientRepo patientRepo;

    @Autowired
    private DoctorRepo doctorRepo;

    @Autowired
    private OpRegistrationRepo registrationRepo;

    private static final AtomicLong billCounter = new AtomicLong(3001);

    @jakarta.annotation.PostConstruct
    public void initCounters() {
        try {
            long maxBill = 3000;
            List<OpBill> bills = billRepo.findAll();
            for (OpBill b : bills) {
                if (b.getBillNumber() != null) {
                    String clean = b.getBillNumber().trim();
                    if (clean.startsWith("BILL") && !clean.contains("-")) {
                        try {
                            long val = Long.parseLong(clean.substring(4));
                            if (val > maxBill) maxBill = val;
                        } catch (Exception ignored) {}
                    }
                }
            }
            billCounter.set(maxBill + 1);
        } catch (Exception e) {
            billCounter.set(3001);
        }
    }

    @Transactional
    public OpBill createOpServiceBill(OpBillCreateReq req) {
        Patient patient = patientRepo.findById(req.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found with id: " + req.getPatientId()));

        Doctor doctor = doctorRepo.findById(req.getDoctorId())
                .orElseThrow(() -> new RuntimeException("Doctor not found with id: " + req.getDoctorId()));

        OpRegistration opRegistration = null;
        if (req.getOpRegistrationId() != null) {
            opRegistration = registrationRepo.findById(req.getOpRegistrationId()).orElse(null);
            if (opRegistration != null) {
                if ("BILLED".equalsIgnoreCase(opRegistration.getStatus()) || "PAID".equalsIgnoreCase(opRegistration.getStatus())) {
                    throw new RuntimeException("OP Registration " + opRegistration.getOpNumber() + " is already BILLED and PAID. Duplicate billing is not allowed.");
                }
                opRegistration.setStatus("BILLED");
                registrationRepo.save(opRegistration);
            }
        }

        OpBill bill = new OpBill();
        bill.setBillNumber("BILL" + billCounter.getAndIncrement());
        bill.setPatient(patient);
        bill.setDoctor(doctor);
        bill.setOpRegistration(opRegistration);
        bill.setBillDate(LocalDateTime.now());
        bill.setPaymentType(req.getPaymentType() != null ? req.getPaymentType() : "CASH");
        bill.setRemarks(req.getRemarks());

        double subTotal = 0.0;
        List<OpBillItem> billItems = new ArrayList<>();

        if (req.getItems() != null && !req.getItems().isEmpty()) {
            for (OpBillItemReq itemReq : req.getItems()) {
                OpBillItem item = new OpBillItem();
                item.setBill(bill);
                item.setServiceName(itemReq.getServiceName());
                item.setServiceCategory(itemReq.getServiceCategory() != null ? itemReq.getServiceCategory() : "PROCEDURE");
                int qty = itemReq.getQuantity() != null && itemReq.getQuantity() > 0 ? itemReq.getQuantity() : 1;
                double price = itemReq.getUnitPrice() != null ? itemReq.getUnitPrice() : 0.0;
                double total = qty * price;
                item.setQuantity(qty);
                item.setUnitPrice(price);
                item.setTotalPrice(total);
                item.setStatus("BILLED");
                subTotal += total;
                billItems.add(item);
            }
        }

        double discount = req.getDiscount() != null ? req.getDiscount() : 0.0;
        double tax = req.getTax() != null ? req.getTax() : 0.0;
        double totalAmount = Math.max(0.0, subTotal - discount + tax);
        double paidAmount = req.getPaidAmount() != null ? req.getPaidAmount() : totalAmount;

        bill.setSubTotal(subTotal);
        bill.setDiscount(discount);
        bill.setTax(tax);
        bill.setTotalAmount(totalAmount);
        bill.setPaidAmount(paidAmount);

        if (paidAmount >= totalAmount) {
            bill.setPaymentStatus("PAID");
        } else if (paidAmount > 0) {
            bill.setPaymentStatus("PARTIAL");
        } else {
            bill.setPaymentStatus("UNPAID");
        }

        bill.setItems(billItems);
        return billRepo.save(bill);
    }

    @Transactional
    public OpBill modifyBillDoctor(Long billId, Long newDoctorId, String reason) {
        OpBill bill = billRepo.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found with id: " + billId));
        Doctor newDoctor = doctorRepo.findById(newDoctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found with id: " + newDoctorId));

        bill.setDoctor(newDoctor);
        if (reason != null && !reason.trim().isEmpty()) {
            bill.setRemarks((bill.getRemarks() != null ? bill.getRemarks() + " | " : "") + "Doctor changed: " + reason);
        }
        return billRepo.save(bill);
    }

    @Transactional
    public OpBill modifyBillPaymentType(Long billId, String newPaymentType, String reason) {
        OpBill bill = billRepo.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found with id: " + billId));

        bill.setPaymentType(newPaymentType);
        if (reason != null && !reason.trim().isEmpty()) {
            bill.setRemarks((bill.getRemarks() != null ? bill.getRemarks() + " | " : "") + "Payment Type changed: " + reason);
        }
        return billRepo.save(bill);
    }

    @Transactional
    public OpBill refundBillItem(Long billId, Long itemId, String reason) {
        OpBill bill = billRepo.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found with id: " + billId));

        OpBillItem targetItem = bill.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Bill item not found with id: " + itemId));

        if ("REFUNDED".equalsIgnoreCase(targetItem.getStatus())) {
            throw new RuntimeException("Item is already refunded: " + targetItem.getServiceName());
        }

        targetItem.setStatus("REFUNDED");
        targetItem.setRefundReason(reason != null && !reason.trim().isEmpty() ? reason.trim() : "Service skipped / cancelled");
        targetItem.setRefundDate(LocalDateTime.now());
        billItemRepo.save(targetItem);

        // Recalculate refunded amount
        double totalRefunded = bill.getItems().stream()
                .filter(item -> "REFUNDED".equalsIgnoreCase(item.getStatus()))
                .mapToDouble(item -> item.getTotalPrice() != null ? item.getTotalPrice() : 0.0)
                .sum();

        bill.setRefundedAmount(totalRefunded);

        boolean allRefunded = bill.getItems().stream()
                .allMatch(item -> "REFUNDED".equalsIgnoreCase(item.getStatus()));

        if (allRefunded) {
            bill.setPaymentStatus("REFUNDED");
        } else if (totalRefunded > 0) {
            bill.setPaymentStatus("PARTIAL_REFUND");
        }

        if (reason != null && !reason.trim().isEmpty()) {
            bill.setRemarks((bill.getRemarks() != null ? bill.getRemarks() + " | " : "") + 
                    "Refunded " + targetItem.getServiceName() + " (₹" + targetItem.getTotalPrice() + "): " + reason);
        }

        return billRepo.save(bill);
    }

    @Transactional
    public OpBill refundFullBill(Long billId, String reason) {
        OpBill bill = billRepo.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found with id: " + billId));

        for (OpBillItem item : bill.getItems()) {
            if (!"REFUNDED".equalsIgnoreCase(item.getStatus())) {
                item.setStatus("REFUNDED");
                item.setRefundReason(reason != null && !reason.trim().isEmpty() ? reason.trim() : "Full bill refund");
                item.setRefundDate(LocalDateTime.now());
                billItemRepo.save(item);
            }
        }

        double totalRefunded = bill.getItems().stream()
                .mapToDouble(item -> item.getTotalPrice() != null ? item.getTotalPrice() : 0.0)
                .sum();

        bill.setRefundedAmount(totalRefunded);
        bill.setPaymentStatus("REFUNDED");
        if (reason != null && !reason.trim().isEmpty()) {
            bill.setRemarks((bill.getRemarks() != null ? bill.getRemarks() + " | " : "") + "Full bill refunded: " + reason);
        }

        return billRepo.save(bill);
    }

    public List<OpBill> getAllBills() {
        return billRepo.findAllByOrderByBillDateDesc();
    }

    public List<OpBill> searchBills(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllBills();
        }
        return billRepo.searchBills(query.trim());
    }

    public OpBill getBillById(Long id) {
        return billRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Bill not found with id: " + id));
    }
}
