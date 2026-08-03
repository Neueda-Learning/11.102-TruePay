/* ─── TruePay App ─────────────────────────────────────────────────── */
const state = { user: null, accounts: [], beneficiaries: [], payments: [], summary: null, charts: {} };

/* Theme */
function getTheme() { return localStorage.getItem('theme') || 'dark'; }
function applyTheme(t) { document.body.classList.toggle('dark', t === 'dark'); document.getElementById('themeSwitch').classList.toggle('on', t === 'dark'); }
applyTheme(getTheme());
document.getElementById('themeSwitch').addEventListener('click', () => {
  const next = getTheme() === 'dark' ? 'light' : 'dark';
  localStorage.setItem('theme', next);
  applyTheme(next);
});

/* Navigation */
function navigate(page) {
  document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
  const pageEl = document.getElementById('page-' + page);
  if (pageEl) pageEl.classList.add('active');
  const navEl = document.querySelector(`.nav-item[data-page="${page}"]`);
  if (navEl) navEl.classList.add('active');
  const titles = { dashboard: ['Dashboard', 'Welcome back! Here\'s your overview.'], history: ['Payment History', 'All your transactions in one place.'], 'pay-upi': ['Pay to UPI', 'Send money instantly via UPI.'], 'pay-bank': ['Bank Transfer', 'Transfer funds to any bank account.'], accounts: ['Bank Accounts', 'Manage your linked accounts.'], beneficiaries: ['Beneficiaries', 'Manage saved receivers.'], profile: ['Profile', 'Your personal information.'] };
  const [title, sub] = titles[page] || ['TruePay', ''];
  document.getElementById('topbarTitle').textContent = title;
  document.getElementById('topbarSub').textContent = sub;
}
document.querySelectorAll('.nav-item[data-page]').forEach(btn => btn.addEventListener('click', () => navigate(btn.dataset.page)));

/* API helper */
async function api(path, opts = {}) {
  const res = await fetch(path, { ...opts, headers: { 'Content-Type': 'application/json', ...(opts.headers || {}) } });
  if (res.status === 401) { location.href = '/login.html'; return null; }
  if (!res.ok) { const e = await res.json().catch(() => ({})); throw new Error(e.message || 'Request failed'); }
  if (res.status === 204) return null;
  return res.json().catch(() => null);
}

