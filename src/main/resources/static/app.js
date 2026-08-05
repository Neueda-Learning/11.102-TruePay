/* TruePay dashboard frontend */
const state = { user: null, accounts: [], payments: [], audits: [], summary: null, paymentLimits: null, charts: {}, searchQuery: '' };

/* Theme */
function getTheme() {
  return localStorage.getItem('theme') || 'dark';
}

function getColorBlindMode() {
  return localStorage.getItem('colorBlindMode') === '1';
}

function normalizeStatus(status) {
  const value = String(status || '').toUpperCase();
  if (value === 'COMPLETED') return 'SUCCESS';
  if (value === 'CREATED' || value === 'VALIDATED' || value === 'SENT') return 'PENDING';
  return value;
}

function statusText(status) {
  const normalized = normalizeStatus(status);
  return {
    SUCCESS: 'SUCCESS',
    FAILED: 'FAILED',
    PENDING: 'PENDING',
    CANCELLED: 'CANCELLED'
  }[normalized] || normalized || '-';
}

function statusIcon(status) {
  const normalized = normalizeStatus(status);
  return { SUCCESS: 'OK', FAILED: 'X', PENDING: 'INP', CANCELLED: 'CXL' }[normalized] || '...';
}

function statusCssClass(status) {
  return normalizeStatus(status);
}

function refreshThemeSensitiveViews() {
  if (state.summary) {
    renderCharts();
  }
  // Re-render status badges/tags so visual cues stay in sync after mode switches.
  renderHistory();
  renderAudits();
  renderDashboardRecent();
}

function getChartPalette() {
  if (document.body.classList.contains('color-blind')) {
    // Okabe-Ito inspired palette for broad color-vision accessibility.
    return {
      volume: '#0072B2',
      completed: '#0072B2',
      failed: '#D55E00',
      pending: '#CC79A7',
      fraud: '#E69F00',
      upi: '#56B4E9',
      bank: '#009E73'
    };
  }

  return {
    volume: '#7c5cfc',
    completed: '#22c55e',
    failed: '#ef4444',
    pending: '#7c5cfc',
    fraud: '#fb923c',
    upi: 'rgba(124,92,252,.8)',
    bank: 'rgba(79,140,255,.8)'
  };
}

function applyTheme(theme) {
  const dark = theme === 'dark';
  document.body.classList.toggle('dark', dark);
  document.getElementById('themeSwitch').classList.toggle('on', dark);
}

function applyColorBlindMode(enabled) {
  document.body.classList.toggle('color-blind', enabled);
  const switchEl = document.getElementById('colorBlindSwitch');
  if (switchEl) {
    switchEl.classList.toggle('on', enabled);
  }
}

function toggleTheme() {
  const next = getTheme() === 'dark' ? 'light' : 'dark';
  localStorage.setItem('theme', next);
  applyTheme(next);
  refreshThemeSensitiveViews();
}

function toggleColorBlindMode() {
  const enabled = !getColorBlindMode();
  localStorage.setItem('colorBlindMode', enabled ? '1' : '0');
  applyColorBlindMode(enabled);
  refreshThemeSensitiveViews();
}

const SIDEBAR_PREF_KEY = 'sidebarCollapsed';

function syncMenuToggleAria() {
  const menuToggles = document.querySelectorAll('[data-sidebar-toggle]');
  if (!menuToggles.length) return;

  const expanded = !document.body.classList.contains('sidebar-collapsed');

  menuToggles.forEach((toggle) => toggle.setAttribute('aria-expanded', String(expanded)));
}

function setSidebarCollapsed(collapsed) {
  document.body.classList.toggle('sidebar-collapsed', collapsed);
  localStorage.setItem(SIDEBAR_PREF_KEY, collapsed ? '1' : '0');
  syncMenuToggleAria();
}

function toggleSidebarMenu() {
  const collapsed = document.body.classList.contains('sidebar-collapsed');
  setSidebarCollapsed(!collapsed);
}

