/* ==========================================================================
   SunRise Dental Clinic - AuthService (server backed)

   The original version compared plaintext passwords in the browser and kept
   the "session" in localStorage, which meant anyone could grant themselves
   any role from the console. Sign-in now happens on the server: the password
   is checked against a salted hash, and the session lives in the JSESSIONID
   cookie. What is kept here is only a copy of the profile for showing the
   user's name and hiding menu items.
   ========================================================================== */

class AuthService {
  static SESSION_KEY = 'SUNRISE_AUTH_PROFILE';

  /** The profile cached for this tab. Null when nobody is signed in. */
  static getLoggedInUser() {
    try {
      return JSON.parse(sessionStorage.getItem(AuthService.SESSION_KEY));
    } catch (e) {
      return null;
    }
  }

  static _store(user) {
    sessionStorage.setItem(AuthService.SESSION_KEY, JSON.stringify(user));
    return user;
  }

  /**
   * Signs in against the server. Returns the same
   * { success, user } / { success, message } shape the screens already expect.
   */
  static async login(username, password) {
    try {
      const user = await Api.auth.login(username, password);
      AuthService._store(AuthService._shape(user));
      return { success: true, user: AuthService.getLoggedInUser() };
    } catch (e) {
      return { success: false, message: e.message || 'Sign in failed.' };
    }
  }

  /**
   * Confirms with the server that the session is still valid and refreshes
   * the cached profile. Screens call this on load instead of trusting
   * whatever is sitting in storage.
   */
  static async verify() {
    try {
      const user = await Api.auth.me();
      return AuthService._store(AuthService._shape(user));
    } catch (e) {
      sessionStorage.removeItem(AuthService.SESSION_KEY);
      return null;
    }
  }

  static async logout() {
    try {
      await Api.auth.logout();
    } catch (e) {
      /* signing out locally still matters if the call fails */
    }
    sessionStorage.removeItem(AuthService.SESSION_KEY);
    window.location.href = 'login.html';
  }

  /**
   * Guards a page. Redirects to the sign-in screen when there is no valid
   * server session, and returns the signed-in profile otherwise.
   */
  static async requireSession() {
    const user = await AuthService.verify();
    if (!user) {
      window.location.href = 'login.html';
      return null;
    }
    return user;
  }

  /** Kept for older call sites; the server session is the authority. */
  static checkAuthAndRedirect() {
    const user = AuthService.getLoggedInUser();
    if (!user) {
      window.location.href = 'login.html';
      return null;
    }
    return user;
  }

  /**
   * Role switching was a demo shortcut in the prototype. Roles now come from
   * the account you signed in with, so this only reports the refusal rather
   * than silently doing nothing.
   */
  static switchRole() {
    if (typeof showToast === 'function') {
      showToast('Roles come from your account now. Sign in as that user to see their screens.', 'warning');
    }
    return null;
  }

  static switchUserById() {
    return AuthService.switchRole();
  }

  /** Normalises the server profile into the fields the screens read. */
  static _shape(user) {
    return {
      id: user.posId || user.id,
      username: user.username,
      role: user.role,
      fullName: user.fullName,
      email: user.email,
      phone: user.phone,
      status: user.status || 'ACTIVE',
      specialty: user.specialization || user.specialty,
      consultationFee: user.consultationFee,
      roomNo: user.roomNo
    };
  }
}

window.AuthService = AuthService;
