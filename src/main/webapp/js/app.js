/* ==========================================================================
   SunRise Dental Clinic - Public Portal & Website Controller
   Pure Vanilla JavaScript - Zero Frameworks
   ========================================================================== */

ClinicStore.readyPublic(() => {
  initSplashScreen();
  initNavigation();
  initDoctorSchedules();
  initPatientReportFinder();
  initPublicAppointmentModal();
  initFaqAccordion();
});

/* --------------------------------------------------------------------------
   1. Splash Loading Screen (3 Seconds Zoom-In Animation)
   -------------------------------------------------------------------------- */
function initSplashScreen() {
  const splash = document.getElementById('splash-screen');
  if (!splash) return;

  // Run 3 seconds zoom-in animation then fade out smoothly
  setTimeout(() => {
    splash.classList.add('fade-out');
    setTimeout(() => {
      splash.style.display = 'none';
    }, 600);
  }, 3000);

  // Allow immediate skip on user click
  splash.addEventListener('click', () => {
    splash.classList.add('fade-out');
    setTimeout(() => {
      splash.style.display = 'none';
    }, 400);
  });
}

/* --------------------------------------------------------------------------
   2. Public Navigation & Mobile Menu
   -------------------------------------------------------------------------- */
function initNavigation() {
  const navbar = document.querySelector('.navbar');
  const mobileToggle = document.querySelector('.mobile-toggle');
  const navLinks = document.querySelector('.nav-links');

  window.addEventListener('scroll', () => {
    if (window.scrollY > 40) {
      navbar.classList.add('scrolled');
    } else {
      navbar.classList.remove('scrolled');
    }
  });

  if (mobileToggle && navLinks) {
    mobileToggle.addEventListener('click', () => {
      navLinks.classList.toggle('show');
    });

    document.querySelectorAll('.nav-link').forEach(link => {
      link.addEventListener('click', () => {
        navLinks.classList.remove('show');
      });
    });
  }
}

/* --------------------------------------------------------------------------
   3. Doctor Schedules with Live Date & Doctor Dropdown Filter
   -------------------------------------------------------------------------- */
function initDoctorSchedules() {
  const scheduleContainer = document.getElementById('schedule-grid-container');
  const doctorSelect = document.getElementById('filter-doctor');
  const dateInput = document.getElementById('filter-date');
  const resetBtn = document.getElementById('btn-reset-filter');

  if (!scheduleContainer) return;

  // Populate doctor select dropdown dynamically from ClinicStore
  if (doctorSelect) {
    const doctors = ClinicStore.getDoctors();
    doctorSelect.innerHTML = '<option value="ALL">-- All Specialists & Doctors --</option>';
    doctors.forEach(doc => {
      const opt = document.createElement('option');
      opt.value = doc.id;
      opt.textContent = `${doc.fullName} (${doc.specialty || 'General'})`;
      doctorSelect.appendChild(opt);
    });
  }

  function renderSchedules() {
    const sessions = ClinicStore.getSessions();
    const selectedDoc = doctorSelect ? doctorSelect.value : 'ALL';
    const selectedDate = dateInput ? dateInput.value : '';

    const filtered = sessions.filter(s => {
      if (s.status !== 'ACTIVE') return false;
      if (selectedDoc !== 'ALL' && s.doctorId !== selectedDoc) return false;
      if (selectedDate && s.date !== selectedDate) return false;
      return true;
    });

    if (filtered.length === 0) {
      scheduleContainer.innerHTML = `
        <div style="grid-column: 1 / -1; text-align: center; padding: 3rem; background: #ffffff; border-radius: 12px; border: 1px dashed #cbd5e1;">
          <div style="font-size: 2rem; margin-bottom: 0.5rem;">🗓️</div>
          <h4 style="color: #0b2545; margin-bottom: 0.5rem;">No Doctor Schedules Found</h4>
          <p style="color: #64748b;">There are no available consultation sessions matching your filter criteria.</p>
        </div>
      `;
      return;
    }

    const settings = ClinicStore.getSettings();
    scheduleContainer.innerHTML = filtered.map(session => {
      const available = Math.max(0, session.maxPatients - (session.bookedCount || 0));
      const isFull = available <= 0;

      return `
        <div class="schedule-card">
          <div class="schedule-card-header">
            <div class="doc-avatar">${session.doctorName.replace('Dr. ', '').substring(0, 2).toUpperCase()}</div>
            <div class="doc-info">
              <h4>${session.doctorName}</h4>
              <span>${session.specialty}</span>
            </div>
          </div>

          <div class="schedule-details">
            <div class="schedule-row">
              <span class="label">📅 Session Date:</span>
              <span class="value">${session.date}</span>
            </div>
            <div class="schedule-row">
              <span class="label">⏰ Time Range:</span>
              <span class="value">${session.startTime} - ${session.endTime}</span>
            </div>
            <div class="schedule-row">
              <span class="label">🏥 Clinic Room:</span>
              <span class="value">${session.roomNo}</span>
            </div>
            <div class="schedule-row">
              <span class="label">💵 Consultation Fee:</span>
              <span class="value" style="color: #0077b6;">${settings.currencySymbol} ${Number(session.consultationFee).toLocaleString()}</span>
            </div>
            <div class="schedule-row">
              <span class="label">👥 Patient Slots:</span>
              <span class="value">
                ${isFull ? '<span class="badge badge-danger">SESSION FULL</span>' : `<span class="badge badge-success">${available} of ${session.maxPatients} Available</span>`}
              </span>
            </div>
          </div>

          <div>
            ${isFull ? `
              <button class="btn btn-secondary w-100" disabled>Session Fully Booked</button>
            ` : `
              <button class="btn btn-primary w-100" onclick="openPublicBookingForSession('${session.id}')">
                Book Session Slot
              </button>
            `}
          </div>
        </div>
      `;
    }).join('');
  }

  if (doctorSelect) doctorSelect.addEventListener('change', renderSchedules);
  if (dateInput) dateInput.addEventListener('change', renderSchedules);
  if (resetBtn) {
    resetBtn.addEventListener('click', () => {
      if (doctorSelect) doctorSelect.value = 'ALL';
      if (dateInput) dateInput.value = '';
      renderSchedules();
    });
  }

  // Initial render
  renderSchedules();
}