function initSidebarMenuState() {
  const collapsed = localStorage.getItem(SIDEBAR_PREF_KEY) === '1';
  setSidebarCollapsed(collapsed);
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
    analysis: ['Analysis', 'Trends, status, risk, and recent transaction insights.'],
    history: ['Payment History', 'All your transactions in one place.'],
    audits: ['Audit History', 'Every transaction event, actor, and status change.'],
    'pay-upi': ['Pay to UPI', 'Send money instantly via UPI.'],
    'pay-bank': ['Bank Transfer', 'Transfer funds to any bank account.'],
    accounts: ['Bank Accounts', 'Manage your linked accounts.'],
    'payment-limits': ['Payment Limits', 'Control daily, monthly, and per-transaction transfer caps.']
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

function findAccountById(accountId) {
  return state.accounts.find((account) => Number(account.id) === Number(accountId)) || null;
}

function getSearchTargetPage(query) {
  if (!query) return null;

  const pageKeywords = {
    dashboard: ['dashboard', 'overview', 'summary', 'kpi', 'balance', 'quick actions'],
    analysis: ['analysis', 'graphs', 'chart', 'risk', 'status', 'method split', 'recent transactions', 'day wise'],
    history: ['history', 'payment history', 'payments', 'transactions'],
    audits: ['audit', 'audit history', 'events', 'actor', 'timeline'],
    'pay-upi': ['upi', 'pay upi', 'send upi', 'mobile'],
    'pay-bank': ['bank transfer', 'transfer', 'ifsc', 'receiver'],
    accounts: ['account', 'bank accounts', 'ifsc', 'balance', 'linked'],
    'payment-limits': ['payment limit', 'daily limit', 'monthly limit', 'per transaction limit', 'transfer cap']
  };

  for (const [page, words] of Object.entries(pageKeywords)) {
    if (words.some((word) => word.includes(query) || query.includes(word))) {
      return page;
    }
  }

  if (state.payments.some((p) => paymentMatchesQuery(p, query))) return 'history';
  if (state.audits.some((a) => auditMatchesQuery(a, query))) return 'audits';
  if (state.accounts.some((a) => accountMatchesQuery(a, query))) return 'accounts';

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
  const palette = getChartPalette();
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
    data: {
      labels,
      datasets: [{
        label: 'Payments',
        data: lastSeven,
        borderColor: palette.volume,
        backgroundColor: 'transparent',
        borderWidth: 3,
        pointRadius: 3,
        pointHoverRadius: 4,
        tension: 0.35,
        borderDash: getColorBlindMode() ? [5, 3] : []
      }]
    },
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
    data: {
      labels: ['Completed', 'Failed', 'In Progress'],
      datasets: [{
        data: [completed, failed, inProgress],
        backgroundColor: [palette.completed, palette.failed, palette.pending],
        borderColor: getColorBlindMode() ? ['#ffffff', '#ffffff', '#ffffff'] : undefined,
        borderWidth: getColorBlindMode() ? 2 : 0
      }]
    },
    options: { responsive: true, cutout: '65%', plugins: { legend: { display: false } } }
  });

  const upiCount = state.payments.filter((p) => p.method === 'UPI').length;
  const bankCount = state.payments.filter((p) => p.method === 'BANK' || p.method === 'BANK_TRANSFER').length;
  state.charts.methodChart = new Chart(document.getElementById('methodChart'), {
    type: 'bar',
    data: {
      labels: ['UPI', 'Bank Transfer'],
      datasets: [{
        data: [upiCount, bankCount],
        backgroundColor: [palette.upi, palette.bank],
        borderColor: getColorBlindMode() ? ['#1f2937', '#1f2937'] : undefined,
        borderWidth: getColorBlindMode() ? 1 : 0
      }]
    },
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
    data: {
      labels: ['Completed', 'Failed', 'Fraud Alerts'],
      datasets: [{
        data: [completed, failed, s.fraudAlerts],
        backgroundColor: [palette.completed, palette.failed, palette.fraud],
        borderColor: getColorBlindMode() ? ['#1f2937', '#1f2937', '#1f2937'] : undefined,
        borderWidth: getColorBlindMode() ? 1 : 0
      }]
    },
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
    { label: 'Completed', val: completed, color: palette.completed, glyph: 'OK' },
    { label: 'Failed', val: failed, color: palette.failed, glyph: 'X' },
    { label: 'In Progress', val: inProgress, color: palette.pending, glyph: 'INP' }
  ].map((d) => `<div class="dist-item"><div class="dist-dot" style="background:${d.color}"></div><span class="dist-label">${d.glyph} ${d.label}</span><span class="dist-val">${d.val}</span><span class="dist-pct">${Math.round((d.val / total) * 100)}%</span></div>`).join('');
}