/* Formatters */
function inr(v) { return '₹ ' + Number(v || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }); }
function maskNum(n) { return n ? '**** ' + n.slice(-4) : '—'; }
function fmtDate(d) { return d ? new Date(d).toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—'; }
function statusIcon(s) { return { COMPLETED: '✅', FAILED: '❌', CREATED: '🔵', VALIDATED: '🟡', SENT: '📤' }[s] || '⚪'; }

/* ─── RENDER ──────────────────────────────────────────────────────── */
function renderSidebar() {
  if (!state.user) return;
  document.getElementById('sidebarName').textContent = state.user.fullName;
  document.getElementById('sidebarEmail').textContent = state.user.email;
  document.getElementById('sidebarAvatar').textContent = state.user.fullName.charAt(0).toUpperCase();
  document.getElementById('topbarBalance').textContent = state.summary ? inr(state.summary.combinedBalance) : '—';
}

function renderKPIs() {
  if (!state.summary) return;
  const s = state.summary;
  document.getElementById('kpiBalance').textContent = inr(s.combinedBalance);
  document.getElementById('kpiAccounts').textContent = s.linkedBankAccounts + ' accounts linked';
  document.getElementById('kpiCompleted').textContent = s.completedPayments;
  document.getElementById('kpiFailed').textContent = s.failedPayments;
  document.getElementById('kpiFraud').textContent = s.fraudAlerts;
  document.getElementById('chartTotalLabel').textContent = s.totalPayments + ' total payments';
}

function renderCharts() {
  if (!state.summary) return;
  const s = state.summary;
  const isDark = getTheme() === 'dark';
  const gridColor = isDark ? 'rgba(255,255,255,.06)' : 'rgba(0,0,0,.06)';
  const textColor = isDark ? '#8888aa' : '#6b6f8a';

  ['volumeChart','statusChart','methodChart','riskChart'].forEach(id => { if (state.charts[id]) state.charts[id].destroy(); });

  /* Volume */
  const lastSeven = [0,0,0,0,0,0,0];
  const now = new Date();
  state.payments.forEach(p => { const days = Math.floor((now - new Date(p.createdAt)) / 86400000); if (days >= 0 && days < 7) lastSeven[6-days]++; });
  const dayLabels = [...Array(7)].map((_,i) => { const d = new Date(now); d.setDate(d.getDate()-(6-i)); return (d.getMonth()+1)+'/'+d.getDate(); });

  state.charts.volumeChart = new Chart(document.getElementById('volumeChart'), {
    type: 'line',
    data: { labels: dayLabels, datasets: [{ label: 'Payments', data: lastSeven, borderColor: '#7c5cfc', backgroundColor: 'rgba(124,92,252,.12)', fill: true, tension: .4, pointBackgroundColor: '#7c5cfc', pointRadius: 4 }] },
    options: { responsive: true, plugins: { legend: { display: false } }, scales: { x: { grid: { color: gridColor }, ticks: { color: textColor, font: { size: 11 } } }, y: { grid: { color: gridColor }, ticks: { color: textColor, stepSize: 1, font: { size: 11 } } } } }
  });

  /* Status doughnut */
  const comp = s.completedPayments, fail = s.failedPayments, inprog = Math.max(0, s.totalPayments - comp - fail);
  state.charts.statusChart = new Chart(document.getElementById('statusChart'), {
    type: 'doughnut',
    data: { labels: ['Completed','Failed','In Progress'], datasets: [{ data: [comp, fail, inprog], backgroundColor: ['#22c55e','#ef4444','#7c5cfc'], borderWidth: 0 }] },
    options: { responsive: true, cutout: '65%', plugins: { legend: { display: false } } }
  });

  const total = comp + fail + inprog || 1;
  document.getElementById('distList').innerHTML = [
    { label: 'Completed', val: comp, color: '#22c55e' },
    { label: 'Failed', val: fail, color: '#ef4444' },
    { label: 'In Progress', val: inprog, color: '#7c5cfc' }
  ].map(d => `<div class="dist-item"><div class="dist-dot" style="background:${d.color}"></div><span class="dist-label">${d.label}</span><span class="dist-val">${d.val}</span><span class="dist-pct">${Math.round(d.val/total*100)}%</span></div>`).join('');

  /* Method bar */
  const upiCount = state.payments.filter(p => p.method === 'UPI').length;
  const bankCount = state.payments.filter(p => p.method === 'BANK').length;
  state.charts.methodChart = new Chart(document.getElementById('methodChart'), {
    type: 'bar',
    data: { labels: ['UPI', 'Bank Transfer'], datasets: [{ data: [upiCount, bankCount], backgroundColor: ['rgba(124,92,252,.8)', 'rgba(79,140,255,.8)'], borderRadius: 8 }] },
    options: { responsive: true, plugins: { legend: { display: false } }, scales: { x: { grid: { display: false }, ticks: { color: textColor } }, y: { grid: { color: gridColor }, ticks: { color: textColor, stepSize: 1 } } } }
  });

  /* Risk */
  state.charts.riskChart = new Chart(document.getElementById('riskChart'), {
    type: 'bar',
    data: { labels: ['Completed', 'Failed', 'Fraud Alerts'], datasets: [{ data: [comp, fail, s.fraudAlerts], backgroundColor: ['rgba(34,197,94,.7)', 'rgba(239,68,68,.7)', 'rgba(251,146,60,.7)'], borderRadius: 8 }] },
    options: { responsive: true, plugins: { legend: { display: false } }, scales: { x: { grid: { display: false }, ticks: { color: textColor } }, y: { grid: { color: gridColor }, ticks: { color: textColor, stepSize: 1 } } } }
  });
}

function renderHistory() {
  const filter = document.getElementById('historyFilter').value;
  const data = filter ? state.payments.filter(p => p.status === filter) : state.payments;
  const tbody = document.getElementById('historyTableBody');
  if (!data.length) { tbody.innerHTML = `<tr><td colspan="7" class="empty">No payments found.</td></tr>`; return; }
  tbody.innerHTML = data.slice(0, 50).map(p => `
    <tr>
      <td style="font-family:monospace;font-size:11px;color:var(--text-sub);">${p.id.slice(0,8)}…</td>
      <td><span class="tag tag-${p.method === 'UPI' ? 'upi' : 'bank'}">${p.method}</span></td>
      <td>${p.receiverName || p.destinationUpiId || '—'}</td>
      <td style="font-weight:700;">${p.amount} <span style="color:var(--text-sub);font-size:11px;">${p.currency}</span></td>
      <td><span class="status-badge ${p.status}">${statusIcon(p.status)} ${p.status}</span></td>
      <td style="color:var(--text-sub);">${fmtDate(p.createdAt)}</td>
      <td><button class="btn btn-secondary" style="padding:5px 10px;font-size:11px;" onclick="showPaymentDetail('${p.id}')">Detail</button></td>
    </tr>`).join('');
}

async function showPaymentDetail(paymentId) {
  const p = state.payments.find(x => x.id === paymentId);
  if (!p) return;
  const panel = document.getElementById('paymentDetailPanel');
  const content = document.getElementById('paymentDetailContent');
  content.innerHTML = `
    <div class="page-grid-2" style="margin-bottom:12px;">
      <div><div class="kpi-label">Payment ID</div><div style="font-family:monospace;font-size:12px;">${p.id}</div></div>
      <div><div class="kpi-label">Status</div><span class="status-badge ${p.status}">${statusIcon(p.status)} ${p.status}</span></div>
      <div><div class="kpi-label">Amount</div><div style="font-weight:700;">${p.amount} ${p.currency}</div></div>
      <div><div class="kpi-label">Method</div><span class="tag tag-${p.method === 'UPI' ? 'upi' : 'bank'}">${p.method}</span></div>
      <div><div class="kpi-label">Receiver</div><div>${p.receiverName || p.destinationUpiId || '—'}</div></div>
      <div><div class="kpi-label">Reference</div><div>${p.referenceRemark || '—'}</div></div>
      <div><div class="kpi-label">Created</div><div style="color:var(--text-sub);">${fmtDate(p.createdAt)}</div></div>
      ${p.errorMessage ? `<div style="grid-column:1/-1;"><div class="kpi-label">Error</div><div style="color:var(--red);">${p.errorMessage}</div></div>` : ''}
    </div>`;
  panel.style.display = 'block';
  panel.scrollIntoView({ behavior: 'smooth' });
  try {
    const history = await api(`/api/v1/payments/${paymentId}/history`);
    const icons = { CREATED: '📝', VALIDATED: '✔️', SENT: '📤', COMPLETED: '✅', FAILED: '❌' };
    document.getElementById('paymentTimeline').innerHTML = (history || []).map(h => `
      <div class="timeline-item">
        <div class="timeline-dot ${h.status}">${icons[h.status] || '⚪'}</div>
        <div class="timeline-content">
          <div class="timeline-label">${h.status}</div>
          <div class="timeline-time">${fmtDate(h.changedAt)} · by ${h.triggeredBy}</div>
          <div class="timeline-note">${h.notes || ''}</div>
        </div>
      </div>`).join('');
  } catch (_) {}
}

function renderAccounts() {
  const combined = state.summary ? inr(state.summary.combinedBalance) : '—';
  document.getElementById('accCombined').textContent = combined;

  const html = state.accounts.map(a => `
    <div class="account-chip">
      <div class="account-chip-icon">🏦</div>
      <div class="account-chip-info">
        <div class="account-chip-bank">${a.bankName}</div>
        <div class="account-chip-num">${maskNum(a.accountNumber)} · ${a.ifscCode}</div>
      </div>
      <div class="account-chip-bal">${inr(a.balance)}</div>
      <button class="btn btn-danger" onclick="deleteBankAccount(${a.id})">Delete</button>
    </div>`).join('');
  document.getElementById('accountsList').innerHTML = html || '<div class="empty">No bank accounts linked yet.</div>';

  /* Dropdowns */
  const opts = state.accounts.map(a => `<option value="${a.id}">${a.bankName} · ${maskNum(a.accountNumber)} (${inr(a.balance)})</option>`).join('');
  ['upiSource','bankSource'].forEach(id => { const el = document.getElementById(id); if (el) el.innerHTML = opts; });
  document.getElementById('upiAccountPreview').innerHTML = html || '<div class="empty">No accounts.</div>';

  /* Combined */
  document.getElementById('topbarBalance').textContent = state.summary ? inr(state.summary.combinedBalance) : '—';
}

function renderBeneficiaries() {
  const select = document.getElementById('beneficiarySelect');
  select.innerHTML = '<option value="">— Enter manually —</option>' + state.beneficiaries.map(b => `<option value="${b.id}">${b.name} · ${maskNum(b.accountNumber)}</option>`).join('');

  const html = state.beneficiaries.map(b => `
    <div class="bene-card">
      <div class="bene-avatar">${b.name.charAt(0).toUpperCase()}</div>
      <div class="bene-info"><div class="bene-name">${b.name}</div><div class="bene-num">${b.accountNumber} · ${b.ifscCode}</div></div>
      <button class="btn btn-danger" onclick="deleteBeneficiary(${b.id})">Delete</button>
    </div>`).join('');

  document.getElementById('beneficiariesList').innerHTML = html || '<div class="empty">No beneficiaries saved yet.</div>';
  document.getElementById('bankBenePreview').innerHTML = html || '<div class="empty">No beneficiaries saved yet.</div>';
}

function renderProfile() {
  if (!state.user) return;
  document.getElementById('profileContent').innerHTML = `
    <div class="account-chip" style="margin-bottom:12px;">
      <div class="sidebar-avatar">${state.user.fullName.charAt(0).toUpperCase()}</div>
      <div class="account-chip-info">
        <div class="account-chip-bank">${state.user.fullName}</div>
        <div class="account-chip-num">${state.user.email}</div>
      </div>
    </div>
    <div style="font-size:14px;display:flex;flex-direction:column;gap:10px;">
      <div class="flex-center"><span style="color:var(--text-sub);min-width:100px;">Full Name</span><strong>${state.user.fullName}</strong></div>
      <div class="flex-center"><span style="color:var(--text-sub);min-width:100px;">Email</span><strong>${state.user.email}</strong></div>
      <div class="flex-center"><span style="color:var(--text-sub);min-width:100px;">Mobile</span><strong>${state.user.mobile}</strong></div>
      <div class="flex-center"><span style="color:var(--text-sub);min-width:100px;">User ID</span><strong>#${state.user.id}</strong></div>
    </div>`;
}

/* ─── ACTIONS ─────────────────────────────────────────────────────── */
async function deleteBankAccount(id) {
  if (!confirm('Delete this bank account? Only allowed with zero balance.')) return;
  try { await api(`/api/v1/bank-accounts/${id}`, { method: 'DELETE' }); await loadAll(); }
  catch (e) { alert('❌ ' + e.message); }
}

async function deleteBeneficiary(id) {
  if (!confirm('Remove this beneficiary?')) return;
  try { await api(`/api/v1/beneficiaries/${id}`, { method: 'DELETE' }); await loadAll(); }
  catch (e) { alert('❌ ' + e.message); }
}

window.deleteBankAccount = deleteBankAccount;
window.deleteBeneficiary = deleteBeneficiary;
window.showPaymentDetail = showPaymentDetail;
window.loadAll = loadAll;
window.navigate = navigate;

/* ─── FORMS ──────────────────────────────────────────────────────── */
function showResult(id, msg, ok) {
  const el = document.getElementById(id);
  el.textContent = msg;
  el.className = 'verify-result ' + (ok ? 'show-ok' : 'show-err');
}

document.getElementById('logoutBtn').addEventListener('click', async () => {
  await api('/api/v1/auth/logout', { method: 'POST' });
  location.href = '/login.html';
});

document.getElementById('historyFilter').addEventListener('change', renderHistory);

document.getElementById('beneficiarySelect').addEventListener('change', (e) => {
  const found = state.beneficiaries.find(b => String(b.id) === e.target.value);
  const form = document.getElementById('bankTransferForm');
  if (found) { form.receiverName.value = found.name; form.destinationAccount.value = found.accountNumber; form.destinationIfsc.value = found.ifscCode; }
});

document.getElementById('verifyReceiverBtn').addEventListener('click', async () => {
  const form = document.getElementById('bankTransferForm');
  const accountNumber = form.destinationAccount.value;
  const ifscCode = form.destinationIfsc.value;
  const el = document.getElementById('verifyReceiverMsg');
  if (!accountNumber || !ifscCode) { el.textContent = 'Enter receiver account and IFSC first.'; el.className = 'verify-result show-err'; return; }
  try {
    const r = await api(`/api/v1/payments/verify-receiver?accountNumber=${encodeURIComponent(accountNumber)}&ifscCode=${encodeURIComponent(ifscCode)}`);
    el.textContent = (r.internalAccount ? '✅ ' : 'ℹ️ ') + r.message;
    el.className = 'verify-result ' + (r.internalAccount ? 'show-ok' : 'show-info');
    if (r.internalAccount && !form.receiverName.value) form.receiverName.value = r.receiverName;
  } catch (e) { el.textContent = '❌ ' + e.message; el.className = 'verify-result show-err'; }
});

document.getElementById('bankAccountForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = e.target;
  try {
    await api('/api/v1/bank-accounts', { method: 'POST', body: JSON.stringify({ bankName: f.bankName.value, accountNumber: f.accountNumber.value, ifscCode: f.ifscCode.value, bankPin: f.bankPin.value, openingBalance: Number(f.openingBalance.value) }) });
    f.reset(); showResult('bankAccountResult', '✅ Bank account linked successfully!', true);
    await loadAll();
  } catch (e2) { showResult('bankAccountResult', '❌ ' + e2.message, false); }
});

