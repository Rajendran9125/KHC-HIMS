package com.khc.service;

import com.khc.dto.*;
import com.khc.model.*;
import com.khc.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MasterService {

    @Autowired
    private AppUserRepo userRepo;

    @Autowired
    private DoctorRepo doctorRepo;

    @Autowired
    private PatientTypeRepo patientTypeRepo;

    @Autowired
    private PaymentTypeRepo paymentTypeRepo;

    @Autowired
    private HospitalServiceRepo serviceRepo;

    @Autowired
    private OpRegistrationRepo registrationRepo;

    @Autowired
    private OpBillRepo billRepo;

    @Autowired
    private AppMenuRepo menuRepo;

    @Autowired
    private CountryRepo countryRepo;

    @Autowired
    private StateRepo stateRepo;

    @Autowired
    private CityRepo cityRepo;

    // --- Users ---
    public List<AppUser> getAllUsers() {
        return userRepo.findAll();
    }

    public AppUser createUser(UserDto dto) {
        if (userRepo.existsByUsername(dto.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + dto.getUsername());
        }
        AppUser user = new AppUser();
        user.setUsername(dto.getUsername());
        user.setPassword(dto.getPassword() != null && !dto.getPassword().trim().isEmpty() ? dto.getPassword() : "password123");
        user.setFullName(dto.getFullName());
        user.setEmail(dto.getEmail());
        user.setRole(dto.getRole() != null ? dto.getRole() : "USER");
        user.setDepartment(dto.getDepartment());
        user.setPhone(dto.getPhone());
        user.setAllowedMenus(dto.getAllowedMenus() != null ? dto.getAllowedMenus() : "");
        user.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        return userRepo.save(user);
    }

    public AppUser updateUser(Long id, UserDto dto) {
        AppUser user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        if (dto.getFullName() != null) user.setFullName(dto.getFullName());
        if (dto.getPassword() != null && !dto.getPassword().trim().isEmpty()) user.setPassword(dto.getPassword());
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        if (dto.getRole() != null) user.setRole(dto.getRole());
        if (dto.getDepartment() != null) user.setDepartment(dto.getDepartment());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getAllowedMenus() != null) user.setAllowedMenus(dto.getAllowedMenus());
        if (dto.getIsActive() != null) user.setIsActive(dto.getIsActive());
        return userRepo.save(user);
    }

    public void deleteUser(Long id) {
        userRepo.deleteById(id);
    }

    // --- System Menus (from DB table) ---
    public List<AppMenu> getAllMenus() {
        return menuRepo.findByIsActiveTrueOrderByDisplayOrderAsc();
    }

    // --- User Authentication / Login ---
    public LoginResp login(String username, String password) {
        AppUser user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        if (user.getIsActive() != null && !user.getIsActive()) {
            throw new RuntimeException("Account is inactive. Contact Administrator.");
        }

        // Check password (supports plain text or preset default)
        if (user.getPassword() != null && !user.getPassword().equals(password)) {
            throw new RuntimeException("Invalid username or password");
        }

        List<String> allowedMenusList = new ArrayList<>();
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            // ADMIN has ALL rights
            allowedMenusList = Arrays.asList(
                    "admin", "users", "services", "location-master", "change-password",
                    "op-new", "op-review", "patient-update",
                    "modify-doctor-reg", "modify-patient-type",
                    "op-billing", "copy-bill", "modify-doctor-bill", "modify-payment-type-bill", "refund-billing",
                    "report-billing", "report-revenue", "report-registration"
            );
        } else {
            // USER has selected menus only
            if (user.getAllowedMenus() != null && !user.getAllowedMenus().trim().isEmpty()) {
                String[] menus = user.getAllowedMenus().split(",");
                for (String m : menus) {
                    if (!m.trim().isEmpty()) allowedMenusList.add(m.trim());
                }
            } else {
                // Default staff/user menus: Registration & Billing
                allowedMenusList = Arrays.asList(
                        "op-new", "op-review", "patient-update",
                        "op-billing", "report-registration"
                );
            }
        }

        return new LoginResp(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole(),
                user.getDepartment(),
                allowedMenusList
        );
    }

    public void resetPassword(String username, String newPassword, String emailOrPhone) {
        if (username == null || username.trim().isEmpty()) {
            throw new RuntimeException("Username is required");
        }
        if (emailOrPhone == null || emailOrPhone.trim().isEmpty()) {
            throw new RuntimeException("Registered Email or Phone number is mandatory for identity verification");
        }
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new RuntimeException("New password cannot be empty");
        }
        if (newPassword.trim().length() < 4) {
            throw new RuntimeException("Password must be at least 4 characters long");
        }

        AppUser user = userRepo.findByUsername(username.trim())
                .orElseThrow(() -> new RuntimeException("User account '" + username + "' not found"));

        String input = emailOrPhone.trim().toLowerCase();
        String inputDigits = input.replaceAll("[^0-9]", "");

        boolean emailMatch = user.getEmail() != null && !user.getEmail().trim().isEmpty()
                && user.getEmail().trim().toLowerCase().equals(input);

        boolean phoneMatch = false;
        if (user.getPhone() != null && !user.getPhone().trim().isEmpty() && !inputDigits.isEmpty()) {
            String userDigits = user.getPhone().trim().replaceAll("[^0-9]", "");
            phoneMatch = userDigits.equals(inputDigits)
                    || (userDigits.length() >= 10 && inputDigits.endsWith(userDigits))
                    || (inputDigits.length() >= 10 && userDigits.endsWith(inputDigits));
        }

        if (!emailMatch && !phoneMatch) {
            throw new RuntimeException("Verification failed: The provided Email or Phone does not match the registered records for user '" + username + "'");
        }

        user.setPassword(newPassword.trim());
        userRepo.save(user);
    }

    public void changePassword(ChangePasswordReq req) {
        if (req == null) {
            throw new RuntimeException("Request body cannot be null");
        }
        if (req.getUsername() == null || req.getUsername().trim().isEmpty()) {
            throw new RuntimeException("Username is required");
        }
        if (req.getOldPassword() == null || req.getOldPassword().trim().isEmpty()) {
            throw new RuntimeException("Old password is required");
        }
        if (req.getNewPassword() == null || req.getNewPassword().trim().isEmpty()) {
            throw new RuntimeException("New password cannot be empty");
        }
        if (req.getNewPassword().trim().length() < 4) {
            throw new RuntimeException("New password must be at least 4 characters long");
        }
        if (req.getRePassword() == null || !req.getNewPassword().equals(req.getRePassword())) {
            throw new RuntimeException("New password and Confirm/Re password do not match");
        }

        AppUser user = userRepo.findByUsername(req.getUsername().trim())
                .orElseThrow(() -> new RuntimeException("User account '" + req.getUsername().trim() + "' not found"));

        if (!req.getOldPassword().equals(user.getPassword())) {
            throw new RuntimeException("Incorrect old password. Please verify and try again.");
        }

        if (req.getNewPassword().equals(req.getOldPassword())) {
            throw new RuntimeException("New password cannot be the same as your old password");
        }

        user.setPassword(req.getNewPassword().trim());
        userRepo.save(user);
    }

    // --- Hospital Services (Tariff & Amount Master) ---
    public List<HospitalService> getAllServices(String query) {
        if (query != null && !query.trim().isEmpty()) {
            return serviceRepo.searchServices(query.trim());
        }
        return serviceRepo.findAll();
    }

    public List<HospitalService> getActiveServices() {
        return serviceRepo.findByIsActiveTrue();
    }

    public HospitalService createService(HospitalServiceDto dto) {
        HospitalService service = new HospitalService();
        service.setServiceName(dto.getServiceName());
        service.setHeader(dto.getHeader() != null ? dto.getHeader().toUpperCase() : "GENERAL");
        service.setDepartment(dto.getDepartment());
        service.setOpRate(dto.getOpRate() != null ? dto.getOpRate() : 0.0);
        service.setIpRate(dto.getIpRate() != null ? dto.getIpRate() : 0.0);
        service.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        return serviceRepo.save(service);
    }

    public HospitalService updateService(Long id, HospitalServiceDto dto) {
        HospitalService service = serviceRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found with id: " + id));

        if (dto.getServiceName() != null) service.setServiceName(dto.getServiceName());
        if (dto.getHeader() != null) service.setHeader(dto.getHeader().toUpperCase());
        if (dto.getDepartment() != null) service.setDepartment(dto.getDepartment());
        if (dto.getOpRate() != null) service.setOpRate(dto.getOpRate());
        if (dto.getIpRate() != null) service.setIpRate(dto.getIpRate());
        if (dto.getIsActive() != null) {
            service.setIsActive(dto.getIsActive());
        }
        return serviceRepo.save(service);
    }

    public void deleteService(Long id) {
        serviceRepo.deleteById(id);
    }

    // --- Doctors ---
    public List<Doctor> getAllDoctors() {
        return doctorRepo.findAll();
    }

    public List<Doctor> getActiveDoctors() {
        return doctorRepo.findByIsActiveTrue();
    }

    public Doctor createDoctor(DoctorDto dto) {
        Doctor doctor = new Doctor();
        doctor.setDoctorName(dto.getDoctorName());
        doctor.setSpecialization(dto.getSpecialization());
        doctor.setDepartment(dto.getDepartment());
        doctor.setConsultationFee(dto.getConsultationFee() != null ? dto.getConsultationFee() : 200.0);
        doctor.setPhone(dto.getPhone());
        doctor.setRoomNo(dto.getRoomNo());
        doctor.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        return doctorRepo.save(doctor);
    }

    public Doctor updateDoctor(Long id, DoctorDto dto) {
        Doctor doctor = doctorRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Doctor not found with id: " + id));
        if (dto.getDoctorName() != null) doctor.setDoctorName(dto.getDoctorName());
        if (dto.getSpecialization() != null) doctor.setSpecialization(dto.getSpecialization());
        if (dto.getDepartment() != null) doctor.setDepartment(dto.getDepartment());
        if (dto.getConsultationFee() != null) doctor.setConsultationFee(dto.getConsultationFee());
        if (dto.getPhone() != null) doctor.setPhone(dto.getPhone());
        if (dto.getRoomNo() != null) doctor.setRoomNo(dto.getRoomNo());
        if (dto.getIsActive() != null) doctor.setIsActive(dto.getIsActive());
        return doctorRepo.save(doctor);
    }

    // --- Patient & Payment Types ---
    public List<PatientType> getAllPatientTypes() {
        return patientTypeRepo.findAll();
    }

    public List<PaymentType> getAllPaymentTypes() {
        return paymentTypeRepo.findAll();
    }

    // --- Location: Countries ---
    public List<Country> getAllCountries() {
        return countryRepo.findAll();
    }

    public List<Country> getActiveCountries() {
        return countryRepo.findByIsActiveTrue();
    }

    public Country createCountry(CountryDto dto) {
        Country country = new Country();
        country.setCountryName(dto.getCountryName());
        country.setCountryCode(dto.getCountryCode());
        country.setPhoneCode(dto.getPhoneCode());
        country.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        return countryRepo.save(country);
    }

    public Country updateCountry(Integer id, CountryDto dto) {
        Country country = countryRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Country not found with id: " + id));
        if (dto.getCountryName() != null) country.setCountryName(dto.getCountryName());
        if (dto.getCountryCode() != null) country.setCountryCode(dto.getCountryCode());
        if (dto.getPhoneCode() != null) country.setPhoneCode(dto.getPhoneCode());
        if (dto.getIsActive() != null) country.setIsActive(dto.getIsActive());
        return countryRepo.save(country);
    }

    public void deleteCountry(Integer id) {
        countryRepo.deleteById(id);
    }

    // --- Location: States ---
    public List<State> getAllStates(Integer countryId) {
        if (countryId != null && countryId > 0) {
            return stateRepo.findByCountryId(countryId);
        }
        return stateRepo.findAll();
    }

    public List<State> getActiveStates(Integer countryId) {
        if (countryId != null && countryId > 0) {
            return stateRepo.findByCountryIdAndIsActiveTrue(countryId);
        }
        return stateRepo.findByIsActiveTrue();
    }

    public State createState(StateDto dto) {
        Country country = countryRepo.findById(dto.getCountryId())
                .orElseThrow(() -> new RuntimeException("Country not found with id: " + dto.getCountryId()));
        State state = new State();
        state.setStateName(dto.getStateName());
        state.setStateCode(dto.getStateCode());
        state.setCountry(country);
        state.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        return stateRepo.save(state);
    }

    public State updateState(Integer id, StateDto dto) {
        State state = stateRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("State not found with id: " + id));
        if (dto.getStateName() != null) state.setStateName(dto.getStateName());
        if (dto.getStateCode() != null) state.setStateCode(dto.getStateCode());
        if (dto.getCountryId() != null) {
            Country country = countryRepo.findById(dto.getCountryId())
                    .orElseThrow(() -> new RuntimeException("Country not found with id: " + dto.getCountryId()));
            state.setCountry(country);
        }
        if (dto.getIsActive() != null) state.setIsActive(dto.getIsActive());
        return stateRepo.save(state);
    }

    public void deleteState(Integer id) {
        stateRepo.deleteById(id);
    }

    // --- Location: Cities ---
    public List<City> getAllCities(Integer stateId) {
        if (stateId != null && stateId > 0) {
            return cityRepo.findByStateId(stateId);
        }
        return cityRepo.findAll();
    }

    public List<City> getActiveCities(Integer stateId) {
        if (stateId != null && stateId > 0) {
            return cityRepo.findByStateIdAndIsActiveTrue(stateId);
        }
        return cityRepo.findByIsActiveTrue();
    }

    public City createCity(CityDto dto) {
        State state = stateRepo.findById(dto.getStateId())
                .orElseThrow(() -> new RuntimeException("State not found with id: " + dto.getStateId()));
        City city = new City();
        city.setCityName(dto.getCityName());
        city.setState(state);
        city.setDefaultPincode(dto.getDefaultPincode());
        city.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        return cityRepo.save(city);
    }

    public City updateCity(Integer id, CityDto dto) {
        City city = cityRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("City not found with id: " + id));
        if (dto.getCityName() != null) city.setCityName(dto.getCityName());
        if (dto.getDefaultPincode() != null) city.setDefaultPincode(dto.getDefaultPincode());
        if (dto.getStateId() != null) {
            State state = stateRepo.findById(dto.getStateId())
                    .orElseThrow(() -> new RuntimeException("State not found with id: " + dto.getStateId()));
            city.setState(state);
        }
        if (dto.getIsActive() != null) city.setIsActive(dto.getIsActive());
        return cityRepo.save(city);
    }

    public void deleteCity(Integer id) {
        cityRepo.deleteById(id);
    }

    // --- Admin Dashboard Summary ---
    public Map<String, Object> getAdminDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDoctors", doctorRepo.count());
        stats.put("totalUsers", userRepo.count());
        stats.put("totalServices", serviceRepo.count());
        stats.put("totalRegistrations", registrationRepo.count());
        stats.put("totalBills", billRepo.count());
        Double revenue = billRepo.sumPaidRevenue();
        stats.put("totalRevenue", revenue != null ? revenue : 0.0);
        return stats;
    }
}