function renderHistory() {
  const filter = document.getElementById('historyFilter').value;
  const query = state.searchQuery;
  const byStatus = filter ? state.payments.filter((p) => normalizeStatus(p.status) === filter) : state.payments;
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
      <td><span class="status-badge ${statusCssClass(p.status)}">${statusIcon(p.status)} ${statusText(p.status)}</span></td>
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
      <td><span class="status-badge ${statusCssClass(p.status)}">${statusIcon(p.status)} ${statusText(p.status)}</span></td>
    </tr>`).join('');
}

function renderAudits() {
  const tbody = document.getElementById('auditTableBody');
  if (!tbody) return;

  const filter = document.getElementById('auditStatusFilter')?.value || '';
  const query = state.searchQuery;
  const byStatus = filter ? state.audits.filter((audit) => normalizeStatus(audit.status) === filter) : state.audits;
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
      <td><span class="status-badge ${statusCssClass(audit.status)}">${statusIcon(audit.status)} ${statusText(audit.status)}</span></td>
      <td>${audit.triggeredBy}</td>
      <td>
        <div>${audit.notes || '-'}</div>
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
      <div><div class="kpi-label">Status</div><span class="status-badge ${statusCssClass(payment.status)}">${statusIcon(payment.status)} ${statusText(payment.status)}</span></div>
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
        <div class="timeline-dot ${statusCssClass(h.status)}">${statusIcon(h.status)}</div>
        <div class="timeline-content">
          <div class="timeline-label">${statusText(h.status)}</div>
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
    await refreshAccountsAndSummary();
    showResult('bankAccountResult', 'Bank account deleted successfully.', true);
  } catch (e) {
    showResult('bankAccountResult', e.message, false);
  }
}

function setLimitInputEnabled(toggleId, inputId) {
  const toggle = document.getElementById(toggleId);
  const input = document.getElementById(inputId);
  if (!toggle || !input) return;

  const enabled = toggle.value === 'true';
  input.disabled = !enabled;
  input.required = enabled;
}

function syncLimitInputs() {
  setLimitInputEnabled('dailyEnabled', 'dailyLimit');
  setLimitInputEnabled('monthlyEnabled', 'monthlyLimit');
  setLimitInputEnabled('perTransactionEnabled', 'perTransactionLimit');
}

function renderPaymentLimits() {
  if (!state.paymentLimits) return;
  const form = document.getElementById('paymentLimitsForm');
  if (!form) return;

  form.dailyEnabled.value = String(state.paymentLimits.dailyEnabled);
  form.monthlyEnabled.value = String(state.paymentLimits.monthlyEnabled);
  form.perTransactionEnabled.value = String(state.paymentLimits.perTransactionEnabled);

  form.dailyLimit.value = state.paymentLimits.dailyLimit ?? '';
  form.monthlyLimit.value = state.paymentLimits.monthlyLimit ?? '';
  form.perTransactionLimit.value = state.paymentLimits.perTransactionLimit ?? '';

  syncLimitInputs();
}

async function refreshAccountsAndSummary() {
  const [accountsResult, summaryResult] = await Promise.allSettled([
    api('/api/v1/bank-accounts'),
    api('/api/v1/dashboard/summary')
  ]);

  if (accountsResult.status === 'fulfilled') {
    state.accounts = accountsResult.value || [];
  }

  if (summaryResult.status === 'fulfilled') {
    state.summary = summaryResult.value;
  }

  renderKPIs();
  renderAccounts();
  renderDashboardRecent();
  if (state.summary) {
    renderCharts();
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
const colorBlindSwitch = document.getElementById('colorBlindSwitch');
if (colorBlindSwitch) {
  colorBlindSwitch.addEventListener('click', toggleColorBlindMode);
}
document.querySelectorAll('.nav-item[data-page]').forEach((btn) => btn.addEventListener('click', () => navigate(btn.dataset.page)));

const menuToggles = document.querySelectorAll('[data-sidebar-toggle]');

menuToggles.forEach((toggle) => {
  toggle.addEventListener('click', toggleSidebarMenu);
});


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


async function submitBankAccountForm(form) {
  try {
    await api('/api/v1/bank-accounts', {
      method: 'POST',
      body: JSON.stringify({
        bankName: form.bankName.value,
        accountNumber: form.accountNumber.value,
        ifscCode: form.ifscCode.value,
        accountType: form.accountType.value,
        bankPin: form.bankPin.value,
        openingBalance: Number(form.openingBalance.value)
      })
    });
    form.reset();
    showResult('bankAccountResult', 'Bank account linked successfully.', true);
    try {
      await refreshAccountsAndSummary();
    } catch (refreshErr) {
      console.warn('Bank account linked, but account refresh was partial.', refreshErr);
    }
  } catch (err) {
    showResult('bankAccountResult', err.message, false);
  }
}

const bankAccountForm = document.getElementById('bankAccountForm');
if (bankAccountForm) {
  bankAccountForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    await submitBankAccountForm(e.target);
  });
}

const linkBankAccountButton = document.getElementById('linkBankAccountButton');
if (bankAccountForm && linkBankAccountButton) {
  linkBankAccountButton.addEventListener('click', async () => {
    if (typeof bankAccountForm.reportValidity === 'function' && !bankAccountForm.reportValidity()) {
      return;
    }

    await submitBankAccountForm(bankAccountForm);
  });
}

document.getElementById('upiRecipientType').addEventListener('change', function () {
  const isMobile = this.value === 'mobile';
  document.getElementById('upiIdGroup').style.display = isMobile ? 'none' : '';
  document.getElementById('mobileGroup').style.display = isMobile ? '' : 'none';
  document.getElementById('upiIdInput').required = !isMobile;
  document.getElementById('mobileInput').required = isMobile;
});

async function refreshPaymentData() {
  const [accountsResult, paymentsResult, auditsResult, summaryResult, limitsResult] = await Promise.allSettled([
    api('/api/v1/bank-accounts'),
    api('/api/v1/payments'),
    api('/api/v1/payments/audits'),
    api('/api/v1/dashboard/summary'),
    api('/api/v1/payment-limits')
  ]);

  if (accountsResult.status === 'fulfilled') state.accounts = accountsResult.value || [];
  if (paymentsResult.status === 'fulfilled') state.payments = paymentsResult.value || [];
  if (auditsResult.status === 'fulfilled') state.audits = auditsResult.value || [];
  if (summaryResult.status === 'fulfilled') state.summary = summaryResult.value;
  if (limitsResult.status === 'fulfilled') state.paymentLimits = limitsResult.value;

  renderKPIs();
  renderCharts();
  renderHistory();
  renderAudits();
  renderAccounts();
  renderDashboardRecent();
  renderPaymentLimits();
}

document.getElementById('upiForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = e.target;
  const recipientType = document.getElementById('upiRecipientType').value;

  let receiver = '';
  let receiverType = 'UPI';
  if (recipientType === 'mobile') {
    const mobile = document.getElementById('mobileInput').value.trim();
    if (!/^\d{10}$/.test(mobile)) {
      showResult('upiResult', 'Enter a valid 10-digit mobile number.', false);
      return;
    }
    receiver = mobile;
    receiverType = 'MOBILE_NUMBER';
  } else {
    receiver = document.getElementById('upiIdInput').value.trim();
    if (!receiver) {
      showResult('upiResult', 'Enter a UPI ID.', false);
      return;
    }
  }

  const source = findAccountById(f.sourceAccountId.value);
  if (!source) {
    showResult('upiResult', 'Select a valid source account.', false);
    return;
  }

  try {
    const p = await api('/api/v1/payments/upi', {
      method: 'POST',
      body: JSON.stringify({
        sourceAccount: source.accountNumber,
        receiverType,
        receiver,
        amount: Number(f.amount.value),
        currency: f.currency.value,
        bankPin: f.bankPin.value
      })
    });
    f.reset();
    document.getElementById('upiRecipientType').value = 'upi';
    document.getElementById('upiIdGroup').style.display = '';
    document.getElementById('mobileGroup').style.display = 'none';
    const paymentSucceeded = p.status === 'SUCCESS';
    const paymentMessage = paymentSucceeded
      ? `Payment completed successfully. ID: ${String(p.id).slice(0, 8)}...`
      : (p.failureReason || p.errorMessage || `Payment ${p.status}.`);
    showResult('upiResult', paymentMessage, paymentSucceeded);
    try {
      await refreshPaymentData();
    } catch (refreshErr) {
      console.warn('UPI payment processed, but payment data refresh was partial.', refreshErr);
    }
  } catch (err) {
    showResult('upiResult', err.message, false);
  }
});

document.getElementById('bankTransferForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = e.target;
  const destinationAccount = f.destinationAccount ? f.destinationAccount.value : null;
  const source = findAccountById(f.sourceAccountId.value);
  if (!source) {
    showResult('bankTransferResult', 'Select a valid source account.', false);
    return;
  }

  try {
    const p = await api('/api/v1/payments/bank-transfer', {
      method: 'POST',
      body: JSON.stringify({
        sourceAccount: source.accountNumber,
        destinationAccount,
        amount: Number(f.amount.value),
        currency: f.currency.value,
        bankPin: f.bankPin.value
      })
    });
    f.reset();
    const paymentSucceeded = p.status === 'SUCCESS';
    const paymentMessage = paymentSucceeded
      ? `Transfer completed successfully. ID: ${String(p.id).slice(0, 8)}...`
      : (p.failureReason || p.errorMessage || `Transfer ${p.status}.`);
    showResult('bankTransferResult', paymentMessage, paymentSucceeded);
    try {
      await refreshPaymentData();
    } catch (refreshErr) {
      console.warn('Bank transfer processed, but payment data refresh was partial.', refreshErr);
    }
  } catch (err) {
    showResult('bankTransferResult', err.message, false);
  }
});

const paymentLimitsForm = document.getElementById('paymentLimitsForm');
if (paymentLimitsForm) {
  paymentLimitsForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const form = e.target;
    syncLimitInputs();

    try {
      const response = await api('/api/v1/payment-limits', {
        method: 'PUT',
        body: JSON.stringify({
          dailyEnabled: form.dailyEnabled.value === 'true',
          dailyLimit: form.dailyEnabled.value === 'true' ? Number(form.dailyLimit.value) : null,
          monthlyEnabled: form.monthlyEnabled.value === 'true',
          monthlyLimit: form.monthlyEnabled.value === 'true' ? Number(form.monthlyLimit.value) : null,
          perTransactionEnabled: form.perTransactionEnabled.value === 'true',
          perTransactionLimit: form.perTransactionEnabled.value === 'true' ? Number(form.perTransactionLimit.value) : null
        })
      });

      state.paymentLimits = response;
      renderPaymentLimits();
      showResult('paymentLimitsResult', 'Payment limits updated successfully.', true);
    } catch (err) {
      showResult('paymentLimitsResult', err.message, false);
    }
  });

  ['dailyEnabled', 'monthlyEnabled', 'perTransactionEnabled'].forEach((id) => {
    const el = document.getElementById(id);
    if (el) {
      el.addEventListener('change', syncLimitInputs);
    }
  });

  syncLimitInputs();
}

async function loadAll() {
  state.user = await api('/api/v1/auth/me');
  if (!state.user) return;

  const [accountsResult, paymentsResult, auditsResult, summaryResult, limitsResult] = await Promise.allSettled([
    api('/api/v1/bank-accounts'),
    api('/api/v1/payments'),
    api('/api/v1/payments/audits'),
    api('/api/v1/dashboard/summary'),
    api('/api/v1/payment-limits')
  ]);

  if (accountsResult.status === 'fulfilled') state.accounts = accountsResult.value || [];
  if (paymentsResult.status === 'fulfilled') state.payments = paymentsResult.value || [];
  if (auditsResult.status === 'fulfilled') state.audits = auditsResult.value || [];
  if (summaryResult.status === 'fulfilled') state.summary = summaryResult.value;
  if (limitsResult.status === 'fulfilled') state.paymentLimits = limitsResult.value;

  renderSidebar();
  renderKPIs();
  if (state.summary) {
    renderCharts();
  }
  renderHistory();
  renderAudits();
  renderAccounts();
  renderDashboardRecent();
  renderPaymentLimits();
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
applyColorBlindMode(getColorBlindMode());
initSidebarMenuState();
loadAll().catch((err) => console.error('Failed to load dashboard', err));



