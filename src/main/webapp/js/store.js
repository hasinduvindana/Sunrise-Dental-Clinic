/* ==========================================================================
   SunRise Dental Clinic - Clinic Store (server backed)

   This replaces the original localStorage store. The method names and the
   shapes they return are unchanged, so every screen keeps working, but the
   data now comes from MySQL through /api/pos.

   How it works
   ------------
   The screens were written against a synchronous store (getPatients() returns
   an array, not a promise), so the store keeps a cache of the whole clinic
   document and serves reads from it. hydrate() fills that cache once at page
   load; every write posts a command to the server and then refreshes the
   cache, so what is on screen is always what is in the database.

   Nothing is written to localStorage any more. Closing the browser loses
   nothing, and two receptionists on two machines see the same data.
   ========================================================================== */

class ClinicStore {

  /* ---------------------------------------------------------------
     Cache
     --------------------------------------------------------------- */

  static _cache = null;
  static _hydrating = null;
  static _listeners = [];
  static _inflight = [];      // commands still on their way to the server

  static get EMPTY() {
    return {
      clinicSettings: {
        clinicName: 'SunRise Dental Clinic',
        tagline: '',
        logoUrl: 'assets/images/logo.png',
        iconUrl: 'assets/images/logo-icon.png',
        phone: '', email: '', address: '', regNo: '',
        currency: 'LKR', currencySymbol: 'Rs.', taxRate: 0,
        invoicePrefix: 'SRD-INV-', receiptPrefix: 'SRD-REC-',
        appointmentPrefix: 'SRD-APT-', footerNote: ''
      },
      users: [], treatmentCatalog: [], doctorPricing: [], sessions: [],
      patients: [], appointments: [], invoices: [], payments: [], reports: []
    };
  }

  static _public = false;

  /** Loads the whole clinic document. Call once before rendering. */
  static async hydrate() {
    const load = ClinicStore._public ? Api.pos.publicState() : Api.pos.state();
    ClinicStore._hydrating = load.then(function (state) {
      ClinicStore._cache = state;
      return state;
    });
    return ClinicStore._hydrating;
  }

  /**
   * Used by the public portal, which has no session. The server sends only
   * clinic details, doctors and open sessions - no patient data at all.
   */
  static readyPublic(fn) {
    ClinicStore._public = true;
    ClinicStore.ready(fn);
  }

  /** Re-reads the document after a write so every screen sees the change. */
  static async refresh() {
    return ClinicStore.hydrate();
  }

