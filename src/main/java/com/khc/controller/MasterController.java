package com.khc.controller;

import com.khc.dto.*;
import com.khc.model.*;
import com.khc.service.MasterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/master")
public class MasterController {

    @Autowired
    private MasterService masterService;

    // --- Users (USER CREATION) ---
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<AppUser>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.ok(masterService.getAllUsers()));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<AppUser>> createUser(@RequestBody UserDto dto) {
        return ResponseEntity.ok(ApiResponse.ok("User created successfully", masterService.createUser(dto)));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<ApiResponse<AppUser>> updateUser(@PathVariable Long id, @RequestBody UserDto dto) {
        return ResponseEntity.ok(ApiResponse.ok("User updated successfully", masterService.updateUser(id, dto)));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        masterService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.ok("User deleted successfully", null));
    }

    // --- System Menus (from DB table) ---
    @GetMapping("/menus")
    public ResponseEntity<ApiResponse<List<AppMenu>>> getAllMenus() {
        return ResponseEntity.ok(ApiResponse.ok(masterService.getAllMenus()));
    }

    // --- Services (SERVICE MASTER: Service Name, Header, Department, OPRATE, IPRATE, Status) ---
    @GetMapping("/services")
    public ResponseEntity<ApiResponse<List<HospitalService>>> getAllServices(
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        List<HospitalService> list = activeOnly ? masterService.getActiveServices() : masterService.getAllServices(query);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/services")
    public ResponseEntity<ApiResponse<HospitalService>> createService(@RequestBody HospitalServiceDto dto) {
        return ResponseEntity.ok(ApiResponse.ok("Hospital service tariff created successfully", masterService.createService(dto)));
    }

    @PutMapping("/services/{id}")
    public ResponseEntity<ApiResponse<HospitalService>> updateService(@PathVariable Long id, @RequestBody HospitalServiceDto dto) {
        return ResponseEntity.ok(ApiResponse.ok("Hospital service tariff updated successfully", masterService.updateService(id, dto)));
    }

    @DeleteMapping("/services/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteService(@PathVariable Long id) {
        masterService.deleteService(id);
        return ResponseEntity.ok(ApiResponse.ok("Service tariff deleted successfully", null));
    }

    // --- Doctors (ADMIN) ---
    @GetMapping("/doctors")
    public ResponseEntity<ApiResponse<List<Doctor>>> getAllDoctors(@RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        List<Doctor> doctors = activeOnly ? masterService.getActiveDoctors() : masterService.getAllDoctors();
        return ResponseEntity.ok(ApiResponse.ok(doctors));
    }

    @PostMapping("/doctors")
    public ResponseEntity<ApiResponse<Doctor>> createDoctor(@RequestBody DoctorDto dto) {
        return ResponseEntity.ok(ApiResponse.ok("Doctor saved successfully", masterService.createDoctor(dto)));
    }

    @PutMapping("/doctors/{id}")
    public ResponseEntity<ApiResponse<Doctor>> updateDoctor(@PathVariable Long id, @RequestBody DoctorDto dto) {
        return ResponseEntity.ok(ApiResponse.ok("Doctor updated successfully", masterService.updateDoctor(id, dto)));
    }

    // --- Patient & Payment Types ---
    @GetMapping("/patient-types")
    public ResponseEntity<ApiResponse<List<PatientType>>> getPatientTypes() {
        return ResponseEntity.ok(ApiResponse.ok(masterService.getAllPatientTypes()));
    }

    @GetMapping("/payment-types")
    public ResponseEntity<ApiResponse<List<PaymentType>>> getPaymentTypes() {
        return ResponseEntity.ok(ApiResponse.ok(masterService.getAllPaymentTypes()));
    }

    // --- Location: Countries ---
    @GetMapping("/countries")
    public ResponseEntity<ApiResponse<List<Country>>> getCountries(@RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        List<Country> list = activeOnly ? masterService.getActiveCountries() : masterService.getAllCountries();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/countries")
    public ResponseEntity<ApiResponse<Country>> createCountry(@RequestBody CountryDto dto) {
        return ResponseEntity.ok(ApiResponse.ok("Country created successfully", masterService.createCountry(dto)));
    }

    @PutMapping("/countries/{id}")
    public ResponseEntity<ApiResponse<Country>> updateCountry(@PathVariable Integer id, @RequestBody CountryDto dto) {
        return ResponseEntity.ok(ApiResponse.ok("Country updated successfully", masterService.updateCountry(id, dto)));
    }

    @DeleteMapping("/countries/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCountry(@PathVariable Integer id) {
        masterService.deleteCountry(id);
        return ResponseEntity.ok(ApiResponse.ok("Country deleted successfully", null));
    }

    // --- Location: States ---
    @GetMapping("/states")
    public ResponseEntity<ApiResponse<List<State>>> getStates(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        List<State> list = activeOnly ? masterService.getActiveStates(countryId) : masterService.getAllStates(countryId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/states")
    public ResponseEntity<ApiResponse<State>> createState(@RequestBody StateDto dto) {
        return ResponseEntity.ok(ApiResponse.ok("State created successfully", masterService.createState(dto)));
    }

    @PutMapping("/states/{id}")
    public ResponseEntity<ApiResponse<State>> updateState(@PathVariable Integer id, @RequestBody StateDto dto) {
        return ResponseEntity.ok(ApiResponse.ok("State updated successfully", masterService.updateState(id, dto)));
    }

    @DeleteMapping("/states/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteState(@PathVariable Integer id) {
        masterService.deleteState(id);
        return ResponseEntity.ok(ApiResponse.ok("State deleted successfully", null));
    }

    // --- Location: Cities ---
    @GetMapping("/cities")
    public ResponseEntity<ApiResponse<List<City>>> getCities(
            @RequestParam(required = false) Integer stateId,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        List<City> list = activeOnly ? masterService.getActiveCities(stateId) : masterService.getAllCities(stateId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/cities")
    public ResponseEntity<ApiResponse<City>> createCity(@RequestBody CityDto dto) {
        return ResponseEntity.ok(ApiResponse.ok("City created successfully", masterService.createCity(dto)));
    }

    @PutMapping("/cities/{id}")
    public ResponseEntity<ApiResponse<City>> updateCity(@PathVariable Integer id, @RequestBody CityDto dto) {
        return ResponseEntity.ok(ApiResponse.ok("City updated successfully", masterService.updateCity(id, dto)));
    }

    @DeleteMapping("/cities/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCity(@PathVariable Integer id) {
        masterService.deleteCity(id);
        return ResponseEntity.ok(ApiResponse.ok("City deleted successfully", null));
    }

    // --- Admin Dashboard Stats ---
    @GetMapping("/admin/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAdminStats() {
        return ResponseEntity.ok(ApiResponse.ok(masterService.getAdminDashboardStats()));
    }

    // --- Change Password ---
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