/* --------------------------------------------------------------------------
   4. Patient Reports Finder by NIC (with Enter Button & Modal Viewer)
   -------------------------------------------------------------------------- */
function initPatientReportFinder() {
  const nicInput = document.getElementById('report-search-nic');
  const searchBtn = document.getElementById('btn-search-reports');
  const resultsArea = document.getElementById('reports-results-area');

  if (!nicInput || !searchBtn || !resultsArea) return;

  function performSearch() {
    const nic = nicInput.value.trim();
    if (!FormPopup.requireFields([{ label: 'National Identity Card (NIC) Number', value: nic }], 'The report search')) {
      return;
    }

    const reports = ClinicStore.getReportsByNic(nic);
    const patient = ClinicStore.getPatientByNic(nic);

    if (reports.length === 0) {
      FormPopup.notCompleted(
        `No medical or diagnostic reports are filed under NIC ${nic}.`,
        {
          title: 'Not Completed',
          note: 'If you visited the clinic recently your reports may still be under clinical verification. ' +
                'Please call our reception desk on +94 11 234 5678.'
        }
      );

      resultsArea.innerHTML = `
        <div style="text-align: center; padding: 2.5rem; background: #ffffff; border-radius: 12px; border: 1.5px dashed #fca5a5;">
          <div style="font-size: 2.2rem; color: #ef4444; margin-bottom: 0.5rem;">📋</div>
          <h4 style="color: #0b2545; margin-bottom: 0.5rem;">No Reports Found for NIC: ${nic}</h4>
          <p style="color: #64748b; font-size: 0.95rem; max-width: 480px; margin: 0 auto;">
            We could not find any uploaded medical or diagnostic reports matching this NIC number. If you recently visited the clinic, your reports might still be undergoing clinical verification. Please contact our reception desk at <strong>+94 11 234 5678</strong>.
          </p>
        </div>
      `;
      return;
    }

    resultsArea.innerHTML = `
      <div style="background: #f0fdf4; border: 1px solid #86efac; border-radius: 10px; padding: 1.25rem; margin-bottom: 1.5rem; display: flex; align-items: center; justify-content: space-between;">
        <div>
          <strong style="color: #166534; font-size: 1.05rem;">Verified Patient Records Found</strong>
          <div style="font-size: 0.85rem; color: #15803d;">Patient: <strong>${patient ? patient.fullName : reports[0].patientName}</strong> (NIC: ${nic})</div>
        </div>
        <span class="badge badge-success">${reports.length} Reports Available</span>
      </div>

      <div class="reports-list-container">
        ${reports.map(report => `
          <div class="report-item-card">
            <div>
              <div class="report-item-header">
                <div class="report-icon">📄</div>
                <div>
                  <h4 style="font-size: 1rem; color: #0b2545;">${report.reportType}</h4>
                  <span style="font-size: 0.75rem; color: #64748b;">Ref: ${report.reportNo} | Date: ${report.date}</span>
                </div>
              </div>

              <div style="font-size: 0.85rem; color: #475569; margin: 0.75rem 0; line-height: 1.5; background: #f8fafc; padding: 0.75rem; border-radius: 6px; border: 1px solid #e2e8f0;">
                <strong>Findings:</strong> ${report.findings.length > 130 ? report.findings.substring(0, 130) + '...' : report.findings}
              </div>

              <div style="font-size: 0.75rem; color: #0077b6; margin-bottom: 1rem;">
                Attending Specialist: <strong>${report.doctorName}</strong>
              </div>
            </div>

            <div style="display: flex; gap: 0.5rem;">
              <button class="btn btn-secondary btn-sm w-100" onclick="viewReportDetailsModal('${report.id}')">
                View Findings
              </button>
              <button class="btn btn-primary btn-sm w-100" onclick="downloadOrPrintReport('${report.id}')">
                Print / PDF
              </button>
            </div>
          </div>
        `).join('')}
      </div>
    `;

    FormPopup.completed('Your medical records were located and are listed below.', {
      title: 'Search Completed',
      details: [
        ['Patient', patient ? patient.fullName : reports[0].patientName],
        ['NIC', nic],
        ['Reports Available', String(reports.length)]
      ]
    });
  }

  searchBtn.addEventListener('click', performSearch);
  nicInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') {
      performSearch();
    }
  });
}