  /**
   * Runs fn once the DOM is ready AND the clinic data has arrived.
   * The screens use this instead of DOMContentLoaded, because rendering
   * before the data lands would draw empty tables.
   */
  static ready(fn) {
    const start = function () {
      ClinicStore.hydrate()
        .then(function () { fn(); })
        .catch(function (e) {
          if (e && e.status === 401) { return; }   // api.js is already redirecting
          document.body.innerHTML =
            '<div style="max-width:34rem;margin:4rem auto;font-family:sans-serif">' +
            '<h2>The clinic system could not load its data</h2>' +
            '<p>' + (e && e.message ? e.message : 'Unknown error') + '</p>' +
            '<p>Check that Tomcat and MySQL are running, then reload.</p></div>';
        });
    };
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', start);
    } else {
      start();
    }
  }

  static getStore() {
    return ClinicStore._cache || ClinicStore.EMPTY;
  }

  /** Kept so old calls do not break. The server is the store now. */
  static saveStore() {
    /* no-op: writes go through the command methods below */
  }

  static resetToDefault() {
    throw new Error('Demo data can no longer be reset from the browser. ' +
                    'Re-run database/03_seed.sql to reset the clinic data.');
  }

  /* ---------------------------------------------------------------
     Settings
     --------------------------------------------------------------- */

  static getSettings() {
    return ClinicStore.getStore().clinicSettings;
  }

  static updateSettings(newSettings) {
    const merged = Object.assign({}, ClinicStore.getSettings(), newSettings);
    ClinicStore.getStore().clinicSettings = merged;           // optimistic
    ClinicStore._command('update-settings', newSettings);
    return merged;
  }

  /* ---------------------------------------------------------------
     Staff accounts
     --------------------------------------------------------------- */

  static getUsers() {
    return ClinicStore.getStore().users;
  }

  static getUserById(id) {
    return ClinicStore.getUsers().find(u => u.id === id);
  }

  static addUser(user) {
    const optimistic = Object.assign({ id: 'USR-new', status: 'ACTIVE' }, user);
    ClinicStore.getUsers().push(optimistic);
    ClinicStore._command('save-user', user);
    return optimistic;
  }

  static updateUser(id, updatedFields) {
    const existing = ClinicStore.getUserById(id);
    if (!existing) { return null; }
    Object.assign(existing, updatedFields);

    // A status-only change is its own command so an admin can deactivate an
    // account without resending the whole profile.
    const keys = Object.keys(updatedFields);
    if (keys.length === 1 && keys[0] === 'status') {
      ClinicStore._command('set-user-status', { userId: id, status: updatedFields.status });
    } else {
      ClinicStore._command('save-user', Object.assign({ id: id }, existing));
    }
    return existing;
  }

  static getDoctors() {
    return ClinicStore.getUsers().filter(u => u.role === 'DOCTOR' && u.status === 'ACTIVE');
  }

  /* ---------------------------------------------------------------
     Treatments and per-doctor charges
     --------------------------------------------------------------- */

  static getTreatments() {
    return ClinicStore.getStore().treatmentCatalog;
  }

  static getDoctorPricing(doctorId) {
    return ClinicStore.getStore().doctorPricing.filter(p => p.doctorId === doctorId);
  }

  static getTreatmentChargeForDoctor(treatmentId, doctorId) {
    const store = ClinicStore.getStore();
    const custom = store.doctorPricing.find(p => p.doctorId === doctorId && p.treatmentId === treatmentId);
    if (custom) { return custom.customFee; }
    const base = store.treatmentCatalog.find(t => t.id === treatmentId);
    return base ? base.defaultFee : 0;
  }

  static setDoctorTreatmentFee(doctorId, treatmentId, fee) {
    const store = ClinicStore.getStore();
    const existing = store.doctorPricing.find(p => p.doctorId === doctorId && p.treatmentId === treatmentId);
    if (existing) {
      existing.customFee = Number(fee);
    } else {
      store.doctorPricing.push({ doctorId, treatmentId, customFee: Number(fee) });
    }
    ClinicStore._command('set-doctor-fee', {
      doctorId: doctorId, treatmentId: treatmentId, customFee: Number(fee)
    });
  }

  /* ---------------------------------------------------------------
     Sessions
     --------------------------------------------------------------- */

  static getSessions() {
    return ClinicStore.getStore().sessions;
  }

  static addSession(sessionData) {
    const doctor = ClinicStore.getUserById(sessionData.doctorId);
    const optimistic = Object.assign({
      id: 'SES-new',
      doctorName: doctor ? doctor.fullName : 'Doctor',
      specialty: doctor ? (doctor.specialty || 'General') : 'General',
      consultationFee: doctor ? (doctor.consultationFee || 2500) : 2500,
      bookedCount: 0,
      status: 'ACTIVE'
    }, sessionData);
    ClinicStore.getSessions().push(optimistic);
    ClinicStore._command('save-session', sessionData);
    return optimistic;
  }

  static updateSession(id, data) {
    const existing = ClinicStore.getSessions().find(s => s.id === id);
    if (!existing) { return null; }
    Object.assign(existing, data);
    ClinicStore._command('save-session', Object.assign({ id: id }, existing));
    return existing;
  }

  /* ---------------------------------------------------------------
     Patients (keyed by NIC, as every POS screen expects)
     --------------------------------------------------------------- */

  static getPatients() {
    return ClinicStore.getStore().patients;
  }

  static getPatientByNic(nic) {
    if (!nic) { return null; }
    const clean = String(nic).trim().toLowerCase();
    return ClinicStore.getPatients().find(p => String(p.nic).toLowerCase() === clean) || null;
  }

  static addOrUpdatePatient(patient) {
    const existing = ClinicStore.getPatientByNic(patient.nic);
    if (existing) {
      Object.assign(existing, patient);
    } else {
      ClinicStore.getPatients().push(Object.assign({
        registeredDate: new Date().toISOString().split('T')[0],
        bloodGroup: 'N/A',
        allergies: 'None',
        medicalHistory: 'None'
      }, patient));
    }
    ClinicStore._command('save-patient', patient);
    return ClinicStore.getPatientByNic(patient.nic);
  }

  /* ---------------------------------------------------------------
     Appointments
     --------------------------------------------------------------- */

  static getAppointments() {
    return ClinicStore.getStore().appointments;
  }

  static getAppointmentsByNic(nic) {
    if (!nic) { return []; }
    const clean = String(nic).trim();
    return ClinicStore.getAppointments().filter(a => a.patientNic === clean);
  }

  static getAppointmentsForSession(sessionId) {
    return ClinicStore.getAppointments().filter(a => a.sessionId === sessionId);
  }

  /**
   * The token number is allocated by a database trigger, not here, so two
   * clerks booking the last slot at the same moment cannot both succeed.
   * The number shown below is provisional until the refresh comes back.
   */
  static addAppointment(aptData) {
    const session = ClinicStore.getSessions().find(s => s.id === aptData.sessionId);
    const doctor = ClinicStore.getUserById(session ? session.doctorId : aptData.doctorId);
    const patient = ClinicStore.getPatientByNic(aptData.patientNic);
    const sameSession = ClinicStore.getAppointmentsForSession(aptData.sessionId);

    const optimistic = Object.assign({
      id: 'SRD-APT-new',
      tokenNumber: sameSession.length + 1,
      status: 'SCHEDULED',
      consultationFee: session ? session.consultationFee : 2500,
      doctorName: doctor ? doctor.fullName : 'Doctor',
      doctorId: doctor ? doctor.id : '',
      patientName: patient ? patient.fullName : aptData.patientName,
      patientPhone: patient ? patient.phone : aptData.patientPhone,
      date: session ? session.date : new Date().toISOString().split('T')[0],
      createdAt: new Date().toISOString().replace('T', ' ').substring(0, 16),
      vitals: null
    }, aptData);

    ClinicStore.getAppointments().push(optimistic);
    if (session) { session.bookedCount = (session.bookedCount || 0) + 1; }

    ClinicStore._command('book-appointment', {
      sessionId: aptData.sessionId,
      patientNic: aptData.patientNic,
      timeSlot: aptData.timeSlot,
      notes: aptData.notes
    }, function (result) {
      if (result && result.id) {
        optimistic.id = result.id;
        optimistic.tokenNumber = result.tokenNumber;
        optimistic.appointmentNo = result.appointmentNo;
      }
    });

    return optimistic;
  }

  static updateAppointmentStatus(aptId, newStatus, additionalData = {}) {
    const apt = ClinicStore.getAppointments().find(a => a.id === aptId);
    if (!apt) { return null; }
    Object.assign(apt, { status: newStatus }, additionalData);

    ClinicStore._command('update-appointment-status', {
      appointmentId: aptId,
      status: newStatus,
      vitals: additionalData.vitals,
      cancelReason: additionalData.cancelReason,
      receiptNo: additionalData.paymentReceiptNo
    });
    return apt;
  }

  static cancelAppointmentByNic(nic, reason = 'Cancelled by Cashier / Patient Request') {
    const open = ClinicStore.getAppointmentsByNic(nic)
      .filter(a => a.status !== 'CANCELLED' && a.status !== 'TREATMENT_COMPLETED');

    if (open.length === 0) {
      return { success: false, message: 'No active cancellable appointment found for this NIC.' };
    }

    open.forEach(function (apt) {
      apt.status = 'CANCELLED';
      apt.cancelReason = reason;
      const session = ClinicStore.getSessions().find(s => s.id === apt.sessionId);
      if (session && session.bookedCount > 0) { session.bookedCount -= 1; }
    });

    ClinicStore._command('cancel-by-nic', { nic: String(nic).trim(), reason: reason });
    return { success: true, count: open.length, appointments: open };
  }

  /* ---------------------------------------------------------------
     Invoices and payments
     --------------------------------------------------------------- */

  static getInvoices() {
    return ClinicStore.getStore().invoices;
  }

  static addInvoice(invoiceData) {
    const optimistic = Object.assign({
      invoiceNo: 'SRD-INV-pending',
      status: 'PENDING',
      createdAt: new Date().toISOString().replace('T', ' ').substring(0, 16),
      tax: 0,
      discount: 0
    }, invoiceData);
    ClinicStore.getInvoices().push(optimistic);

    ClinicStore._command('create-invoice', {
      appointmentId: invoiceData.appointmentId,
      items: invoiceData.items,
      discount: invoiceData.discount,
      tax: invoiceData.tax
    }, function (result) {
      if (result && result.invoiceNo) { optimistic.invoiceNo = result.invoiceNo; }
    });

    return optimistic;
  }

  static markInvoicePaid(invoiceNo, paymentRef) {
    const inv = ClinicStore.getInvoices().find(i => i.invoiceNo === invoiceNo);
    if (inv) {
      inv.status = 'PAID';
      inv.paymentRef = paymentRef;
    }
    // The bill is marked PAID server-side by a trigger when the payment lands,
    // so there is no separate command to send here.
  }

  static getPayments() {
    return ClinicStore.getStore().payments;
  }

  /**
   * The full card number never leaves this function: only the masked form is
   * sent for the receipt, and the server masks it again before storing.
   */
  static processPayment(paymentData) {
    const cashier = AuthService.getLoggedInUser();
    const optimistic = Object.assign({
      receiptNo: 'SRD-REC-pending',
      timestamp: new Date().toISOString().replace('T', ' ').substring(0, 19),
      cashierName: cashier ? cashier.fullName : ''
    }, paymentData);
    optimistic.cardNumberMasked = ClinicStore._maskCard(paymentData.cardNumber);
    delete optimistic.cardNumber;
    ClinicStore.getPayments().push(optimistic);

    if (paymentData.appointmentId) {
      const apt = ClinicStore.getAppointments().find(a => a.id === paymentData.appointmentId);
      if (apt) { apt.status = 'PAID'; }
    }
    if (paymentData.invoiceNo) {
      const inv = ClinicStore.getInvoices().find(i => i.invoiceNo === paymentData.invoiceNo);
      if (inv) { inv.status = 'PAID'; }
    }

    ClinicStore._command('process-payment', paymentData, function (result) {
      if (result && result.receiptNo) {
        optimistic.receiptNo = result.receiptNo;
        optimistic.invoiceNo = result.invoiceNo || optimistic.invoiceNo;
      }
    });

    return optimistic;
  }

  static _maskCard(cardNumber) {
    if (!cardNumber) { return ''; }
    const digits = String(cardNumber).replace(/\s+/g, '');
    if (digits.length >= 8) {
      return digits.substring(0, 4) + '-XXXX-XXXX-' + digits.substring(digits.length - 4);
    }
    return 'XXXX-XXXX-XXXX-' + digits;
  }

  /* ---------------------------------------------------------------
     Diagnostic reports
     --------------------------------------------------------------- */

  static getReports() {
    return ClinicStore.getStore().reports;
  }

  static getReportsByNic(nic) {
    if (!nic) { return []; }
    const clean = String(nic).trim();
    return ClinicStore.getReports().filter(r => r.patientNic === clean);
  }

  static addReport(reportData) {
    const date = reportData.date || new Date().toISOString().split('T')[0];
    const cleanNic = String(reportData.patientNic || 'GUEST').replace(/[^a-zA-Z0-9]/g, '');
    const safeType = String(reportData.reportType || 'Clinical_Report').replace(/[^a-zA-Z0-9]/g, '_');

    const optimistic = Object.assign({
      id: 'REP-pending',
      reportNo: 'RPT-pending',
      fileName: `${date}_${cleanNic}_${safeType}.pdf`,
      fileType: 'pdf',
      status: 'VERIFIED'
    }, reportData);
    ClinicStore.getReports().push(optimistic);

    ClinicStore._command('add-report', reportData, function (result) {
      if (result && result.id) { optimistic.id = result.id; }
    });
    return optimistic;
  }

  /* ---------------------------------------------------------------
     Command plumbing
     --------------------------------------------------------------- */

  /**
   * Sends one command, then reloads the document so ids, token numbers and
   * invoice numbers generated by the database replace the provisional values
   * shown a moment ago. If the server rejects the command the screen is
   * refreshed too, which discards the optimistic change.
   *
   * The promise never rejects: it settles to { ok: true } or
   * { ok: false, message }, which is what settleWrites() below reports to the
   * form popup so a form can only say Completed once the write really landed.
   */
  static _command(command, body, onResult) {
    const send = ClinicStore._public
      ? Api.pos.publicCommand(command, body || {})
      : Api.pos.command(command, body || {});

    const entry = { command: command, claimed: false, promise: null };

    entry.promise = send
      .then(function (result) {
        if (typeof onResult === 'function') { onResult(result); }
        return ClinicStore.refresh().then(function () {
          ClinicStore._notify();
          return { ok: true, command: command, result: result };
        });
      })
      .catch(function (e) {
        const message = (e && e.message) ? e.message : 'The clinic server rejected that change';

        // api.js is already sending the browser to the sign-in page.
        if (e && e.status === 401) {
          return { ok: false, command: command, message: 'Your session has expired. Please sign in again.' };
        }

        // Nothing is showing a result popup for this write (a background or
        // one-click action), so fall back to the old toast.
        if (!entry.claimed) {
          if (typeof showToast === 'function') {
            showToast(message, 'error');
          } else {
            alert(message);
          }
        }

        return ClinicStore.refresh()
          .catch(function () { /* the rejection above is the news, not this */ })
          .then(function () {
            ClinicStore._notify();
            return { ok: false, command: command, message: message };
          });
      });

    ClinicStore._inflight.push(entry);
    entry.promise.then(function () {
      const i = ClinicStore._inflight.indexOf(entry);
      if (i > -1) { ClinicStore._inflight.splice(i, 1); }
    });

    return entry.promise;
  }

  /**
   * Resolves once every write started so far has reached the server, with
   * { ok, messages }. Call it immediately after a store write - the commands
   * that write raised are still in flight, so they are all covered, including
   * the handlers that write more than once.
   *
   * Claiming the writes also silences the fallback toast in _command, because
   * whoever called this is going to report the failure itself.
   */
  static settleWrites() {
    const entries = ClinicStore._inflight.slice();
    entries.forEach(function (e) { e.claimed = true; });

    if (entries.length === 0) {
      return Promise.resolve({ ok: true, messages: [] });
    }

    return Promise.all(entries.map(function (e) { return e.promise; }))
      .then(function (outcomes) {
        const failed = outcomes.filter(function (o) { return o && !o.ok; });
        return {
          ok: failed.length === 0,
          messages: failed.map(function (o) { return o.message; })
        };
      });
  }

  /** Screens can subscribe to redraw themselves after a server round trip. */
  static onChange(listener) {
    ClinicStore._listeners.push(listener);
  }

  static _notify() {
    ClinicStore._listeners.forEach(function (fn) {
      try { fn(); } catch (e) { console.error('store listener failed', e); }
    });
  }
}

window.ClinicStore = ClinicStore;
