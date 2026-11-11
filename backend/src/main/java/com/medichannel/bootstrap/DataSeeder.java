package com.medichannel.bootstrap;

import com.medichannel.entity.Doctor;
import com.medichannel.entity.Notice;
import com.medichannel.entity.Patient;
import com.medichannel.entity.Room;
import com.medichannel.entity.Specialisation;
import com.medichannel.entity.UserAccount;
import com.medichannel.enums.Gender;
import com.medichannel.enums.Role;
import com.medichannel.repository.DoctorRepository;
import com.medichannel.repository.NoticeRepository;
import com.medichannel.repository.PatientRepository;
import com.medichannel.repository.RoomRepository;
import com.medichannel.repository.SpecialisationRepository;
import com.medichannel.repository.UserAccountRepository;
import com.medichannel.security.HmacSha256Hasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Map<String, String> SPECIALISATIONS = new LinkedHashMap<>();

    static {
        SPECIALISATIONS.put("Cardiology", "Heart and vascular care");
        SPECIALISATIONS.put("Dermatology", "Skin, hair and nail conditions");
        SPECIALISATIONS.put("ENT", "Ear, nose and throat");
        SPECIALISATIONS.put("General Medicine", "Adult general medical consultations");
        SPECIALISATIONS.put("Gynaecology", "Women's health");
        SPECIALISATIONS.put("Neurology", "Brain and nerve disorders");
        SPECIALISATIONS.put("Orthopaedics", "Bones, joints and sports injuries");
        SPECIALISATIONS.put("Paediatrics", "Child health");
        SPECIALISATIONS.put("Psychiatry", "Mental health consultations");
        SPECIALISATIONS.put("Radiology", "Imaging review clinics");
        SPECIALISATIONS.put("Surgery", "General surgical opinions");
        SPECIALISATIONS.put("Urology", "Urinary and men's health");
        SPECIALISATIONS.put("Ophthalmology", "Eye care");
        SPECIALISATIONS.put("Pulmonology", "Chest and lung clinics");
        SPECIALISATIONS.put("Endocrinology", "Hormone and diabetes care");
    }

    private final UserAccountRepository userAccountRepository;
    private final PatientRepository patientRepository;
    private final SpecialisationRepository specialisationRepository;
    private final DoctorRepository doctorRepository;
    private final RoomRepository roomRepository;
    private final NoticeRepository noticeRepository;
    private final PasswordEncoder passwordEncoder;
    private final HmacSha256Hasher hmacSha256Hasher;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminName;
    private final boolean seedDemo;
    private final String seedPassword;

    public DataSeeder(
            UserAccountRepository userAccountRepository,
            PatientRepository patientRepository,
            SpecialisationRepository specialisationRepository,
            DoctorRepository doctorRepository,
            RoomRepository roomRepository,
            NoticeRepository noticeRepository,
            PasswordEncoder passwordEncoder,
            HmacSha256Hasher hmacSha256Hasher,
            @Value("${medichannel.admin.email}") String adminEmail,
            @Value("${medichannel.admin.password}") String adminPassword,
            @Value("${medichannel.admin.full-name}") String adminName,
            @Value("${medichannel.seed.demo:true}") boolean seedDemo,
            @Value("${medichannel.seed.password:ChangeMe#1}") String seedPassword
    ) {
        this.userAccountRepository = userAccountRepository;
        this.patientRepository = patientRepository;
        this.specialisationRepository = specialisationRepository;
        this.doctorRepository = doctorRepository;
        this.roomRepository = roomRepository;
        this.noticeRepository = noticeRepository;
        this.passwordEncoder = passwordEncoder;
        this.hmacSha256Hasher = hmacSha256Hasher;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminName = adminName;
        this.seedDemo = seedDemo;
        this.seedPassword = seedPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedSpecialisations();
        seedRooms();
        UserAccount admin = ensureUser(adminEmail, adminName, adminPassword, Role.ADMIN, false);
        if (seedDemo) {
            ensureUser("manager@lankacare.lk", "Nimali Fernando", seedPassword, Role.MANAGER, false);
            UserAccount doctorUser = ensureUser("doctor@lankacare.lk", "Dr. Kavindu Perera", seedPassword, Role.DOCTOR, false);
            ensureDoctor(doctorUser);
            UserAccount patientUser = ensureUser("patient@lankacare.lk", "Amaya Silva", seedPassword, Role.CUSTOMER, false);
            ensurePatient(patientUser, "199512345678", "1995-12-03", Gender.FEMALE, "0771234567", "12 Flower Road, Colombo 07");
            seedNotice(admin);
        }
    }

    private void seedSpecialisations() {
        SPECIALISATIONS.forEach((name, description) -> {
            if (specialisationRepository.findByNameIgnoreCase(name).isEmpty()) {
                Specialisation specialisation = new Specialisation();
                specialisation.setName(name);
                specialisation.setDescription(description);
                specialisationRepository.save(specialisation);
            }
        });
    }

    private void seedRooms() {
        if (roomRepository.count() > 0) {
            return;
        }
        Map<String, String> rooms = Map.of(
                "Room 101", "Block A, Ground Floor",
                "Room 102", "Block A, Ground Floor",
                "Room 201", "Block B, First Floor",
                "Room 202", "Block B, First Floor",
                "Paediatric Clinic", "Block C, Ground Floor"
        );
        rooms.forEach((name, location) -> {
            Room room = new Room();
            room.setName(name);
            room.setLocation(location);
            roomRepository.save(room);
        });
    }

    private UserAccount ensureUser(String email, String fullName, String rawPassword, Role role, boolean mustChange) {
        String hmac = hmacSha256Hasher.hashEmail(email);
        return userAccountRepository.findByEmailHmac(hmac).orElseGet(() -> {
            UserAccount user = new UserAccount();
            user.setEmail(HmacSha256Hasher.normaliseEmail(email));
            user.setEmailHmac(hmac);
            user.setFullName(fullName);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.setRole(role);
            user.setMustChangePassword(mustChange);
            return userAccountRepository.save(user);
        });
    }

    private void ensureDoctor(UserAccount user) {
        if (doctorRepository.findByActiveTrue().stream().anyMatch(d -> d.getUser().getId().equals(user.getId()))) {
            return;
        }
        Specialisation specialisation = specialisationRepository.findByNameIgnoreCase("Cardiology")
                .orElseThrow();
        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setSpecialisation(specialisation);
        doctor.setSlmcNumber("SLMC-44021");
        doctor.setQualifications("MBBS (Colombo), MD (Cardiology)");
        doctor.setConsultationFee(new BigDecimal("3500.00"));
        doctor.setBio("Consultant cardiologist at Lanka Care Hospital.");
        doctorRepository.save(doctor);
    }

    private void ensurePatient(UserAccount user, String nic, String dob, Gender gender, String phone, String address) {
        if (patientRepository.findByUserId(user.getId()).isPresent()) {
            return;
        }
        Patient patient = new Patient();
        patient.setUser(user);
        patient.setNic(HmacSha256Hasher.normaliseNic(nic));
        patient.setNicHmac(hmacSha256Hasher.hashNic(nic));
        patient.setDateOfBirth(dob);
        patient.setGender(gender);
        patient.setPhone(phone);
        patient.setAddress(address);
        patientRepository.save(patient);
    }

    private void seedNotice(UserAccount admin) {
        if (!noticeRepository.findByPublishedTrueOrderByPublishedAtDesc().isEmpty()) {
            return;
        }
        Notice notice = new Notice();
        notice.setTitle("Channelling desk hours");
        notice.setBody("The channelling centre is open 7:00–20:00 daily. Please arrive 15 minutes before your number.");
        notice.setPublished(true);
        notice.setPublishedAt(Instant.now());
        notice.setCreatedBy(admin);
        noticeRepository.save(notice);
    }
}