document.getElementById('beneficiaryForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = e.target;
  try {
    await api('/api/v1/beneficiaries', { method: 'POST', body: JSON.stringify({ name: f.name.value, accountNumber: f.accountNumber.value, ifscCode: f.ifscCode.value }) });
    f.reset(); showResult('beneResult', '✅ Beneficiary added!', true);
    await loadAll();
  } catch (e2) { showResult('beneResult', '❌ ' + e2.message, false); }
});

document.getElementById('upiForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = e.target;
  try {
    const p = await api('/api/v1/payments/pay-to-upi', { method: 'POST', body: JSON.stringify({ sourceAccountId: Number(f.sourceAccountId.value), amount: Number(f.amount.value), currency: f.currency.value, destinationUpiId: f.destinationUpiId.value, idempotencyKey: f.idempotencyKey.value, appPin: f.appPin.value, bankPin: f.bankPin.value }) });
    f.reset(); showResult('upiResult', `✅ Payment ${p.status}! ID: ${p.id.slice(0,8)}…`, true);
    await loadAll();
  } catch (e2) { showResult('upiResult', '❌ ' + e2.message, false); }
});

document.getElementById('bankTransferForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = e.target;
  try {
    const p = await api('/api/v1/payments/pay-to-bank', { method: 'POST', body: JSON.stringify({ sourceAccountId: Number(f.sourceAccountId.value), amount: Number(f.amount.value), currency: f.currency.value, beneficiaryId: f.beneficiaryId.value ? Number(f.beneficiaryId.value) : null, receiverName: f.receiverName.value || null, destinationAccount: f.destinationAccount.value || null, destinationIfsc: f.destinationIfsc.value || null, reference: f.reference.value || null, idempotencyKey: f.idempotencyKey.value, appPin: f.appPin.value, bankPin: f.bankPin.value }) });
    f.reset(); showResult('bankTransferResult', `✅ Transfer ${p.status}! ID: ${p.id.slice(0,8)}…`, true);
    await loadAll();
  } catch (e2) { showResult('bankTransferResult', '❌ ' + e2.message, false); }
});

