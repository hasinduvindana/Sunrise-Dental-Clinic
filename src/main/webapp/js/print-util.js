/* ==========================================================================
   SunRise Dental Clinic - Print & Document Generator Utility
   Pure Vanilla JavaScript - Zero Frameworks
   Outputs standardized documents with naming convention:
   YYYY-MM-DD_{NIC}_{TreatmentType_or_ReceiptType}.pdf
   ========================================================================== */

class PrintUtil {
  /**
   * Generates standardized filename according to user specification:
   * YYYY-MM-DD_{NIC}_{treatmentType_or_receiptType}.pdf
   */
  static generateFileName(dateStr, nic, docType) {
    const d = dateStr || new Date().toISOString().split('T')[0];
    const cleanNic = (nic || 'GUEST').replace(/[^a-zA-Z0-9]/g, '');
    const cleanType = (docType || 'Document').replace(/[^a-zA-Z0-9]/g, '_');
    return `${d}_${cleanNic}_${cleanType}.pdf`;
  }

  /**
   * Print 80mm Thermal Appointment Booking Slip
   */
  static printAppointmentSlip(p) {
    const settings = ClinicStore.getSettings();
    const fileName = PrintUtil.generateFileName(p.date, p.patientNic, 'AppointmentSlip');
    const statusColor = p.status === 'PAID' ? '#10b981' : '#f59e0b';
    const paymentNotice = p.status !== 'PAID' 
      ? '<p style="font-weight: bold; font-size: 11px; color: #b45309;">** PLEASE PAY AT CASHIER DESK UPON ARRIVAL **</p>'
      : '<p style="font-weight: bold; font-size: 11px; color: #15803d;">** PAID & CONFIRMED **</p>';

    const html = `
      <div class="thermal-slip">
        <div class="thermal-header">
          <img src="${settings.logoUrl}" class="thermal-logo" alt="Logo" />
          <div class="thermal-title">${settings.clinicName}</div>
          <div class="thermal-sub">${settings.tagline}</div>
          <div class="thermal-sub">${settings.address}</div>
          <div class="thermal-sub">Tel: ${settings.phone}</div>
        </div>

        <div class="thermal-token-box">
          <div>APPOINTMENT TOKEN</div>
          <div style="font-size: 26px; margin: 4px 0;"># ${p.tokenNumber}</div>
          <div style="font-size: 11px; font-weight: normal;">Ref: ${p.id || p.appointmentId}</div>
        </div>

        <div class="thermal-section">
          <div class="thermal-row"><span>Patient NIC:</span> <strong>${p.patientNic}</strong></div>
          <div class="thermal-row"><span>Patient Name:</span> <strong>${p.patientName}</strong></div>
          <div class="thermal-row"><span>Contact:</span> <span>${p.patientPhone || 'N/A'}</span></div>
        </div>

        <div class="thermal-section">
          <div class="thermal-row"><span>Doctor:</span> <strong>${p.doctorName}</strong></div>
          <div class="thermal-row"><span>Date:</span> <strong>${p.date}</strong></div>
          <div class="thermal-row"><span>Time Slot:</span> <span>${p.timeSlot || 'Session Slot'}</span></div>
        </div>

        <div class="thermal-section">
          <div class="thermal-row"><span>Consultation Fee:</span> <strong>${settings.currencySymbol} ${Number(p.consultationFee).toLocaleString()}</strong></div>
          <div class="thermal-row"><span>Payment Status:</span> <strong style="color: ${statusColor};">${p.status}</strong></div>
        </div>

        <div class="thermal-footer">
          ${paymentNotice}
          <p style="margin-top: 4px;">${settings.footerNote}</p>
          <p style="font-size: 9px; color: #666; margin-top: 4px;">Printed on: ${new Date().toLocaleString()} | Saved: storage/receipts/${fileName}</p>
        </div>
      </div>
    `;

    PrintUtil.executePrint(html, fileName);
  }

