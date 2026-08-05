/* TruePay dashboard frontend */
const state = { user: null, accounts: [], payments: [], audits: [], summary: null, charts: {}, searchQuery: '' };

/* Theme */
function getTheme() {
  return localStorage.getItem('theme') || 'dark';
}

function applyTheme(theme) {
  const dark = theme === 'dark';
  document.body.classList.toggle('dark', dark);
  document.getElementById('themeSwitch').classList.toggle('on', dark);
}

function toggleTheme() {
  const next = getTheme() === 'dark' ? 'light' : 'dark';
  localStorage.setItem('theme', next);
  applyTheme(next);
}

/* Navigation */
function navigate(page) {
  document.querySelectorAll('.page').forEach((p) => p.classList.remove('active'));
  document.querySelectorAll('.nav-item').forEach((n) => n.classList.remove('active'));

  const pageEl = document.getElementById('page-' + page);
  if (pageEl) pageEl.classList.add('active');

  const navEl = document.querySelector(`.nav-item[data-page="${page}"]`);
  if (navEl) navEl.classList.add('active');

  const titles = {
    dashboard: ['Dashboard', "Welcome back! Here's your overview."],
    history: ['Payment History', 'All your transactions in one place.'],
    audits: ['Audit History', 'Every transaction event, actor, and status change.'],
    'pay-upi': ['Pay to UPI', 'Send money instantly via UPI.'],
    'pay-bank': ['Bank Transfer', 'Transfer funds to any bank account.'],
    accounts: ['Bank Accounts', 'Manage your linked accounts.'],
    profile: ['Profile', 'Your personal information.']
  };

  const [title, sub] = titles[page] || ['TruePay', ''];
  document.getElementById('topbarTitle').textContent = title;
  document.getElementById('topbarSub').textContent = sub;
}

/* API */
async function api(path, options = {}) {
  const res = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    }
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || 'Request failed');
  }

  if (res.status === 204) return null;
  return res.json().catch(() => null);
}

