/* ==========================================================================
   SunRise Dental Clinic - Form Result Popup
   Pure Vanilla JavaScript - Zero Frameworks

   Every form in the clinic system finishes the same way: a popup that says
   Completed or Not Completed. Toasts are still used for passing remarks
   ("added to cart"), but anything the user filled in and submitted gets an
   answer they have to acknowledge.

   Writes reach MySQL through ClinicStore, which posts a command and then
   re-reads the document. That round trip is what decides the wording, so a
   saving form shows a spinner first and only says Completed once the server
   has actually taken the change - see FormPopup.forWrite().

   Typical use:

     if (!FormPopup.requireFields([{ label: 'NIC Number', value: nic }])) return;

     ClinicStore.addOrUpdatePatient({ ... });

     FormPopup.forWrite({
       message: 'The patient profile was saved.',
       details: [['Patient', fullName], ['NIC', nic]]
     });
   ========================================================================== */

class FormPopup {

  static _el = null;          // the single backdrop element, reused every time
  static _seq = 0;            // guards against a slow write answering a newer popup
  static _lastFocus = null;
  static _onKeyDown = null;

  /* ---------------------------------------------------------------
     Rendering
     --------------------------------------------------------------- */

  /**
   * Draws (or redraws) the popup.
   *
   * options:
   *   status     'success' | 'error' | 'pending'
   *   title      heading; defaults to Completed / Not Completed / Please Wait
   *   message    one plain sentence about what happened
   *   highlight  { label, value, note } - big reference number block
   *   details    [ [label, value], ... ] - summary of what was saved
   *   missing    [ 'Field name', ... ]  - listed in red on a failure
   *   note       amber advisory strip
   *   actions    [ { label, onClick, style, keepOpen } ] - extra buttons
   *   closeLabel text of the dismiss button ('' hides it, for pending popups)
   */
  static show(options) {
    const opt = options || {};
    const status = opt.status || 'success';
    const seq = ++FormPopup._seq;

    const backdrop = FormPopup._element();
    backdrop.className = 'fp-backdrop fp-' + status;

    const title = opt.title || FormPopup._defaultTitle(status);
    const closeLabel = opt.closeLabel === undefined
      ? (status === 'pending' ? '' : (status === 'success' ? 'Done' : 'Close'))
      : opt.closeLabel;

    backdrop.innerHTML =
      '<div class="fp-card" role="alertdialog" aria-modal="true" aria-labelledby="fp-title">' +
        '<div class="fp-banner"></div>' +
        '<div class="fp-body">' +
          '<div class="fp-icon">' + FormPopup._icon(status) + '</div>' +
          '<h3 class="fp-title" id="fp-title">' + FormPopup._esc(title) + '</h3>' +
          '<p class="fp-message" aria-live="polite">' + FormPopup._esc(opt.message || '') + '</p>' +
          FormPopup._highlightHtml(opt.highlight) +
          FormPopup._missingHtml(opt.missing) +
          FormPopup._detailsHtml(opt.details) +
          (opt.note ? '<div class="fp-note">' + FormPopup._esc(opt.note) + '</div>' : '') +
        '</div>' +
        '<div class="fp-actions"></div>' +
      '</div>';

    // Buttons are built as real nodes so the handlers stay plain functions
    // instead of strings hung off the window object.
    const actionBar = backdrop.querySelector('.fp-actions');
    (opt.actions || []).forEach(function (action) {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'btn btn-' + (action.style || 'secondary');
      btn.textContent = action.label;
      btn.addEventListener('click', function () {
        if (!action.keepOpen) { FormPopup.close(); }
        if (typeof action.onClick === 'function') { action.onClick(); }
      });
      actionBar.appendChild(btn);
    });

    if (closeLabel) {
      const closeBtn = document.createElement('button');
      closeBtn.type = 'button';
      closeBtn.className = 'btn btn-' + (status === 'error' ? 'danger' : 'primary');
      closeBtn.textContent = closeLabel;
      closeBtn.addEventListener('click', FormPopup.close);
      actionBar.appendChild(closeBtn);
    }

    if (!backdrop.classList.contains('fp-open')) {
      FormPopup._lastFocus = document.activeElement;
      // One frame before adding the class, otherwise the transition is skipped.
      requestAnimationFrame(function () { backdrop.classList.add('fp-open'); });
    }

    const firstButton = actionBar.querySelector('button');
    if (firstButton) { setTimeout(function () { firstButton.focus(); }, 60); }

    return seq;
  }

  static close() {
    const backdrop = FormPopup._el;
    if (!backdrop) { return; }
    FormPopup._seq++;                       // any pending write no longer owns it
    backdrop.classList.remove('fp-open');
    setTimeout(function () { backdrop.innerHTML = ''; }, 220);

    if (FormPopup._lastFocus && typeof FormPopup._lastFocus.focus === 'function') {
      FormPopup._lastFocus.focus();
    }
    FormPopup._lastFocus = null;
  }

  /* ---------------------------------------------------------------
     The two answers every form gives
     --------------------------------------------------------------- */

  /** "Completed" - the form went through. */
  static completed(message, options) {
    const opt = Object.assign({}, options);
    opt.status = 'success';
    opt.message = message;
    return FormPopup.show(opt);
  }

  /** "Not Completed" - the form did not go through, and why. */
  static notCompleted(message, options) {
    const opt = Object.assign({}, options);
    opt.status = 'error';
    opt.message = message;
    return FormPopup.show(opt);
  }

  /* ---------------------------------------------------------------
     Validation
     --------------------------------------------------------------- */