  /**
   * Print 80mm Official Payment Receipt (Cash / Card)
   */
  static printPaymentReceipt(payment) {
    const settings = ClinicStore.getSettings();
    const docType = payment.invoiceNo ? 'TreatmentPaymentReceipt' : 'ConsultationReceipt';
    const datePart = payment.timestamp ? payment.timestamp.split(' ')[0] : new Date().toISOString().split('T')[0];
    const fileName = PrintUtil.generateFileName(datePart, payment.patientNic, docType);

    const cardBlock = payment.paymentType === 'CARD' ? `
      <div class="thermal-row"><span>Card Type:</span> <span>${payment.cardType} (${payment.cardProvider || 'CARD'})</span></div>
      <div class="thermal-row"><span>Card Number:</span> <span>${payment.cardNumberMasked || 'XXXX-XXXX'}</span></div>
      <div class="thermal-row"><span>Bank:</span> <span>${payment.bankName || 'Issuing Bank'}</span></div>
    ` : '';

    const html = `
      <div class="thermal-slip">
        <div class="thermal-header">
          <img src="${settings.logoUrl}" class="thermal-logo" alt="Logo" />
          <div class="thermal-title">${settings.clinicName}</div>
          <div class="thermal-sub">${settings.address} | Tel: ${settings.phone}</div>
          <div class="thermal-sub" style="margin-top: 4px; font-weight: bold; font-size: 13px;">OFFICIAL PAYMENT RECEIPT</div>
        </div>

        <div class="thermal-section">
          <div class="thermal-row"><span>Receipt No:</span> <strong>${payment.receiptNo}</strong></div>
          <div class="thermal-row"><span>Date & Time:</span> <span>${payment.timestamp || new Date().toLocaleString()}</span></div>
          <div class="thermal-row"><span>Cashier:</span> <span>${payment.cashierName}</span></div>
        </div>

        <div class="thermal-section">
          <div class="thermal-row"><span>Patient NIC:</span> <strong>${payment.patientNic}</strong></div>
          <div class="thermal-row"><span>Patient Name:</span> <strong>${payment.patientName}</strong></div>
          <div class="thermal-row"><span>Doctor:</span> <span>${payment.doctorName || 'N/A'}</span></div>
          ${payment.invoiceNo ? `<div class="thermal-row"><span>Invoice Ref:</span> <span>${payment.invoiceNo}</span></div>` : ''}
          ${payment.appointmentId ? `<div class="thermal-row"><span>Appointment Ref:</span> <span>${payment.appointmentId}</span></div>` : ''}
        </div>

        <div class="thermal-section">
          <div class="thermal-row"><span>Payment Mode:</span> <strong>${payment.paymentType}</strong></div>
          ${cardBlock}
        </div>

        <div class="thermal-section">
          <div class="thermal-row thermal-total-row">
            <span>NET AMOUNT PAID</span>
            <span>${settings.currencySymbol} ${Number(payment.amountPaid).toLocaleString()}</span>
          </div>
        </div>

        <div class="thermal-footer">
          <p style="font-weight: bold; margin-bottom: 4px;">THANK YOU FOR YOUR PAYMENT!</p>
          <p>${settings.footerNote}</p>
          <p style="font-size: 9px; color: #666; margin-top: 4px;">Saved File: storage/receipts/${fileName}</p>
        </div>
      </div>
    `;

    PrintUtil.executePrint(html, fileName);
  }