/* Formatting */
function inr(v) {
  return 'INR ' + Number(v || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function maskNum(n) {
  return n ? '**** ' + n.slice(-4) : '-';
}

function fmtDate(d) {
  return d ? new Date(d).toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '-';
}

function statusIcon(s) {
  return { COMPLETED: 'OK', FAILED: 'X', CREATED: 'NEW', VALIDATED: 'CHK', SENT: 'SENT' }[s] || '...';
}

function toSearchText(value) {
  return String(value || '').toLowerCase().trim();
}

function paymentMatchesQuery(payment, query) {
  if (!query) return true;
  const haystack = [
    payment.id,
    payment.method,
    payment.status,
    payment.receiverName,
    payment.destinationUpiId,
    payment.currency,
    payment.amount,
    fmtDate(payment.createdAt)
  ].map(toSearchText).join(' ');
  return haystack.includes(query);
}

function auditMatchesQuery(audit, query) {
  if (!query) return true;
  const haystack = [
    audit.paymentId,
    audit.method,
    audit.status,
    audit.receiver,
    audit.currency,
    audit.amount,
    audit.triggeredBy,
    audit.notes,
    audit.referenceRemark,
    fmtDate(audit.changedAt)
  ].map(toSearchText).join(' ');
  return haystack.includes(query);
}

function accountMatchesQuery(account, query) {
  if (!query) return true;
  const haystack = [
    account.bankName,
    account.accountHolderName,
    account.accountNumber,
    account.ifscCode,
    account.accountType,
    account.balance
  ].map(toSearchText).join(' ');
  return haystack.includes(query);
}

function getSearchTargetPage(query) {
  if (!query) return null;

  const pageKeywords = {
    dashboard: ['dashboard', 'overview', 'summary', 'kpi', 'risk'],
    history: ['history', 'payment history', 'payments', 'transactions'],
    audits: ['audit', 'audit history', 'events', 'actor', 'timeline'],
    'pay-upi': ['upi', 'pay upi', 'send upi', 'mobile'],
    'pay-bank': ['bank transfer', 'transfer', 'ifsc', 'receiver'],
    accounts: ['account', 'bank accounts', 'ifsc', 'balance', 'linked'],
    profile: ['profile', 'user', 'email', 'mobile']
  };

  for (const [page, words] of Object.entries(pageKeywords)) {
    if (words.some((word) => word.includes(query) || query.includes(word))) {
      return page;
    }
  }

  if (state.payments.some((p) => paymentMatchesQuery(p, query))) return 'history';
  if (state.audits.some((a) => auditMatchesQuery(a, query))) return 'audits';
  if (state.accounts.some((a) => accountMatchesQuery(a, query))) return 'accounts';

  if (state.user) {
    const userText = [state.user.fullName, state.user.email, state.user.mobile].map(toSearchText).join(' ');
    if (userText.includes(query)) return 'profile';
  }

  return null;
}

function runSearch(query, options = {}) {
  state.searchQuery = toSearchText(query);

  const searchWrap = document.querySelector('.topbar-search-wrap');
  if (searchWrap) {
    searchWrap.classList.toggle('search-active', Boolean(state.searchQuery));
  }

  if (options.navigateFirstMatch) {
    const targetPage = getSearchTargetPage(state.searchQuery);
    if (targetPage) navigate(targetPage);
  }

  renderHistory();
  renderAudits();
  renderAccounts();
  renderDashboardRecent();
}

/* Render */
function renderSidebar() {
  if (!state.user) return;
  const sidebarName = document.getElementById('sidebarName');
  const sidebarEmail = document.getElementById('sidebarEmail');
  const sidebarAvatar = document.getElementById('sidebarAvatar');

  if (sidebarName) sidebarName.textContent = state.user.fullName;
  if (sidebarEmail) sidebarEmail.textContent = state.user.email;
  if (sidebarAvatar) sidebarAvatar.textContent = state.user.fullName.charAt(0).toUpperCase();
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

  ['volumeChart', 'statusChart', 'methodChart', 'riskChart'].forEach((id) => {
    if (state.charts[id]) state.charts[id].destroy();
  });

  const s = state.summary;
  const completed = s.completedPayments;
  const failed = s.failedPayments;
  const inProgress = Math.max(0, s.totalPayments - completed - failed);

  const lastSeven = [0, 0, 0, 0, 0, 0, 0];
  const now = new Date();
  state.payments.forEach((p) => {
    const days = Math.floor((now - new Date(p.createdAt)) / 86400000);
    if (days >= 0 && days < 7) lastSeven[6 - days] += 1;
  });
  const labels = [...Array(7)].map((_, i) => {
    const d = new Date(now);
    d.setDate(d.getDate() - (6 - i));
    return (d.getMonth() + 1) + '/' + d.getDate();
  });

  state.charts.volumeChart = new Chart(document.getElementById('volumeChart'), {
    type: 'line',
    data: { labels, datasets: [{ label: 'Payments', data: lastSeven, borderColor: '#7c5cfc', tension: 0.35 }] },
    options: {
      responsive: true,
      plugins: { legend: { display: false } },
      scales: {
        y: {
          beginAtZero: true,
          ticks: {
            precision: 0,
            stepSize: 1
          }
        }
      }
    }
  });

  state.charts.statusChart = new Chart(document.getElementById('statusChart'), {
    type: 'doughnut',
    data: { labels: ['Completed', 'Failed', 'In Progress'], datasets: [{ data: [completed, failed, inProgress], backgroundColor: ['#22c55e', '#ef4444', '#7c5cfc'] }] },
    options: { responsive: true, cutout: '65%', plugins: { legend: { display: false } } }
  });

  const upiCount = state.payments.filter((p) => p.method === 'UPI').length;
  const bankCount = state.payments.filter((p) => p.method === 'BANK').length;
  state.charts.methodChart = new Chart(document.getElementById('methodChart'), {
    type: 'bar',
    data: { labels: ['UPI', 'Bank Transfer'], datasets: [{ data: [upiCount, bankCount], backgroundColor: ['rgba(124,92,252,.8)', 'rgba(79,140,255,.8)'] }] },
    options: {
      responsive: true,
      plugins: { legend: { display: false } },
      scales: {
        y: {
          beginAtZero: true,
          ticks: {
            precision: 0,
            stepSize: 1
          }
        }
      }
    }
  });

  state.charts.riskChart = new Chart(document.getElementById('riskChart'), {
    type: 'bar',
    data: { labels: ['Completed', 'Failed', 'Fraud Alerts'], datasets: [{ data: [completed, failed, s.fraudAlerts], backgroundColor: ['rgba(34,197,94,.7)', 'rgba(239,68,68,.7)', 'rgba(251,146,60,.7)'] }] },
    options: {
      responsive: true,
      plugins: { legend: { display: false } },
      scales: {
        y: {
          beginAtZero: true,
          ticks: {
            precision: 0,
            stepSize: 1
          }
        }
      }
    }
  });

  const total = completed + failed + inProgress || 1;
  document.getElementById('distList').innerHTML = [
    { label: 'Completed', val: completed, color: '#22c55e' },
    { label: 'Failed', val: failed, color: '#ef4444' },
    { label: 'In Progress', val: inProgress, color: '#7c5cfc' }
  ].map((d) => `<div class="dist-item"><div class="dist-dot" style="background:${d.color}"></div><span class="dist-label">${d.label}</span><span class="dist-val">${d.val}</span><span class="dist-pct">${Math.round((d.val / total) * 100)}%</span></div>`).join('');
}

function renderHistory() {
  const filter = document.getElementById('historyFilter').value;
  const query = state.searchQuery;
  const byStatus = filter ? state.payments.filter((p) => p.status === filter) : state.payments;
  const data = byStatus.filter((p) => paymentMatchesQuery(p, query));
  const tbody = document.getElementById('historyTableBody');

  if (!data.length) {
    const emptyMessage = query ? 'No payments match your search.' : 'No payments found.';
    tbody.innerHTML = `<tr><td colspan="7" class="empty">${emptyMessage}</td></tr>`;
    return;
  }

  tbody.innerHTML = data.slice(0, 50).map((p) => `
    <tr>
      <td style="font-family:monospace;font-size:11px;color:var(--text-sub);">${p.id.slice(0, 8)}...</td>
      <td><span class="tag tag-${p.method === 'UPI' ? 'upi' : 'bank'}">${p.method}</span></td>
      <td>${p.receiverName || p.destinationUpiId || '-'}</td>
      <td style="font-weight:700;">${p.amount} <span style="color:var(--text-sub);font-size:11px;">${p.currency}</span></td>
      <td><span class="status-badge ${p.status}">${statusIcon(p.status)} ${p.status}</span></td>
      <td style="color:var(--text-sub);">${fmtDate(p.createdAt)}</td>
      <td><button class="btn btn-secondary" style="padding:5px 10px;font-size:11px;" onclick="showPaymentDetail('${p.id}')">Detail</button></td>
    </tr>`).join('');
}

function renderAccounts() {
  document.getElementById('accCombined').textContent = state.summary ? inr(state.summary.combinedBalance) : 'INR 0.00';

  const query = state.searchQuery;
  const visibleAccounts = state.accounts.filter((a) => accountMatchesQuery(a, query));

  const html = visibleAccounts.map((a) => `
    <div class="account-chip">
      <div class="account-chip-icon">B</div>
      <div class="account-chip-info">
        <div class="account-chip-bank">${a.bankName}</div>
        <div class="account-chip-num">${maskNum(a.accountNumber)} | ${a.ifscCode}</div>
        <div style="font-size:11px;color:var(--text-sub);margin-top:2px;">${a.accountHolderName || ''} ${a.accountType ? '· <span class="tag" style="font-size:10px;padding:2px 6px;">' + a.accountType + '</span>' : ''}</div>
      </div>
      <div class="account-chip-bal">${inr(a.balance)}</div>
      <button class="btn btn-danger" onclick="deleteBankAccount(${a.id})">Delete</button>
    </div>`).join('');

  const emptyMessage = query ? 'No bank accounts match your search.' : 'No bank accounts linked yet.';
  document.getElementById('accountsList').innerHTML = html || `<div class="empty">${emptyMessage}</div>`;
  document.getElementById('upiAccountPreview').innerHTML = html || '<div class="empty">No accounts.</div>';

  const options = state.accounts.map((a) => `<option value="${a.id}">${a.bankName} | ${maskNum(a.accountNumber)} (${inr(a.balance)})</option>`).join('');
  ['upiSource', 'bankSource'].forEach((id) => {
    const el = document.getElementById(id);
    if (el) el.innerHTML = options;
  });

  const sideList = visibleAccounts.slice(0, 5).map((a) => `
    <div class="dashboard-account-item">
      <div>
        <div class="dashboard-account-bank">${a.bankName}</div>
        <div class="dashboard-account-num">${maskNum(a.accountNumber)}${a.accountType ? ' · ' + a.accountType : ''}</div>
      </div>
      <div class="dashboard-account-bal">${inr(a.balance)}</div>
    </div>`).join('');
  const dashboardAccounts = document.getElementById('dashboardBankAccountsList');
  if (dashboardAccounts) {
    const sideEmptyMessage = query ? 'No linked accounts match your search.' : 'No linked accounts yet.';
    dashboardAccounts.innerHTML = sideList || `<div class="empty">${sideEmptyMessage}</div>`;
  }
}

function renderDashboardRecent() {
  const tbody = document.getElementById('dashboardRecentBody');
  if (!tbody) return;

  const query = state.searchQuery;
  const visiblePayments = state.payments.filter((p) => paymentMatchesQuery(p, query));

  if (!visiblePayments.length) {
    const emptyMessage = query ? 'No recent transactions match your search.' : 'No recent transactions.';
    tbody.innerHTML = `<tr><td colspan="4" class="empty">${emptyMessage}</td></tr>`;
    return;
  }

  tbody.innerHTML = visiblePayments.slice(0, 6).map((p) => `
    <tr>
      <td style="font-family:monospace;font-size:11px;color:var(--text-sub);">${p.id.slice(0, 8)}...</td>
      <td>${p.receiverName || p.destinationUpiId || '-'}</td>
      <td style="font-weight:700;">${p.amount} ${p.currency}</td>
      <td><span class="status-badge ${p.status}">${p.status}</span></td>
    </tr>`).join('');
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

function renderAudits() {
  const tbody = document.getElementById('auditTableBody');
  if (!tbody) return;

  const filter = document.getElementById('auditStatusFilter')?.value || '';
  const query = state.searchQuery;
  const byStatus = filter ? state.audits.filter((audit) => audit.status === filter) : state.audits;
  const data = byStatus.filter((audit) => auditMatchesQuery(audit, query));

  const eventCount = document.getElementById('auditEventCount');
  const transactionCount = document.getElementById('auditTransactionCount');
  const latestAt = document.getElementById('auditLatestAt');

  if (eventCount) eventCount.textContent = data.length;
  if (transactionCount) transactionCount.textContent = new Set(data.map((audit) => audit.paymentId)).size;
  if (latestAt) latestAt.textContent = data.length ? fmtDate(data[0].changedAt) : '-';

  if (!data.length) {
    const emptyMessage = query ? 'No audit events match your search.' : 'No audit events found.';
    tbody.innerHTML = `<tr><td colspan="9" class="empty">${emptyMessage}</td></tr>`;
    return;
  }

  tbody.innerHTML = data.slice(0, 100).map((audit) => `
    <tr>
      <td style="color:var(--text-sub);">${fmtDate(audit.changedAt)}</td>
      <td style="font-family:monospace;font-size:11px;color:var(--text-sub);">${audit.paymentId.slice(0, 8)}...</td>
      <td><span class="tag tag-${audit.method === 'UPI' ? 'upi' : 'bank'}">${audit.method}</span></td>
      <td>${audit.receiver || '-'}</td>
      <td style="font-weight:700;">${audit.amount} <span style="color:var(--text-sub);font-size:11px;">${audit.currency}</span></td>
      <td><span class="status-badge ${audit.status}">${statusIcon(audit.status)} ${audit.status}</span></td>
      <td>${audit.triggeredBy}</td>
      <td>
        <div>${audit.notes || '-'}</div>
        ${audit.idempotencyKey ? `<div class="audit-reference">Key: ${audit.idempotencyKey}</div>` : ''}
        ${audit.referenceRemark ? `<div class="audit-reference">Ref: ${audit.referenceRemark}</div>` : ''}
      </td>
      <td><button class="btn btn-secondary" style="padding:5px 10px;font-size:11px;" onclick="openAuditPayment('${audit.paymentId}')">Transaction</button></td>
    </tr>`).join('');
}

async function showPaymentDetail(paymentId) {
  const payment = state.payments.find((p) => p.id === paymentId);
  if (!payment) return;

  const content = document.getElementById('paymentDetailContent');
  content.innerHTML = `
    <div class="page-grid-2" style="margin-bottom:12px;">
      <div><div class="kpi-label">Payment ID</div><div style="font-family:monospace;font-size:12px;">${payment.id}</div></div>
      <div><div class="kpi-label">Status</div><span class="status-badge ${payment.status}">${statusIcon(payment.status)} ${payment.status}</span></div>
      <div><div class="kpi-label">Amount</div><div style="font-weight:700;">${payment.amount} ${payment.currency}</div></div>
      <div><div class="kpi-label">Method</div><span class="tag tag-${payment.method === 'UPI' ? 'upi' : 'bank'}">${payment.method}</span></div>
      <div><div class="kpi-label">Receiver</div><div>${payment.receiverName || payment.destinationUpiId || '-'}</div></div>
      <div><div class="kpi-label">Reference</div><div>${payment.referenceRemark || '-'}</div></div>
      <div><div class="kpi-label">Created</div><div style="color:var(--text-sub);">${fmtDate(payment.createdAt)}</div></div>
      ${payment.errorMessage ? `<div style="grid-column:1/-1;"><div class="kpi-label">Error</div><div style="color:var(--red);">${payment.errorMessage}</div></div>` : ''}
    </div>`;

  document.getElementById('paymentDetailPanel').style.display = 'block';

  try {
    const history = await api(`/api/v1/payments/${paymentId}/history`);
    document.getElementById('paymentTimeline').innerHTML = (history || []).map((h) => `
      <div class="timeline-item">
        <div class="timeline-dot ${h.status}">${statusIcon(h.status)}</div>
        <div class="timeline-content">
          <div class="timeline-label">${h.status}</div>
          <div class="timeline-time">${fmtDate(h.changedAt)} by ${h.triggeredBy}</div>
          <div class="timeline-note">${h.notes || ''}</div>
        </div>
      </div>`).join('');
  } catch (_) {
    document.getElementById('paymentTimeline').innerHTML = '';
  }
}

function openAuditPayment(paymentId) {
  navigate('history');
  showPaymentDetail(paymentId);
}

/* Actions */
async function deleteBankAccount(id) {
  if (!confirm('Delete this bank account? Only allowed with zero balance.')) return;
  try {
    await api(`/api/v1/bank-accounts/${id}`, { method: 'DELETE' });
    await loadAll();
  } catch (e) {
    alert(e.message);
  }
}

/* Form helpers */
function showResult(id, message, ok) {
  const el = document.getElementById(id);
  el.textContent = message;
  el.className = 'verify-result ' + (ok ? 'show-ok' : 'show-err');
}

/* Event wiring */
document.getElementById('themeSwitch').addEventListener('click', toggleTheme);
document.querySelectorAll('.nav-item[data-page]').forEach((btn) => btn.addEventListener('click', () => navigate(btn.dataset.page)));

document.getElementById('historyFilter').addEventListener('change', renderHistory);

const topbarSearchInput = document.getElementById('globalSearch');
if (topbarSearchInput) {
  topbarSearchInput.addEventListener('input', (e) => {
    runSearch(e.target.value);
  });

  topbarSearchInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      runSearch(e.target.value, { navigateFirstMatch: true });
    }

    if (e.key === 'Escape') {
      e.preventDefault();
      e.target.value = '';
      runSearch('');
    }
  });
}