/* ─── LOAD ────────────────────────────────────────────────────────── */
async function loadAll() {
  state.user = await api('/api/v1/auth/me');
  if (!state.user) return;
  [state.accounts, state.beneficiaries, state.payments, state.summary] = await Promise.all([
    api('/api/v1/bank-accounts'),
    api('/api/v1/beneficiaries'),
    api('/api/v1/payments'),
    api('/api/v1/dashboard/summary')
  ]);
  renderSidebar();
  renderKPIs();
  renderCharts();
  renderHistory();
  renderAccounts();
  renderBeneficiaries();
  renderProfile();
}

loadAll();


async function api(path, options = {}) {
  const res = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    }
  });

  if (res.status === 401) {
    location.href = '/login.html';
    return;
  }

  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: 'Request failed' }));
    throw new Error(err.message || 'Request failed');
  }

  if (res.status === 204) {
    return null;
  }

  return res.json().catch(() => null);
}

function rupee(value) {
  return `Rs ${Number(value || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function maskAccount(acc) {
  const tail = acc.slice(-4);
  return `**** **** ${tail}`;
}

function getMode() {
  return localStorage.getItem('theme') || 'light';
}

function applyTheme(mode) {
  document.body.classList.toggle('dark', mode === 'dark');
}

function bindTheme() {
  applyTheme(getMode());
  document.getElementById('themeBtn').addEventListener('click', () => {
    const next = getMode() === 'dark' ? 'light' : 'dark';
    localStorage.setItem('theme', next);
    applyTheme(next);
  });
}

function renderProfile() {
  const pane = document.getElementById('personalPane');
  pane.innerHTML = `
    <div><b>${state.user.fullName}</b></div>
    <div class="tagline">${state.user.email}</div>
    <div class="tagline">${state.user.mobile}</div>
  `;
}

function renderAccounts() {
  const html = state.accounts.map(a => `
    <div class="card mt">
      <div><b>${a.bankName}</b></div>
      <div class="tagline">${maskAccount(a.accountNumber)} | ${a.ifscCode}</div>
      <div>${rupee(a.balance)}</div>
      <button class="secondary mt" onclick="deleteBankAccount(${a.id})">Delete (requires zero balance)</button>
    </div>
  `).join('');
  document.getElementById('bankList').innerHTML = html || 'No linked bank account yet.';

  const optionHtml = state.accounts.map(a => `<option value="${a.id}">${a.bankName} ${maskAccount(a.accountNumber)} (${rupee(a.balance)})</option>`).join('');
  document.getElementById('upiSource').innerHTML = optionHtml;
  document.getElementById('bankSource').innerHTML = optionHtml;
}

function renderBeneficiaries() {
  const select = document.getElementById('beneficiarySelect');
  const options = state.beneficiaries.map(b => `<option value="${b.id}">${b.name} - ${maskAccount(b.accountNumber)} (${b.ifscCode})</option>`).join('');
  select.innerHTML = `<option value="">Manual Entry</option>${options}`;

  const listHtml = state.beneficiaries.map(b => `
    <div class="card mt">
      <div><b>${b.name}</b></div>
      <div class="tagline">${b.accountNumber} | ${b.ifscCode}</div>
      <button class="secondary mt" onclick="deleteBeneficiary(${b.id})">Delete Beneficiary</button>
    </div>
  `).join('');
  document.getElementById('beneficiaryList').innerHTML = listHtml || 'No beneficiaries added yet.';
}

function renderSummary() {
  document.getElementById('combinedBalance').textContent = rupee(state.summary.combinedBalance);
}

function renderHistory() {
  const rows = state.payments.slice(0, 20).map(p => `
    <tr>
      <td>${p.id}</td>
      <td>${p.method}</td>
      <td>${p.amount} ${p.currency}</td>
      <td><span class="pill">${p.status}</span></td>
      <td>${p.receiverName || p.destinationUpiId || '-'}</td>
      <td>${new Date(p.createdAt).toLocaleString()}</td>
    </tr>
  `).join('');

  document.querySelector('#historyTable tbody').innerHTML = rows;
}

function destroyChart(name) {
  if (state.charts[name]) {
    state.charts[name].destroy();
  }
}

function renderCharts() {
  destroyChart('status');
  destroyChart('risk');
  destroyChart('volume');

  const completed = state.summary.completedPayments;
  const failed = state.summary.failedPayments;
  const inProgress = Math.max(0, state.summary.totalPayments - completed - failed);

  state.charts.status = new Chart(document.getElementById('statusChart'), {
    type: 'doughnut',
    data: {
      labels: ['Completed', 'Failed', 'In Progress'],
      datasets: [{
        data: [completed, failed, inProgress],
        backgroundColor: ['#2ecc71', '#e74c3c', '#4f8cff']
      }]
    }
  });

  state.charts.risk = new Chart(document.getElementById('riskChart'), {
    type: 'bar',
    data: {
      labels: ['Failed', 'Fraud Alerts'],
      datasets: [{
        label: 'Count',
        data: [failed, state.summary.fraudAlerts],
        backgroundColor: ['#ff7f50', '#9b6bff']
      }]
    }
  });

  const lastSeven = [0, 0, 0, 0, 0, 0, 0];
  const now = new Date();
  state.payments.forEach(p => {
    const days = Math.floor((now - new Date(p.createdAt)) / (1000 * 60 * 60 * 24));
    if (days >= 0 && days < 7) {
      lastSeven[6 - days] += 1;
    }
  });
  const labels = [...Array(7)].map((_, i) => {
    const d = new Date(now);
    d.setDate(now.getDate() - (6 - i));
    return `${d.getMonth() + 1}/${d.getDate()}`;
  });

  state.charts.volume = new Chart(document.getElementById('volumeChart'), {
    type: 'line',
    data: {
      labels,
      datasets: [{
        label: 'Payments per day',
        data: lastSeven,
        borderColor: '#4f8cff',
        tension: 0.3
      }]
    }
  });
}

async function deleteBankAccount(id) {
  try {
    await api(`/api/v1/bank-accounts/${id}`, { method: 'DELETE' });
    await loadAll();
  } catch (e) {
    alert(e.message);
  }
}

async function deleteBeneficiary(id) {
  try {
    await api(`/api/v1/beneficiaries/${id}`, { method: 'DELETE' });
    await loadAll();
  } catch (e) {
    alert(e.message);
  }
}

window.deleteBankAccount = deleteBankAccount;
window.deleteBeneficiary = deleteBeneficiary;

function bindTabs() {
  const personal = document.getElementById('personalPane');
  const bank = document.getElementById('bankPane');
  document.getElementById('tabPersonal').addEventListener('click', () => {
    personal.classList.remove('hidden');
    bank.classList.add('hidden');
  });
  document.getElementById('tabBank').addEventListener('click', () => {
    bank.classList.remove('hidden');
    personal.classList.add('hidden');
  });
}

function bindForms() {
  document.getElementById('logoutBtn').addEventListener('click', async () => {
    await api('/api/v1/auth/logout', { method: 'POST' });
    location.href = '/login.html';
  });

  document.getElementById('bankForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = e.target;
    try {
      await api('/api/v1/bank-accounts', {
        method: 'POST',
        body: JSON.stringify({
          bankName: f.bankName.value,
          accountNumber: f.accountNumber.value,
          ifscCode: f.ifscCode.value,
          bankPin: f.bankPin.value,
          openingBalance: Number(f.openingBalance.value)
        })
      });
      f.reset();
      await loadAll();
    } catch (e2) {
      document.getElementById('bankList').textContent = e2.message;
    }
  });

  document.getElementById('beneficiaryForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = e.target;
    try {
      await api('/api/v1/beneficiaries', {
        method: 'POST',
        body: JSON.stringify({
          name: f.name.value,
          accountNumber: f.accountNumber.value,
          ifscCode: f.ifscCode.value
        })
      });
      f.reset();
      await loadAll();
    } catch (e2) {
      document.getElementById('beneficiaryList').textContent = e2.message;
    }
  });

  document.getElementById('beneficiarySelect').addEventListener('change', (e) => {
    const selected = state.beneficiaries.find(b => String(b.id) === e.target.value);
    const form = document.getElementById('bankTransferForm');
    if (selected) {
      form.receiverName.value = selected.name;
      form.destinationAccount.value = selected.accountNumber;
      form.destinationIfsc.value = selected.ifscCode;
    }
  });

  document.getElementById('verifyReceiverBtn').addEventListener('click', async () => {
    const form = document.getElementById('bankTransferForm');
    const accountNumber = form.destinationAccount.value;
    const ifscCode = form.destinationIfsc.value;
    if (!accountNumber || !ifscCode) {
      document.getElementById('verifyReceiverMsg').textContent = 'Enter receiver account and IFSC first.';
      return;
    }
    try {
      const result = await api(`/api/v1/payments/verify-receiver?accountNumber=${encodeURIComponent(accountNumber)}&ifscCode=${encodeURIComponent(ifscCode)}`);
      document.getElementById('verifyReceiverMsg').textContent = result.message;
      if (result.internalAccount && !form.receiverName.value) {
        form.receiverName.value = result.receiverName;
      }
    } catch (e2) {
      document.getElementById('verifyReceiverMsg').textContent = e2.message;
    }
  });

  document.getElementById('upiForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = e.target;
    try {
      const payment = await api('/api/v1/payments/pay-to-upi', {
        method: 'POST',
        body: JSON.stringify({
          sourceAccountId: Number(f.sourceAccountId.value),
          amount: Number(f.amount.value),
          currency: f.currency.value,
          destinationUpiId: f.destinationUpiId.value,
          idempotencyKey: f.idempotencyKey.value,
          appPin: f.appPin.value,
          bankPin: f.bankPin.value
        })
      });
      document.getElementById('upiMsg').textContent = `Payment ${payment.status}: ${payment.id}`;
      f.reset();
      await loadAll();
    } catch (e2) {
      document.getElementById('upiMsg').textContent = e2.message;
    }
  });

  document.getElementById('bankTransferForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = e.target;
    try {
      const payload = {
        sourceAccountId: Number(f.sourceAccountId.value),
        amount: Number(f.amount.value),
        currency: f.currency.value,
        beneficiaryId: f.beneficiaryId.value ? Number(f.beneficiaryId.value) : null,
        receiverName: f.receiverName.value || null,
        destinationAccount: f.destinationAccount.value || null,
        destinationIfsc: f.destinationIfsc.value || null,
        reference: f.reference.value || null,
        idempotencyKey: f.idempotencyKey.value,
        appPin: f.appPin.value,
        bankPin: f.bankPin.value
      };
      const payment = await api('/api/v1/payments/pay-to-bank', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      document.getElementById('bankTransferMsg').textContent = `Transfer ${payment.status}: ${payment.id}`;
      f.reset();
      await loadAll();
    } catch (e2) {
      document.getElementById('bankTransferMsg').textContent = e2.message;
    }
  });
}

async function loadAll() {
  state.user = await api('/api/v1/auth/me');
  state.accounts = await api('/api/v1/bank-accounts');
  state.beneficiaries = await api('/api/v1/beneficiaries');
  state.payments = await api('/api/v1/payments');
  state.summary = await api('/api/v1/dashboard/summary');

  renderProfile();
  renderAccounts();
  renderBeneficiaries();
  renderSummary();
  renderHistory();
  renderCharts();
}

(async function init() {
  bindTheme();
  bindTabs();
  bindForms();
  await loadAll();
})();

