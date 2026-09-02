/* ==========================================================================
   SunRise Dental Clinic - Unified Dashboard & POS System Controller
   Pure Vanilla JavaScript - Zero Frameworks
   Role-Based Architecture: Super Admin, Admin, Doctor, Nurse, Patient Admin, Cashier
   ========================================================================== */

ClinicStore.ready(() => {
  initDashboard();
});

let currentUser = null;
let currentActiveTab = 'overview';

async function initDashboard() {
  // The server decides whether this session is still valid; the browser only
  // caches the profile for showing the name and building the menu.
  currentUser = await AuthService.requireSession();
  if (!currentUser) return;

  // Any write that lands on the server redraws whatever screen is open, so
  // two people working at once see each other's changes.
  ClinicStore.onChange(() => {
    if (currentActiveTab) switchDashboardTab(currentActiveTab);
  });

  renderTopbar();
  renderSidebarNav();
  initClock();
  initRoleQuickSwitcher();
  
  // Set default view based on role
  setDefaultViewForRole(currentUser.role);
}

/* --------------------------------------------------------------------------
   1. Topbar & User Information
   -------------------------------------------------------------------------- */
function renderTopbar() {
  const userAvatar = document.getElementById('topbar-user-avatar');
  const userName = document.getElementById('topbar-user-name');
  const userRole = document.getElementById('topbar-user-role');
  const clinicTitle = document.getElementById('topbar-clinic-title');
  const settings = ClinicStore.getSettings();

  if (clinicTitle) clinicTitle.textContent = settings.clinicName;
  if (userName) userName.textContent = currentUser.fullName;
  if (userRole) {
    userRole.textContent = formatRole(currentUser.role);
    userRole.className = `user-role-tag badge ${getRoleBadgeClass(currentUser.role)}`;
  }
  if (userAvatar) {
    userAvatar.textContent = getInitials(currentUser.fullName);
  }

  // Sidebar toggle for mobile drawer
  const toggleBtn = document.getElementById('sidebar-toggle-btn');
  const sidebar = document.querySelector('.sidebar');
  const backdrop = document.getElementById('sidebar-backdrop');

  if (toggleBtn && sidebar) {
    toggleBtn.onclick = () => {
      sidebar.classList.toggle('open');
      if (backdrop) backdrop.classList.toggle('active');
    };
  }

  if (backdrop && sidebar) {
    backdrop.onclick = () => {
      sidebar.classList.remove('open');
      backdrop.classList.remove('active');
    };
  }
}

function initClock() {
  const clockEl = document.getElementById('live-clock');
  function update() {
    if (clockEl) {
      const now = new Date();
      clockEl.textContent = '🕒 ' + now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) + ' | ' + now.toISOString().split('T')[0];
    }
  }
  update();
  setInterval(update, 1000);
}

/**
 * The role used to be switchable from the topbar for demonstration. It now
 * comes from the signed-in account and is enforced on every API call, so the
 * control simply reports which role is active.
 */
function initRoleQuickSwitcher() {
  const label = document.getElementById('active-role-label');
  if (label) {
    label.textContent = formatRole(currentUser.role);
    label.className = `badge ${getRoleBadgeClass(currentUser.role)}`;
  }
}

/* --------------------------------------------------------------------------
   2. Dynamic Sidebar Navigation per Role
   -------------------------------------------------------------------------- */
function renderSidebarNav() {
  const navContainer = document.getElementById('sidebar-nav-list');
  if (!navContainer) return;

  const role = currentUser.role;
  let navItems = [];

  // Super Admin: All modules + System Settings + Audit
  if (role === 'SUPER_ADMIN') {
    navItems = [
      { id: 'overview', label: 'Dashboard Overview', icon: '📊' },
      { id: 'pos-cashier', label: 'POS Cashier & Payments', icon: '💳' },
      { id: 'patient-admin', label: 'Patient Registration & Booking', icon: '👥' },
      { id: 'nurse-triage', label: 'Nurse Station & Billing', icon: '🩺' },
      { id: 'doctor-portal', label: 'Doctor Sessions & Consultations', icon: '🦷' },
      { id: 'income-reports', label: 'Income & Revenue Analytics', icon: '📈' },
      { id: 'patient-reports-mgmt', label: 'Patient Diagnostic Reports', icon: '📁' },
      { id: 'user-management', label: 'Staff & User Management', icon: '🛡️' },
      { id: 'clinic-settings', label: 'Clinic Global Settings', icon: '⚙️' }
    ];
  }
  // Admin: Staff, Reports, Invoices, Income
  else if (role === 'ADMIN') {
    navItems = [
      { id: 'overview', label: 'Admin Overview', icon: '📊' },
      { id: 'user-management', label: 'Staff Management', icon: '🛡️' },
      { id: 'income-reports', label: 'Monthly Income Reports', icon: '📈' },
      { id: 'invoices-list', label: 'Treatment Invoices Oversight', icon: '🧾' },
      { id: 'patient-reports-mgmt', label: 'Upload Patient Reports', icon: '📁' },
      { id: 'all-sessions', label: 'All Doctor Schedules', icon: '🗓️' }
    ];
  }
  // Doctor: Sessions, Queue, Patient History, Reports, Custom Treatment Pricing
  else if (role === 'DOCTOR') {
    navItems = [
      { id: 'doctor-portal', label: 'My Consultation Queue', icon: '🩺' },
      { id: 'doctor-sessions', label: 'My Scheduled Sessions', icon: '🗓️' },
      { id: 'doctor-pricing', label: 'My Treatment Charges', icon: '💰' },
      { id: 'doctor-patient-history', label: 'Patient Medical History', icon: '📋' },
      { id: 'patient-reports-mgmt', label: 'Add Diagnostic Reports', icon: '📄' }
    ];
  }
  // Nurse: Queue Triage, Send to Doctor, Reports, Bill Generation
  else if (role === 'NURSE') {
    navItems = [
      { id: 'nurse-triage', label: 'Patient Triage & Queue', icon: '🩺' },
      { id: 'nurse-billing', label: 'Generate Treatment Invoice', icon: '🧾' },
      { id: 'patient-reports-mgmt', label: 'Upload Diagnostic Reports', icon: '📁' },
      { id: 'all-sessions', label: 'View Doctor Sessions', icon: '🗓️' }
    ];
  }
  // Patient Administrator: New Patient, Session Assignment, Token Receipt
  else if (role === 'PATIENT_ADMIN') {
    navItems = [
      { id: 'patient-admin', label: 'Patient Registration & Booking', icon: '👥' },
      { id: 'all-appointments', label: 'Appointments Directory', icon: '📋' },
      { id: 'all-sessions', label: 'Doctor Session Slots', icon: '🗓️' }
    ];
  }
  // Cashier: Search appointment by NIC, Cash/Card checkout, Cancel appointment, Invoices
  else if (role === 'CASHIER') {
    navItems = [
      { id: 'pos-cashier', label: 'Cashier POS Checkout', icon: '💳' },
      { id: 'cashier-invoices', label: 'Settle Treatment Invoices', icon: '🧾' },
      { id: 'cancel-appointment', label: 'Cancel Appointments (by NIC)', icon: '❌' },
      { id: 'cashier-history', label: 'Daily Payment Log', icon: '📜' }
    ];
  }

  navContainer.innerHTML = navItems.map(item => `
    <li>
      <a class="sidebar-nav-item ${item.id === currentActiveTab ? 'active' : ''}" onclick="switchDashboardTab('${item.id}')">
        <span class="sidebar-nav-icon">${item.icon}</span>
        <span>${item.label}</span>
      </a>
    </li>
  `).join('');
}

function setDefaultViewForRole(role) {
  if (role === 'SUPER_ADMIN' || role === 'ADMIN') switchDashboardTab('overview');
  else if (role === 'DOCTOR') switchDashboardTab('doctor-portal');
  else if (role === 'NURSE') switchDashboardTab('nurse-triage');
  else if (role === 'PATIENT_ADMIN') switchDashboardTab('patient-admin');
  else if (role === 'CASHIER') switchDashboardTab('pos-cashier');
}

window.switchDashboardTab = function(tabId) {
  currentActiveTab = tabId;
  renderSidebarNav();

  // Auto-close mobile sidebar drawer on selection
  const sidebar = document.querySelector('.sidebar');
  const backdrop = document.getElementById('sidebar-backdrop');
  if (sidebar) sidebar.classList.remove('open');
  if (backdrop) backdrop.classList.remove('active');

  // Hide all views
  document.querySelectorAll('.dashboard-view').forEach(view => {
    view.classList.remove('active');
  });

  const targetView = document.getElementById(`view-${tabId}`);
  if (targetView) {
    targetView.classList.add('active');
  }

  // Update page title
  const pageTitleEl = document.getElementById('dashboard-page-title');
  if (pageTitleEl) {
    pageTitleEl.textContent = formatTabTitle(tabId);
  }

  // Render specific tab content
  if (tabId === 'overview') renderOverviewView();
  else if (tabId === 'pos-cashier') renderPosCashierView();
  else if (tabId === 'patient-admin') renderPatientAdminView();
  else if (tabId === 'nurse-triage') renderNurseTriageView();
  else if (tabId === 'nurse-billing') renderNurseBillingView();
  else if (tabId === 'doctor-portal') renderDoctorPortalView();
  else if (tabId === 'doctor-sessions') renderDoctorSessionsView();
  else if (tabId === 'doctor-pricing') renderDoctorPricingView();
  else if (tabId === 'doctor-patient-history') renderDoctorPatientHistoryView();
  else if (tabId === 'income-reports') renderIncomeReportsView();
  else if (tabId === 'invoices-list' || tabId === 'cashier-invoices') renderInvoicesListView();
  else if (tabId === 'patient-reports-mgmt') renderPatientReportsMgmtView();
  else if (tabId === 'user-management') renderUserManagementView();
  else if (tabId === 'clinic-settings') renderClinicSettingsView();
  else if (tabId === 'all-appointments') renderAllAppointmentsView();
  else if (tabId === 'all-sessions') renderAllSessionsView();
  else if (tabId === 'cancel-appointment') renderCancelAppointmentView();
  else if (tabId === 'cashier-history') renderCashierHistoryView();
};

/* --------------------------------------------------------------------------
   3. Overview / Analytics View
   -------------------------------------------------------------------------- */