  /**
   * Print A4 Dental Treatment Invoice
   */
  static printTreatmentInvoice(invoice) {
    const settings = ClinicStore.getSettings();
    const treatmentName = (invoice.treatmentType || 'Dental_Treatment').replace(/[^a-zA-Z0-9]/g, '_');
    const datePart = invoice.createdAt ? invoice.createdAt.split(' ')[0] : new Date().toISOString().split('T')[0];
    const fileName = PrintUtil.generateFileName(datePart, invoice.patientNic, `${treatmentName}_Invoice`);

    let itemsRows = '';
    if (invoice.items && invoice.items.length > 0) {
      invoice.items.forEach((item, idx) => {
        itemsRows += `
          <tr>
            <td style="width: 40px; text-align: center;">${idx + 1}</td>
            <td><strong>${item.description}</strong></td>
            <td style="text-align: right;">${settings.currencySymbol} ${Number(item.amount).toLocaleString()}</td>
          </tr>
        `;
      });
    }

    const html = `
      <div class="a4-document">
        <div class="a4-header">
          <div class="a4-logo-info">
            <img src="${settings.logoUrl}" alt="Logo" />
            <div>
              <div class="a4-clinic-name">${settings.clinicName}</div>
              <div style="font-size: 13px; color: #0077b6; font-weight: 600;">${settings.tagline}</div>
              <div style="font-size: 12px; color: #64748b;">${settings.address} | Tel: ${settings.phone}</div>
              <div style="font-size: 11px; color: #64748b;">Reg No: ${settings.regNo} | Email: ${settings.email}</div>
            </div>
          </div>
          <div class="a4-doc-meta">
            <div class="a4-doc-title">DENTAL INVOICE</div>
            <div style="font-size: 14px; font-weight: bold; color: #0f172a;">${invoice.invoiceNo}</div>
            <div style="font-size: 12px; color: #64748b;">Date: ${invoice.createdAt}</div>
            <div style="margin-top: 5px;">
              <span class="badge ${invoice.status === 'PAID' ? 'badge-success' : 'badge-warning'}">${invoice.status}</span>
            </div>
          </div>
        </div>

        <div class="a4-grid-2">
          <div>
            <div style="font-size: 11px; font-weight: bold; color: #0077b6; text-transform: uppercase; margin-bottom: 5px;">PATIENT DETAILS</div>
            <div style="font-size: 14px; font-weight: bold;">${invoice.patientName}</div>
            <div style="font-size: 12px; color: #475569;">NIC Number: <strong>${invoice.patientNic}</strong></div>
            <div style="font-size: 12px; color: #475569;">Appointment Ref: ${invoice.appointmentId || 'N/A'}</div>
          </div>
          <div>
            <div style="font-size: 11px; font-weight: bold; color: #0077b6; text-transform: uppercase; margin-bottom: 5px;">ATTENDING DENTIST</div>
            <div style="font-size: 14px; font-weight: bold;">${invoice.doctorName}</div>
            <div style="font-size: 12px; color: #475569;">Primary Treatment: <strong>${invoice.treatmentType || 'Clinical Procedure'}</strong></div>
            ${invoice.paymentRef ? `<div style="font-size: 12px; color: #10b981;">Paid via Receipt: <strong>${invoice.paymentRef}</strong></div>` : ''}
          </div>
        </div>

        <table class="a4-table">
          <thead>
            <tr>
              <th style="width: 40px; text-align: center;">#</th>
              <th>Description of Treatment / Clinical Service</th>
              <th style="text-align: right; width: 140px;">Amount (${settings.currencySymbol})</th>
            </tr>
          </thead>
          <tbody>
            ${itemsRows}
          </tbody>
        </table>

        <div class="a4-summary-box">
          <div class="a4-summary-row">
            <span>Subtotal:</span>
            <span>${settings.currencySymbol} ${Number(invoice.subtotal).toLocaleString()}</span>
          </div>
          ${invoice.discount > 0 ? `
            <div class="a4-summary-row" style="color: #10b981;">
              <span>Discount:</span>
              <span>- ${settings.currencySymbol} ${Number(invoice.discount).toLocaleString()}</span>
            </div>
          ` : ''}
          <div class="a4-summary-row total">
            <span>Total Payable:</span>
            <span>${settings.currencySymbol} ${Number(invoice.totalAmount).toLocaleString()}</span>
          </div>
        </div>

        <div class="a4-footer">
          <div>
            <p><strong>Payment Terms:</strong> Settlement upon treatment completion.</p>
            <p>${settings.footerNote}</p>
            <p style="font-size: 10px; color: #94a3b8; margin-top: 4px;">Saved File: storage/invoices/${fileName}</p>
          </div>
          <div class="a4-signature-box">
            Authorized Medical Officer / Cashier
          </div>
        </div>
      </div>
    `;

    PrintUtil.executePrint(html, fileName);
  }