/* --------------------------------------------------------------------------
   5. Public Appointment Booking Modal & "Payment Pending" Slip Generation
   -------------------------------------------------------------------------- */
function initPublicAppointmentModal() {
  const modal = document.getElementById('modal-public-booking');
  const form = document.getElementById('form-public-booking');
  const sessionSelect = document.getElementById('book-session-select');

  if (!modal || !form) return;

  window.openPublicBookingModal = function(preselectedSessionId = '') {
    // Populate sessions
    const sessions = ClinicStore.getSessions().filter(s => s.status === 'ACTIVE');
    sessionSelect.innerHTML = '<option value="">-- Select an Available Doctor Session --</option>';
    sessions.forEach(s => {
      const avail = Math.max(0, s.maxPatients - (s.bookedCount || 0));
      if (avail > 0) {
        const opt = document.createElement('option');
        opt.value = s.id;
        opt.textContent = `${s.date} | ${s.doctorName} (${s.specialty}) | ${s.startTime}-${s.endTime} [${avail} slots left]`;
        if (s.id === preselectedSessionId) opt.selected = true;
        sessionSelect.appendChild(opt);
      }
    });

    modal.classList.add('active');
  };

  window.openPublicBookingForSession = function(sessionId) {
    window.openPublicBookingModal(sessionId);
  };

  window.closePublicBookingModal = function() {
    modal.classList.remove('active');
    form.reset();
  };

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const sessionId = sessionSelect.value;
    const nic = document.getElementById('book-nic').value.trim();
    const fullName = document.getElementById('book-name').value.trim();
    const phone = document.getElementById('book-phone').value.trim();
    const email = document.getElementById('book-email').value.trim();
    const reason = document.getElementById('book-reason').value.trim();

    const complete = FormPopup.requireFields([
      { label: 'Doctor Session', value: sessionId },
      { label: 'NIC Number', value: nic },
      { label: 'Full Name', value: fullName },
      { label: 'Phone Contact', value: phone }
    ], 'Your appointment booking');
    if (!complete) return;

    const session = ClinicStore.getSessions().find(s => s.id === sessionId);
    if (!session) {
      FormPopup.notCompleted('The doctor session you selected is no longer available. Please pick another session and book again.');
      return;
    }

    // Register or update patient
    ClinicStore.addOrUpdatePatient({
      nic,
      fullName,
      phone,
      email,
      medicalHistory: reason || 'Self-service online booking'
    });

    // Create appointment
    const newApt = ClinicStore.addAppointment({
      sessionId: session.id,
      doctorId: session.doctorId,
      patientNic: nic,
      patientName: fullName,
      patientPhone: phone,
      consultationFee: session.consultationFee
    });

    window.closePublicBookingModal();
    initDoctorSchedules(); // refresh UI slots

    // The token number is allocated by the database, so the popup only quotes
    // it once the booking has come back confirmed.
    FormPopup.forWrite({
      title: 'Booking Completed',
      pendingMessage: 'Reserving your slot with the clinic. Please wait.',
      message: 'Your appointment has been booked and your slot is reserved.',
      highlight: () => ({
        label: 'Your Appointment Token',
        value: '# ' + newApt.tokenNumber,
        note: 'Reference ID: ' + newApt.id
      }),
      details: () => [
        ['Patient Name', `${newApt.patientName} (${newApt.patientNic})`],
        ['Attending Doctor', newApt.doctorName],
        ['Date of Consultation', newApt.date],
        ['Consultation Fee', `Rs. ${Number(newApt.consultationFee).toLocaleString()}`]
      ],
      note: 'Payment Notice: online payment is not collected. Please present this token and settle the ' +
            'consultation fee at our cashier reception counter when you arrive.',
      failTitle: 'Booking Not Completed',
      actions: [{
        label: '🖨️ Print Appointment Slip',
        style: 'secondary',
        onClick: () => PrintUtil.printAppointmentSlip(
          ClinicStore.getAppointments().find(a => a.id === newApt.id) || newApt
        )
      }]
    });
  });
}

