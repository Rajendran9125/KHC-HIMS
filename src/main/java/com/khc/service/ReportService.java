package com.khc.service;

import com.khc.model.OpBill;
import com.khc.model.OpBillItem;
import com.khc.model.OpRegistration;
import com.khc.repository.OpBillItemRepo;
import com.khc.repository.OpBillRepo;
import com.khc.repository.OpRegistrationRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {

    @Autowired
    private OpBillRepo billRepo;

    @Autowired
    private OpBillItemRepo billItemRepo;

    @Autowired
    private OpRegistrationRepo registrationRepo;

    // 1. OP BILLING REPORT (Image 5)
    public Map<String, Object> getOpBillingReport(String startDate, String endDate, Long doctorId) {
        List<OpBill> bills = billRepo.findAllByOrderByBillDateDesc();

        if (startDate != null && !startDate.isEmpty()) {
            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            bills = bills.stream()
                    .filter(b -> b.getBillDate() != null && !b.getBillDate().isBefore(start))
                    .collect(Collectors.toList());
        }

        if (endDate != null && !endDate.isEmpty()) {
            LocalDateTime end = LocalDate.parse(endDate).atTime(LocalTime.MAX);
            bills = bills.stream()
                    .filter(b -> b.getBillDate() != null && !b.getBillDate().isAfter(end))
                    .collect(Collectors.toList());
        }

        if (doctorId != null) {
            bills = bills.stream()
                    .filter(b -> b.getDoctor() != null && doctorId.equals(b.getDoctor().getId()))
                    .collect(Collectors.toList());
        }

        double totalAmount = bills.stream().mapToDouble(b -> b.getTotalAmount() != null ? b.getTotalAmount() : 0.0).sum();
        double totalRefunded = bills.stream().mapToDouble(b -> b.getRefundedAmount() != null ? b.getRefundedAmount() : 0.0).sum();
        double totalDiscount = bills.stream().mapToDouble(b -> b.getDiscount() != null ? b.getDiscount() : 0.0).sum();
        
        // OS Amount: if UNPAID or PARTIAL or total > paid, calculate outstanding
        double totalOsAmount = bills.stream().mapToDouble(b -> {
            double tot = b.getTotalAmount() != null ? b.getTotalAmount() : 0.0;
            double pd = b.getPaidAmount() != null ? b.getPaidAmount() : 0.0;
            if ("PAID".equalsIgnoreCase(b.getPaymentStatus())) return 0.0;
            return Math.max(0.0, tot - pd);
        }).sum();

        // RCPT Amount: net paid amount
        double totalPaid = bills.stream().mapToDouble(b -> {
            double pd = b.getPaidAmount() != null ? b.getPaidAmount() : 0.0;
            double ref = b.getRefundedAmount() != null ? b.getRefundedAmount() : 0.0;
            return Math.max(0.0, pd - ref);
        }).sum();

        Map<String, Object> report = new HashMap<>();
        report.put("bills", bills);
        report.put("totalCount", bills.size());
        report.put("totalAmount", totalAmount);
        report.put("totalRefunded", totalRefunded);
        report.put("totalPaid", totalPaid); // Net Collected
        report.put("totalDiscount", totalDiscount);
        report.put("totalOsAmount", totalOsAmount);
        report.put("totalRcptAmount", totalPaid);
        return report;
    }

    // 2. REVENUE REPORT (Image 2)
    public Map<String, Object> getRevenueReport(String startDate, String endDate) {
        List<OpBill> bills = billRepo.findAllByOrderByBillDateDesc();

        if (startDate != null && !startDate.isEmpty()) {
            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            bills = bills.stream()
                    .filter(b -> b.getBillDate() != null && !b.getBillDate().isBefore(start))
                    .collect(Collectors.toList());
        }

        if (endDate != null && !endDate.isEmpty()) {
            LocalDateTime end = LocalDate.parse(endDate).atTime(LocalTime.MAX);
            bills = bills.stream()
                    .filter(b -> b.getBillDate() != null && !b.getBillDate().isAfter(end))
                    .collect(Collectors.toList());
        }

        double totalBilled = bills.stream().mapToDouble(b -> b.getTotalAmount() != null ? b.getTotalAmount() : 0.0).sum();
        double totalRefunded = bills.stream().mapToDouble(b -> b.getRefundedAmount() != null ? b.getRefundedAmount() : 0.0).sum();
        double totalPaid = bills.stream().mapToDouble(b -> Math.max(0.0, (b.getPaidAmount() != null ? b.getPaidAmount() : 0.0) - (b.getRefundedAmount() != null ? b.getRefundedAmount() : 0.0))).sum();
        double totalDiscount = bills.stream().mapToDouble(b -> b.getDiscount() != null ? b.getDiscount() : 0.0).sum();

        // Payment type breakdown net of refunds
        Map<String, Double> paymentTypeBreakdown = bills.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getPaymentType() != null ? b.getPaymentType() : "OTHER",
                        Collectors.summingDouble(b -> Math.max(0.0, (b.getPaidAmount() != null ? b.getPaidAmount() : 0.0) - (b.getRefundedAmount() != null ? b.getRefundedAmount() : 0.0)))
                ));

        // Group by Header / Category for Image 2
        Map<String, Double> serviceWiseRevenue = new HashMap<>();
        Map<String, Integer> serviceWiseCount = new HashMap<>();

        for (OpBill bill : bills) {
            if (bill.getItems() != null) {
                for (OpBillItem item : bill.getItems()) {
                    if ("REFUNDED".equalsIgnoreCase(item.getStatus())) continue;
                    String cat = item.getServiceCategory() != null ? item.getServiceCategory().toUpperCase() : "GENERAL";
                    serviceWiseRevenue.put(cat, serviceWiseRevenue.getOrDefault(cat, 0.0) + (item.getTotalPrice() != null ? item.getTotalPrice() : 0.0));
                    serviceWiseCount.put(cat, serviceWiseCount.getOrDefault(cat, 0) + (item.getQuantity() != null ? item.getQuantity() : 1));
                }
            }
        }

        // Build Table Rows matching BackBone Revenue Report (Image 2)
        List<Map<String, Object>> headerRows = new ArrayList<>();
        int sNo = 1;
        List<String> sortedHeaders = new ArrayList<>(serviceWiseRevenue.keySet());
        Collections.sort(sortedHeaders);

        int grandQty = 0;
        double grandBillAmount = 0.0;
        double grandTaxAmount = 0.0;
        double grandBillTotal = 0.0;
        double grandConcession = 0.0;
        double grandNetAmount = 0.0;

        for (String header : sortedHeaders) {
            int qty = serviceWiseCount.getOrDefault(header, 0);
            double billAmt = serviceWiseRevenue.getOrDefault(header, 0.0);
            double taxAmt = 0.0;
            double billTot = billAmt + taxAmt;
            double concAmt = 0.0;
            double netAmt = billTot - concAmt;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sNo", sNo++);
            row.put("header", header);
            row.put("qty", qty);
            row.put("billAmount", billAmt);
            row.put("taxAmount", taxAmt);
            row.put("billTotal", billTot);
            row.put("concessionAmount", concAmt);
            row.put("netAmount", netAmt);
            headerRows.add(row);

            grandQty += qty;
            grandBillAmount += billAmt;
            grandTaxAmount += taxAmt;
            grandBillTotal += billTot;
            grandConcession += concAmt;
            grandNetAmount += netAmt;
        }

        Map<String, Object> report = new HashMap<>();
        report.put("totalBilled", totalBilled);
        report.put("totalRefunded", totalRefunded);
        report.put("totalPaid", totalPaid); // Net Realized Revenue
        report.put("totalDiscount", totalDiscount);
        report.put("billCount", bills.size());
        report.put("paymentTypeBreakdown", paymentTypeBreakdown);
        report.put("serviceWiseRevenue", serviceWiseRevenue);
        report.put("serviceWiseCount", serviceWiseCount);
        report.put("headerRows", headerRows);
        report.put("grandQty", grandQty);
        report.put("grandBillAmount", grandBillAmount);
        report.put("grandTaxAmount", grandTaxAmount);
        report.put("grandBillTotal", grandBillTotal);
        report.put("grandConcession", grandConcession);
        report.put("grandNetAmount", grandNetAmount);
        return report;
    }

    // 3. OP REGISTRATION REPORT (Image 6)
    public Map<String, Object> getOpRegistrationReport(String startDate, String endDate, String visitType, Long doctorId) {
        List<OpRegistration> list = registrationRepo.findAllByOrderByRegistrationDateDesc();

        if (startDate != null && !startDate.isEmpty()) {
            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            list = list.stream()
                    .filter(r -> r.getRegistrationDate() != null && !r.getRegistrationDate().isBefore(start))
                    .collect(Collectors.toList());
        }

        if (endDate != null && !endDate.isEmpty()) {
            LocalDateTime end = LocalDate.parse(endDate).atTime(LocalTime.MAX);
            list = list.stream()
                    .filter(r -> r.getRegistrationDate() != null && !r.getRegistrationDate().isAfter(end))
                    .collect(Collectors.toList());
        }

        if (visitType != null && !visitType.isEmpty() && !"ALL".equalsIgnoreCase(visitType)) {
            list = list.stream()
                    .filter(r -> visitType.equalsIgnoreCase(r.getVisitType()))
                    .collect(Collectors.toList());
        }

        if (doctorId != null) {
            list = list.stream()
                    .filter(r -> r.getDoctor() != null && doctorId.equals(r.getDoctor().getId()))
                    .collect(Collectors.toList());
        }

        long newCount = list.stream().filter(r -> "NEW".equalsIgnoreCase(r.getVisitType())).count();
        long reviewCount = list.stream().filter(r -> "REVIEW".equalsIgnoreCase(r.getVisitType())).count();
        double totalFees = list.stream().mapToDouble(r -> r.getConsultationFee() != null ? r.getConsultationFee() : 0.0).sum();

        Map<String, Long> doctorCounts = list.stream()
                .filter(r -> r.getDoctor() != null)
                .collect(Collectors.groupingBy(r -> r.getDoctor().getDoctorName(), Collectors.counting()));

        Map<String, Long> patientTypeCounts = list.stream()
                .filter(r -> r.getPatientType() != null)
                .collect(Collectors.groupingBy(OpRegistration::getPatientType, Collectors.counting()));

        Map<String, Object> report = new HashMap<>();
        report.put("registrations", list);
        report.put("totalCount", list.size());
        report.put("newCount", newCount);
        report.put("reviewCount", reviewCount);
        report.put("totalFees", totalFees);
        report.put("doctorCounts", doctorCounts);
        report.put("patientTypeCounts", patientTypeCounts);
        return report;
    }

    // 4. COLLECTION REPORT (Image 4 Summary + Image 7 Detail)
    public Map<String, Object> getCollectionReport(String startDate, String endDate, String paymentType, String collectionTypeFilter) {
        List<OpBill> bills = billRepo.findAllByOrderByBillDateDesc();

        if (startDate != null && !startDate.isEmpty()) {
            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            bills = bills.stream()
                    .filter(b -> b.getBillDate() != null && !b.getBillDate().isBefore(start))
                    .collect(Collectors.toList());
        }

        if (endDate != null && !endDate.isEmpty()) {
            LocalDateTime end = LocalDate.parse(endDate).atTime(LocalTime.MAX);
            bills = bills.stream()
                    .filter(b -> b.getBillDate() != null && !b.getBillDate().isAfter(end))
                    .collect(Collectors.toList());
        }

        if (paymentType != null && !paymentType.isEmpty() && !"ALL".equalsIgnoreCase(paymentType)) {
            bills = bills.stream()
                    .filter(b -> paymentType.equalsIgnoreCase(b.getPaymentType()))
                    .collect(Collectors.toList());
        }

        // Filter bills that have collections
        List<OpBill> collectedBills = bills.stream()
                .filter(b -> b.getPaidAmount() != null && b.getPaidAmount() > 0)
                .collect(Collectors.toList());

        // Detail Rows (Image 7)
        List<Map<String, Object>> details = new ArrayList<>();
        int sNo = 1;

        double cashTotal = 0.0;
        double cardTotal = 0.0;
        double upiTotal = 0.0;
        double chequeTotal = 0.0;
        double ddTotal = 0.0;
        double eftTotal = 0.0;

        for (OpBill b : collectedBills) {
            double netRcpt = Math.max(0.0, (b.getPaidAmount() != null ? b.getPaidAmount() : 0.0) - (b.getRefundedAmount() != null ? b.getRefundedAmount() : 0.0));
            String payMode = b.getPaymentType() != null ? b.getPaymentType().toUpperCase() : "CASH";

            if (payMode.contains("CASH")) cashTotal += netRcpt;
            else if (payMode.contains("CARD")) cardTotal += netRcpt;
            else if (payMode.contains("UPI")) upiTotal += netRcpt;
            else if (payMode.contains("CHEQUE")) chequeTotal += netRcpt;
            else upiTotal += netRcpt;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sNo", sNo++);
            row.put("patientName", b.getPatient() != null ? b.getPatient().getFullName() : "-");
            row.put("regNumber", b.getPatient() != null ? b.getPatient().getMrn() : "-");
            row.put("billType", "OP");
            row.put("ipNumber", "-");
            row.put("billNumber", b.getBillNumber());
            row.put("date", b.getBillDate() != null ? b.getBillDate().toString() : "-");
            row.put("receiptNumber", "RCPT-" + b.getBillNumber());
            row.put("userName", "Admin");
            row.put("payMode", payMode);
            row.put("paymodeNumber", "TXN-" + b.getId());
            row.put("collectionType", "BILL COLLECTION");
            row.put("fromBank", "-");
            row.put("toBank", "INDIAN BANK");
            row.put("receiptAmount", netRcpt);
            row.put("storeName", "OP PHARMACY KH");
            details.add(row);
        }

        // Summary Rows (Image 4): ADVANCE COLLECTION & BILL COLLECTION
        List<Map<String, Object>> summary = new ArrayList<>();
        Map<String, Object> billCollectionRow = new LinkedHashMap<>();
        billCollectionRow.put("sNo", 1);
        billCollectionRow.put("collectionType", "BILL COLLECTION");
        billCollectionRow.put("cash", cashTotal);
        billCollectionRow.put("cheque", chequeTotal);
        billCollectionRow.put("card", cardTotal);
        billCollectionRow.put("demandDraft", ddTotal);
        billCollectionRow.put("eft", eftTotal);
        billCollectionRow.put("upi", upiTotal);
        billCollectionRow.put("total", cashTotal + cardTotal + upiTotal + chequeTotal + ddTotal + eftTotal);
        summary.add(billCollectionRow);

        double totalAll = cashTotal + cardTotal + upiTotal + chequeTotal + ddTotal + eftTotal;

        Map<String, Object> report = new HashMap<>();
        report.put("details", details);
        report.put("summary", summary);
        report.put("totalCount", details.size());
        report.put("totalCash", cashTotal);
        report.put("totalCard", cardTotal);
        report.put("totalUpi", upiTotal);
        report.put("totalCheque", chequeTotal);
        report.put("totalAmount", totalAll);
        return report;
    }

    // 5. OUTSTANDING REPORT (Image 1 Summary + Image 3 Detail)
    public Map<String, Object> getOutstandingReport(String startDate, String endDate, String sourceFilter, Long doctorId) {
        List<OpBill> bills = billRepo.findAllByOrderByBillDateDesc();

        if (startDate != null && !startDate.isEmpty()) {
            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            bills = bills.stream()
                    .filter(b -> b.getBillDate() != null && !b.getBillDate().isBefore(start))
                    .collect(Collectors.toList());
        }

        if (endDate != null && !endDate.isEmpty()) {
            LocalDateTime end = LocalDate.parse(endDate).atTime(LocalTime.MAX);
            bills = bills.stream()
                    .filter(b -> b.getBillDate() != null && !b.getBillDate().isAfter(end))
                    .collect(Collectors.toList());
        }

        if (doctorId != null) {
            bills = bills.stream()
                    .filter(b -> b.getDoctor() != null && doctorId.equals(b.getDoctor().getId()))
                    .collect(Collectors.toList());
        }

        // Outstanding bills: totalAmount > paidAmount or status != PAID
        List<OpBill> osBills = bills.stream()
                .filter(b -> {
                    double tot = b.getTotalAmount() != null ? b.getTotalAmount() : 0.0;
                    double pd = b.getPaidAmount() != null ? b.getPaidAmount() : 0.0;
                    return (tot - pd) > 0.0 || !"PAID".equalsIgnoreCase(b.getPaymentStatus());
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> details = new ArrayList<>();
        int sNo = 1;

        double corpOsTotal = 0.0;
        double genOsTotal = 0.0;

        for (OpBill b : osBills) {
            double tot = b.getTotalAmount() != null ? b.getTotalAmount() : 0.0;
            double pd = b.getPaidAmount() != null ? b.getPaidAmount() : 0.0;
            double osAmt = Math.max(0.0, tot - pd);
            if (osAmt <= 0.0 && !"PAID".equalsIgnoreCase(b.getPaymentStatus())) {
                osAmt = tot;
            }

            boolean isCorp = false;
            String sourceName = "General";
            if (b.getOpRegistration() != null && b.getOpRegistration().getPatientType() != null) {
                String pt = b.getOpRegistration().getPatientType().toUpperCase();
                if (pt.contains("CORP") || pt.contains("INSURANCE")) {
                    isCorp = true;
                    sourceName = b.getOpRegistration().getPatientType();
                }
            }

            if (isCorp) {
                corpOsTotal += osAmt;
            } else {
                genOsTotal += osAmt;
            }

            String sourceStr = isCorp ? "Corporate" : "General";

            if (sourceFilter != null && !sourceFilter.isEmpty() && !"ALL".equalsIgnoreCase(sourceFilter)) {
                if (!sourceStr.equalsIgnoreCase(sourceFilter)) continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sNo", sNo++);
            row.put("billType", "OP");
            row.put("patientName", b.getPatient() != null ? b.getPatient().getFullName() : "-");
            row.put("regNumber", b.getPatient() != null ? b.getPatient().getMrn() : "-");
            row.put("ipNumber", "-");
            row.put("admissionDate", "-");
            row.put("corpRefNo", b.getPatient() != null ? b.getPatient().getMrn() : "-");
            row.put("billNumber", b.getBillNumber());
            row.put("outstandingDate", b.getBillDate() != null ? b.getBillDate().toString() : "-");
            row.put("userName", "Admin");
            row.put("membershipId", "-");
            row.put("source", sourceStr);
            row.put("sourceName", sourceName);
            row.put("doctorName", b.getDoctor() != null ? b.getDoctor().getDoctorName() : "-");
            row.put("billAmount", tot);
            row.put("outstandingAmount", osAmt);
            row.put("balanceAmount", osAmt);
            row.put("remarks", b.getRemarks() != null ? b.getRemarks() : "Review Registration Bill");
            details.add(row);
        }

        // Summary Rows (Image 1): Corporate & General
        List<Map<String, Object>> summary = new ArrayList<>();
        Map<String, Object> corpRow = new LinkedHashMap<>();
        corpRow.put("sNo", 1);
        corpRow.put("source", "Corporate");
        corpRow.put("outstandingAmount", corpOsTotal);
        corpRow.put("balanceAmount", corpOsTotal);
        summary.add(corpRow);

        Map<String, Object> genRow = new LinkedHashMap<>();
        genRow.put("sNo", 2);
        genRow.put("source", "General");
        genRow.put("outstandingAmount", genOsTotal);
        genRow.put("balanceAmount", genOsTotal);
        summary.add(genRow);

        double totalOs = corpOsTotal + genOsTotal;

        Map<String, Object> report = new HashMap<>();
        report.put("details", details);
        report.put("summary", summary);
        report.put("totalCount", details.size());
        report.put("totalBillAmount", details.stream().mapToDouble(d -> (Double) d.get("billAmount")).sum());
        report.put("totalOutstandingAmount", totalOs);
        report.put("totalBalanceAmount", totalOs);
        return report;
    }
}