document.getElementById('verifyReceiverBtn').addEventListener('click', async () => {
  const form = document.getElementById('bankTransferForm');
  const accountNumber = form.destinationAccount.value;
  const ifscCode = form.destinationIfsc.value;
  const msg = document.getElementById('verifyReceiverMsg');

  if (!accountNumber || !ifscCode) {
    msg.textContent = 'Enter receiver account and IFSC first.';
    msg.className = 'verify-result show-err';
    return;
  }

  try {
    const r = await api(`/api/v1/payments/verify-receiver?accountNumber=${encodeURIComponent(accountNumber)}&ifscCode=${encodeURIComponent(ifscCode)}`);
    msg.textContent = r.message;
    msg.className = 'verify-result ' + (r.internalAccount ? 'show-ok' : 'show-info');
    if (r.internalAccount && !form.receiverName.value) form.receiverName.value = r.receiverName;
  } catch (e) {
    msg.textContent = e.message;
    msg.className = 'verify-result show-err';
  }
});

document.getElementById('bankAccountForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = e.target;
  try {
    await api('/api/v1/bank-accounts', {
      method: 'POST',
      body: JSON.stringify({
        accountHolderName: f.accountHolderName.value,
        bankName: f.bankName.value,
        accountNumber: f.accountNumber.value,
        ifscCode: f.ifscCode.value,
        accountType: f.accountType.value,
        bankPin: f.bankPin.value,
        openingBalance: Number(f.openingBalance.value)
      })
    });
    f.reset();
    showResult('bankAccountResult', 'Bank account linked successfully.', true);
    await loadAll();
  } catch (err) {
    showResult('bankAccountResult', err.message, false);
  }
});