  /**
   * Print A4 Diagnostic Radiology & Medical Report
   */
  static printClinicalReport(report) {
    const settings = ClinicStore.getSettings();
    const safeType = (report.reportType || 'Report').replace(/[^a-zA-Z0-9]/g, '_');
    const fileName = PrintUtil.generateFileName(report.date, report.patientNic, safeType);

    const html = `
      <div class="a4-document">
        <div class="a4-header">
          <div class="a4-logo-info">
            <img src="${settings.logoUrl}" alt="Logo" />
            <div>
              <div class="a4-clinic-name">${settings.clinicName}</div>
              <div style="font-size: 13px; color: #0077b6; font-weight: 600;">Department of Dental Radiology & Diagnostics</div>
              <div style="font-size: 12px; color: #64748b;">${settings.address} | Tel: ${settings.phone}</div>
            </div>
          </div>
          <div class="a4-doc-meta">
            <div class="a4-doc-title">DIAGNOSTIC REPORT</div>
            <div style="font-size: 14px; font-weight: bold; color: #0f172a;">${report.reportNo}</div>
            <div style="font-size: 12px; color: #64748b;">Date: ${report.date}</div>
            <div style="margin-top: 5px;"><span class="badge badge-success">${report.status}</span></div>
          </div>
        </div>

        <div class="a4-grid-2">
          <div>
            <div style="font-size: 11px; font-weight: bold; color: #0077b6; text-transform: uppercase; margin-bottom: 5px;">PATIENT INFORMATION</div>
            <div style="font-size: 14px; font-weight: bold;">${report.patientName}</div>
            <div style="font-size: 12px; color: #475569;">NIC Number: <strong>${report.patientNic}</strong></div>
          </div>
          <div>
            <div style="font-size: 11px; font-weight: bold; color: #0077b6; text-transform: uppercase; margin-bottom: 5px;">ATTENDING CLINICIAN</div>
            <div style="font-size: 14px; font-weight: bold;">${report.doctorName}</div>
            <div style="font-size: 12px; color: #475569;">Investigation Type: <strong>${report.reportType}</strong></div>
          </div>
        </div>

        <div style="background: #ffffff; border: 1px solid #e2e8f0; border-radius: 6px; padding: 20px; margin-bottom: 30px;">
          <h4 style="color: #0b2545; margin-bottom: 12px; border-bottom: 1px solid #e2e8f0; padding-bottom: 8px;">CLINICAL OBSERVATIONS & FINDINGS</h4>
          <p style="font-size: 14px; line-height: 1.7; color: #1e293b; white-space: pre-line;">${report.findings}</p>
        </div>

        <div style="background: #f1f5f9; border-radius: 6px; padding: 15px; margin-bottom: 30px; font-size: 12px; color: #475569;">
          <strong>Digital File Archive:</strong> Stored locally as <code>storage/reports/${fileName}</code>
        </div>

        <div class="a4-footer">
          <div>
            <p>Authenticated by SunRise Dental Digital Health Information System.</p>
            <p style="font-size: 10px; color: #94a3b8; margin-top: 4px;">Document Reference: ${fileName}</p>
          </div>
          <div class="a4-signature-box">
            Consultant Dental Radiologist / Attending Doctor
          </div>
        </div>
      </div>
    `;

    PrintUtil.executePrint(html, fileName);
  }

