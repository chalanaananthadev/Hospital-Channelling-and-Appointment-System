package com.medichannel.bootstrap;

import com.medichannel.entity.AppNotification;
import com.medichannel.entity.Appointment;
import com.medichannel.entity.ChannelSession;
import com.medichannel.entity.ChatMessage;
import com.medichannel.entity.Consultation;
import com.medichannel.entity.Doctor;
import com.medichannel.entity.Feedback;
import com.medichannel.entity.Notice;
import com.medichannel.entity.Patient;
import com.medichannel.entity.Room;
import com.medichannel.entity.Specialisation;
import com.medichannel.entity.UserAccount;
import com.medichannel.enums.AppointmentStatus;
import com.medichannel.enums.FeedbackStatus;
import com.medichannel.enums.Gender;
import com.medichannel.enums.NotificationType;
import com.medichannel.enums.PaymentMethod;
import com.medichannel.enums.PaymentStatus;
import com.medichannel.enums.Role;
import com.medichannel.enums.SessionStatus;
import com.medichannel.enums.SessionType;
import com.medichannel.repository.AppNotificationRepository;
import com.medichannel.repository.AppointmentRepository;
import com.medichannel.repository.ChannelSessionRepository;
import com.medichannel.repository.ChatMessageRepository;
import com.medichannel.repository.ConsultationRepository;
import com.medichannel.repository.DoctorRepository;
import com.medichannel.repository.FeedbackRepository;
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
import java.time.LocalDate;
import java.time.LocalTime;
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
    private final ChannelSessionRepository channelSessionRepository;
    private final AppointmentRepository appointmentRepository;
    private final ConsultationRepository consultationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final FeedbackRepository feedbackRepository;
    private final AppNotificationRepository appNotificationRepository;
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
            ChannelSessionRepository channelSessionRepository,
            AppointmentRepository appointmentRepository,
            ConsultationRepository consultationRepository,
            ChatMessageRepository chatMessageRepository,
            FeedbackRepository feedbackRepository,
            AppNotificationRepository appNotificationRepository,
            PasswordEncoder passwordEncoder,
            HmacSha256Hasher hmacSha256Hasher,
            @Value("${medichannel.admin.email}") String adminEmail,
            @Value("${medichannel.admin.password}") String adminPassword,
            @Value("${medichannel.admin.full-name}") String adminName,
            @Value("${medichannel.seed.demo:false}") boolean seedDemo,
            @Value("${medichannel.seed.password:}") String seedPassword
    ) {
        if (seedDemo && (seedPassword == null || seedPassword.trim().isEmpty())) {
            throw new IllegalStateException("medichannel.seed.demo is enabled (SEED_DEMO=true), but SEED_PASSWORD is missing or blank.");
        }
        this.userAccountRepository = userAccountRepository;
        this.patientRepository = patientRepository;
        this.specialisationRepository = specialisationRepository;
        this.doctorRepository = doctorRepository;
        this.roomRepository = roomRepository;
        this.noticeRepository = noticeRepository;
        this.channelSessionRepository = channelSessionRepository;
        this.appointmentRepository = appointmentRepository;
        this.consultationRepository = consultationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.feedbackRepository = feedbackRepository;
        this.appNotificationRepository = appNotificationRepository;
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
            seedRichDemoDataset(admin);
        }
    }

    private void seedRichDemoDataset(UserAccount admin) {
        // Idempotency: skip if already seeded
        if (appointmentRepository.count() > 0) {
            return;
        }

        // 1. Manager (mustChangePassword = true for testing change-password flow)
        ensureUser("manager@lankacare.lk", "Nimali Fernando", seedPassword, Role.MANAGER, true);

        // 2. Three Doctors in distinct specialisations
        UserAccount doc1User = ensureUser("doctor.kavindu@lankacare.lk", "Dr. Kavindu Perera", seedPassword, Role.DOCTOR, false);
        Doctor doctor1 = ensureDoctor(
                doc1User,
                "Cardiology",
                "SLMC-44021",
                "MBBS (Colombo), MD (Cardiology)",
                new BigDecimal("3500.00"),
                "Consultant cardiologist at Lanka Care Hospital."
        );

        UserAccount doc2User = ensureUser("doctor.rohini@lankacare.lk", "Dr. Rohini Wickramasinghe", seedPassword, Role.DOCTOR, false);
        Doctor doctor2 = ensureDoctor(
                doc2User,
                "Dermatology",
                "SLMC-31890",
                "MBBS (Peradeniya), MD (Dermatology)",
                new BigDecimal("3000.00"),
                "Consultant dermatologist specialising in clinical and aesthetic dermatology."
        );

        UserAccount doc3User = ensureUser("doctor.asanka@lankacare.lk", "Dr. Asanka Senanayake", seedPassword, Role.DOCTOR, false);
        Doctor doctor3 = ensureDoctor(
                doc3User,
                "Paediatrics",
                "SLMC-52104",
                "MBBS (Ruhuna), DCH, MD (Paediatrics)",
                new BigDecimal("3200.00"),
                "Consultant paediatrician with 12+ years experience in child healthcare."
        );

        // 3. Four Patients (diverse NIC formats: new 12-digit and old 9-digit + V/X)
        UserAccount p1User = ensureUser("patient.amaya@lankacare.lk", "Amaya Silva", seedPassword, Role.CUSTOMER, false);
        Patient patient1 = ensurePatient(p1User, "199512345678", "1995-12-03", Gender.FEMALE, "0771234567", "12 Flower Road, Colombo 07");

        UserAccount p2User = ensureUser("patient.kamal@lankacare.lk", "Kamal Gunaratne", seedPassword, Role.CUSTOMER, false);
        Patient patient2 = ensurePatient(p2User, "851234567V", "1985-05-02", Gender.MALE, "0712345678", "45 Galle Road, Mount Lavinia");

        UserAccount p3User = ensureUser("patient.dilani@lankacare.lk", "Dilani Jayawardena", seedPassword, Role.CUSTOMER, false);
        Patient patient3 = ensurePatient(p3User, "200056789012", "2000-08-14", Gender.FEMALE, "0753456789", "78 Kandy Road, Kiribathgoda");

        UserAccount p4User = ensureUser("patient.saman@lankacare.lk", "Saman Kumara", seedPassword, Role.CUSTOMER, false);
        Patient patient4 = ensurePatient(p4User, "782345678X", "1978-08-21", Gender.MALE, "0764567890", "23 Temple Road, Nugegoda");

        // 4. Rooms
        Room room101 = roomRepository.findByName("Room 101").orElse(null);
        Room room102 = roomRepository.findByName("Room 102").orElse(null);

        // 5. Sessions: Today Morning, Today Evening, Tomorrow Morning, Next Week Cancelled
        LocalDate today = LocalDate.now();

        ChannelSession session1 = new ChannelSession();
        session1.setDoctor(doctor1);
        session1.setRoom(room101);
        session1.setSessionDate(today);
        session1.setStartTime(LocalTime.of(8, 30));
        session1.setEndTime(LocalTime.of(11, 30));
        session1.setSessionType(SessionType.PHYSICAL);
        session1.setMaxPatients(15);
        session1.setMinutesPerPatient(12);
        session1.setFee(doctor1.getConsultationFee());
        session1.setStatus(SessionStatus.IN_PROGRESS);
        session1.setNowServing(3);
        channelSessionRepository.save(session1);

        ChannelSession session2 = new ChannelSession();
        session2.setDoctor(doctor2);
        session2.setRoom(room102);
        session2.setSessionDate(today);
        session2.setStartTime(LocalTime.of(16, 0));
        session2.setEndTime(LocalTime.of(19, 0));
        session2.setSessionType(SessionType.PHYSICAL);
        session2.setMaxPatients(12);
        session2.setMinutesPerPatient(15);
        session2.setFee(doctor2.getConsultationFee());
        session2.setStatus(SessionStatus.OPEN);
        channelSessionRepository.save(session2);

        ChannelSession session3 = new ChannelSession();
        session3.setDoctor(doctor3);
        session3.setRoom(null); // ONLINE session must not have room
        session3.setSessionDate(today.plusDays(1));
        session3.setStartTime(LocalTime.of(9, 0));
        session3.setEndTime(LocalTime.of(12, 0));
        session3.setSessionType(SessionType.ONLINE);
        session3.setMaxPatients(10);
        session3.setMinutesPerPatient(15);
        session3.setFee(doctor3.getConsultationFee());
        session3.setStatus(SessionStatus.SCHEDULED);
        channelSessionRepository.save(session3);

        ChannelSession session4 = new ChannelSession();
        session4.setDoctor(doctor1);
        session4.setRoom(room101);
        session4.setSessionDate(today.plusDays(7));
        session4.setStartTime(LocalTime.of(8, 30));
        session4.setEndTime(LocalTime.of(11, 30));
        session4.setSessionType(SessionType.PHYSICAL);
        session4.setMaxPatients(15);
        session4.setMinutesPerPatient(12);
        session4.setFee(doctor1.getConsultationFee());
        session4.setStatus(SessionStatus.CANCELLED);
        channelSessionRepository.save(session4);

        // 6. Appointments covering all 6 statuses: BOOKED, ARRIVED, IN_CONSULTATION, COMPLETED, NO_SHOW, CANCELLED
        // Appointment 1: COMPLETED (Session 1, Patient 1)
        Appointment apt1 = new Appointment();
        apt1.setSession(session1);
        apt1.setPatient(patient1);
        apt1.setAppointmentNumber(1);
        apt1.setAppointmentType(SessionType.PHYSICAL);
        apt1.setReasonForVisit("Routine cardiac follow-up and ECG review");
        apt1.setStatus(AppointmentStatus.COMPLETED);
        apt1.setPaymentStatus(PaymentStatus.PAID);
        apt1.setPaymentMethod(PaymentMethod.CASH);
        apt1.setEstimatedTime(LocalTime.of(8, 30));
        apt1.setBookingReference("LC-" + today.toString().replace("-", "") + "-001");
        appointmentRepository.save(apt1);

        // Appointment 2: COMPLETED (Session 1, Patient 2)
        Appointment apt2 = new Appointment();
        apt2.setSession(session1);
        apt2.setPatient(patient2);
        apt2.setAppointmentNumber(2);
        apt2.setAppointmentType(SessionType.PHYSICAL);
        apt2.setReasonForVisit("Chest tightness during brisk exercise");
        apt2.setStatus(AppointmentStatus.COMPLETED);
        apt2.setPaymentStatus(PaymentStatus.PAID);
        apt2.setPaymentMethod(PaymentMethod.CARD);
        apt2.setEstimatedTime(LocalTime.of(8, 42));
        apt2.setBookingReference("LC-" + today.toString().replace("-", "") + "-002");
        appointmentRepository.save(apt2);

        // Appointment 3: IN_CONSULTATION (Session 1, Patient 3)
        Appointment apt3 = new Appointment();
        apt3.setSession(session1);
        apt3.setPatient(patient3);
        apt3.setAppointmentNumber(3);
        apt3.setAppointmentType(SessionType.PHYSICAL);
        apt3.setReasonForVisit("Mild palpitations and family history consultation");
        apt3.setStatus(AppointmentStatus.IN_CONSULTATION);
        apt3.setPaymentStatus(PaymentStatus.PAID);
        apt3.setPaymentMethod(PaymentMethod.CARD);
        apt3.setEstimatedTime(LocalTime.of(8, 54));
        apt3.setBookingReference("LC-" + today.toString().replace("-", "") + "-003");
        appointmentRepository.save(apt3);

        // Appointment 4: ARRIVED (Session 1, Patient 4)
        Appointment apt4 = new Appointment();
        apt4.setSession(session1);
        apt4.setPatient(patient4);
        apt4.setAppointmentNumber(4);
        apt4.setAppointmentType(SessionType.PHYSICAL);
        apt4.setReasonForVisit("Hypertension medication dosage check");
        apt4.setStatus(AppointmentStatus.ARRIVED);
        apt4.setPaymentStatus(PaymentStatus.PAID);
        apt4.setPaymentMethod(PaymentMethod.CASH);
        apt4.setEstimatedTime(LocalTime.of(9, 6));
        apt4.setBookingReference("LC-" + today.toString().replace("-", "") + "-004");
        appointmentRepository.save(apt4);

        // Appointment 5: BOOKED (Session 2, Patient 1)
        Appointment apt5 = new Appointment();
        apt5.setSession(session2);
        apt5.setPatient(patient1);
        apt5.setAppointmentNumber(1);
        apt5.setAppointmentType(SessionType.PHYSICAL);
        apt5.setReasonForVisit("Skin allergy and recurring dermatitis");
        apt5.setStatus(AppointmentStatus.BOOKED);
        apt5.setPaymentStatus(PaymentStatus.PENDING);
        apt5.setEstimatedTime(LocalTime.of(16, 0));
        apt5.setBookingReference("LC-" + today.toString().replace("-", "") + "-005");
        appointmentRepository.save(apt5);

        // Appointment 6: NO_SHOW (Session 2, Patient 2)
        Appointment apt6 = new Appointment();
        apt6.setSession(session2);
        apt6.setPatient(patient2);
        apt6.setAppointmentNumber(2);
        apt6.setAppointmentType(SessionType.PHYSICAL);
        apt6.setReasonForVisit("Eczema assessment");
        apt6.setStatus(AppointmentStatus.NO_SHOW);
        apt6.setPaymentStatus(PaymentStatus.PENDING);
        apt6.setEstimatedTime(LocalTime.of(16, 15));
        apt6.setBookingReference("LC-" + today.toString().replace("-", "") + "-006");
        appointmentRepository.save(apt6);

        // Appointment 7: BOOKED ONLINE (Session 3, Patient 3)
        Appointment apt7 = new Appointment();
        apt7.setSession(session3);
        apt7.setPatient(patient3);
        apt7.setAppointmentNumber(1);
        apt7.setAppointmentType(SessionType.ONLINE);
        apt7.setReasonForVisit("Paediatric recurring cough follow-up");
        apt7.setStatus(AppointmentStatus.BOOKED);
        apt7.setPaymentStatus(PaymentStatus.PAID);
        apt7.setPaymentMethod(PaymentMethod.CARD);
        apt7.setEstimatedTime(LocalTime.of(9, 0));
        apt7.setBookingReference("LC-" + today.toString().replace("-", "") + "-007");
        appointmentRepository.save(apt7);

        // Appointment 8: CANCELLED (Session 4, Patient 4)
        Appointment apt8 = new Appointment();
        apt8.setSession(session4);
        apt8.setPatient(patient4);
        apt8.setAppointmentNumber(1);
        apt8.setAppointmentType(SessionType.PHYSICAL);
        apt8.setReasonForVisit("Annual cardiac check-up");
        apt8.setStatus(AppointmentStatus.CANCELLED);
        apt8.setPaymentStatus(PaymentStatus.PAID);
        apt8.setPaymentMethod(PaymentMethod.CARD);
        apt8.setEstimatedTime(LocalTime.of(8, 30));
        apt8.setBookingReference("LC-" + today.toString().replace("-", "") + "-008");
        apt8.setCancelledAt(Instant.now());
        appointmentRepository.save(apt8);

        // 7. Consultation (completed, encrypted diagnosis, advice, and prescription)
        Consultation consultation1 = new Consultation();
        consultation1.setAppointment(apt1);
        consultation1.setDoctor(doctor1);
        consultation1.setDiagnosis("Stage 1 Essential Hypertension with normal sinus rhythm.");
        consultation1.setAdvice("Adopt DASH dietary pattern. Limit dietary sodium to < 2000mg/day. Regular walking 30 mins 5 days/week.");
        consultation1.setPrescription("Tab. Losartan Potassium 50mg - 1 tablet once daily in the morning for 30 days.\nTab. Atorvastatin 10mg - 1 tablet at bedtime for 30 days.");
        consultation1.setReadOnly(true);
        consultation1.setStartedAt(Instant.now().minusSeconds(3600));
        consultation1.setEndedAt(Instant.now().minusSeconds(2400));
        consultationRepository.save(consultation1);

        // 8. Online Consultation & Encrypted Chat Messages (Exercise AES-256-GCM converter)
        Consultation consultationOnline = new Consultation();
        consultationOnline.setAppointment(apt7);
        consultationOnline.setDoctor(doctor3);
        consultationOnline.setDiagnosis("Mild allergic bronchitis under observation.");
        consultationOnline.setAdvice("Maintain adequate hydration. Keep room ventilated.");
        consultationOnline.setPrescription("Syr. Cetirizine 5mg/5ml - 2.5ml once daily at night for 5 days.");
        consultationOnline.setReadOnly(false);
        consultationOnline.setStartedAt(Instant.now().minusSeconds(600));
        consultationRepository.save(consultationOnline);

        ChatMessage chatMsg1 = new ChatMessage();
        chatMsg1.setConsultation(consultationOnline);
        chatMsg1.setSender(p3User);
        chatMsg1.setBody("Good morning Doctor, I have uploaded the child's latest chest auscultation report.");
        chatMessageRepository.save(chatMsg1);

        ChatMessage chatMsg2 = new ChatMessage();
        chatMsg2.setConsultation(consultationOnline);
        chatMsg2.setSender(doc3User);
        chatMsg2.setBody("Good morning. The lungs sound clear; continue the cetirizine as prescribed for 5 days.");
        chatMessageRepository.save(chatMsg2);

        // 9. Feedback (one APPROVED, one PENDING)
        Feedback fb1 = new Feedback();
        fb1.setAppointment(apt1);
        fb1.setPatient(patient1);
        fb1.setDoctor(doctor1);
        fb1.setRating(5);
        fb1.setComment("Dr. Kavindu explained my ECG with exceptional clarity and gave practical lifestyle advice.");
        fb1.setStatus(FeedbackStatus.APPROVED);
        feedbackRepository.save(fb1);

        Feedback fb2 = new Feedback();
        fb2.setAppointment(apt2);
        fb2.setPatient(patient2);
        fb2.setDoctor(doctor1);
        fb2.setRating(4);
        fb2.setComment("Session was punctual and reception process was swift. Very satisfied.");
        fb2.setStatus(FeedbackStatus.PENDING);
        feedbackRepository.save(fb2);

        // 10. Notifications of each of the 5 types
        seedNotification(p1User, NotificationType.APPOINTMENT_CONFIRMED, "Appointment Confirmed",
                "Your appointment #1 with Dr. Kavindu Perera has been confirmed.", session1, apt1);

        seedNotification(p4User, NotificationType.QUEUE_UPDATE, "Live Queue Update",
                "Now serving number 3. Please be present at Waiting Area A, Room 101.", session1, apt4);

        seedNotification(p3User, NotificationType.DELAY_ALERT, "Doctor Delay Notice",
                "Dr. Kavindu Perera's clinic is running approximately 10 minutes delayed.", session1, apt3);

        seedNotification(p1User, NotificationType.PRESCRIPTION_READY, "Prescription Ready",
                "Your digital consultation record and prescription are ready to download.", session1, apt1);

        seedNotification(p2User, NotificationType.SYSTEM, "Welcome to MediChannel",
                "Your Lanka Care Hospital profile is verified. Access your health records anytime.", null, null);

        // 11. Notice
        seedNotice(admin);
    }

    private void seedNotification(
            UserAccount user,
            NotificationType type,
            String title,
            String body,
            ChannelSession session,
            Appointment appointment
    ) {
        AppNotification notification = new AppNotification();
        notification.setUser(user);
        notification.setNotificationType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setRead(false);
        notification.setSession(session);
        notification.setAppointment(appointment);
        appNotificationRepository.save(notification);
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

    private Doctor ensureDoctor(
            UserAccount user,
            String specialisationName,
            String slmcNumber,
            String qualifications,
            BigDecimal fee,
            String bio
    ) {
        return doctorRepository.findByActiveTrue().stream()
                .filter(d -> d.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElseGet(() -> {
                    Specialisation specialisation = specialisationRepository.findByNameIgnoreCase(specialisationName)
                            .orElseThrow(() -> new IllegalStateException("Specialisation not found: " + specialisationName));
                    Doctor doctor = new Doctor();
                    doctor.setUser(user);
                    doctor.setSpecialisation(specialisation);
                    doctor.setSlmcNumber(slmcNumber);
                    doctor.setQualifications(qualifications);
                    doctor.setConsultationFee(fee);
                    doctor.setBio(bio);
                    return doctorRepository.save(doctor);
                });
    }

    private Patient ensurePatient(UserAccount user, String nic, String dob, Gender gender, String phone, String address) {
        return patientRepository.findByUserId(user.getId()).orElseGet(() -> {
            Patient patient = new Patient();
            patient.setUser(user);
            patient.setNic(HmacSha256Hasher.normaliseNic(nic));
            patient.setNicHmac(hmacSha256Hasher.hashNic(nic));
            patient.setDateOfBirth(dob);
            patient.setGender(gender);
            patient.setPhone(phone);
            patient.setAddress(address);
            return patientRepository.save(patient);
        });
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
