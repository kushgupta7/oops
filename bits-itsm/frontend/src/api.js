const sessionEvents = new EventTarget();

// App listens here so every page returns to login when the session expires.
export function onSessionExpired(callback) {
  sessionEvents.addEventListener('expired', callback);
  return () => sessionEvents.removeEventListener('expired', callback);
}

const RETRY_DELAY_MS = 700;
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

export async function apiRequest(path, options = {}) {
  let response;
  // Reads are idempotent, so one automatic retry hides a brief network glitch; writes are never retried.
  const attempts = !options.method || options.method === 'GET' ? 2 : 1;
  for (let attempt = 1; ; attempt++) {
    try {
      response = await fetch(`/api/${path}`, { ...options, credentials: 'include' });
      break;
    } catch {
      if (attempt >= attempts) throw new Error('Unable to connect to the BITSolve server. Check your connection and try again.');
      await wait(RETRY_DELAY_MS);
    }
  }
  const data = await response.json().catch(() => null);
  if (response.status === 401 && path !== 'auth/login' && path !== 'auth/me') {
    sessionEvents.dispatchEvent(new Event('expired'));
  }
  if (!response.ok || !data) {
    const error = new Error(response.status === 401 && path !== 'auth/login' ? 'Please sign in again. Your session may have expired.' : response.status >= 500 || !data ? 'Unable to connect to the BITSolve server.' : data.message || 'The request could not be completed.');
    error.status = response.status;
    throw error;
  }
  return data;
}

export async function apiSend(path, body, method = 'POST', extraHeaders = {}) {
  const csrf = await apiRequest('auth/csrf');
  return apiRequest(path, {
    method,
    headers: { 'Content-Type': 'application/json', ...extraHeaders, [csrf.headerName]: csrf.token },
    body: body ? JSON.stringify(body) : undefined,
  });
}

export const apiPost = apiSend;
