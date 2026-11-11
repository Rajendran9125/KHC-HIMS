package com.khc.service;

import com.khc.dto.OpNewRegistrationReq;
import com.khc.dto.OpReviewReq;
import com.khc.dto.PatientUpdateReq;
import com.khc.model.Doctor;
import com.khc.model.OpRegistration;
import com.khc.model.Patient;
import com.khc.repository.DoctorRepo;
import com.khc.repository.OpRegistrationRepo;
import com.khc.repository.PatientRepo;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.time.LocalTime;
import java.util.stream.Collectors;

@Service
public class RegistrationService {

    @Autowired
    private PatientRepo patientRepo;

    @Autowired
    private OpRegistrationRepo registrationRepo;

    @Autowired
    private DoctorRepo doctorRepo;

    private static final AtomicLong mrnCounter = new AtomicLong(1);
    private static final AtomicLong opCounter = new AtomicLong(1);

    @PostConstruct
    public void initCounters() {
        try {
            String currentYear = String.valueOf(LocalDate.now().getYear());
            String prefixKH = "KHC" + currentYear;
            String prefixMRN = "MRN" + currentYear;
            long maxSeq = 0;
            List<Patient> patients = patientRepo.findAll();
            for (Patient p : patients) {
                if (p.getMrn() != null) {
                    String clean = p.getMrn().trim();
                    if (clean.startsWith(prefixKH)) {
                        try {
                            String numPart = clean.substring(prefixKH.length());
                            long val = Long.parseLong(numPart);
                            if (val > maxSeq) {
                                maxSeq = val;
                            }
                        } catch (Exception ignored) {}
                    } else if (clean.startsWith(prefixMRN)) {
                        try {
                            String numPart = clean.substring(prefixMRN.length());
                            long val = Long.parseLong(numPart);
                            if (val > maxSeq) {
                                maxSeq = val;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            mrnCounter.set(maxSeq + 1);
        } catch (Exception e) {
            mrnCounter.set(1);
        }

        try {
            long maxOp = 0;
            List<OpRegistration> ops = registrationRepo.findAll();
            for (OpRegistration o : ops) {
                if (o.getOpNumber() != null) {
                    String clean = o.getOpNumber().trim();
                    if (clean.startsWith("OP") && !clean.contains("-")) {
                        try {
                            long val = Long.parseLong(clean.substring(2));
                            if (val < 2000 && val > maxOp) {
                                maxOp = val;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            opCounter.set(maxOp + 1);
        } catch (Exception e) {
            opCounter.set(1);
        }
    }

    @Transactional
    public OpRegistration registerOpNew(OpNewRegistrationReq req) {
        Doctor doctor = doctorRepo.findById(req.getDoctorId())
                .orElseThrow(() -> new RuntimeException("Doctor not found with id: " + req.getDoctorId()));

        // Create new Patient with Patient ID format: KH + Year + 4 digits (e.g. KH20260001)
        Patient patient = new Patient();
        String year = String.valueOf(LocalDate.now().getYear());
        long seq = mrnCounter.getAndIncrement();
        patient.setMrn("KHC" + year + String.format("%04d", seq));
        patient.setFirstName(req.getFirstName());
        patient.setLastName(req.getLastName());
        patient.setDob(req.getDob());
        patient.setAge(req.getAge());
        patient.setGender(req.getGender() != null ? req.getGender() : "MALE");
        patient.setMobile(req.getMobile());
        patient.setEmail(req.getEmail());
        patient.setAddress(req.getAddress() != null && !req.getAddress().isEmpty() ? req.getAddress() : req.getResidentialAddress());
        patient.setCountryId(req.getCountryId());
        patient.setStateId(req.getStateId());
        patient.setCityId(req.getCityId());
        patient.setPincode(req.getPincode());
        patient.setBloodGroup(req.getBloodGroup());
        patient.setEmergencyContact(req.getEmergencyContact());
        patient = patientRepo.save(patient);

        // Create OP Registration with OP Token Number format: OP + 4 digits (e.g. OP0001)
        OpRegistration op = new OpRegistration();
        op.setOpNumber(String.format("OP%04d", opCounter.getAndIncrement()));
        op.setPatient(patient);
        op.setDoctor(doctor);
        op.setVisitType("NEW");
        op.setPatientType(req.getPatientType() != null ? req.getPatientType() : "GENERAL");
        op.setPaymentType(req.getPaymentType() != null ? req.getPaymentType() : "CASH");
        op.setDepartment(req.getDepartment() != null ? req.getDepartment() : doctor.getDepartment());
        op.setConsultationFee(req.getConsultationFee() != null ? req.getConsultationFee() : doctor.getConsultationFee());
        op.setStatus("WAITING");
        op.setRemarks(req.getRemarks());
        op.setCountryId(req.getCountryId());
        op.setStateId(req.getStateId());
        op.setCityId(req.getCityId());
        op.setPincode(req.getPincode());
        op.setResidentialAddress(req.getResidentialAddress() != null && !req.getResidentialAddress().isEmpty() ? req.getResidentialAddress() : req.getAddress());
        op.setRegistrationDate(LocalDateTime.now());

        return registrationRepo.save(op);
    }

    @Transactional
    public OpRegistration registerOpReview(OpReviewReq req) {
        Patient patient;
        if (req.getPatientId() != null) {
            patient = patientRepo.findById(req.getPatientId())
                    .orElseThrow(() -> new RuntimeException("Patient not found with id: " + req.getPatientId()));
        } else if (req.getMrn() != null && !req.getMrn().trim().isEmpty()) {
            patient = patientRepo.findByMrn(req.getMrn().trim())
                    .orElseThrow(() -> new RuntimeException("Patient not found with MRN / Patient ID: " + req.getMrn()));
        } else {
            throw new IllegalArgumentException("Either patientId or MRN is required for review visit");
        }

        Doctor doctor = doctorRepo.findById(req.getDoctorId())
                .orElseThrow(() -> new RuntimeException("Doctor not found with id: " + req.getDoctorId()));

        OpRegistration op = new OpRegistration();
        op.setOpNumber(String.format("OP%04d", opCounter.getAndIncrement()));
        op.setPatient(patient);
        op.setDoctor(doctor);
        op.setVisitType("REVIEW");
        op.setPatientType(req.getPatientType() != null ? req.getPatientType() : "GENERAL");
        op.setPaymentType(req.getPaymentType() != null ? req.getPaymentType() : "CASH");
        op.setDepartment(req.getDepartment() != null ? req.getDepartment() : doctor.getDepartment());
        // Review consultation fee usually 50% or nominal
        Double fee = req.getConsultationFee() != null ? req.getConsultationFee() : (doctor.getConsultationFee() != null ? doctor.getConsultationFee() * 0.5 : 100.0);
        op.setConsultationFee(fee);
        op.setStatus("WAITING");
        op.setRemarks(req.getRemarks() != null ? req.getRemarks() : "Follow-up / Review Visit");
        op.setRegistrationDate(LocalDateTime.now());

        return registrationRepo.save(op);
    }

    @Transactional
    public Patient updatePatientDetail(PatientUpdateReq req) {
        Patient patient = patientRepo.findById(req.getId())
                .orElseThrow(() -> new RuntimeException("Patient not found with id: " + req.getId()));

        if (req.getFirstName() != null) patient.setFirstName(req.getFirstName());
        if (req.getLastName() != null) patient.setLastName(req.getLastName());
        if (req.getDob() != null) patient.setDob(req.getDob());
        if (req.getAge() != null) patient.setAge(req.getAge());
        if (req.getGender() != null) patient.setGender(req.getGender());
        if (req.getMobile() != null) patient.setMobile(req.getMobile());
        if (req.getEmail() != null) patient.setEmail(req.getEmail());
        if (req.getAddress() != null) patient.setAddress(req.getAddress());
        if (req.getCountryId() != null) patient.setCountryId(req.getCountryId());
        if (req.getStateId() != null) patient.setStateId(req.getStateId());
        if (req.getCityId() != null) patient.setCityId(req.getCityId());
        if (req.getPincode() != null) patient.setPincode(req.getPincode());
        if (req.getBloodGroup() != null) patient.setBloodGroup(req.getBloodGroup());
        if (req.getEmergencyContact() != null) patient.setEmergencyContact(req.getEmergencyContact());

        return patientRepo.save(patient);
    }

    @Transactional
    public OpRegistration modifyRegistrationDoctor(Long opId, Long newDoctorId, String reason) {
        OpRegistration op = registrationRepo.findById(opId)
                .orElseThrow(() -> new RuntimeException("OP Registration not found with id: " + opId));
        Doctor newDoctor = doctorRepo.findById(newDoctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found with id: " + newDoctorId));

        op.setDoctor(newDoctor);
        op.setDepartment(newDoctor.getDepartment());
        if (reason != null && !reason.trim().isEmpty()) {
            op.setRemarks((op.getRemarks() != null ? op.getRemarks() + " | " : "") + "Doctor changed: " + reason);
        }
        return registrationRepo.save(op);
    }

    @Transactional
    public OpRegistration modifyRegistrationPatientType(Long opId, String newType, String reason) {
        OpRegistration op = registrationRepo.findById(opId)
                .orElseThrow(() -> new RuntimeException("OP Registration not found with id: " + opId));

        op.setPatientType(newType);
        if (reason != null && !reason.trim().isEmpty()) {
            op.setRemarks((op.getRemarks() != null ? op.getRemarks() + " | " : "") + "Patient Type changed: " + reason);
        }
        return registrationRepo.save(op);
    }

    @Transactional
    public OpRegistration modifyRegistrationPaymentType(Long opId, String newPaymentType, String reason) {
        OpRegistration op = registrationRepo.findById(opId)
                .orElseThrow(() -> new RuntimeException("OP Registration not found with id: " + opId));

        op.setPaymentType(newPaymentType);
        if (reason != null && !reason.trim().isEmpty()) {
            op.setRemarks((op.getRemarks() != null ? op.getRemarks() + " | " : "") + "Payment Type changed: " + reason);
        }
        return registrationRepo.save(op);
    }

    public List<OpRegistration> getAllRegistrations() {
        return registrationRepo.findAllByOrderByRegistrationDateDesc();
    }

    private LocalDateTime parseStartOfDay(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(dateStr.trim()).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseEndOfDay(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(dateStr.trim()).atTime(LocalTime.MAX);
        } catch (Exception e) {
            return null;
        }
    }

    public List<OpRegistration> searchRegistrations(String query) {
        return searchRegistrations(query, null, null);
    }

    public List<OpRegistration> searchRegistrations(String query, String startDate, String endDate) {
        List<OpRegistration> regs = (query == null || query.trim().isEmpty())
                ? registrationRepo.findAllByOrderByRegistrationDateDesc()
                : registrationRepo.searchRegistrations(query.trim());

        LocalDateTime start = parseStartOfDay(startDate);
        if (start != null) {
            regs = regs.stream()
                    .filter(r -> r.getRegistrationDate() != null && !r.getRegistrationDate().isBefore(start))
                    .collect(Collectors.toList());
        }

        LocalDateTime end = parseEndOfDay(endDate);
        if (end != null) {
            regs = regs.stream()
                    .filter(r -> r.getRegistrationDate() != null && !r.getRegistrationDate().isAfter(end))
                    .collect(Collectors.toList());
        }

        return regs;
    }

    public List<OpRegistration> getCopyRegistrations(String query, String startDate, String endDate, Boolean allRecords) {
        boolean showAll = Boolean.TRUE.equals(allRecords);
        String start = startDate;
        String end = endDate;

        if (!showAll && (start == null || start.trim().isEmpty()) && (end == null || end.trim().isEmpty())) {
            String today = LocalDate.now().toString();
            start = today;
            end = today;
        }

        return searchRegistrations(query, start, end);
    }

    public List<Patient> searchPatients(String query) {
        if (query == null || query.trim().isEmpty()) {
            return patientRepo.findAll();
        }
        return patientRepo.searchPatients(query.trim());
    }

    public OpRegistration getRegistrationById(Long id) {
        return registrationRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("OP Registration not found with id: " + id));
    }
}
