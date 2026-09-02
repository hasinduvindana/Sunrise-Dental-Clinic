/*
 * Sunrise Dental Clinic - API client
 *
 * One place that knows how to talk to the servlets. Every page uses this, so
 * error handling, session expiry and JSON shaping are written once.
 * No framework, no build step: a plain ES module attached to window.
 */
(function () {
  'use strict';

  var BASE = 'api';

  function url(path, params) {
    var full = BASE + (path.charAt(0) === '/' ? path : '/' + path);
    if (params) {
      var query = Object.keys(params)
        .filter(function (k) { return params[k] !== null && params[k] !== undefined && params[k] !== ''; })
        .map(function (k) { return encodeURIComponent(k) + '=' + encodeURIComponent(params[k]); })
        .join('&');
      if (query) { full += '?' + query; }
    }
    return full;
  }

  async function request(method, path, options) {
    var opts = options || {};
    var init = {
      method: method,
      credentials: 'same-origin',
      headers: {}
    };

    if (opts.body !== undefined && !(opts.body instanceof FormData)) {
      init.headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(opts.body);
    } else if (opts.body instanceof FormData) {
      init.body = opts.body;
    }

    var response;
    try {
      response = await fetch(url(path, opts.params), init);
    } catch (networkError) {
      throw new ApiError('The server could not be reached. Check that Tomcat is running.', 0);
    }

    if (response.status === 401) {
      Session.clear();
      if (!/index\.html$|\/$/.test(window.location.pathname)) {
        window.location.href = 'index.html?expired=1';
      }
      throw new ApiError('Your session has ended. Please sign in again.', 401);
    }

    var payload = null;
    var contentType = response.headers.get('content-type') || '';
    if (contentType.indexOf('application/json') >= 0) {
      payload = await response.json();
    }

    if (!response.ok) {
      var message = (payload && payload.message) || 'The request failed (' + response.status + ')';
      throw new ApiError(message, response.status);
    }

    return payload && Object.prototype.hasOwnProperty.call(payload, 'data') ? payload.data : payload;
  }

  function ApiError(message, status) {
    this.name = 'ApiError';
    this.message = message;
    this.status = status;
  }
  ApiError.prototype = Object.create(Error.prototype);

  /* Keeps the signed-in profile for the current tab. The real session lives in
     the JSESSIONID cookie - this is only for showing the name and hiding menu
     items, never for deciding access. The server checks every request. */
  var Session = {
    key: 'sunrise.user',
    save: function (user) { sessionStorage.setItem(this.key, JSON.stringify(user)); },
    get: function () {
      try { return JSON.parse(sessionStorage.getItem(this.key)); } catch (e) { return null; }
    },
    clear: function () { sessionStorage.removeItem(this.key); },
    role: function () { var u = this.get(); return u ? u.role : null; },
    /* Redirects to the sign-in page unless the current role is allowed here. */
    guard: async function (allowedRoles) {
      var user;
      try {
        user = await Api.auth.me();
        this.save(user);
      } catch (e) {
        window.location.href = 'index.html';
        return null;
      }
      if (allowedRoles && allowedRoles.indexOf(user.role) === -1) {
        window.location.href = user.home;
        return null;
      }
      return user;
    }
  };

  var Api = {
    error: ApiError,
    session: Session,

    auth: {
      login: function (username, password) {
        return request('POST', '/auth/login', { body: { username: username, password: password } })
          .then(function (user) { Session.save(user); return user; });
      },
      logout: function () {
        return request('POST', '/auth/logout').then(function (r) { Session.clear(); return r; });
      },
      me: function () { return request('GET', '/auth/me'); },
      changePassword: function (currentPassword, newPassword) {
        return request('POST', '/auth/change-password',
          { body: { currentPassword: currentPassword, newPassword: newPassword } });
      }
    },

    users: {
      list: function (params) { return request('GET', '/users', { params: params }); },
      doctors: function () { return request('GET', '/users/doctors'); },
      get: function (id) { return request('GET', '/users/' + id); },
      create: function (body) { return request('POST', '/users', { body: body }); },
      update: function (id, body) { return request('PUT', '/users/' + id, { body: body }); },
      setStatus: function (id, status) {
        return request('PATCH', '/users/' + id + '/status', { body: { status: status } });
      },
      resetPassword: function (id, newPassword) {
        return request('POST', '/users/' + id + '/reset-password', { body: { newPassword: newPassword } });
      },
      remove: function (id) { return request('DELETE', '/users/' + id); }
    },

    patients: {
      list: function (search) { return request('GET', '/patients', { params: { search: search } }); },
      get: function (id) { return request('GET', '/patients/' + id); },
      me: function () { return request('GET', '/patients/me'); },
      history: function (id) { return request('GET', '/patients/' + id + '/history'); },
      register: function (body) { return request('POST', '/patients', { body: body }); },
      update: function (id, body) { return request('PUT', '/patients/' + id, { body: body }); },
      createLogin: function (id, body) { return request('POST', '/patients/' + id + '/login', { body: body }); },
      remove: function (id) { return request('DELETE', '/patients/' + id); }
    },

    treatments: {
      list: function (all) { return request('GET', '/treatments', { params: { all: all ? 'true' : '' } }); },
      create: function (body) { return request('POST', '/treatments', { body: body }); },
      update: function (id, body) { return request('PUT', '/treatments/' + id, { body: body }); },
      retire: function (id) { return request('DELETE', '/treatments/' + id); }
    },

    sessions: {
      list: function (params) { return request('GET', '/sessions', { params: params }); },
      mine: function (date) { return request('GET', '/sessions/mine', { params: { date: date } }); },
      bookable: function () { return request('GET', '/sessions/bookable'); },
      get: function (id) { return request('GET', '/sessions/' + id); },
      appointments: function (id) { return request('GET', '/sessions/' + id + '/appointments'); },
      queue: function (id) { return request('GET', '/sessions/' + id + '/queue'); },
      create: function (body) { return request('POST', '/sessions', { body: body }); },
      update: function (id, body) { return request('PUT', '/sessions/' + id, { body: body }); },
      setStatus: function (id, status) {
        return request('PATCH', '/sessions/' + id + '/status', { body: { status: status } });
      },
      callNext: function (id) { return request('POST', '/sessions/' + id + '/call-next'); },
      remove: function (id) { return request('DELETE', '/sessions/' + id); }
    },

    appointments: {
      forDate: function (date) { return request('GET', '/appointments', { params: { date: date } }); },
      mine: function () { return request('GET', '/appointments/mine'); },
      search: function (appointmentNo) { return request('GET', '/appointments/search', { params: { no: appointmentNo } }); },
      get: function (id) { return request('GET', '/appointments/' + id); },
      book: function (body) { return request('POST', '/appointments', { body: body }); },
      checkIn: function (id) { return request('POST', '/appointments/' + id + '/check-in'); },
      setStatus: function (id, status) {
        return request('PATCH', '/appointments/' + id + '/status', { body: { status: status } });
      },
      update: function (id, body) { return request('PUT', '/appointments/' + id, { body: body }); }
    },

    bills: {
      list: function (params) { return request('GET', '/bills', { params: params }); },
      mine: function () { return request('GET', '/bills/mine'); },
      get: function (id) { return request('GET', '/bills/' + id); },
      forAppointment: function (appointmentId) { return request('GET', '/bills/appointment/' + appointmentId); },
      receipt: function (id) { return request('GET', '/bills/' + id + '/receipt'); },
      payments: function (id) { return request('GET', '/bills/' + id + '/payments'); },
      generate: function (body) { return request('POST', '/bills', { body: body }); },
      pay: function (body) { return request('POST', '/bills/pay', { body: body }); },
      cancel: function (id) { return request('DELETE', '/bills/' + id); }
    },

    prescriptions: {
      forPatient: function (patientId) { return request('GET', '/prescriptions', { params: { patientId: patientId } }); },
      mine: function () { return request('GET', '/prescriptions/mine'); },
      forAppointment: function (id) { return request('GET', '/prescriptions/appointment/' + id); },
      create: function (body) { return request('POST', '/prescriptions', { body: body }); }
    },

    medicalReports: {
      forPatient: function (patientId) {
        return request('GET', '/medical-reports', { params: { patientId: patientId } });
      },
      mine: function () { return request('GET', '/medical-reports/mine'); },
      upload: function (formData) { return request('POST', '/medical-reports', { body: formData }); },
      remove: function (id) { return request('DELETE', '/medical-reports/' + id); }
    },

    reports: {
      dashboard: function () { return request('GET', '/reports/dashboard'); },
      income: function (from, to) { return request('GET', '/reports/income', { params: { from: from, to: to } }); },
      incomeByDoctor: function (from, to) {
        return request('GET', '/reports/income-by-doctor', { params: { from: from, to: to } });
      },
      doctorIncome: function (doctorId, from, to) {
        return request('GET', '/reports/doctor-income', { params: { doctorId: doctorId, from: from, to: to } });
      },
      patients: function (from, to) { return request('GET', '/reports/patients', { params: { from: from, to: to } }); },
      treatments: function (from, to) { return request('GET', '/reports/treatments', { params: { from: from, to: to } }); }
    },

    settings: {
      all: function () { return request('GET', '/settings'); },
      branding: function () { return request('GET', '/settings/public'); },
      update: function (body) { return request('PUT', '/settings', { body: body }); }
    },

    /* The POS screens: one document in, named commands out. */
    pos: {
      state: function () { return request('GET', '/pos/state'); },
      command: function (name, body) { return request('POST', '/pos/' + name, { body: body }); },
      publicState: function () { return request('GET', '/pos/public'); },
      publicCommand: function (name, body) {
        return request('POST', '/pos/public/' + name, { body: body });
      }
    },

    health: function () { return request('GET', '/health'); }
  };

  window.Api = Api;
  window.Session = Session;
})();