/* --------------------------------------------------------------------------
   6. Report Details Modal & Print Triggers
   -------------------------------------------------------------------------- */
window.viewReportDetailsModal = function(reportId) {
  const report = ClinicStore.getReports().find(r => r.id === reportId);
  if (!report) return;

  const modalHtml = `
    <div class="modal-backdrop active" id="modal-view-report">
      <div class="modal modal-lg">
        <div class="modal-header">
          <div class="modal-title">
            <span>📋</span> ${report.reportType} - Details
          </div>
          <button class="modal-close" onclick="document.getElementById('modal-view-report').remove()">&times;</button>
        </div>
        <div class="modal-body">
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; background: #f8fafc; border: 1px solid #e2e8f0; padding: 1.25rem; border-radius: 8px; margin-bottom: 1.5rem; font-size: 0.9rem;">
            <div>
              <div><strong>Patient:</strong> ${report.patientName}</div>
              <div><strong>NIC:</strong> ${report.patientNic}</div>
              <div><strong>Report Ref:</strong> ${report.reportNo}</div>
            </div>
            <div>
              <div><strong>Clinician:</strong> ${report.doctorName}</div>
              <div><strong>Date:</strong> ${report.date}</div>
              <div><strong>Status:</strong> <span class="badge badge-success">${report.status}</span></div>
            </div>
          </div>

          <div style="margin-bottom: 1.5rem;">
            <h4 style="color: #0b2545; font-size: 1rem; margin-bottom: 0.5rem;">Clinical Findings & Observations:</h4>
            <div style="background: #ffffff; border: 1px solid #cbd5e1; padding: 1.25rem; border-radius: 8px; line-height: 1.6; color: #1e293b;">
              ${report.findings}
            </div>
          </div>

          <div style="background: #f1f5f9; border-radius: 8px; padding: 1rem; font-size: 0.85rem; color: #64748b; margin-bottom: 1rem;">
            📁 Attached Document: <code>storage/reports/${report.fileName}</code>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" onclick="document.getElementById('modal-view-report').remove()">Close</button>
          <button class="btn btn-primary" onclick="PrintUtil.printClinicalReport(ClinicStore.getReports().find(r => r.id === '${report.id}'))">
            🖨️ Print / Save PDF
          </button>
        </div>
      </div>
    </div>
  `;

  document.body.insertAdjacentHTML('beforeend', modalHtml);
};

window.downloadOrPrintReport = function(reportId) {
  const report = ClinicStore.getReports().find(r => r.id === reportId);
  if (report) {
    PrintUtil.printClinicalReport(report);
  }
};

/* --------------------------------------------------------------------------
   7. FAQ Accordion
   -------------------------------------------------------------------------- */
function initFaqAccordion() {
  document.querySelectorAll('.faq-question').forEach(header => {
    header.addEventListener('click', () => {
      const item = header.parentElement;
      item.classList.toggle('active');
    });
  });
}

/* --------------------------------------------------------------------------
   8. Global Toast Notification Helper
   -------------------------------------------------------------------------- */
window.showToast = function(message, type = 'info', title = '') {
  let toastContainer = document.getElementById('toast-container');
  if (!toastContainer) {
    toastContainer = document.createElement('div');
    toastContainer.id = 'toast-container';
    toastContainer.className = 'toast-container';
    document.body.appendChild(toastContainer);
  }

  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;

  const icon = type === 'success' ? '✅' : (type === 'error' ? '❌' : (type === 'warning' ? '⚠️' : 'ℹ️'));
  const headerTitle = title || (type === 'success' ? 'Success' : (type === 'error' ? 'Error' : (type === 'warning' ? 'Warning' : 'Notice')));

  toast.innerHTML = `
    <div style="font-size: 1.3rem;">${icon}</div>
    <div class="toast-content">
      <div class="toast-title">${headerTitle}</div>
      <div class="toast-msg">${message}</div>
    </div>
  `;

  toastContainer.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(100%)';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
};