document.getElementById('upiRecipientType').addEventListener('change', function () {
  const isMobile = this.value === 'mobile';
  document.getElementById('upiIdGroup').style.display = isMobile ? 'none' : '';
  document.getElementById('mobileGroup').style.display = isMobile ? '' : 'none';
  document.getElementById('upiIdInput').required = !isMobile;
  document.getElementById('mobileInput').required = isMobile;
});

document.getElementById('upiForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = e.target;
  const recipientType = document.getElementById('upiRecipientType').value;

  let destinationUpiId = '';
  if (recipientType === 'mobile') {
    const mobile = document.getElementById('mobileInput').value.trim();
    if (!/^\d{10}$/.test(mobile)) {
      showResult('upiResult', 'Enter a valid 10-digit mobile number.', false);
      return;
    }
    destinationUpiId = mobile + '@mobile';
  } else {
    destinationUpiId = document.getElementById('upiIdInput').value.trim();
    if (!destinationUpiId) {
      showResult('upiResult', 'Enter a UPI ID.', false);
      return;
    }
  }

  try {
    const p = await api('/api/v1/payments/pay-to-upi', {
      method: 'POST',
      body: JSON.stringify({
        sourceAccountId: Number(f.sourceAccountId.value),
        amount: Number(f.amount.value),
        currency: f.currency.value,
        destinationUpiId,
        appPin: f.appPin.value,
        bankPin: f.bankPin.value
      })
    });
    f.reset();
    document.getElementById('upiRecipientType').value = 'upi';
    document.getElementById('upiIdGroup').style.display = '';
    document.getElementById('mobileGroup').style.display = 'none';
    showResult('upiResult', `Payment ${p.status}. ID: ${p.id.slice(0, 8)}...`, true);
    await loadAll();
  } catch (err) {
    showResult('upiResult', err.message, false);
  }
});

