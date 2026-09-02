package service;

import dao.AppointmentDAO;
import dao.BillDAO;
import dao.DAOFactory;
import dao.PatientDAO;
import dao.SettingsDAO;
import dao.TreatmentDAO;
import exception.ConflictException;
import exception.ForbiddenException;
import exception.NotFoundException;
import exception.ValidationException;
import model.Appointment;
import model.Bill;
import model.BillItem;
import model.Patient;
import model.Payment;
import model.Role;
import model.Treatment;
import model.User;
import service.notify.ClinicEvent;
import service.notify.EventBus;
import service.pricing.PricingStrategy;
import service.pricing.PricingStrategyFactory;
import util.IdGenerator;
import util.Validator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bill generation and settlement.
 *
 * The arithmetic is deliberately thin: the consultation fee plus the treatment
 * lines make the gross, a PricingStrategy decides the discount and the tax, and
 * the result is written in one transaction with its lines.
 */
public class BillingService {

    private final BillDAO billDAO;
    private final AppointmentDAO appointmentDAO;
    private final PatientDAO patientDAO;
    private final TreatmentDAO treatmentDAO;
    private final SettingsDAO settingsDAO;

    public BillingService() {
        this(DAOFactory.getInstance().bills(),
             DAOFactory.getInstance().appointments(),
             DAOFactory.getInstance().patients(),
             DAOFactory.getInstance().treatments(),
             DAOFactory.getInstance().settings());
    }

    public BillingService(BillDAO billDAO, AppointmentDAO appointmentDAO, PatientDAO patientDAO,
                          TreatmentDAO treatmentDAO, SettingsDAO settingsDAO) {
        this.billDAO = billDAO;
        this.appointmentDAO = appointmentDAO;
        this.patientDAO = patientDAO;
        this.treatmentDAO = treatmentDAO;
        this.settingsDAO = settingsDAO;
    }

    public Bill get(int id) {
        Bill bill = billDAO.findById(id);
        if (bill == null) {
            throw new NotFoundException("That bill does not exist");
        }
        return bill;
    }

    public List<Bill> list(String status, String from, String to, Integer patientId, Integer doctorId) {
        return billDAO.find(status, from, to, patientId, doctorId);
    }

    public Bill findForAppointment(int appointmentId) {
        return billDAO.findByAppointment(appointmentId);
    }

    /**
     * Builds a bill for one finished appointment.
     * Body: { appointmentId, items:[{treatmentId, quantity}], extraCharges:[{description, amount}],
     *         pricingStrategy?, manualDiscount? }
     */
    @SuppressWarnings("unchecked")
    public Bill generate(Map<String, Object> body, User actor) {
        if (!canBill(actor.getRole())) {
            throw new ForbiddenException("Your role cannot generate a bill");
        }

        int appointmentId = Validator.requireInt(body, "appointmentId");
        Appointment appointment = appointmentDAO.findById(appointmentId);
        if (appointment == null) {
            throw new NotFoundException("That appointment does not exist");
        }
        if (billDAO.findByAppointment(appointmentId) != null) {
            throw new ConflictException("A bill has already been generated for this appointment");
        }
        Patient patient = patientDAO.findById(appointment.getPatientId());

        BigDecimal consultationFee = appointment.getConsultationFee() == null
                ? BigDecimal.ZERO : appointment.getConsultationFee();

        List<BillItem> items = new ArrayList<>();
        items.add(new BillItem(null, "Consultation - " + appointment.getDoctorName(), 1, consultationFee));

        BigDecimal treatmentTotal = BigDecimal.ZERO;

        Object rawItems = body.get("items");
        if (rawItems instanceof List) {
            for (Object entry : (List<Object>) rawItems) {
                if (!(entry instanceof Map)) {
                    continue;
                }
                Map<String, Object> line = (Map<String, Object>) entry;
                int treatmentId = Validator.requireInt(line, "treatmentId");
                int quantity = Math.max(1, Validator.optionalInt(line, "quantity", 1));
                Treatment treatment = treatmentDAO.findById(treatmentId);
                if (treatment == null) {
                    throw new NotFoundException("Treatment " + treatmentId + " is not in the price list");
                }
                BillItem item = new BillItem(treatment.getId(), treatment.getName(), quantity, treatment.getBasePrice());
                items.add(item);
                treatmentTotal = treatmentTotal.add(item.getLineTotal());
            }
        }

        Object rawExtras = body.get("extraCharges");
        if (rawExtras instanceof List) {
            for (Object entry : (List<Object>) rawExtras) {
                if (!(entry instanceof Map)) {
                    continue;
                }
                Map<String, Object> line = (Map<String, Object>) entry;
                String description = Validator.requireText(line, "description", 150);
                BigDecimal amount = Validator.requireMoney(line, "amount");
                BillItem item = new BillItem(null, description, 1, amount);
                items.add(item);
                treatmentTotal = treatmentTotal.add(amount);
            }
        }

        BigDecimal gross = consultationFee.add(treatmentTotal).setScale(2, RoundingMode.HALF_UP);

        BigDecimal vipPercent = new BigDecimal(settingsDAO.get("billing.vip.discount.percent", "10"));
        BigDecimal taxPercent = new BigDecimal(settingsDAO.get("billing.tax.percent", "0"));

        PricingStrategy strategy = body.containsKey("pricingStrategy")
                ? PricingStrategyFactory.byName(String.valueOf(body.get("pricingStrategy")), vipPercent, consultationFee)
                : PricingStrategyFactory.forPatient(patient, vipPercent, consultationFee);

        BigDecimal discount = strategy.calculateDiscount(gross);
        BigDecimal manualDiscount = Validator.optionalMoney(body, "manualDiscount", BigDecimal.ZERO);
        discount = discount.add(manualDiscount);
        if (discount.compareTo(gross) > 0) {
            throw new ValidationException("The discount cannot be larger than the bill");
        }

        BigDecimal net = gross.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = strategy.calculateTax(net, taxPercent);
        BigDecimal total = net.add(tax).setScale(2, RoundingMode.HALF_UP);

        Bill.Builder builder = new Bill.Builder()
                .appointment(appointment.getId(), appointment.getAppointmentNo())
                .patient(appointment.getPatientId(), appointment.getPatientNo(), appointment.getPatientName())
                .doctor(appointment.getDoctorId(), appointment.getDoctorName())
                .consultationFee(consultationFee)
                .treatmentTotal(treatmentTotal.setScale(2, RoundingMode.HALF_UP))
                .discount(discount)
                .tax(tax)
                .total(total)
                .pricingStrategy(strategy.name())
                .generatedBy(actor.getId());
        for (BillItem item : items) {
            builder.item(item);
        }

        Bill bill = builder.build();
        bill.setBillNo(IdGenerator.billNo(settingsDAO.get("billing.bill.prefix", "SDC"), billDAO.nextSequence()));
        billDAO.insert(bill);

        if (!"COMPLETED".equals(appointment.getStatus())) {
            appointmentDAO.updateStatus(appointment.getId(), "COMPLETED");
        }

        publish(ClinicEvent.Type.BILL_GENERATED, actor, bill.getBillNo(),
                "Bill " + bill.getBillNo() + " raised for " + bill.getTotal());

        return get(bill.getId());
    }