  /**
   * Print Certified Monthly Income Audit PDF
   */
  static printMonthlyIncomeReport(reportData) {
    const settings = ClinicStore.getSettings();
    const fileName = `income-report-${reportData.monthYear}.pdf`;

    let docRows = '';
    if (reportData.doctorBreakdown && reportData.doctorBreakdown.length > 0) {
      reportData.doctorBreakdown.forEach((doc, idx) => {
        docRows += `
          <tr>
            <td style="text-align: center;">${idx + 1}</td>
            <td><strong>${doc.doctorName}</strong></td>
            <td>${doc.specialty}</td>
            <td style="text-align: center;">${doc.appointmentsCount}</td>
            <td style="text-align: center;">${doc.treatmentsCount}</td>
            <td style="text-align: right; font-weight: bold;">${settings.currencySymbol} ${Number(doc.totalRevenue).toLocaleString()}</td>
          </tr>
        `;
      });
    }

    const html = `
      <div class="a4-document">
        <div class="a4-header">
          <div class="a4-logo-info">
            <img src="${settings.logoUrl}" alt="Logo" />
            <div>
              <div class="a4-clinic-name">${settings.clinicName}</div>
              <div style="font-size: 13px; color: #0077b6; font-weight: 600;">Financial & Operational Revenue Audit</div>
              <div style="font-size: 12px; color: #64748b;">${settings.address}</div>
            </div>
          </div>
          <div class="a4-doc-meta">
            <div class="a4-doc-title">INCOME REPORT</div>
            <div style="font-size: 14px; font-weight: bold; color: #0f172a;">Period: ${reportData.monthYear}</div>
            <div style="font-size: 12px; color: #64748b;">Generated: ${new Date().toLocaleString()}</div>
          </div>
        </div>

        <div class="a4-grid-2">
          <div>
            <div style="font-size: 11px; font-weight: bold; color: #0077b6; text-transform: uppercase; margin-bottom: 5px;">OVERALL REVENUE SUMMARY</div>
            <div style="font-size: 20px; font-weight: 800; color: #10b981;">${settings.currencySymbol} ${Number(reportData.totalRevenue).toLocaleString()}</div>
            <div style="font-size: 12px; color: #475569;">Total Patients Served: <strong>${reportData.totalPatients}</strong></div>
          </div>
          <div>
            <div style="font-size: 11px; font-weight: bold; color: #0077b6; text-transform: uppercase; margin-bottom: 5px;">SETTLEMENT BREAKDOWN</div>
            <div style="font-size: 12px; color: #475569;">Cash Payments: <strong>${settings.currencySymbol} ${Number(reportData.cashRevenue).toLocaleString()}</strong></div>
            <div style="font-size: 12px; color: #475569;">Card Payments: <strong>${settings.currencySymbol} ${Number(reportData.cardRevenue).toLocaleString()}</strong></div>
          </div>
        </div>

        <h4 style="color: #0b2545; margin-bottom: 12px;">DOCTOR-WISE MONTHLY REVENUE BREAKDOWN</h4>
        <table class="a4-table">
          <thead>
            <tr>
              <th style="width: 40px; text-align: center;">#</th>
              <th>Doctor Name</th>
              <th>Specialty</th>
              <th style="text-align: center;">Consultations</th>
              <th style="text-align: center;">Treatments</th>
              <th style="text-align: right;">Revenue (${settings.currencySymbol})</th>
            </tr>
          </thead>
          <tbody>
            ${docRows}
          </tbody>
        </table>

        <div class="a4-footer">
          <div>
            <p>Certified accurate according to SunRise Dental POS Database Records.</p>
            <p style="font-size: 10px; color: #94a3b8; margin-top: 4px;">Saved File: storage/reports/${fileName}</p>
          </div>
          <div class="a4-signature-box">
            Chief Financial Officer / Super Admin
          </div>
        </div>
      </div>
    `;

    PrintUtil.executePrint(html, fileName);
  }

  /**
   * Internal print executor
   */
  static executePrint(htmlContent, documentTitle) {
    let printContainer = document.getElementById('printable-area');
    if (!printContainer) {
      printContainer = document.createElement('div');
      printContainer.id = 'printable-area';
      document.body.appendChild(printContainer);
    }
    printContainer.innerHTML = htmlContent;

    const originalTitle = document.title;
    if (documentTitle) {
      document.title = documentTitle;
    }

    setTimeout(() => {
      window.print();
      document.title = originalTitle;
    }, 250);
  }
}

window.PrintUtil = PrintUtil;