document.getElementById('bankTransferForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = e.target;
  try {
    const p = await api('/api/v1/payments/pay-to-bank', {
      method: 'POST',
      body: JSON.stringify({
        sourceAccountId: Number(f.sourceAccountId.value),
        amount: Number(f.amount.value),
        currency: f.currency.value,
        receiverName: f.receiverName.value || null,
        destinationAccount: f.destinationAccount.value || null,
        destinationIfsc: f.destinationIfsc.value || null,
        reference: f.reference.value || null,
        appPin: f.appPin.value,
        bankPin: f.bankPin.value
      })
    });
    f.reset();
    showResult('bankTransferResult', `Transfer ${p.status}. ID: ${p.id.slice(0, 8)}...`, true);
    await loadAll();
  } catch (err) {
    showResult('bankTransferResult', err.message, false);
  }
});

async function loadAll() {
  state.user = await api('/api/v1/auth/me');
  if (!state.user) return;

  [state.accounts, state.payments, state.audits, state.summary] = await Promise.all([
    api('/api/v1/bank-accounts'),
    api('/api/v1/payments'),
    api('/api/v1/payments/audits'),
    api('/api/v1/dashboard/summary')
  ]);

  renderSidebar();
  renderKPIs();
  renderCharts();
  renderHistory();
  renderAudits();
  renderAccounts();
  renderDashboardRecent();
  renderProfile();
  runSearch(state.searchQuery);
}

const auditStatusFilter = document.getElementById('auditStatusFilter');
if (auditStatusFilter) auditStatusFilter.addEventListener('change', renderAudits);

window.deleteBankAccount = deleteBankAccount;
window.openAuditPayment = openAuditPayment;
window.showPaymentDetail = showPaymentDetail;
window.loadAll = loadAll;
window.navigate = navigate;

applyTheme(getTheme());
loadAll().catch((err) => console.error('Failed to load dashboard', err));