    /** Records a settlement. The DB trigger flips the bill to PAID when covered. */
    public Bill pay(Map<String, Object> body, User actor) {
        int billId = Validator.requireInt(body, "billId");
        Bill bill = get(billId);

        if (actor.getRole() == Role.PATIENT) {
            Patient self = patientDAO.findByUserId(actor.getId());
            if (self == null || self.getId() != bill.getPatientId()) {
                throw new ForbiddenException("You can only pay your own bill");
            }
        } else if (!canBill(actor.getRole())) {
            throw new ForbiddenException("Your role cannot take a payment");
        }

        if ("PAID".equals(bill.getStatus())) {
            throw new ConflictException("This bill has already been settled");
        }
        if ("CANCELLED".equals(bill.getStatus())) {
            throw new ConflictException("This bill was cancelled");
        }

        BigDecimal amount = Validator.requireMoney(body, "amount");
        if (amount.signum() <= 0) {
            throw new ValidationException("The payment amount must be greater than zero");
        }
        BigDecimal balance = bill.getTotal().subtract(billDAO.amountPaid(billId));
        if (amount.compareTo(balance) > 0) {
            throw new ValidationException("That is more than the outstanding balance of " + balance);
        }

        Payment payment = new Payment();
        payment.setBillId(billId);
        payment.setAmount(amount);
        payment.setMethod(Validator.requireOneOf(body, "method", "CASH", "CARD", "ONLINE"));
        payment.setReference(Validator.optionalText(body, "reference", 60));
        payment.setReceivedBy(actor.getRole() == Role.PATIENT ? null : actor.getId());
        billDAO.insertPayment(payment);

        publish(ClinicEvent.Type.PAYMENT_RECEIVED, actor, bill.getBillNo(),
                "Payment of " + amount + " received for " + bill.getBillNo());

        return get(billId);
    }

    public List<Payment> payments(int billId) {
        get(billId);
        return billDAO.findPayments(billId);
    }

    public void cancel(int billId, User actor) {
        if (!actor.getRole().isAdministrative()) {
            throw new ForbiddenException("Only an admin can cancel a bill");
        }
        Bill bill = get(billId);
        if ("PAID".equals(bill.getStatus())) {
            throw new ConflictException("A settled bill cannot be cancelled");
        }
        billDAO.updateStatus(billId, "CANCELLED");
    }

    /** Everything the printable receipt needs, including the clinic header. */
    public Map<String, Object> receipt(int billId) {
        Bill bill = get(billId);
        Map<String, Object> receipt = new LinkedHashMap<>();
        Map<String, Object> clinic = new LinkedHashMap<>();
        clinic.put("name", settingsDAO.get("clinic.name", "Sunrise Dental Clinic"));
        clinic.put("address", settingsDAO.get("clinic.address", ""));
        clinic.put("phone", settingsDAO.get("clinic.phone", ""));
        clinic.put("email", settingsDAO.get("clinic.email", ""));
        clinic.put("currency", settingsDAO.get("billing.currency", "LKR"));
        receipt.put("clinic", clinic);
        receipt.put("bill", bill.toMap());
        List<Map<String, Object>> payments = new ArrayList<>();
        for (Payment p : billDAO.findPayments(billId)) {
            payments.add(p.toMap());
        }
        receipt.put("payments", payments);
        return receipt;
    }

    private boolean canBill(Role role) {
        return role == Role.CASHIER || role == Role.ADMIN || role == Role.SUPER_ADMIN || role == Role.DOCTOR;
    }

    private void publish(ClinicEvent.Type type, User actor, String entityId, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entity", "BILL");
        data.put("entityId", entityId);
        data.put("actorId", actor.getId());
        data.put("actorRole", actor.getRole().name());
        EventBus.get().publish(new ClinicEvent(type, message, data));
    }
}