  /**
   * Checks the mandatory fields of a form. Returns true when they are all
   * filled; otherwise pops "Not Completed" listing what is missing and
   * returns false, so a handler can just do:  if (!requireFields(...)) return;
   *
   * fields: [ { label, value }, ... ]
   */
  static requireFields(fields, formName) {
    const missing = (fields || [])
      .filter(function (f) {
        return f.value === null || f.value === undefined || String(f.value).trim() === '';
      })
      .map(function (f) { return f.label; });

    if (missing.length === 0) { return true; }

    FormPopup.notCompleted(
      (formName ? formName + ' was not submitted. ' : '') +
      'Please fill in the ' + (missing.length === 1 ? 'field' : 'fields') + ' below and try again.',
      { title: 'Not Completed', missing: missing }
    );
    return false;
  }

  /* ---------------------------------------------------------------
     Waiting for the server
     --------------------------------------------------------------- */

  /**
   * Call straight after a ClinicStore write. Shows a spinner while the
   * command is in flight, then Completed if the server took it, or Not
   * Completed with the server's own reason if it refused.
   *
   * Everything ClinicStore is still sending at this moment is covered, which
   * matters for the handlers that write twice (a report plus the appointment
   * status, for instance).
   */
  static forWrite(options) {
    const opt = options || {};
    const seq = FormPopup.show({
      status: 'pending',
      title: opt.pendingTitle || 'Saving...',
      message: opt.pendingMessage || 'Sending your details to the clinic server. Please wait.'
    });

    const settle = (typeof ClinicStore !== 'undefined' && ClinicStore.settleWrites)
      ? ClinicStore.settleWrites()
      : Promise.resolve({ ok: true, messages: [] });

    return settle.then(function (outcome) {
      // A newer popup (or a close) has taken over - do not talk over it.
      if (seq !== FormPopup._seq) { return outcome; }

      if (outcome.ok) {
        // Reference numbers (tokens, receipts, invoice numbers) are allocated
        // by the database, so anything that quotes one is passed as a function
        // and read here, once the refresh has brought the real value back.
        FormPopup.completed(FormPopup._resolve(opt.message) || 'Your details were saved successfully.', {
          title: opt.title,
          highlight: FormPopup._resolve(opt.highlight),
          details: FormPopup._resolve(opt.details),
          note: FormPopup._resolve(opt.note),
          actions: FormPopup._resolve(opt.actions),
          closeLabel: opt.closeLabel
        });
      } else {
        FormPopup.notCompleted(
          outcome.messages[0] || 'The clinic server rejected this submission. Nothing was saved.',
          {
            title: opt.failTitle,
            details: FormPopup._resolve(opt.details),
            note: 'Nothing was saved. Check the details above and submit the form again.',
            actions: opt.failActions
          }
        );
      }
      return outcome;
    });
  }

  /* ---------------------------------------------------------------
     Internals
     --------------------------------------------------------------- */

  static _element() {
    if (FormPopup._el && document.body.contains(FormPopup._el)) { return FormPopup._el; }

    const backdrop = document.createElement('div');
    backdrop.className = 'fp-backdrop';
    backdrop.id = 'form-result-popup';

    // Clicking the dimmed area dismisses, but only once there is something to
    // dismiss - a pending popup has no buttons and must not be clicked away.
    backdrop.addEventListener('click', function (e) {
      if (e.target === backdrop && backdrop.querySelector('.fp-actions button')) {
        FormPopup.close();
      }
    });

    if (!FormPopup._onKeyDown) {
      FormPopup._onKeyDown = function (e) {
        if (e.key !== 'Escape' || !FormPopup._el) { return; }
        if (FormPopup._el.querySelector('.fp-actions button')) { FormPopup.close(); }
      };
      document.addEventListener('keydown', FormPopup._onKeyDown);
    }

    document.body.appendChild(backdrop);
    FormPopup._el = backdrop;
    return backdrop;
  }

  /** Lets forWrite() options be written as functions and read after the save. */
  static _resolve(value) {
    return typeof value === 'function' ? value() : value;
  }

  static _defaultTitle(status) {
    if (status === 'success') { return 'Completed'; }
    if (status === 'error') { return 'Not Completed'; }
    return 'Please Wait';
  }

  static _icon(status) {
    if (status === 'success') { return '&#10003;'; }        // check mark
    if (status === 'error') { return '&#10007;'; }          // cross mark
    return '<div class="fp-spinner"></div>';
  }

  static _highlightHtml(highlight) {
    if (!highlight || !highlight.value) { return ''; }
    return '<div class="fp-highlight">' +
      (highlight.label ? '<div class="fp-highlight-label">' + FormPopup._esc(highlight.label) + '</div>' : '') +
      '<div class="fp-highlight-value">' + FormPopup._esc(highlight.value) + '</div>' +
      (highlight.note ? '<div class="fp-highlight-note">' + FormPopup._esc(highlight.note) + '</div>' : '') +
      '</div>';
  }

  static _detailsHtml(details) {
    const rows = (details || []).filter(function (row) {
      return row && row[1] !== null && row[1] !== undefined && String(row[1]).trim() !== '';
    });
    if (rows.length === 0) { return ''; }

    return '<div class="fp-details">' + rows.map(function (row) {
      return '<div class="fp-detail-row">' +
        '<span class="fp-detail-label">' + FormPopup._esc(row[0]) + '</span>' +
        '<span class="fp-detail-value">' + FormPopup._esc(row[1]) + '</span>' +
        '</div>';
    }).join('') + '</div>';
  }

  static _missingHtml(missing) {
    if (!missing || missing.length === 0) { return ''; }
    return '<ul class="fp-missing">' + missing.map(function (label) {
      return '<li>' + FormPopup._esc(label) + '</li>';
    }).join('') + '</ul>';
  }

  /** Server messages and typed-in values both land in here, so escape them. */
  static _esc(value) {
    return String(value === null || value === undefined ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
}

window.FormPopup = FormPopup;