function renderOverviewView() {
  const container = document.getElementById('view-overview');
  if (!container) return;

  const payments = ClinicStore.getPayments();
  const totalRevenue = payments.reduce((acc, p) => acc + (Number(p.amountPaid) || 0), 0);
  const patients = ClinicStore.getPatients();
  const appointments = ClinicStore.getAppointments();
  const sessions = ClinicStore.getSessions();
  const settings = ClinicStore.getSettings();

  container.innerHTML = `
    <!-- Top Stats -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon-wrapper" style="background: #d1fae5; color: #10b981;">💰</div>
        <div>
          <div class="stat-val">${settings.currencySymbol} ${totalRevenue.toLocaleString()}</div>
          <div class="stat-label">Total POS Revenue</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-wrapper" style="background: #e0f2fe; color: #0284c7;">👥</div>
        <div>
          <div class="stat-val">${patients.length}</div>
          <div class="stat-label">Registered Patients</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-wrapper" style="background: #ede9fe; color: #8b5cf6;">🗓️</div>
        <div>
          <div class="stat-val">${appointments.length}</div>
          <div class="stat-label">Total Appointments</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon-wrapper" style="background: #fef3c7; color: #f59e0b;">🏥</div>
        <div>
          <div class="stat-val">${sessions.filter(s => s.status === 'ACTIVE').length}</div>
          <div class="stat-label">Active Doctor Sessions</div>
        </div>
      </div>
    </div>

    <!-- Quick Role Portals Navigation -->
    <h3 style="margin-bottom: 1.25rem; color: #0b2545;">Quick System Workflows</h3>
    <div class="actions-grid">
      <div class="action-card" onclick="switchDashboardTab('pos-cashier')">
        <div class="action-icon">💳</div>
        <div>
          <div class="action-title">Cashier POS Desk</div>
          <div class="action-desc">Collect appointment fees (Cash/Card with card details) and print thermal slips.</div>
        </div>
      </div>
      <div class="action-card" onclick="switchDashboardTab('patient-admin')">
        <div class="action-icon">👤</div>
        <div>
          <div class="action-title">Patient Administration</div>
          <div class="action-desc">Register new patients, allocate doctor session slots, and print appointment slips.</div>
        </div>
      </div>
      <div class="action-card" onclick="switchDashboardTab('nurse-triage')">
        <div class="action-icon">🩺</div>
        <div>
          <div class="action-title">Nurse Triage & Billing</div>
          <div class="action-desc">Record vitals, confirm patients to doctor, and generate custom dental treatment bills.</div>
        </div>
      </div>
      <div class="action-card" onclick="switchDashboardTab('doctor-portal')">
        <div class="action-icon">🦷</div>
        <div>
          <div class="action-title">Doctor Consultation Station</div>
          <div class="action-desc">Manage sessions, view patient medical history, upload reports, and configure charges.</div>
        </div>
      </div>
    </div>

    <!-- Today's Sessions & Live Appointments Table -->
    <div class="card mt-4">
      <div class="d-flex justify-between align-center mb-3">
        <h4 style="color: #0b2545;">Today's Scheduled Consultations</h4>
        <button class="btn btn-primary btn-sm" onclick="openAddSessionModal()">+ Add New Doctor Session</button>
      </div>
      <div class="table-responsive">
        <table class="table">
          <thead>
            <tr>
              <th>Session ID</th>
              <th>Doctor Name</th>
              <th>Specialty</th>
              <th>Date & Time</th>
              <th>Room</th>
              <th>Slots Booked</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            ${sessions.map(s => `
              <tr>
                <td><strong>${s.id}</strong></td>
                <td>${s.doctorName}</td>
                <td>${s.specialty}</td>
                <td>${s.date} (${s.startTime} - ${s.endTime})</td>
                <td>${s.roomNo}</td>
                <td><span class="badge badge-info">${s.bookedCount} / ${s.maxPatients}</span></td>
                <td><span class="badge ${s.status === 'ACTIVE' ? 'badge-success' : 'badge-warning'}">${s.status}</span></td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

/* --------------------------------------------------------------------------
   4. POS Cashier Desk & Payments (Cash / Card with Full Card Details)
   -------------------------------------------------------------------------- */
let cashierSelectedAppointment = null;
let cashierPaymentMode = 'CASH';

function renderPosCashierView() {
  const container = document.getElementById('view-pos-cashier');
  if (!container) return;

  const settings = ClinicStore.getSettings();
  const appointments = ClinicStore.getAppointments().filter(a => a.status === 'SCHEDULED' || a.status === 'CONFIRMED_BY_NURSE');

  container.innerHTML = `
    <div class="pos-container">
      <div>
        <!-- Appointment Search by NIC / ID -->
        <div class="pos-search-box">
          <h4 style="color: #0b2545; margin-bottom: 1rem;">🔍 Search Appointment by Patient NIC or Token ID</h4>
          <div class="input-group">
            <input type="text" id="pos-search-input" class="form-control" placeholder="Enter Patient NIC (e.g. 200012345678) or Appointment ID..." />
            <button class="btn btn-primary" onclick="searchAppointmentForCashier()">Search</button>
          </div>
        </div>

        <!-- Pending Unpaid Appointments Queue -->
        <div class="card">
          <div class="d-flex justify-between align-center mb-3">
            <h4 style="color: #0b2545;">Unpaid Appointments Queue (Ready for Checkout)</h4>
            <span class="badge badge-warning">${appointments.length} Pending</span>
          </div>
          <div class="table-responsive">
            <table class="table">
              <thead>
                <tr>
                  <th>Token</th>
                  <th>Patient NIC</th>
                  <th>Patient Name</th>
                  <th>Doctor</th>
                  <th>Date</th>
                  <th>Fee (${settings.currencySymbol})</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody id="pos-queue-tbody">
                ${appointments.length === 0 ? `
                  <tr><td colspan="7" class="text-center text-muted" style="padding: 2rem;">No pending unpaid appointments.</td></tr>
                ` : appointments.map(apt => `
                  <tr>
                    <td><span class="badge badge-primary"># ${apt.tokenNumber}</span></td>
                    <td><strong>${apt.patientNic}</strong></td>
                    <td>${apt.patientName}</td>
                    <td>${apt.doctorName}</td>
                    <td>${apt.date}</td>
                    <td style="font-weight: bold; color: #0077b6;">${settings.currencySymbol} ${Number(apt.consultationFee).toLocaleString()}</td>
                    <td>
                      <button class="btn btn-primary btn-sm" onclick="selectAppointmentForCheckout('${apt.id}')">
                        Checkout
                      </button>
                    </td>
                  </tr>
                `).join('')}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- POS Checkout Cart & Payment Processing Panel -->
      <div>
        <div class="pos-cart-panel" id="pos-checkout-panel">
          <div>
            <div class="d-flex justify-between align-center mb-3 border-bottom pb-2">
              <h4 style="color: #0b2545;">💳 Payment Settlement Desk</h4>
              <span class="badge badge-info">POS Terminal</span>
            </div>

            <div id="pos-checkout-details">
              <div style="text-align: center; padding: 3rem 1rem; color: #64748b;">
                <div style="font-size: 2.5rem; margin-bottom: 0.5rem;">👈</div>
                <p>Select an appointment from the queue or search by NIC to proceed with billing & payment collection.</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `;
}

window.searchAppointmentForCashier = function() {
  const query = document.getElementById('pos-search-input').value.trim();
  if (!query) {
    showToast('Please enter an NIC or Appointment ID to search.', 'warning');
    return;
  }
  const appointments = ClinicStore.getAppointments();
  const found = appointments.find(a => a.patientNic.toLowerCase() === query.toLowerCase() || a.id.toLowerCase() === query.toLowerCase());

  if (found) {
    selectAppointmentForCheckout(found.id);
  } else {
    showToast(`No appointment found matching query: ${query}`, 'error');
  }
};

window.selectAppointmentForCheckout = function(aptId) {
  const apt = ClinicStore.getAppointments().find(a => a.id === aptId);
  if (!apt) return;
  cashierSelectedAppointment = apt;
  cashierPaymentMode = 'CASH';

  const settings = ClinicStore.getSettings();
  const panel = document.getElementById('pos-checkout-details');
  if (!panel) return;

  panel.innerHTML = `
    <div style="background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 1.25rem; margin-bottom: 1.25rem; font-size: 0.9rem;">
      <div class="d-flex justify-between mb-1">
        <span>Appointment Token:</span>
        <strong style="font-size: 1.1rem; color: #0077b6;"># ${apt.tokenNumber} (${apt.id})</strong>
      </div>
      <div class="d-flex justify-between mb-1">
        <span>Patient Name:</span>
        <strong>${apt.patientName}</strong>
      </div>
      <div class="d-flex justify-between mb-1">
        <span>Patient NIC:</span>
        <strong>${apt.patientNic}</strong>
      </div>
      <div class="d-flex justify-between mb-1">
        <span>Attending Doctor:</span>
        <strong>${apt.doctorName}</strong>
      </div>
      <div class="d-flex justify-between" style="border-top: 1px dashed #cbd5e1; padding-top: 0.5rem; margin-top: 0.5rem;">
        <span style="font-weight: bold;">Consultation Charge:</span>
        <strong style="font-size: 1.25rem; color: #10b981;">${settings.currencySymbol} ${Number(apt.consultationFee).toLocaleString()}</strong>
      </div>
    </div>

    <!-- Payment Mode Selector -->
    <div class="form-group">
      <label class="form-label">Select Payment Mode:</label>
      <div class="payment-method-selector">
        <div class="payment-method-card active" id="btn-pay-cash" onclick="setCashierPaymentMode('CASH')">
          <div style="font-size: 1.5rem;">💵</div>
          <div>CASH PAYMENT</div>
        </div>
        <div class="payment-method-card" id="btn-pay-card" onclick="setCashierPaymentMode('CARD')">
          <div style="font-size: 1.5rem;">💳</div>
          <div>CARD PAYMENT</div>
        </div>
      </div>
    </div>

    <!-- Card Details Form (Dynamically shown when Card is selected) -->
    <div id="pos-card-fields-container" class="card-details-box" style="display: none;">
      <h5 style="margin-bottom: 0.75rem; color: #0b2545;">Credit / Debit Card Details</h5>
      <div class="form-row">
        <div class="form-group">
          <label class="form-label">Card Type:</label>
          <select id="pos-card-type" class="form-select">
            <option value="CREDIT">Credit Card</option>
            <option value="DEBIT">Debit Card</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">Card Network / Provider:</label>
          <select id="pos-card-provider" class="form-select">
            <option value="VISA">Visa</option>
            <option value="MASTERCARD">MasterCard</option>
            <option value="AMEX">American Express</option>
          </select>
        </div>
      </div>
      <div class="form-group">
        <label class="form-label">Card Number (16 Digits):</label>
        <input type="text" id="pos-card-number" class="form-control" placeholder="4111 2222 3333 4444" maxlength="19" />
      </div>
      <div class="form-group">
        <label class="form-label">Issuing Bank Name:</label>
        <input type="text" id="pos-bank-name" class="form-control" placeholder="e.g. Commercial Bank, HNB, Sampath Bank, BOC" />
      </div>
    </div>

    <div style="margin-top: 1.5rem;">
      <button class="btn btn-success btn-lg w-100" onclick="processCashierCheckout()">
        ✓ Collect Payment & Print Official Receipt
      </button>
    </div>
  `;

  setTimeout(() => {
    document.getElementById('pos-checkout-panel')?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }, 100);
};

window.setCashierPaymentMode = function(mode) {
  cashierPaymentMode = mode;
  const cashBtn = document.getElementById('btn-pay-cash');
  const cardBtn = document.getElementById('btn-pay-card');
  const cardBox = document.getElementById('pos-card-fields-container');

  if (mode === 'CASH') {
    if (cashBtn) cashBtn.classList.add('active');
    if (cardBtn) cardBtn.classList.remove('active');
    if (cardBox) cardBox.style.display = 'none';
  } else {
    if (cardBtn) cardBtn.classList.add('active');
    if (cashBtn) cashBtn.classList.remove('active');
    if (cardBox) cardBox.style.display = 'block';
  }
};

window.processCashierCheckout = function() {
  if (!cashierSelectedAppointment) return;

  let cardData = {};
  if (cashierPaymentMode === 'CARD') {
    const cardNum = document.getElementById('pos-card-number').value.trim();
    const bankName = document.getElementById('pos-bank-name').value.trim();
    const cardType = document.getElementById('pos-card-type').value;
    const cardProvider = document.getElementById('pos-card-provider').value;

    if (!cardNum || !bankName) {
      showToast('Please enter the Card Number and Issuing Bank Name.', 'error');
      return;
    }

    cardData = {
      cardType,
      cardNumber: cardNum,
      bankName,
      cardProvider
    };
  }

  // Process payment in ClinicStore
  const payment = ClinicStore.processPayment({
    appointmentId: cashierSelectedAppointment.id,
    patientNic: cashierSelectedAppointment.patientNic,
    patientName: cashierSelectedAppointment.patientName,
    doctorId: cashierSelectedAppointment.doctorId,
    doctorName: cashierSelectedAppointment.doctorName,
    paymentType: cashierPaymentMode,
    amountPaid: cashierSelectedAppointment.consultationFee,
    cashierName: currentUser.fullName,
    ...cardData
  });

  showToast(`Payment successfully processed! Receipt: ${payment.receiptNo}`, 'success');

  // Trigger Print Receipt
  PrintUtil.printPaymentReceipt(payment);

  // Refresh Cashier view
  cashierSelectedAppointment = null;
  renderPosCashierView();
};

/* --------------------------------------------------------------------------
   5. Appointment Cancellation by NIC (Cashier / Patient Request)
   -------------------------------------------------------------------------- */
function renderCancelAppointmentView() {
  const container = document.getElementById('view-cancel-appointment');
  if (!container) return;

  container.innerHTML = `
    <div class="card" style="max-width: 650px; margin: 0 auto;">
      <h4 style="color: #0b2545; margin-bottom: 1rem;">❌ Cancel Patient Appointment by NIC</h4>
      <p style="color: #64748b; font-size: 0.9rem; margin-bottom: 1.5rem;">
        Enter the patient's NIC to search for active, unpaid appointments and cancel the reservation, releasing the doctor session slot.
      </p>

      <div class="form-group">
        <label class="form-label">Patient National Identity Card (NIC):</label>
        <div class="input-group">
          <input type="text" id="cancel-nic-input" class="form-control" placeholder="Enter Patient NIC..." />
          <button class="btn btn-primary" onclick="searchCancelAppointments()">Search Active Appointments</button>
        </div>
      </div>

      <div id="cancel-results-box" style="margin-top: 1.5rem;"></div>
    </div>
  `;
}

window.searchCancelAppointments = function() {
  const nic = document.getElementById('cancel-nic-input').value.trim();
  const resultsBox = document.getElementById('cancel-results-box');
  if (!nic || !resultsBox) return;

  const appointments = ClinicStore.getAppointments().filter(a => a.patientNic.toLowerCase() === nic.toLowerCase() && a.status !== 'CANCELLED' && a.status !== 'PAID');

  if (appointments.length === 0) {
    resultsBox.innerHTML = `
      <div style="background: #fef2f2; border: 1px solid #fecaca; border-radius: 8px; padding: 1.25rem; text-align: center; color: #991b1b;">
        No active cancellable appointments found for NIC: <strong>${nic}</strong>.
      </div>
    `;
    return;
  }

  resultsBox.innerHTML = `
    <h5 style="color: #0b2545; margin-bottom: 0.75rem;">Active Appointments Found (${appointments.length}):</h5>
    ${appointments.map(apt => `
      <div style="background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 1rem; margin-bottom: 0.75rem; display: flex; justify-content: space-between; align-items: center;">
        <div>
          <div><strong>Token # ${apt.tokenNumber}</strong> (Ref: ${apt.id})</div>
          <div style="font-size: 0.85rem; color: #64748b;">Patient: ${apt.patientName} | Doctor: ${apt.doctorName}</div>
          <div style="font-size: 0.85rem; color: #0077b6;">Date: ${apt.date} | Fee: Rs. ${Number(apt.consultationFee).toLocaleString()}</div>
        </div>
        <button class="btn btn-danger btn-sm" onclick="executeCancelAppointment('${apt.patientNic}')">
          Cancel This Appointment
        </button>
      </div>
    `).join('')}
  `;
};

window.executeCancelAppointment = function(nic) {
  if (!confirm(`Are you sure you want to cancel appointments for NIC: ${nic}? This action cannot be undone.`)) {
    return;
  }

  const result = ClinicStore.cancelAppointmentByNic(nic);
  if (result.success) {
    showToast(`Successfully cancelled ${result.count} appointment(s) for NIC: ${nic}.`, 'success');
    renderCancelAppointmentView();
  } else {
    showToast(result.message, 'error');
  }
};

/* --------------------------------------------------------------------------
   6. Patient Administrator View (Register Patient, Book & Assign Session)
   -------------------------------------------------------------------------- */
function renderPatientAdminView() {
  const container = document.getElementById('view-patient-admin');
  if (!container) return;

  const sessions = ClinicStore.getSessions().filter(s => s.status === 'ACTIVE');
  const settings = ClinicStore.getSettings();

  container.innerHTML = `
    <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 1.5rem; align-items: start;">
      <!-- Patient Registration Form -->
      <div class="card">
        <h4 style="color: #0b2545; margin-bottom: 1.25rem;">👤 Register New Patient / Update Profile</h4>
        <form id="form-patient-reg" onsubmit="handlePatientRegistration(event)">
          <div class="form-row">
            <div class="form-group">
              <label class="form-label">NIC Number <span class="required">*</span>:</label>
              <input type="text" id="reg-pat-nic" class="form-control" placeholder="e.g. 200012345678" required />
            </div>
            <div class="form-group">
              <label class="form-label">Full Name <span class="required">*</span>:</label>
              <input type="text" id="reg-pat-name" class="form-control" placeholder="e.g. Nimal Jayawardena" required />
            </div>
          </div>

          <div class="form-row">
            <div class="form-group">
              <label class="form-label">Phone Contact <span class="required">*</span>:</label>
              <input type="tel" id="reg-pat-phone" class="form-control" placeholder="0771234567" required />
            </div>
            <div class="form-group">
              <label class="form-label">Email Address:</label>
              <input type="email" id="reg-pat-email" class="form-control" placeholder="patient@gmail.com" />
            </div>
          </div>

          <div class="form-row">
            <div class="form-group">
              <label class="form-label">Date of Birth:</label>
              <input type="date" id="reg-pat-dob" class="form-control" />
            </div>
            <div class="form-group">
              <label class="form-label">Gender:</label>
              <select id="reg-pat-gender" class="form-select">
                <option value="Male">Male</option>
                <option value="Female">Female</option>
                <option value="Other">Other</option>
              </select>
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">Residential Address:</label>
            <input type="text" id="reg-pat-address" class="form-control" placeholder="No, Street, City" />
          </div>

          <div class="form-row">
            <div class="form-group">
              <label class="form-label">Blood Group:</label>
              <select id="reg-pat-blood" class="form-select">
                <option value="O+">O+</option>
                <option value="A+">A+</option>
                <option value="B+">B+</option>
                <option value="AB+">AB+</option>
                <option value="O-">O-</option>
                <option value="A-">A-</option>
                <option value="B-">B-</option>
                <option value="AB-">AB-</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">Known Drug Allergies:</label>
              <input type="text" id="reg-pat-allergies" class="form-control" placeholder="e.g. Penicillin, Sulfa" />
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">Medical History / Chronic Conditions:</label>
            <textarea id="reg-pat-history" class="form-control" placeholder="e.g. Hypertension, Diabetic, Asthmatic..."></textarea>
          </div>

          <button type="submit" class="btn btn-primary w-100">
            💾 Save Patient Profile
          </button>
        </form>
      </div>

      <!-- Session Assignment & Appointment Slip Generator -->
      <div class="card">
        <h4 style="color: #0b2545; margin-bottom: 1.25rem;">🎟️ Assign to Doctor Session & Issue Slip</h4>
        <form id="form-assign-session" onsubmit="handleSessionAssignment(event)">
          <div class="form-group">
            <label class="form-label">Select Registered Patient (or enter NIC) <span class="required">*</span>:</label>
            <input type="text" id="assign-pat-nic" class="form-control" placeholder="Enter Patient NIC..." required />
          </div>

          <div class="form-group">
            <label class="form-label">Select Available Doctor Session <span class="required">*</span>:</label>
            <select id="assign-session-id" class="form-select" onchange="updateSelectedSessionDetails()" required>
              <option value="">-- Choose Active Session --</option>
              ${sessions.map(s => {
                const avail = Math.max(0, s.maxPatients - (s.bookedCount || 0));
                return `<option value="${s.id}">${s.date} | ${s.doctorName} (${s.specialty}) | ${s.startTime}-${s.endTime} [${avail} slots left]</option>`;
              }).join('')}
            </select>
          </div>

          <!-- Live Session Info Box -->
          <div id="assign-session-info-box" style="background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 1.25rem; margin-bottom: 1.5rem; display: none;">
            <div class="d-flex justify-between mb-1">
              <span>Attending Doctor:</span>
              <strong id="info-doc-name">-</strong>
            </div>
            <div class="d-flex justify-between mb-1">
              <span>Room Number:</span>
              <strong id="info-room-no">-</strong>
            </div>
            <div class="d-flex justify-between mb-1">
              <span>Time Slot:</span>
              <strong id="info-time-slot">-</strong>
            </div>
            <div class="d-flex justify-between" style="border-top: 1px dashed #cbd5e1; padding-top: 0.5rem; margin-top: 0.5rem;">
              <span style="font-weight: bold;">Consultation Charge:</span>
              <strong style="color: #0077b6; font-size: 1.15rem;" id="info-consultation-fee">-</strong>
            </div>
          </div>

          <button type="submit" class="btn btn-success btn-lg w-100">
            🎟️ Book Appointment & Print Token Slip
          </button>
        </form>
      </div>
    </div>
  `;
}

window.handlePatientRegistration = function(e) {
  e.preventDefault();
  const nic = document.getElementById('reg-pat-nic').value.trim();
  const fullName = document.getElementById('reg-pat-name').value.trim();
  const phone = document.getElementById('reg-pat-phone').value.trim();
  const email = document.getElementById('reg-pat-email').value.trim();
  const dob = document.getElementById('reg-pat-dob').value;
  const gender = document.getElementById('reg-pat-gender').value;
  const address = document.getElementById('reg-pat-address').value.trim();
  const bloodGroup = document.getElementById('reg-pat-blood').value;
  const allergies = document.getElementById('reg-pat-allergies').value.trim();
  const medicalHistory = document.getElementById('reg-pat-history').value.trim();

  const patient = ClinicStore.addOrUpdatePatient({
    nic,
    fullName,
    phone,
    email,
    dob,
    gender,
    address,
    bloodGroup,
    allergies,
    medicalHistory
  });

  showToast(`Patient profile for ${patient.fullName} successfully saved!`, 'success');
  document.getElementById('assign-pat-nic').value = nic;
};

window.updateSelectedSessionDetails = function() {
  const select = document.getElementById('assign-session-id');
  const infoBox = document.getElementById('assign-session-info-box');
  const settings = ClinicStore.getSettings();

  if (!select || !infoBox) return;

  const session = ClinicStore.getSessions().find(s => s.id === select.value);
  if (session) {
    infoBox.style.display = 'block';
    document.getElementById('info-doc-name').textContent = session.doctorName;
    document.getElementById('info-room-no').textContent = session.roomNo;
    document.getElementById('info-time-slot').textContent = `${session.startTime} - ${session.endTime}`;
    document.getElementById('info-consultation-fee').textContent = `${settings.currencySymbol} ${Number(session.consultationFee).toLocaleString()}`;
  } else {
    infoBox.style.display = 'none';
  }
};

window.handleSessionAssignment = function(e) {
  e.preventDefault();
  const nic = document.getElementById('assign-pat-nic').value.trim();
  const sessionId = document.getElementById('assign-session-id').value;

  const patient = ClinicStore.getPatientByNic(nic);
  if (!patient) {
    showToast(`Patient with NIC "${nic}" is not registered. Please complete the registration form first.`, 'error');
    return;
  }

  const session = ClinicStore.getSessions().find(s => s.id === sessionId);
  if (!session) {
    showToast('Please select a valid doctor session.', 'error');
    return;
  }

  // Create appointment
  const newApt = ClinicStore.addAppointment({
    sessionId: session.id,
    doctorId: session.doctorId,
    patientNic: patient.nic,
    patientName: patient.fullName,
    patientPhone: patient.phone,
    consultationFee: session.consultationFee
  });

  showToast(`Appointment # ${newApt.tokenNumber} successfully booked!`, 'success');

  // Print official Appointment Slip
  PrintUtil.printAppointmentSlip(newApt);

  renderPatientAdminView();
};

/* --------------------------------------------------------------------------
   7. Nurse Station: Triage, Record Vitals & Send to Doctor
   -------------------------------------------------------------------------- */
function renderNurseTriageView() {
  const container = document.getElementById('view-nurse-triage');
  if (!container) return;

  const appointments = ClinicStore.getAppointments().filter(a => a.status === 'SCHEDULED' || a.status === 'PAID');
  const confirmedApts = ClinicStore.getAppointments().filter(a => a.status === 'CONFIRMED_BY_NURSE');

  container.innerHTML = `
    <div class="card mb-4">
      <div class="d-flex justify-between align-center mb-3">
        <h4 style="color: #0b2545;">🩺 Patient Triage Station (Queue for Doctor)</h4>
        <span class="badge badge-primary">${appointments.length} Patients in Queue</span>
      </div>
      <p style="color: #64748b; font-size: 0.9rem; margin-bottom: 1.25rem;">
        Check in arrived patients, record clinical vitals (BP, Pulse, Chief Complaints), and confirm them to send directly to the Attending Doctor's consultation desk.
      </p>

      <div class="table-responsive">
        <table class="table">
          <thead>
            <tr>
              <th>Token</th>
              <th>Patient NIC</th>
              <th>Patient Name</th>
              <th>Assigned Doctor</th>
              <th>Payment Status</th>
              <th>Current Vitals</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            ${appointments.length === 0 ? `
              <tr><td colspan="7" class="text-center text-muted" style="padding: 2rem;">No pending patients in triage queue.</td></tr>
            ` : appointments.map(apt => `
              <tr>
                <td><span class="badge badge-primary"># ${apt.tokenNumber}</span></td>
                <td><strong>${apt.patientNic}</strong></td>
                <td>${apt.patientName}</td>
                <td>${apt.doctorName}</td>
                <td><span class="badge ${apt.status === 'PAID' ? 'badge-success' : 'badge-warning'}">${apt.status}</span></td>
                <td>${apt.vitals ? `BP: ${apt.vitals.bp}, Pulse: ${apt.vitals.pulse}` : '<span class="text-muted">Not Recorded</span>'}</td>
                <td>
                  <button class="btn btn-primary btn-sm" onclick="openRecordVitalsModal('${apt.id}')">
                    Record Vitals & Send to Doctor ➔
                  </button>
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    </div>

    <!-- Active Confirmed Patients Currently with Doctor -->
    <div class="card">
      <h4 style="color: #0b2545; margin-bottom: 1rem;">✅ Confirmed Patients with Attending Doctor</h4>
      <div class="table-responsive">
        <table class="table">
          <thead>
            <tr>
              <th>Token</th>
              <th>Patient NIC & Name</th>
              <th>Attending Doctor</th>
              <th>Recorded Vitals</th>
              <th>Chief Complaint</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            ${confirmedApts.length === 0 ? `
              <tr><td colspan="6" class="text-center text-muted" style="padding: 1.5rem;">No confirmed appointments currently in consultation.</td></tr>
            ` : confirmedApts.map(apt => `
              <tr>
                <td><span class="badge badge-success"># ${apt.tokenNumber}</span></td>
                <td><strong>${apt.patientName}</strong> (${apt.patientNic})</td>
                <td>${apt.doctorName}</td>
                <td>${apt.vitals ? `BP: ${apt.vitals.bp} mmHg, Pulse: ${apt.vitals.pulse} bpm` : 'N/A'}</td>
                <td>${apt.vitals ? apt.vitals.chiefComplaint : 'Routine Check'}</td>
                <td><span class="badge badge-purple">WITH DOCTOR</span></td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

window.openRecordVitalsModal = function(aptId) {
  const apt = ClinicStore.getAppointments().find(a => a.id === aptId);
  if (!apt) return;

  const modalHtml = `
    <div class="modal-backdrop active" id="modal-record-vitals">
      <div class="modal">
        <div class="modal-header">
          <div class="modal-title">
            <span>🩺</span> Record Vitals & Confirm: Token # ${apt.tokenNumber}
          </div>
          <button class="modal-close" onclick="document.getElementById('modal-record-vitals').remove()">&times;</button>
        </div>
        <div class="modal-body">
          <div style="background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 1rem; margin-bottom: 1.25rem; font-size: 0.9rem;">
            <div><strong>Patient:</strong> ${apt.patientName} (NIC: ${apt.patientNic})</div>
            <div><strong>Assigned Doctor:</strong> ${apt.doctorName}</div>
          </div>

          <form id="form-vitals-submit" onsubmit="submitPatientVitals(event, '${apt.id}')">
            <div class="form-row">
              <div class="form-group">
                <label class="form-label">Blood Pressure (mmHg):</label>
                <input type="text" id="vitals-bp" class="form-control" placeholder="e.g. 120/80" value="${apt.vitals ? apt.vitals.bp : '120/80'}" required />
              </div>
              <div class="form-group">
                <label class="form-label">Pulse Rate (bpm):</label>
                <input type="text" id="vitals-pulse" class="form-control" placeholder="e.g. 72" value="${apt.vitals ? apt.vitals.pulse : '72'}" required />
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">Chief Dental Complaint / Patient Symptoms:</label>
              <textarea id="vitals-complaint" class="form-control" placeholder="Describe symptoms (e.g. lower right molar pain, bleeding gums)..." required>${apt.vitals ? apt.vitals.chiefComplaint : ''}</textarea>
            </div>

            <div class="modal-footer" style="padding: 1rem 0 0 0; background: transparent;">
              <button type="button" class="btn btn-secondary" onclick="document.getElementById('modal-record-vitals').remove()">Cancel</button>
              <button type="submit" class="btn btn-success">
                ✓ Confirm & Send to Doctor View
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `;

  document.body.insertAdjacentHTML('beforeend', modalHtml);
};

window.submitPatientVitals = function(e, aptId) {
  e.preventDefault();
  const bp = document.getElementById('vitals-bp').value.trim();
  const pulse = document.getElementById('vitals-pulse').value.trim();
  const complaint = document.getElementById('vitals-complaint').value.trim();

  ClinicStore.updateAppointmentStatus(aptId, 'CONFIRMED_BY_NURSE', {
    vitals: {
      bp,
      pulse,
      chiefComplaint: complaint
    }
  });

  const modal = document.getElementById('modal-record-vitals');
  if (modal) modal.remove();

  showToast('Patient confirmed and queue dispatched to doctor!', 'success');
  renderNurseTriageView();
};

/* --------------------------------------------------------------------------
   8. Nurse & Admin Treatment Billing Generator (with Doctor-Wise Pricing)
   -------------------------------------------------------------------------- */
let billingSelectedItems = [];

function renderNurseBillingView() {
  const container = document.getElementById('view-nurse-billing');
  if (!container) return;

  const doctors = ClinicStore.getDoctors();
  const treatments = ClinicStore.getTreatments();
  const appointments = ClinicStore.getAppointments().filter(a => a.status === 'CONFIRMED_BY_NURSE' || a.status === 'PAID');
  const settings = ClinicStore.getSettings();

  container.innerHTML = `
    <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 1.5rem; align-items: start;">
      <div class="card">
        <h4 style="color: #0b2545; margin-bottom: 1rem;">🧾 Dental Treatment Bill Generator</h4>
        <p style="color: #64748b; font-size: 0.9rem; margin-bottom: 1.5rem;">
          Select the patient appointment and add performed treatments. The system automatically fetches the specific doctor's custom procedure charges.
        </p>

        <div class="form-group">
          <label class="form-label">Select Patient Consultation Appointment:</label>
          <select id="bill-appointment-select" class="form-select" onchange="onBillingAppointmentChange()">
            <option value="">-- Choose Patient / Appointment --</option>
            ${appointments.map(a => `
              <option value="${a.id}">${a.patientName} (NIC: ${a.patientNic}) | Doctor: ${a.doctorName} | Token # ${a.tokenNumber}</option>
            `).join('')}
          </select>
        </div>

        <div class="form-group">
          <label class="form-label">Add Dental Procedures / Treatments:</label>
          <div class="input-group">
            <select id="bill-treatment-select" class="form-select">
              ${treatments.map(t => `<option value="${t.id}">${t.name} (${t.category})</option>`).join('')}
            </select>
            <button class="btn btn-primary" onclick="addTreatmentToBill()">+ Add Procedure</button>
          </div>
        </div>

        <div class="mt-4">
          <h5 style="color: #0b2545; margin-bottom: 0.75rem;">Selected Procedures & Fees</h5>
          <div id="billing-items-container" style="border: 1px solid #e2e8f0; border-radius: 8px; padding: 1rem; background: #f8fafc; min-height: 120px;">
            <p class="text-muted text-center" style="margin-top: 2rem;">No treatments added yet.</p>
          </div>
        </div>
      </div>

      <!-- Bill Summary & Invoice Action -->
      <div class="card" style="display: flex; flex-direction: column; justify-content: space-between;">
        <div>
          <h4 style="color: #0b2545; margin-bottom: 1.25rem;">📊 Bill & Invoice Summary</h4>
          <div id="bill-summary-box" style="font-size: 0.95rem;">
            <div class="d-flex justify-between mb-2">
              <span>Subtotal:</span>
              <strong id="bill-subtotal">${settings.currencySymbol} 0</strong>
            </div>
            <div class="d-flex justify-between mb-2">
              <span>Discount Amount:</span>
              <input type="number" id="bill-discount-input" class="form-control" style="width: 130px; padding: 0.3rem 0.6rem;" value="0" min="0" oninput="recalculateBillTotals()" />
            </div>
            <div class="d-flex justify-between pt-2 border-top" style="font-size: 1.25rem;">
              <strong>Total Invoice Amount:</strong>
              <strong style="color: #10b981;" id="bill-net-total">${settings.currencySymbol} 0</strong>
            </div>
          </div>
        </div>

        <div class="mt-4">
          <button class="btn btn-success btn-lg w-100" onclick="generateAndSaveTreatmentInvoice()">
            ✓ Generate & Save Treatment Invoice
          </button>
        </div>
      </div>
    </div>
  `;

  billingSelectedItems = [];
}

window.onBillingAppointmentChange = function() {
  const aptId = document.getElementById('bill-appointment-select').value;
  const apt = ClinicStore.getAppointments().find(a => a.id === aptId);
  billingSelectedItems = [];

  if (apt) {
    // Automatically include doctor consultation fee
    billingSelectedItems.push({
      description: `Doctor Consultation Fee (${apt.doctorName})`,
      amount: apt.consultationFee
    });
  }
  renderBillingItems();
};

window.addTreatmentToBill = function() {
  const aptId = document.getElementById('bill-appointment-select').value;
  const trtId = document.getElementById('bill-treatment-select').value;

  if (!aptId) {
    showToast('Please select a patient appointment first.', 'warning');
    return;
  }

  const apt = ClinicStore.getAppointments().find(a => a.id === aptId);
  const treatment = ClinicStore.getTreatments().find(t => t.id === trtId);
  if (!treatment || !apt) return;

  // Pull doctor-specific customized charge for this treatment
  const fee = ClinicStore.getTreatmentChargeForDoctor(treatment.id, apt.doctorId);

  billingSelectedItems.push({
    description: treatment.name,
    amount: fee
  });

  renderBillingItems();
  showToast(`Added ${treatment.name} (Fee: Rs. ${fee.toLocaleString()})`, 'info');
};

function renderBillingItems() {
  const container = document.getElementById('billing-items-container');
  const settings = ClinicStore.getSettings();
  if (!container) return;

  if (billingSelectedItems.length === 0) {
    container.innerHTML = '<p class="text-muted text-center" style="margin-top: 2rem;">No treatments added yet.</p>';
    recalculateBillTotals();
    return;
  }

  container.innerHTML = billingSelectedItems.map((item, idx) => `
    <div class="treatment-item-row">
      <span style="width: 25px; color: #64748b; font-weight: bold;">${idx + 1}.</span>
      <span style="flex: 1; font-weight: 600;">${item.description}</span>
      <strong style="color: #0077b6;">${settings.currencySymbol} ${Number(item.amount).toLocaleString()}</strong>
      <button class="btn btn-danger btn-sm" onclick="removeBillingItem(${idx})" style="padding: 0.2rem 0.5rem;">✕</button>
    </div>
  `).join('');

  recalculateBillTotals();
}

window.removeBillingItem = function(idx) {
  billingSelectedItems.splice(idx, 1);
  renderBillingItems();
};

function recalculateBillTotals() {
  const settings = ClinicStore.getSettings();
  const subtotal = billingSelectedItems.reduce((acc, item) => acc + item.amount, 0);
  const discountInput = document.getElementById('bill-discount-input');
  const discount = discountInput ? (Number(discountInput.value) || 0) : 0;
  const netTotal = Math.max(0, subtotal - discount);

  const subtotalEl = document.getElementById('bill-subtotal');
  const netTotalEl = document.getElementById('bill-net-total');

  if (subtotalEl) subtotalEl.textContent = `${settings.currencySymbol} ${subtotal.toLocaleString()}`;
  if (netTotalEl) netTotalEl.textContent = `${settings.currencySymbol} ${netTotal.toLocaleString()}`;
}

window.generateAndSaveTreatmentInvoice = function() {
  const aptId = document.getElementById('bill-appointment-select').value;
  if (!aptId || billingSelectedItems.length === 0) {
    showToast('Please select an appointment and add at least one treatment.', 'error');
    return;
  }

  const apt = ClinicStore.getAppointments().find(a => a.id === aptId);
  const subtotal = billingSelectedItems.reduce((acc, item) => acc + item.amount, 0);
  const discountInput = document.getElementById('bill-discount-input');
  const discount = discountInput ? (Number(discountInput.value) || 0) : 0;
  const totalAmount = Math.max(0, subtotal - discount);

  const primaryTreatment = billingSelectedItems.length > 1 ? billingSelectedItems[1].description : 'Dental Consultation';

  const newInvoice = ClinicStore.addInvoice({
    appointmentId: apt.id,
    patientNic: apt.patientNic,
    patientName: apt.patientName,
    doctorId: apt.doctorId,
    doctorName: apt.doctorName,
    treatmentType: primaryTreatment,
    items: billingSelectedItems,
    subtotal,
    discount,
    totalAmount,
    status: 'PENDING'
  });

  showToast(`Treatment Invoice ${newInvoice.invoiceNo} successfully generated!`, 'success');

  // Print Invoice Preview
  PrintUtil.printTreatmentInvoice(newInvoice);

  renderNurseBillingView();
};

/* --------------------------------------------------------------------------
   9. Doctor Consultation Portal (Queue, History, Custom Charges)
   -------------------------------------------------------------------------- */
function renderDoctorPortalView() {
  const container = document.getElementById('view-doctor-portal');
  if (!container) return;

  const doctorId = currentUser.role === 'DOCTOR' ? currentUser.id : 'USR-003';
  const doctor = ClinicStore.getUserById(doctorId);
  const confirmedQueue = ClinicStore.getAppointments().filter(a => a.doctorId === doctorId && a.status === 'CONFIRMED_BY_NURSE');

  container.innerHTML = `
    <div class="card mb-4">
      <div class="d-flex justify-between align-center mb-3">
        <h4 style="color: #0b2545;">🦷 Live Consultation Queue: ${doctor ? doctor.fullName : 'Doctor'}</h4>
        <span class="badge badge-success">${confirmedQueue.length} Patients Ready</span>
      </div>
      <p style="color: #64748b; font-size: 0.9rem; margin-bottom: 1.25rem;">
        These patients have been checked in, triaged, and dispatched by the nurse station. Click to view vitals, access past report history, or record clinical observations.
      </p>

      <div class="table-responsive">
        <table class="table">
          <thead>
            <tr>
              <th>Token</th>
              <th>Patient NIC</th>
              <th>Patient Name</th>
              <th>Recorded Vitals</th>
              <th>Chief Complaint</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            ${confirmedQueue.length === 0 ? `
              <tr><td colspan="6" class="text-center text-muted" style="padding: 2rem;">No patients currently waiting in consultation queue.</td></tr>
            ` : confirmedQueue.map(apt => `
              <tr>
                <td><span class="badge badge-success" style="font-size: 0.95rem;"># ${apt.tokenNumber}</span></td>
                <td><strong>${apt.patientNic}</strong></td>
                <td>${apt.patientName}</td>
                <td>${apt.vitals ? `BP: ${apt.vitals.bp}, Pulse: ${apt.vitals.pulse} bpm` : 'Normal'}</td>
                <td>${apt.vitals ? apt.vitals.chiefComplaint : 'General checkup'}</td>
                <td>
                  <button class="btn btn-primary btn-sm" onclick="openDoctorConsultationModal('${apt.id}')">
                    🩺 Consult Patient & Add Report
                  </button>
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

window.openDoctorConsultationModal = function(aptId) {
  const apt = ClinicStore.getAppointments().find(a => a.id === aptId);
  if (!apt) return;

  const pastReports = ClinicStore.getReportsByNic(apt.patientNic);
  const patient = ClinicStore.getPatientByNic(apt.patientNic);

  const modalHtml = `
    <div class="modal-backdrop active" id="modal-doc-consult">
      <div class="modal modal-xl">
        <div class="modal-header">
          <div class="modal-title">
            <span>🦷</span> Active Consultation: ${apt.patientName} (Token # ${apt.tokenNumber})
          </div>
          <button class="modal-close" onclick="document.getElementById('modal-doc-consult').remove()">&times;</button>
        </div>
        <div class="modal-body">
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; margin-bottom: 1.5rem;">
            <!-- Patient Medical Summary -->
            <div style="background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 1.25rem; font-size: 0.9rem;">
              <h5 style="color: #0b2545; margin-bottom: 0.75rem;">Patient Details & Clinical Vitals</h5>
              <div><strong>NIC:</strong> ${apt.patientNic}</div>
              <div><strong>Allergies:</strong> <span style="color: #ef4444; font-weight: bold;">${patient ? patient.allergies : 'None'}</span></div>
              <div><strong>Blood Group:</strong> ${patient ? patient.bloodGroup : 'N/A'}</div>
              <div><strong>Medical History:</strong> ${patient ? patient.medicalHistory : 'None recorded'}</div>
              <div style="margin-top: 0.5rem; padding-top: 0.5rem; border-top: 1px dashed #cbd5e1;">
                <strong>Vitals:</strong> BP: ${apt.vitals ? apt.vitals.bp : '120/80'} | Pulse: ${apt.vitals ? apt.vitals.pulse : '72'} bpm
              </div>
              <div><strong>Chief Complaint:</strong> ${apt.vitals ? apt.vitals.chiefComplaint : 'N/A'}</div>
            </div>

            <!-- Past Diagnostic Reports History -->
            <div style="background: #ffffff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 1.25rem; max-height: 220px; overflow-y: auto;">
              <h5 style="color: #0b2545; margin-bottom: 0.75rem;">Past Reports & Imaging History (${pastReports.length})</h5>
              ${pastReports.length === 0 ? '<p class="text-muted">No previous diagnostic reports found for this patient.</p>' : pastReports.map(r => `
                <div style="border-bottom: 1px solid #f1f5f9; padding: 0.5rem 0; font-size: 0.85rem; display: flex; justify-content: space-between; align-items: center;">
                  <div>
                    <strong>${r.reportType}</strong> (${r.date})
                    <div style="color: #64748b; font-size: 0.75rem;">${r.doctorName}</div>
                  </div>
                  <button class="btn btn-secondary btn-sm" onclick="PrintUtil.printClinicalReport(ClinicStore.getReports().find(rep => rep.id === '${r.id}'))">
                    View PDF
                  </button>
                </div>
              `).join('')}
            </div>
          </div>

          <!-- Add Clinical Report & Findings -->
          <div class="card">
            <h5 style="color: #0b2545; margin-bottom: 1rem;">📝 Upload New Diagnostic Report / Treatment Notes</h5>
            <form onsubmit="handleDoctorReportSubmit(event, '${apt.id}', '${apt.patientNic}', '${apt.patientName}')">
              <div class="form-row">
                <div class="form-group">
                  <label class="form-label">Investigation / Report Type:</label>
                  <select id="doc-report-type" class="form-select">
                    <option value="IOPA Digital X-Ray">IOPA Digital Dental X-Ray</option>
                    <option value="Panoramic OPG Radiograph">Panoramic OPG Radiograph</option>
                    <option value="CBCT 3D Bone Scan">CBCT 3D Bone Scan</option>
                    <option value="Root Canal Treatment Summary">Root Canal Treatment Summary</option>
                    <option value="Orthodontic Cephalometric Analysis">Orthodontic Cephalometric Analysis</option>
                    <option value="Periodontal Deep Pocket Assessment">Periodontal Deep Pocket Assessment</option>
                  </select>
                </div>
                <div class="form-group">
                  <label class="form-label">Report Date:</label>
                  <input type="date" id="doc-report-date" class="form-control" value="${new Date().toISOString().split('T')[0]}" />
                </div>
              </div>

              <div class="form-group">
                <label class="form-label">Clinical Observations, Findings & Prescription:</label>
                <textarea id="doc-report-findings" class="form-control" rows="4" placeholder="Enter clinical diagnosis, tooth numbers involved, prescription medications, post-op instructions..." required></textarea>
              </div>

              <div class="d-flex justify-between align-center">
                <button type="button" class="btn btn-secondary" onclick="document.getElementById('modal-doc-consult').remove()">Close</button>
                <button type="submit" class="btn btn-success">
                  💾 Save Clinical Report & Complete Consultation
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  `;

  document.body.insertAdjacentHTML('beforeend', modalHtml);
};

window.handleDoctorReportSubmit = function(e, aptId, nic, patientName) {
  e.preventDefault();
  const reportType = document.getElementById('doc-report-type').value;
  const date = document.getElementById('doc-report-date').value;
  const findings = document.getElementById('doc-report-findings').value.trim();

  const newReport = ClinicStore.addReport({
    patientNic: nic,
    patientName,
    doctorId: currentUser.id,
    doctorName: currentUser.fullName,
    reportType,
    date,
    findings
  });

  // Mark appointment as in treatment / completed
  ClinicStore.updateAppointmentStatus(aptId, 'TREATMENT_COMPLETED');

  const modal = document.getElementById('modal-doc-consult');
  if (modal) modal.remove();

  showToast(`Clinical report ${newReport.reportNo} saved successfully! (File: storage/reports/${newReport.fileName})`, 'success');

  // Trigger printable report preview
  PrintUtil.printClinicalReport(newReport);

  renderDoctorPortalView();
};

/* --------------------------------------------------------------------------
   10. Doctor Treatment Charges Configuration (Doctor Custom Pricing)
   -------------------------------------------------------------------------- */
function renderDoctorPricingView() {
  const container = document.getElementById('view-doctor-pricing');
  if (!container) return;

  const doctorId = currentUser.role === 'DOCTOR' ? currentUser.id : 'USR-003';
  const doctor = ClinicStore.getUserById(doctorId);
  const treatments = ClinicStore.getTreatments();
  const settings = ClinicStore.getSettings();

  container.innerHTML = `
    <div class="card">
      <div class="d-flex justify-between align-center mb-3">
        <div>
          <h4 style="color: #0b2545;">💰 Custom Treatment Charges Configuration</h4>
          <p style="color: #64748b; font-size: 0.85rem; margin-top: 0.2rem;">Doctor: <strong>${doctor ? doctor.fullName : 'Doctor'}</strong></p>
        </div>
      </div>
      <p style="color: #64748b; font-size: 0.9rem; margin-bottom: 1.5rem;">
        As a dentist at SunRise Dental Clinic, you can customize your professional procedure charges. When the nurse station generates a bill for your patients, your customized rates will be automatically applied.
      </p>

      <div class="table-responsive">
        <table class="table">
          <thead>
            <tr>
              <th>Procedure Code</th>
              <th>Treatment Name</th>
              <th>Category</th>
              <th>Standard Clinic Base Fee</th>
              <th>Your Custom Charge (${settings.currencySymbol})</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            ${treatments.map(t => {
              const currentFee = ClinicStore.getTreatmentChargeForDoctor(t.id, doctorId);
              return `
                <tr>
                  <td><span class="badge badge-info">${t.code}</span></td>
                  <td><strong>${t.name}</strong></td>
                  <td>${t.category}</td>
                  <td>${settings.currencySymbol} ${t.defaultFee.toLocaleString()}</td>
                  <td>
                    <input type="number" id="charge-input-${t.id}" class="form-control" style="width: 140px; font-weight: bold; color: #0077b6;" value="${currentFee}" />
                  </td>
                  <td>
                    <button class="btn btn-primary btn-sm" onclick="saveDoctorCustomFee('${doctorId}', '${t.id}')">
                      Update Fee
                    </button>
                  </td>
                </tr>
              `;
            }).join('')}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

window.saveDoctorCustomFee = function(doctorId, treatmentId) {
  const input = document.getElementById(`charge-input-${treatmentId}`);
  if (!input) return;
  const newFee = Number(input.value) || 0;

  ClinicStore.setDoctorTreatmentFee(doctorId, treatmentId, newFee);
  showToast(`Custom treatment fee updated to Rs. ${newFee.toLocaleString()}!`, 'success');
};

/* --------------------------------------------------------------------------
   11. Doctor Sessions Management (Add/Edit Session with Date, Time, Max Patients)
   -------------------------------------------------------------------------- */
function renderDoctorSessionsView() {
  const container = document.getElementById('view-doctor-sessions');
  if (!container) return;

  const doctorId = currentUser.role === 'DOCTOR' ? currentUser.id : 'USR-003';
  const sessions = ClinicStore.getSessions().filter(s => s.doctorId === doctorId);

  container.innerHTML = `
    <div class="card">
      <div class="d-flex justify-between align-center mb-3">
        <h4 style="color: #0b2545;">🗓️ My Scheduled Consultation Sessions</h4>
        <button class="btn btn-primary" onclick="openAddSessionModal()">+ Schedule New Session</button>
      </div>

      <div class="table-responsive">
        <table class="table">
          <thead>
            <tr>
              <th>Session Ref</th>
              <th>Date</th>
              <th>Time Range</th>
              <th>Room</th>
              <th>Max Patient Allocation</th>
              <th>Booked Slots</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            ${sessions.map(s => `
              <tr>
                <td><strong>${s.id}</strong></td>
                <td>${s.date}</td>
                <td>${s.startTime} - ${s.endTime}</td>
                <td>${s.roomNo}</td>
                <td><strong>${s.maxPatients} Patients</strong></td>
                <td><span class="badge badge-info">${s.bookedCount} Booked</span></td>
                <td><span class="badge ${s.status === 'ACTIVE' ? 'badge-success' : 'badge-warning'}">${s.status}</span></td>
                <td>
                  <button class="btn btn-secondary btn-sm" onclick="openEditSessionModal('${s.id}')">
                    Edit Capacity / Time
                  </button>
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

window.openAddSessionModal = function() {
  const doctors = ClinicStore.getDoctors();

  const modalHtml = `
    <div class="modal-backdrop active" id="modal-add-session">
      <div class="modal">
        <div class="modal-header">
          <div class="modal-title"><span>🗓️</span> Add New Doctor Session</div>
          <button class="modal-close" onclick="document.getElementById('modal-add-session').remove()">&times;</button>
        </div>
        <div class="modal-body">
          <form onsubmit="handleCreateSession(event)">
            <div class="form-group">
              <label class="form-label">Attending Doctor <span class="required">*</span>:</label>
              <select id="modal-session-doc" class="form-select" required>
                ${doctors.map(d => `<option value="${d.id}" ${currentUser.id === d.id ? 'selected' : ''}>${d.fullName} (${d.specialty})</option>`).join('')}
              </select>
            </div>

            <div class="form-row">
              <div class="form-group">
                <label class="form-label">Session Date <span class="required">*</span>:</label>
                <input type="date" id="modal-session-date" class="form-control" value="${new Date().toISOString().split('T')[0]}" required />
              </div>
              <div class="form-group">
                <label class="form-label">Clinic Room No:</label>
                <select id="modal-session-room" class="form-select">
                  <option value="Room 01">Room 01 (Cosmetic & Restorative)</option>
                  <option value="Room 02">Room 02 (Oral Surgery Unit)</option>
                  <option value="Room 03">Room 03 (Implantology Center)</option>
                  <option value="Room 04">Room 04 (Pediatric Dentistry)</option>
                </select>
              </div>
            </div>

            <div class="form-row">
              <div class="form-group">
                <label class="form-label">Start Time <span class="required">*</span>:</label>
                <input type="time" id="modal-session-start" class="form-control" value="09:00" required />
              </div>
              <div class="form-group">
                <label class="form-label">End Time <span class="required">*</span>:</label>
                <input type="time" id="modal-session-end" class="form-control" value="13:00" required />
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">Maximum Patient Allocation Capacity <span class="required">*</span>:</label>
              <input type="number" id="modal-session-capacity" class="form-control" value="12" min="1" max="40" required />
            </div>

            <div class="modal-footer" style="padding: 1rem 0 0 0; background: transparent;">
              <button type="button" class="btn btn-secondary" onclick="document.getElementById('modal-add-session').remove()">Cancel</button>
              <button type="submit" class="btn btn-primary">Create Session</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `;

  document.body.insertAdjacentHTML('beforeend', modalHtml);
};

window.handleCreateSession = function(e) {
  e.preventDefault();
  const doctorId = document.getElementById('modal-session-doc').value;
  const date = document.getElementById('modal-session-date').value;
  const roomNo = document.getElementById('modal-session-room').value;
  const startTime = document.getElementById('modal-session-start').value;
  const endTime = document.getElementById('modal-session-end').value;
  const maxPatients = Number(document.getElementById('modal-session-capacity').value) || 10;

  const newSession = ClinicStore.addSession({
    doctorId,
    date,
    roomNo,
    startTime,
    endTime,
    maxPatients
  });

  const modal = document.getElementById('modal-add-session');
  if (modal) modal.remove();

  showToast(`Doctor session ${newSession.id} created successfully!`, 'success');
  if (currentActiveTab === 'doctor-sessions') renderDoctorSessionsView();
  else if (currentActiveTab === 'overview') renderOverviewView();
};

window.openEditSessionModal = function(sessionId) {
  const session = ClinicStore.getSessions().find(s => s.id === sessionId);
  if (!session) return;

  const modalHtml = `
    <div class="modal-backdrop active" id="modal-edit-session">
      <div class="modal">
        <div class="modal-header">
          <div class="modal-title"><span>✏️</span> Edit Session: ${session.id}</div>
          <button class="modal-close" onclick="document.getElementById('modal-edit-session').remove()">&times;</button>
        </div>
        <div class="modal-body">
          <form onsubmit="handleUpdateSession(event, '${session.id}')">
            <div class="form-row">
              <div class="form-group">
                <label class="form-label">Session Date:</label>
                <input type="date" id="edit-session-date" class="form-control" value="${session.date}" required />
              </div>
              <div class="form-group">
                <label class="form-label">Room Number:</label>
                <input type="text" id="edit-session-room" class="form-control" value="${session.roomNo}" required />
              </div>
            </div>

            <div class="form-row">
              <div class="form-group">
                <label class="form-label">Start Time:</label>
                <input type="time" id="edit-session-start" class="form-control" value="${session.startTime}" required />
              </div>
              <div class="form-group">
                <label class="form-label">End Time:</label>
                <input type="time" id="edit-session-end" class="form-control" value="${session.endTime}" required />
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">Max Patient Capacity:</label>
              <input type="number" id="edit-session-capacity" class="form-control" value="${session.maxPatients}" min="${session.bookedCount || 1}" max="50" required />
            </div>

            <div class="modal-footer" style="padding: 1rem 0 0 0; background: transparent;">
              <button type="button" class="btn btn-secondary" onclick="document.getElementById('modal-edit-session').remove()">Cancel</button>
              <button type="submit" class="btn btn-primary">Update Session</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `;

  document.body.insertAdjacentHTML('beforeend', modalHtml);
};

window.handleUpdateSession = function(e, sessionId) {
  e.preventDefault();
  const date = document.getElementById('edit-session-date').value;
  const roomNo = document.getElementById('edit-session-room').value;
  const startTime = document.getElementById('edit-session-start').value;
  const endTime = document.getElementById('edit-session-end').value;
  const maxPatients = Number(document.getElementById('edit-session-capacity').value);

  ClinicStore.updateSession(sessionId, {
    date,
    roomNo,
    startTime,
    endTime,
    maxPatients
  });

  const modal = document.getElementById('modal-edit-session');
  if (modal) modal.remove();

  showToast('Session updated successfully!', 'success');
  renderDoctorSessionsView();
};

/* --------------------------------------------------------------------------
   12. Income Reports (Overall & Doctor-Wise Monthly Analytics & PDF Export)
   -------------------------------------------------------------------------- */
function renderIncomeReportsView() {
  const container = document.getElementById('view-income-reports');
  if (!container) return;

  const payments = ClinicStore.getPayments();
  const doctors = ClinicStore.getDoctors();
  const settings = ClinicStore.getSettings();

  const totalRevenue = payments.reduce((acc, p) => acc + Number(p.amountPaid), 0);
  const cashRevenue = payments.filter(p => p.paymentType === 'CASH').reduce((acc, p) => acc + Number(p.amountPaid), 0);
  const cardRevenue = payments.filter(p => p.paymentType === 'CARD').reduce((acc, p) => acc + Number(p.amountPaid), 0);

  // Compute Doctor-wise breakdown
  const doctorBreakdown = doctors.map(doc => {
    const docPayments = payments.filter(p => p.doctorId === doc.id);
    const rev = docPayments.reduce((acc, p) => acc + Number(p.amountPaid), 0);
    const apts = ClinicStore.getAppointments().filter(a => a.doctorId === doc.id && a.status === 'PAID');
    const invoices = ClinicStore.getInvoices().filter(i => i.doctorId === doc.id && i.status === 'PAID');

    return {
      doctorId: doc.id,
      doctorName: doc.fullName,
      specialty: doc.specialty || 'General Dentistry',
      appointmentsCount: apts.length,
      treatmentsCount: invoices.length,
      totalRevenue: rev
    };
  });

  const maxDocRev = Math.max(1, ...doctorBreakdown.map(d => d.totalRevenue));

  container.innerHTML = `
    <div class="card mb-4">
      <div class="d-flex justify-between align-center mb-3">
        <div>
          <h4 style="color: #0b2545;">📈 Monthly Financial Revenue & Doctor-Wise Income Audit</h4>
          <p style="color: #64748b; font-size: 0.85rem;">Reporting Period: <strong>September 2026</strong></p>
        </div>
        <button class="btn btn-primary" onclick="exportMonthlyIncomePdf()">
          🖨️ Export Certified Income PDF
        </button>
      </div>

      <!-- High-level KPI Cards -->
      <div class="stats-grid" style="margin-bottom: 2rem;">
        <div class="stat-card">
          <div class="stat-icon-wrapper" style="background: #d1fae5; color: #10b981;">💵</div>
          <div>
            <div class="stat-val">${settings.currencySymbol} ${totalRevenue.toLocaleString()}</div>
            <div class="stat-label">Total Net Clinic Income</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon-wrapper" style="background: #e0f2fe; color: #0284c7;">💶</div>
          <div>
            <div class="stat-val">${settings.currencySymbol} ${cashRevenue.toLocaleString()}</div>
            <div class="stat-label">Cash Collections</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon-wrapper" style="background: #ede9fe; color: #8b5cf6;">💳</div>
          <div>
            <div class="stat-val">${settings.currencySymbol} ${cardRevenue.toLocaleString()}</div>
            <div class="stat-label">Card Collections (POS)</div>
          </div>
        </div>
      </div>

      <!-- Pure CSS Doctor-Wise Income Bar Chart -->
      <div class="chart-card">
        <h5 style="color: #0b2545; margin-bottom: 1rem;">Doctor-Wise Revenue Contribution Graph</h5>
        <div class="income-bar-chart">
          ${doctorBreakdown.map(doc => {
            const heightPct = Math.max(15, Math.round((doc.totalRevenue / maxDocRev) * 100));
            return `
              <div class="chart-bar-group">
                <div class="chart-bar" style="height: ${heightPct}%;">
                  <div class="chart-bar-tooltip">
                    ${doc.doctorName}: ${settings.currencySymbol} ${doc.totalRevenue.toLocaleString()}
                  </div>
                </div>
                <div class="chart-bar-label">${doc.doctorName.replace('Dr. ', '')}</div>
              </div>
            `;
          }).join('')}
        </div>
      </div>

      <!-- Doctor-Wise Income Table -->
      <h5 style="color: #0b2545; margin-bottom: 0.75rem;">Doctor-Wise Income Table</h5>
      <div class="table-responsive">
        <table class="table">
          <thead>
            <tr>
              <th>#</th>
              <th>Doctor Name</th>
              <th>Specialty</th>
              <th style="text-align: center;">Paid Consultations</th>
              <th style="text-align: center;">Treatments Billed</th>
              <th style="text-align: right;">Total Generated Revenue</th>
            </tr>
          </thead>
          <tbody>
            ${doctorBreakdown.map((doc, idx) => `
              <tr>
                <td>${idx + 1}</td>
                <td><strong>${doc.doctorName}</strong></td>
                <td>${doc.specialty}</td>
                <td style="text-align: center;">${doc.appointmentsCount}</td>
                <td style="text-align: center;">${doc.treatmentsCount}</td>
                <td style="text-align: right; font-weight: bold; color: #10b981;">
                  ${settings.currencySymbol} ${doc.totalRevenue.toLocaleString()}
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

window.exportMonthlyIncomePdf = function() {
  const payments = ClinicStore.getPayments();
  const doctors = ClinicStore.getDoctors();
  const totalRevenue = payments.reduce((acc, p) => acc + Number(p.amountPaid), 0);
  const cashRevenue = payments.filter(p => p.paymentType === 'CASH').reduce((acc, p) => acc + Number(p.amountPaid), 0);
  const cardRevenue = payments.filter(p => p.paymentType === 'CARD').reduce((acc, p) => acc + Number(p.amountPaid), 0);

  const doctorBreakdown = doctors.map(doc => {
    const docPayments = payments.filter(p => p.doctorId === doc.id);
    const rev = docPayments.reduce((acc, p) => acc + Number(p.amountPaid), 0);
    const apts = ClinicStore.getAppointments().filter(a => a.doctorId === doc.id && a.status === 'PAID');
    const invoices = ClinicStore.getInvoices().filter(i => i.doctorId === doc.id && i.status === 'PAID');

    return {
      doctorId: doc.id,
      doctorName: doc.fullName,
      specialty: doc.specialty || 'General Dentistry',
      appointmentsCount: apts.length,
      treatmentsCount: invoices.length,
      totalRevenue: rev
    };
  });

  PrintUtil.printMonthlyIncomeReport({
    monthYear: 'September 2026',
    totalRevenue,
    cashRevenue,
    cardRevenue,
    totalPatients: ClinicStore.getPatients().length,
    doctorBreakdown
  });
};

/* --------------------------------------------------------------------------
   13. Patient Reports Management View (Upload PDF / Image Reports)
   -------------------------------------------------------------------------- */
function renderPatientReportsMgmtView() {
  const container = document.getElementById('view-patient-reports-mgmt');
  if (!container) return;

  const reports = ClinicStore.getReports();
  const doctors = ClinicStore.getDoctors();

  container.innerHTML = `
    <div class="card">
      <div class="d-flex justify-between align-center mb-3">
        <h4 style="color: #0b2545;">📁 Patient Diagnostic Reports & Radiology Archive</h4>
        <button class="btn btn-primary" onclick="openUploadReportModal()">+ Upload New Patient Report</button>
      </div>

      <div class="table-responsive">
        <table class="table">
          <thead>
            <tr>
              <th>Report No</th>
              <th>Patient NIC</th>
              <th>Patient Name</th>
              <th>Attending Doctor</th>
              <th>Investigation / Report Type</th>
              <th>Date</th>
              <th>Archived File Name</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            ${reports.map(r => `
              <tr>
                <td><span class="badge badge-info">${r.reportNo}</span></td>
                <td><strong>${r.patientNic}</strong></td>
                <td>${r.patientName}</td>
                <td>${r.doctorName}</td>
                <td>${r.reportType}</td>
                <td>${r.date}</td>
                <td><code>storage/reports/${r.fileName}</code></td>
                <td>
                  <button class="btn btn-secondary btn-sm" onclick="PrintUtil.printClinicalReport(ClinicStore.getReports().find(rep => rep.id === '${r.id}'))">
                    Print / PDF
                  </button>
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

window.openUploadReportModal = function() {
  const doctors = ClinicStore.getDoctors();

  const modalHtml = `
    <div class="modal-backdrop active" id="modal-upload-report">
      <div class="modal">
        <div class="modal-header">
          <div class="modal-title"><span>📁</span> Upload Patient Diagnostic Report</div>
          <button class="modal-close" onclick="document.getElementById('modal-upload-report').remove()">&times;</button>
        </div>
        <div class="modal-body">
          <form onsubmit="handleUploadReportSubmit(event)">
            <div class="form-row">
              <div class="form-group">
                <label class="form-label">Patient NIC <span class="required">*</span>:</label>
                <input type="text" id="up-pat-nic" class="form-control" placeholder="Enter Patient NIC..." required />
              </div>
              <div class="form-group">
                <label class="form-label">Patient Name <span class="required">*</span>:</label>
                <input type="text" id="up-pat-name" class="form-control" placeholder="Enter Patient Name..." required />
              </div>
            </div>

            <div class="form-row">
              <div class="form-group">
                <label class="form-label">Attending Doctor <span class="required">*</span>:</label>
                <select id="up-doctor-id" class="form-select">
                  ${doctors.map(d => `<option value="${d.id}">${d.fullName} (${d.specialty})</option>`).join('')}
                </select>
              </div>
              <div class="form-group">
                <label class="form-label">Report Date:</label>
                <input type="date" id="up-report-date" class="form-control" value="${new Date().toISOString().split('T')[0]}" />
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">Investigation / Diagnostic Type <span class="required">*</span>:</label>
              <select id="up-report-type" class="form-select">
                <option value="IOPA Digital X-Ray">IOPA Digital Dental X-Ray</option>
                <option value="Panoramic OPG Radiograph">Panoramic OPG Radiograph</option>
                <option value="CBCT 3D Bone Density Scan">CBCT 3D Bone Density Scan</option>
                <option value="Orthodontic Cephalometric Tracing">Orthodontic Cephalometric Tracing</option>
                <option value="Periodontal Deep Pocket Assessment">Periodontal Deep Pocket Assessment</option>
                <option value="Histopathology Lab Report">Histopathology Lab Report</option>
              </select>
            </div>

            <div class="form-group">
              <label class="form-label">Upload PDF / Diagnostic Image File:</label>
              <input type="file" id="up-file-input" class="form-control" accept=".pdf,.png,.jpg,.jpeg" />
              <small class="text-muted">Standard local save format: <code>storage/reports/YYYY-MM-DD_NIC_ReportType.pdf</code></small>
            </div>

            <div class="form-group">
              <label class="form-label">Clinical Observations & Diagnostic Findings <span class="required">*</span>:</label>
              <textarea id="up-report-findings" class="form-control" rows="4" placeholder="Enter diagnostic observations..." required></textarea>
            </div>

            <div class="modal-footer" style="padding: 1rem 0 0 0; background: transparent;">
              <button type="button" class="btn btn-secondary" onclick="document.getElementById('modal-upload-report').remove()">Cancel</button>
              <button type="submit" class="btn btn-primary">Upload & Verify Report</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `;

  document.body.insertAdjacentHTML('beforeend', modalHtml);
};

window.handleUploadReportSubmit = function(e) {
  e.preventDefault();
  const patientNic = document.getElementById('up-pat-nic').value.trim();
  const patientName = document.getElementById('up-pat-name').value.trim();
  const doctorId = document.getElementById('up-doctor-id').value;
  const doctor = ClinicStore.getUserById(doctorId);
  const date = document.getElementById('up-report-date').value;
  const reportType = document.getElementById('up-report-type').value;
  const findings = document.getElementById('up-report-findings').value.trim();

  const newReport = ClinicStore.addReport({
    patientNic,
    patientName,
    doctorId,
    doctorName: doctor ? doctor.fullName : 'Doctor',
    reportType,
    date,
    findings
  });

  const modal = document.getElementById('modal-upload-report');
  if (modal) modal.remove();

  showToast(`Report ${newReport.reportNo} successfully archived! Saved as: storage/reports/${newReport.fileName}`, 'success');
  renderPatientReportsMgmtView();
};

/* --------------------------------------------------------------------------
   14. Super Admin Clinic Global Settings (Logo, Name, Invoicing Format)
   -------------------------------------------------------------------------- */
function renderClinicSettingsView() {
  const container = document.getElementById('view-clinic-settings');
  if (!container) return;

  const s = ClinicStore.getSettings();

  container.innerHTML = `
    <div class="card" style="max-width: 800px; margin: 0 auto;">
      <h4 style="color: #0b2545; margin-bottom: 1.25rem;">⚙️ Clinic Master Settings & Branding</h4>
      <form onsubmit="handleSaveClinicSettings(event)">
        <div class="form-row">
          <div class="form-group">
            <label class="form-label">Clinic Name:</label>
            <input type="text" id="set-clinic-name" class="form-control" value="${s.clinicName}" required />
          </div>
          <div class="form-group">
            <label class="form-label">Tagline:</label>
            <input type="text" id="set-tagline" class="form-control" value="${s.tagline}" />
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label class="form-label">Hotline Phone Numbers:</label>
            <input type="text" id="set-phone" class="form-control" value="${s.phone}" required />
          </div>
          <div class="form-group">
            <label class="form-label">Official Email:</label>
            <input type="email" id="set-email" class="form-control" value="${s.email}" required />
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">Clinic Physical Address:</label>
          <input type="text" id="set-address" class="form-control" value="${s.address}" required />
        </div>

        <div class="form-row">
          <div class="form-group">
            <label class="form-label">Medical Registration No:</label>
            <input type="text" id="set-regno" class="form-control" value="${s.regNo}" />
          </div>
          <div class="form-group">
            <label class="form-label">Currency Symbol:</label>
            <input type="text" id="set-currency" class="form-control" value="${s.currencySymbol}" />
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label class="form-label">Invoice Numbering Prefix:</label>
            <input type="text" id="set-inv-prefix" class="form-control" value="${s.invoicePrefix}" />
          </div>
          <div class="form-group">
            <label class="form-label">Receipt Numbering Prefix:</label>
            <input type="text" id="set-rec-prefix" class="form-control" value="${s.receiptPrefix}" />
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">Receipt & Invoice Footer Disclaimer:</label>
          <textarea id="set-footer-note" class="form-control">${s.footerNote}</textarea>
        </div>

        <button type="submit" class="btn btn-primary btn-lg w-100">
          💾 Save Master Settings
        </button>
      </form>
    </div>
  `;
}

window.handleSaveClinicSettings = function(e) {
  e.preventDefault();
  const clinicName = document.getElementById('set-clinic-name').value.trim();
  const tagline = document.getElementById('set-tagline').value.trim();
  const phone = document.getElementById('set-phone').value.trim();
  const email = document.getElementById('set-email').value.trim();
  const address = document.getElementById('set-address').value.trim();
  const regNo = document.getElementById('set-regno').value.trim();
  const currencySymbol = document.getElementById('set-currency').value.trim();
  const invoicePrefix = document.getElementById('set-inv-prefix').value.trim();
  const receiptPrefix = document.getElementById('set-rec-prefix').value.trim();
  const footerNote = document.getElementById('set-footer-note').value.trim();

  ClinicStore.updateSettings({
    clinicName,
    tagline,
    phone,
    email,
    address,
    regNo,
    currencySymbol,
    invoicePrefix,
    receiptPrefix,
    footerNote
  });

  showToast('Clinic global settings updated successfully!', 'success');
  renderTopbar();
};

/* --------------------------------------------------------------------------
   15. Staff & User Management (Super Admin & Admin)
   -------------------------------------------------------------------------- */
function renderUserManagementView() {
  const container = document.getElementById('view-user-management');
  if (!container) return;

  const users = ClinicStore.getUsers();

  container.innerHTML = `
    <div class="card">
      <div class="d-flex justify-between align-center mb-3">
        <h4 style="color: #0b2545;">🛡️ Staff Users & Role Access Management</h4>
        <button class="btn btn-primary" onclick="openAddUserModal()">+ Add New Staff Member</button>
      </div>

      <div class="table-responsive">
        <table class="table">
          <thead>
            <tr>
              <th>User ID</th>
              <th>Full Name</th>
              <th>Username</th>
              <th>Role</th>
              <th>Contact Phone</th>
              <th>Email</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            ${users.map(u => `
              <tr>
                <td><strong>${u.id}</strong></td>
                <td>${u.fullName}</td>
                <td><code>${u.username}</code></td>
                <td><span class="badge ${getRoleBadgeClass(u.role)}">${formatRole(u.role)}</span></td>
                <td>${u.phone}</td>
                <td>${u.email}</td>
                <td><span class="badge ${u.status === 'ACTIVE' ? 'badge-success' : 'badge-danger'}">${u.status}</span></td>
                <td>
                  <button class="btn btn-secondary btn-sm" onclick="toggleUserStatus('${u.id}')">
                    ${u.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                  </button>
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

window.openAddUserModal = function() {
  const modalHtml = `
    <div class="modal-backdrop active" id="modal-add-user">
      <div class="modal">
        <div class="modal-header">
          <div class="modal-title"><span>👤</span> Add New Staff User</div>
          <button class="modal-close" onclick="document.getElementById('modal-add-user').remove()">&times;</button>
        </div>
        <div class="modal-body">
          <form onsubmit="handleCreateUser(event)">
            <div class="form-group">
              <label class="form-label">Full Name <span class="required">*</span>:</label>
              <input type="text" id="usr-name" class="form-control" required />
            </div>

            <div class="form-row">
              <div class="form-group">
                <label class="form-label">Username <span class="required">*</span>:</label>
                <input type="text" id="usr-login" class="form-control" required />
              </div>
              <div class="form-group">
                <label class="form-label">Password <span class="required">*</span>:</label>
                <input type="password" id="usr-pass" class="form-control" value="123" required />
              </div>
            </div>

            <div class="form-row">
              <div class="form-group">
                <label class="form-label">System Role <span class="required">*</span>:</label>
                <select id="usr-role" class="form-select">
                  ${currentUser.role === 'SUPER_ADMIN' ? '<option value="ADMIN">Clinic Admin</option>' : ''}
                  <option value="DOCTOR">Doctor / Specialist</option>
                  <option value="NURSE">Nurse</option>
                  <option value="CASHIER">Cashier (POS)</option>
                  <option value="PATIENT_ADMIN">Patient Administrator</option>
                </select>
              </div>
              <div class="form-group">
                <label class="form-label">Contact Phone:</label>
                <input type="tel" id="usr-phone" class="form-control" />
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">Email Address:</label>
              <input type="email" id="usr-email" class="form-control" />
            </div>

            <div class="modal-footer" style="padding: 1rem 0 0 0; background: transparent;">
              <button type="button" class="btn btn-secondary" onclick="document.getElementById('modal-add-user').remove()">Cancel</button>
              <button type="submit" class="btn btn-primary">Create User</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `;

  document.body.insertAdjacentHTML('beforeend', modalHtml);
};

window.handleCreateUser = function(e) {
  e.preventDefault();
  const fullName = document.getElementById('usr-name').value.trim();
  const username = document.getElementById('usr-login').value.trim();
  const password = document.getElementById('usr-pass').value;
  const role = document.getElementById('usr-role').value;
  const phone = document.getElementById('usr-phone').value.trim();
  const email = document.getElementById('usr-email').value.trim();

  const newUser = ClinicStore.addUser({
    fullName,
    username,
    password,
    role,
    phone,
    email,
    consultationFee: role === 'DOCTOR' ? 2500 : 0
  });

  const modal = document.getElementById('modal-add-user');
  if (modal) modal.remove();

  showToast(`Staff member ${newUser.fullName} successfully created!`, 'success');
  renderUserManagementView();
};

window.toggleUserStatus = function(userId) {
  const user = ClinicStore.getUserById(userId);
  if (!user) return;
  const newStatus = user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
  ClinicStore.updateUser(userId, { status: newStatus });
  showToast(`User ${user.fullName} is now ${newStatus}.`, 'info');
  renderUserManagementView();
};

/* --------------------------------------------------------------------------
   16. Invoices, Appointments & Session Tables
   -------------------------------------------------------------------------- */
function renderInvoicesListView() {
  const container = document.getElementById('view-invoices-list') || document.getElementById('view-cashier-invoices');
  if (!container) return;

  const invoices = ClinicStore.getInvoices();
  const settings = ClinicStore.getSettings();

  container.innerHTML = `
    <div class="card">
      <div class="d-flex justify-between align-center mb-3">
        <h4 style="color: #0b2545;">🧾 Treatment Invoices Oversight & Settlement</h4>
        <span class="badge badge-info">${invoices.length} Total Invoices</span>
      </div>

      <div class="table-responsive">
        <table class="table">
          <thead>
            <tr>
              <th>Invoice No</th>
              <th>Patient Name</th>
              <th>NIC</th>
              <th>Attending Doctor</th>
              <th>Treatment Type</th>
              <th>Total Amount</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            ${invoices.map(inv => `
              <tr>
                <td><strong>${inv.invoiceNo}</strong></td>
                <td>${inv.patientName}</td>
                <td>${inv.patientNic}</td>
                <td>${inv.doctorName}</td>
                <td>${inv.treatmentType || 'Dental Service'}</td>
                <td style="font-weight: bold; color: #0077b6;">${settings.currencySymbol} ${Number(inv.totalAmount).toLocaleString()}</td>
                <td><span class="badge ${inv.status === 'PAID' ? 'badge-success' : 'badge-warning'}">${inv.status}</span></td>
                <td>
                  <button class="btn btn-secondary btn-sm" onclick="PrintUtil.printTreatmentInvoice(ClinicStore.getInvoices().find(i => i.invoiceNo === '${inv.invoiceNo}'))">
                    Print Invoice
                  </button>
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

function renderAllAppointmentsView() {
  const container = document.getElementById('view-all-appointments');
  if (!container) return;

  const appointments = ClinicStore.getAppointments();
  const settings = ClinicStore.getSettings();

  container.innerHTML = `
    <div class="card">
      <div class="d-flex justify-between align-center mb-3">
        <h4 style="color: #0b2545;">📋 All Appointments Directory</h4>
        <span class="badge badge-info">${appointments.length} Records</span>
      </div>

      <div class="table-responsive">
        <table class="table">
          <thead>
            <tr>
              <th>Token</th>
              <th>Ref ID</th>
              <th>Patient Name</th>
              <th>NIC</th>
              <th>Doctor</th>
              <th>Date</th>
              <th>Fee</th>
              <th>Status</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            ${appointments.map(a => `
              <tr>
                <td><span class="badge badge-primary"># ${a.tokenNumber}</span></td>
                <td>${a.id}</td>
                <td><strong>${a.patientName}</strong></td>
                <td>${a.patientNic}</td>
                <td>${a.doctorName}</td>
                <td>${a.date}</td>
                <td>${settings.currencySymbol} ${Number(a.consultationFee).toLocaleString()}</td>
                <td><span class="badge ${a.status === 'PAID' ? 'badge-success' : (a.status === 'CANCELLED' ? 'badge-danger' : 'badge-warning')}">${a.status}</span></td>
                <td>
                  <button class="btn btn-secondary btn-sm" onclick="PrintUtil.printAppointmentSlip(ClinicStore.getAppointments().find(apt => apt.id === '${a.id}'))">
                    Print Slip
                  </button>
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

function renderAllSessionsView() {
  const container = document.getElementById('view-all-sessions');
  if (!container) return;

  const sessions = ClinicStore.getSessions();
  const settings = ClinicStore.getSettings();

  container.innerHTML = `
    <div class="card">
      <div class="d-flex justify-between align-center mb-3">
        <h4 style="color: #0b2545;">🗓️ All Doctor Consultation Schedules</h4>
      </div>

      <div class="table-responsive">
        <table class="table">
          <thead>
            <tr>
              <th>Session ID</th>
              <th>Doctor</th>
              <th>Specialty</th>
              <th>Date</th>
              <th>Time Range</th>
              <th>Room</th>
              <th>Fee</th>
              <th>Booked / Max</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            ${sessions.map(s => `
              <tr>
                <td><strong>${s.id}</strong></td>
                <td>${s.doctorName}</td>
                <td>${s.specialty}</td>
                <td>${s.date}</td>
                <td>${s.startTime} - ${s.endTime}</td>
                <td>${s.roomNo}</td>
                <td>${settings.currencySymbol} ${Number(s.consultationFee).toLocaleString()}</td>
                <td><span class="badge badge-info">${s.bookedCount} / ${s.maxPatients}</span></td>
                <td><span class="badge ${s.status === 'ACTIVE' ? 'badge-success' : 'badge-warning'}">${s.status}</span></td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

function renderCashierHistoryView() {
  const container = document.getElementById('view-cashier-history');
  if (!container) return;

  const payments = ClinicStore.getPayments();
  const settings = ClinicStore.getSettings();

  container.innerHTML = `
    <div class="card">
      <div class="d-flex justify-between align-center mb-3">
        <h4 style="color: #0b2545;">📜 Cashier Daily Settlement & Payment Audit Log</h4>
        <span class="badge badge-success">${payments.length} Transactions</span>
      </div>

      <div class="table-responsive">
        <table class="table">
          <thead>
            <tr>
              <th>Receipt No</th>
              <th>Date & Time</th>
              <th>Patient Name</th>
              <th>NIC</th>
              <th>Payment Mode</th>
              <th>Card / Bank Details</th>
              <th>Amount Paid</th>
              <th>Cashier</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            ${payments.map(p => `
              <tr>
                <td><strong>${p.receiptNo}</strong></td>
                <td>${p.timestamp}</td>
                <td>${p.patientName}</td>
                <td>${p.patientNic}</td>
                <td><span class="badge ${p.paymentType === 'CARD' ? 'badge-purple' : 'badge-success'}">${p.paymentType}</span></td>
                <td>${p.paymentType === 'CARD' ? `${p.cardProvider || 'CARD'} (${p.bankName}) - ${p.cardNumberMasked}` : 'CASH'}</td>
                <td style="font-weight: bold; color: #10b981;">${settings.currencySymbol} ${Number(p.amountPaid).toLocaleString()}</td>
                <td>${p.cashierName}</td>
                <td>
                  <button class="btn btn-secondary btn-sm" onclick="PrintUtil.printPaymentReceipt(ClinicStore.getPayments().find(pay => pay.receiptNo === '${p.receiptNo}'))">
                    Print Receipt
                  </button>
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

function renderDoctorPatientHistoryView() {
  const container = document.getElementById('view-doctor-patient-history');
  if (!container) return;

  const patients = ClinicStore.getPatients();

  container.innerHTML = `
    <div class="card">
      <h4 style="color: #0b2545; margin-bottom: 1rem;">📋 Patient Directory & Diagnostic History</h4>
      <div class="table-responsive">
        <table class="table">
          <thead>
            <tr>
              <th>Patient NIC</th>
              <th>Full Name</th>
              <th>Phone</th>
              <th>Blood Group</th>
              <th>Known Allergies</th>
              <th>Medical History</th>
              <th>Reports on File</th>
            </tr>
          </thead>
          <tbody>
            ${patients.map(p => {
              const repCount = ClinicStore.getReportsByNic(p.nic).length;
              return `
                <tr>
                  <td><strong>${p.nic}</strong></td>
                  <td>${p.fullName}</td>
                  <td>${p.phone}</td>
                  <td><span class="badge badge-info">${p.bloodGroup}</span></td>
                  <td><span style="color: #ef4444; font-weight: bold;">${p.allergies}</span></td>
                  <td>${p.medicalHistory}</td>
                  <td><span class="badge badge-success">${repCount} Reports</span></td>
                </tr>
              `;
            }).join('')}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

/* --------------------------------------------------------------------------
   Helpers & Formatters
   -------------------------------------------------------------------------- */
function formatRole(role) {
  const map = {
    'SUPER_ADMIN': 'Super Administrator',
    'ADMIN': 'Clinic Administrator',
    'DOCTOR': 'Attending Doctor / Specialist',
    'NURSE': 'Nurse Station',
    'PATIENT_ADMIN': 'Patient Administrator',
    'CASHIER': 'POS Cashier'
  };
  return map[role] || role;
}

function getRoleBadgeClass(role) {
  const map = {
    'SUPER_ADMIN': 'badge-purple',
    'ADMIN': 'badge-primary',
    'DOCTOR': 'badge-info',
    'NURSE': 'badge-success',
    'PATIENT_ADMIN': 'badge-warning',
    'CASHIER': 'badge-purple'
  };
  return map[role] || 'badge-info';
}

function formatTabTitle(tabId) {
  const map = {
    'overview': 'Dashboard Overview',
    'pos-cashier': 'POS Cashier & Payment Checkout Desk',
    'patient-admin': 'Patient Registration & Session Slot Booking',
    'nurse-triage': 'Nurse Station & Patient Triage Queue',
    'nurse-billing': 'Dental Treatment Invoice & Bill Generator',
    'doctor-portal': 'Doctor Consultation & Live Queue Desk',
    'doctor-sessions': 'Doctor Scheduled Consultation Sessions',
    'doctor-pricing': 'Doctor Custom Treatment Charges',
    'doctor-patient-history': 'Patient Medical & Clinical History',
    'income-reports': 'Monthly Revenue & Financial Income Reports',
    'invoices-list': 'Treatment Invoices Oversight',
    'cashier-invoices': 'Settle Treatment Invoices',
    'patient-reports-mgmt': 'Patient Diagnostic Reports & File Vault',
    'user-management': 'Staff Users & Role Management',
    'clinic-settings': 'Clinic Global Settings & Branding',
    'all-appointments': 'Appointments Master Directory',
    'all-sessions': 'Doctor Sessions Master Schedule',
    'cancel-appointment': 'Cancel Patient Appointments by NIC',
    'cashier-history': 'Daily Cashier Payment Audit Log'
  };
  return map[tabId] || 'SunRise Dental Management';
}

function getInitials(name) {
  if (!name) return 'SR';
  const parts = name.replace('Dr. ', '').replace('Sister ', '').replace('Nurse ', '').split(' ');
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
  return name.substring(0, 2).toUpperCase();
}
