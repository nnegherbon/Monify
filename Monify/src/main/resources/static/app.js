const API_BASE_URL = `${window.location.origin}/api`;
let currentUser = null;
let allTransactions = [];
let pendingPayables = [];
let pendingReceivables = [];
let categories = [];
let accounts = [];
let creditCards = [];
let limits = [];
let gastosChartInstance = null;
let relChartExpense = null;
let relChartIncome = null;
let relCategoryEvolutionChart = null;
let relFlowBarChart = null;
let relFlowEvolutionChart = null;
let relViewMode = 'categories';
let editingTransactionId = null;
let editingLimitId = null;
let lancSearchTerm = '';
let currentPeriod = 'diario';
const reportFilters = {
    types: new Set(['INCOME', 'EXPENSE']),
    categoryIds: new Set(),
    originKeys: new Set()
};

const monthState = { lanc: new Date(), rel: new Date(), lim: new Date() };
const MONTHS = ['Janeiro','Fevereiro','Março','Abril','Maio','Junho','Julho','Agosto','Setembro','Outubro','Novembro','Dezembro'];

// ===== INIT =====
document.addEventListener('DOMContentLoaded', () => {
    checkAuthStatus();
    setGreeting();
    renderMonthLabels();
    setupDropdownClose();
    const today = new Date().toISOString().split('T')[0];
    const qDate = document.getElementById('q-date');
    if (qDate) qDate.value = today;
    const limMonth = document.getElementById('lim-month');
    if (limMonth) limMonth.value = today.slice(0,7);
    document.addEventListener('keydown', handleGlobalKeydown);
});

function setGreeting() {
    const h = new Date().getHours();
    const el = document.getElementById('greeting-time');
    if (el) el.textContent = h < 12 ? 'Bom dia,' : h < 18 ? 'Boa tarde,' : 'Boa noite,';
}

function renderMonthLabels() {
    ['lanc','rel','lim'].forEach(k => {
        const el = document.getElementById(`${k}-month-label`);
        if (el) el.textContent = `${MONTHS[monthState[k].getMonth()]} ${monthState[k].getFullYear()}`;
    });
    const dashboardLimitsTitle = document.getElementById('dashboard-limits-title');
    if (dashboardLimitsTitle) {
        dashboardLimitsTitle.textContent = `Limites de gastos de ${MONTHS[new Date().getMonth()]}`;
    }
}

function changeMonth(dir, key) {
    monthState[key] = new Date(monthState[key].getFullYear(), monthState[key].getMonth() + dir, 1);
    renderMonthLabels();
    if (key === 'lim') {
        updateLimEmptyMsg();
        renderLimites();
    }
    if (key === 'lanc') loadLancamentos();
    if (key === 'rel') loadRelatorios();
}

function updateLimEmptyMsg() {
    const el = document.getElementById('lim-empty-msg');
    if (el) el.textContent = `Nenhum limite de gasto definido em ${MONTHS[monthState.lim.getMonth()]} ${monthState.lim.getFullYear()}`;
}

// ===== AUTH =====
async function handleLogin(e) {
    e.preventDefault();
    const email = document.getElementById('login-email').value;
    const password = document.getElementById('login-password').value;
    try {
        const res = await fetch(`${API_BASE_URL}/users/login`, {
            method:'POST', headers:{'Content-Type':'application/json'},
            body: JSON.stringify({ email, password })
        });
        const data = await res.json();
        if (res.ok) {
            currentUser = data;
            localStorage.setItem('user', JSON.stringify(data));
            showApp();
            await fetchAppData();
            loadDashboard();
        } else alert('Erro: ' + data.error);
    } catch { alert('Erro ao fazer login. Verifique se o backend esta rodando em http://localhost:8080/api'); }
}

async function handleRegister(e) {
    e.preventDefault();
    const name = document.getElementById('register-name').value;
    const email = document.getElementById('register-email').value;
    const password = document.getElementById('register-password').value;
    try {
        const res = await fetch(`${API_BASE_URL}/users/register`, {
            method:'POST', headers:{'Content-Type':'application/json'},
            body: JSON.stringify({ name, email, password })
        });
        const data = await res.json();
        if (res.ok) {
            alert('Cadastro realizado! Faça login para continuar.');
            switchTab('login');
            document.getElementById('login-email').value = email;
        } else alert('Erro: ' + data.error);
    } catch { alert('Erro ao cadastrar'); }
}

function logout() {
    currentUser = null; allTransactions = [];
    pendingPayables = []; pendingReceivables = [];
    accounts = []; creditCards = []; limits = [];
    localStorage.removeItem('user');
    document.getElementById('app-page').style.display = 'none';
    document.getElementById('auth-page').style.display = 'flex';
    document.getElementById('auth-page').style.flexDirection = 'column';
    closeProfileMenu();
}

function checkAuthStatus() {
    const u = localStorage.getItem('user');
    if (u) {
        currentUser = JSON.parse(u);
        showApp();
        fetchAppData().then(() => loadDashboard());
    }
}

function showApp() {
    document.getElementById('auth-page').style.display = 'none';
    document.getElementById('app-page').style.display = 'block';
    const el = document.getElementById('greeting-name');
    if (el && currentUser) el.textContent = (currentUser.name || 'Usuário') + '!';
    syncProfileForm();
}

function switchTab(tab) {
    document.querySelectorAll('.auth-form').forEach(f => f.classList.remove('active'));
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    document.getElementById(tab === 'login' ? 'login-form' : 'register-form').classList.add('active');
    document.querySelectorAll('.tab-btn')[tab === 'login' ? 0 : 1].classList.add('active');
}

// ===== FETCH DATA =====
async function fetchAppData() {
    await Promise.all([
        loadCategories(),
        fetchAllTransactions(),
        fetchPendingTransactions(),
        fetchAccounts(),
        fetchCreditCards(),
        fetchLimitsForMonth(getMonthKey(new Date()))
    ]);
}

async function fetchAllTransactions() {
    if (!currentUser) return;
    try {
        const res = await fetch(`${API_BASE_URL}/transactions/user/${currentUser.id}`);
        allTransactions = await res.json();
        if (!Array.isArray(allTransactions)) allTransactions = [];
    } catch(e) { console.error(e); allTransactions = []; }
}

async function fetchPendingTransactions() {
    if (!currentUser) return;
    try {
        const [payablesResponse, receivablesResponse] = await Promise.all([
            fetch(`${API_BASE_URL}/transactions/user/${currentUser.id}/pending?type=EXPENSE`),
            fetch(`${API_BASE_URL}/transactions/user/${currentUser.id}/pending?type=INCOME`)
        ]);
        if (!payablesResponse.ok || !receivablesResponse.ok) {
            throw new Error('Nao foi possivel carregar os lancamentos pendentes');
        }
        const [payables, receivables] = await Promise.all([
            payablesResponse.json(),
            receivablesResponse.json()
        ]);
        pendingPayables = Array.isArray(payables) ? payables : [];
        pendingReceivables = Array.isArray(receivables) ? receivables : [];
    } catch (error) {
        console.error(error);
        pendingPayables = [];
        pendingReceivables = [];
    }
}

async function loadCategories() {
    try {
        const res = await fetch(`${API_BASE_URL}/categories`);
        if (!res.ok) throw new Error('Não foi possível carregar as categorias');
        const data = await res.json();
        categories = Array.isArray(data)
            ? data.filter(category =>
                Number.isInteger(Number(category.id)) &&
                String(category.name || '').trim()
            )
            : [];
    } catch(e) {
        console.error(e);
        categories = [];
    } finally {
        syncCategoryConsumers();
    }
}

function syncCategoryConsumers() {
    populateModalCategories();
    populateLancamentoCategoryFilters();
    populateReportFilterOptions();
    populateLimitCategories();
}

async function fetchAccounts() {
    if (!currentUser) return;
    try {
        const res = await fetch(`${API_BASE_URL}/users/${currentUser.id}/accounts`);
        const data = await res.json();
        accounts = Array.isArray(data) ? data : [];
    } catch(e) { console.error(e); accounts = []; }
}

async function fetchCreditCards() {
    if (!currentUser) return;
    try {
        const res = await fetch(`${API_BASE_URL}/users/${currentUser.id}/cards`);
        const data = await res.json();
        creditCards = Array.isArray(data) ? data : [];
    } catch(e) { console.error(e); creditCards = []; }
}

async function fetchLimitsForMonth(monthKey) {
    if (!currentUser) return;
    try {
        const res = await fetch(`${API_BASE_URL}/users/${currentUser.id}/limits?month=${monthKey}&type=EXPENSE`);
        const data = await res.json();
        limits = Array.isArray(data) ? data : [];
    } catch(e) { console.error(e); limits = []; }
}

// ===== NAVIGATION =====
function showSection(section, event) {
    if (!currentUser) return;
    document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
    document.getElementById(`${section}-section`).classList.add('active');
    document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
    const target = event && event.target ? event.target : null;
    if (target && target.classList.contains('nav-btn')) target.classList.add('active');
    else if (section === 'dashboard') document.querySelector('.nav-btn')?.classList.add('active');
    if (section === 'dashboard') loadDashboard();
    if (section === 'lancamentos') loadLancamentos();
    if (section === 'relatorios') loadRelatorios();
    if (section === 'limites') renderLimites();
}

function goHome() {
    closeProfileMenu();
    showSection('dashboard', { target: document.querySelector('.nav-btn') });
}

// ===== DASHBOARD =====
async function loadDashboard() {
    if (!currentUser) return;
    await Promise.all([
        fetchAllTransactions(),
        fetchPendingTransactions(),
        fetchAccounts(),
        fetchCreditCards(),
        fetchLimitsForMonth(getMonthKey(new Date()))
    ]);

    const now = new Date();
    const monthTx = allTransactions.filter(t => isTransactionCompleted(t)).filter(t => {
        const d = parseLocalDate(t.date);
        return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear();
    });

    const income = monthTx.filter(t => t.type === 'INCOME').reduce((a,t) => a+t.amount, 0);
    const expense = monthTx.filter(t => t.type === 'EXPENSE').reduce((a,t) => a+t.amount, 0);
    const transactionBalance = income - expense;
    const accountBalance = accounts.reduce((total, account) => total + Number(account.balance || 0), 0);
    const balance = accounts.length ? accountBalance : transactionBalance;

    setElText('balance-val', formatCurrency(balance));
    setElText('hero-income', formatCurrency(income));
    setElText('hero-expense', formatCurrency(expense));
    setElText('faturas-val', formatCurrency(creditCards.reduce((total, card) => total + Number(card.usedAmount || 0), 0)));

    renderAccountsCard();
    renderCardsCard();
    renderPendingPayables();
    renderPendingReceivables();
    renderGastosCard();
    renderLimitesCard();
}

function setElText(id, text) {
    const el = document.getElementById(id);
    if (el) el.textContent = text;
}

function getMonthKey(date) {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

// ===== CARD TOGGLE =====
function toggleCard(bodyId, btn) {
    const body = document.getElementById(bodyId);
    if (!body) return;
    const isCollapsed = body.classList.contains('collapsed');
    if (isCollapsed) {
        body.classList.remove('collapsed');
        btn.style.transform = '';
    } else {
        body.classList.add('collapsed');
        btn.style.transform = 'rotate(180deg)';
    }
    btn.setAttribute('aria-expanded', String(isCollapsed));
}

// ===== VISIBILITY TOGGLE (eye button) =====
function toggleVisibility(elId) {
    const el = document.getElementById(elId);
    if (!el) return;
    const isHidden = el.classList.toggle('hidden');
    const eyeBtn = document.querySelector(`[onclick="toggleVisibility('${elId}')"]`);
    if (eyeBtn) {
        eyeBtn.innerHTML = isHidden ? iconEyeOff() : iconEye();
        eyeBtn.setAttribute('aria-label', isHidden ? 'Mostrar valor' : 'Ocultar valor');
    }
}

function iconEye() {
    return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/></svg>`;
}

function iconEyeOff() {
    return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3l18 18"/><path d="M10.6 10.6A2 2 0 0 0 12 14a2 2 0 0 0 1.4-.6"/><path d="M9.9 5.3A10.7 10.7 0 0 1 12 5c6.5 0 10 7 10 7a18.7 18.7 0 0 1-3 4.1"/><path d="M6.1 6.8C3.5 8.6 2 12 2 12s3.5 7 10 7a10.6 10.6 0 0 0 5-1.2"/></svg>`;
}

function iconUser() {
    return `<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="4"/><path d="M4 21c1.7-4 4.4-6 8-6s6.3 2 8 6"/></svg>`;
}

function iconSettings() {
    return `<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a8 8 0 0 0 .1-1l2-1.5-2-3.5-2.4 1a7.8 7.8 0 0 0-1.7-1L15 6.5h-6L8.6 9a7.8 7.8 0 0 0-1.7 1l-2.4-1-2 3.5 2 1.5a8 8 0 0 0 0 2l-2 1.5 2 3.5 2.4-1a7.8 7.8 0 0 0 1.7 1l.4 2.5h6l.4-2.5a7.8 7.8 0 0 0 1.7-1l2.4 1 2-3.5-2.1-1.5Z"/></svg>`;
}

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.eye-btn').forEach(btn => {
        if (!btn.innerHTML.includes('<svg')) btn.innerHTML = iconEye();
        btn.setAttribute('aria-label', 'Ocultar valor');
    });
    const profileBtn = document.getElementById('profile-menu-btn');
    if (profileBtn) profileBtn.innerHTML = iconUser();
    const settingsBtn = document.getElementById('settings-menu-btn');
    if (settingsBtn) settingsBtn.innerHTML = iconSettings();
});

// ===== ACCOUNTS / CARDS =====
function renderAccountsCard() {
    const body = document.getElementById('accounts-list');
    if (!body) return;

    if (!accounts.length) {
        body.innerHTML = `<div class="card-empty-state">
            <span class="card-empty-icon">$</span>
            <p>Adicione sua primeira conta</p>
        </div>`;
        return;
    }

    body.innerHTML = accounts.map(account => `<div class="account-item">
        <div>
            <div class="account-name">${escapeHtml(account.name)}</div>
            <div class="account-type">${formatAccountType(account.type)}</div>
        </div>
        <strong>${formatCurrency(account.balance)}</strong>
        <button class="mini-action danger" onclick="deleteAccount(${account.id})" title="Excluir conta">x</button>
    </div>`).join('');
}

function renderCardsCard() {
    const body = document.getElementById('cards-list');
    if (!body) return;

    if (!creditCards.length) {
        body.innerHTML = `<div class="card-empty-state">
            <span class="card-empty-icon card-icon-sm">CARD</span>
            <p>Adicione o seu primeiro cartao</p>
        </div>`;
        return;
    }

    body.innerHTML = creditCards.map(card => `<div class="credit-card-preview">
        <div class="credit-card-brand">${escapeHtml(card.brand)}</div>
        <div class="credit-card-number">**** **** **** ${escapeHtml(card.lastFour)}</div>
        <div class="credit-card-row">
            <span>${escapeHtml(card.name)}</span>
            <strong>${formatCurrency(card.usedAmount || 0)} / ${formatCurrency(card.limitAmount)}</strong>
        </div>
        <button class="mini-action card-delete" onclick="deleteCreditCard(${card.id})" title="Excluir cartao">x</button>
    </div>`).join('');
}

function openAccountsModal() {
    renderAccountsModalList();
    document.getElementById('accounts-modal').style.display = 'flex';
}

function closeAccountsModal() {
    document.getElementById('accounts-modal').style.display = 'none';
}

function openCardsModal() {
    renderCardsModalList();
    document.getElementById('cards-modal').style.display = 'flex';
}

function closeCardsModal() {
    document.getElementById('cards-modal').style.display = 'none';
}

function renderAccountsModalList() {
    const list = document.getElementById('accounts-modal-list');
    if (!list) return;
    list.innerHTML = accounts.length ? accounts.map(account => `<div class="modal-list-item">
        <span>${escapeHtml(account.name)} <small>${formatAccountType(account.type)}</small></span>
        <strong>${formatCurrency(account.balance)}</strong>
        <button type="button" class="mini-action danger" onclick="deleteAccount(${account.id})">x</button>
    </div>`).join('') : '<p class="modal-empty">Nenhuma conta cadastrada.</p>';
}

function renderCardsModalList() {
    const list = document.getElementById('cards-modal-list');
    if (!list) return;
    list.innerHTML = creditCards.length ? creditCards.map(card => `<div class="modal-list-item">
        <span>${escapeHtml(card.name)} <small>${escapeHtml(card.brand)} final ${escapeHtml(card.lastFour)}</small></span>
        <strong>${formatCurrency(card.usedAmount || 0)} / ${formatCurrency(card.limitAmount)}</strong>
        <button type="button" class="mini-action danger" onclick="deleteCreditCard(${card.id})">x</button>
    </div>`).join('') : '<p class="modal-empty">Nenhum cartao cadastrado.</p>';
}

async function handleAddAccount(e) {
    e.preventDefault();
    const name = document.getElementById('account-name').value.trim();
    const type = document.getElementById('account-type').value;
    const balance = parseFloat(document.getElementById('account-balance').value || '0');
    if (!name || !type || Number.isNaN(balance)) return;

    try {
        const res = await fetch(`${API_BASE_URL}/users/${currentUser.id}/accounts`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, type, balance })
        });
        if (!res.ok) throw await res.json();
        e.target.reset();
        await fetchAccounts();
        renderAccountsCard();
        renderAccountsModalList();
        loadDashboard();
    } catch(err) {
        alert('Erro: ' + (err.error || 'nao foi possivel salvar a conta'));
    }
}

async function deleteAccount(id) {
    if (!confirm('Excluir esta conta?')) return;
    try {
        const res = await fetch(`${API_BASE_URL}/users/${currentUser.id}/accounts/${id}`, { method: 'DELETE' });
        if (!res.ok) throw await res.json();
        await fetchAccounts();
        renderAccountsCard();
        renderAccountsModalList();
        loadDashboard();
    } catch(err) {
        alert('Erro: ' + (err.error || 'nao foi possivel excluir a conta'));
    }
}

async function handleAddCard(e) {
    e.preventDefault();
    const name = document.getElementById('card-name').value.trim();
    const brand = document.getElementById('card-brand').value.trim();
    const limitAmount = parseFloat(document.getElementById('card-limit').value || '0');
    const lastFour = document.getElementById('card-last-four').value.trim();
    if (!name || !brand || !/^\d{4}$/.test(lastFour) || Number.isNaN(limitAmount)) {
        alert('Preencha todos os campos. O final precisa ter 4 digitos.');
        return;
    }

    try {
        const res = await fetch(`${API_BASE_URL}/users/${currentUser.id}/cards`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, brand, limitAmount, lastFour })
        });
        if (!res.ok) throw await res.json();
        e.target.reset();
        await fetchCreditCards();
        renderCardsCard();
        renderCardsModalList();
        loadDashboard();
    } catch(err) {
        alert('Erro: ' + (err.error || 'nao foi possivel salvar o cartao'));
    }
}

async function deleteCreditCard(id) {
    if (!confirm('Excluir este cartao?')) return;
    try {
        const res = await fetch(`${API_BASE_URL}/users/${currentUser.id}/cards/${id}`, { method: 'DELETE' });
        if (!res.ok) throw await res.json();
        await fetchCreditCards();
        renderCardsCard();
        renderCardsModalList();
        loadDashboard();
    } catch(err) {
        alert('Erro: ' + (err.error || 'nao foi possivel excluir o cartao'));
    }
}

function formatAccountType(type) {
    const map = { CHECKING: 'Conta corrente', SAVINGS: 'Poupanca', CASH: 'Dinheiro', INVESTMENT: 'Investimento' };
    return map[type] || type;
}

// ===== CONTAS A PAGAR =====
function renderContasPagar() {
    const body = document.getElementById('pagar-body');
    if (!body) return;
    const today = new Date();
    today.setHours(0,0,0,0);

    const overdue = allTransactions.filter(t => {
        if (t.type !== 'EXPENSE') return false;
        const d = parseLocalDate(t.date);
        d.setHours(0,0,0,0);
        return d < today;
    });

    const upcoming = allTransactions.filter(t => {
        if (t.type !== 'EXPENSE') return false;
        const d = parseLocalDate(t.date);
        d.setHours(0,0,0,0);
        return d > today;
    });

    if (!overdue.length && !upcoming.length) {
        body.innerHTML = '<div class="card-empty-state alone"><p>No momento você não possui contas a pagar</p></div>';
        return;
    }

    let html = '';
    if (overdue.length) {
        html += `<div class="pagar-overdue-banner">⚠ Contas a pagar atrasadas (${overdue.length})</div>`;
        overdue.forEach(t => {
            html += buildPagarItem(t, true);
        });
    }
    upcoming.forEach(t => {
        html += buildPagarItem(t, false);
    });
    body.innerHTML = html;
}

function buildPagarItem(t, isOverdue) {
    return `<div class="pagar-item">
        <div class="pagar-item-icon">${t.categoryIcon || '🏦'}</div>
        <div class="pagar-item-info">
            <div class="pagar-item-name">${t.description}</div>
            <div class="pagar-item-sub">${t.categoryName || ''} · Venc. ${formatDate(t.date)}</div>
        </div>
        <div class="pagar-item-amount" style="color:${isOverdue ? 'var(--red)' : '#f59e0b'}">
            R$ ${Math.abs(t.amount).toFixed(2).replace('.',',')}
        </div>
    </div>`;
}

function renderContasReceber() {
    const body = document.getElementById('receber-body');
    if (!body) return;

    const today = new Date();
    today.setHours(0,0,0,0);
    const receivables = allTransactions
        .filter(t => t.type === 'INCOME')
        .filter(t => {
            const d = parseLocalDate(t.date);
            d.setHours(0,0,0,0);
            return d >= today;
        })
        .sort((a,b) => parseLocalDate(a.date) - parseLocalDate(b.date))
        .slice(0, 4);

    if (!receivables.length) {
        body.innerHTML = '<div class="card-empty-state alone"><p>No momento voce nao possui contas a receber pendentes</p></div><button class="manage-btn" onclick="openQuickAdd(\'INCOME\')">Cadastrar conta a receber</button>';
        return;
    }

    body.innerHTML = receivables.map(t => `<div class="pagar-item income-item">
        <div class="pagar-item-icon">${t.categoryIcon || '+'}</div>
        <div class="pagar-item-info">
            <div class="pagar-item-name">${escapeHtml(t.description)}</div>
            <div class="pagar-item-sub">${escapeHtml(t.categoryName || '')} - Prev. ${formatDate(t.date)}</div>
        </div>
        <div class="pagar-item-amount" style="color:var(--green)">
            ${formatCurrency(t.amount)}
        </div>
    </div>`).join('') + '<button class="manage-btn" onclick="openQuickAdd(\'INCOME\')">Cadastrar conta a receber</button>';
}

// ===== MAIORES GASTOS CARD =====
function renderPendingPayables() {
    const body = document.getElementById('pagar-body');
    if (!body) return;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const pending = pendingPayables
        .filter(transaction => transaction.status === 'PENDING')
        .sort((a, b) => parseLocalDate(a.date) - parseLocalDate(b.date));

    if (!pending.length) {
        body.innerHTML = '<div class="card-empty-state alone"><p>No momento voce nao possui contas a pagar</p></div>' +
            '<button class="manage-btn" onclick="openQuickAdd(\'EXPENSE\',true)">Cadastrar conta a pagar</button>';
        return;
    }

    const overdueCount = pending.filter(transaction => parseLocalDate(transaction.date) < today).length;
    const banner = overdueCount
        ? `<div class="pagar-overdue-banner">Contas a pagar atrasadas (${overdueCount})</div>`
        : '';
    body.innerHTML = banner + pending.map(transaction => {
        const overdue = parseLocalDate(transaction.date) < today;
        return buildPendingItem(transaction, overdue, 'Pagar');
    }).join('') + '<button class="manage-btn" onclick="openQuickAdd(\'EXPENSE\',true)">Cadastrar conta a pagar</button>';
}

function renderPendingReceivables() {
    const body = document.getElementById('receber-body');
    if (!body) return;
    const pending = pendingReceivables
        .filter(transaction => transaction.status === 'PENDING')
        .sort((a, b) => parseLocalDate(a.date) - parseLocalDate(b.date))
        .slice(0, 4);

    if (!pending.length) {
        body.innerHTML = '<div class="card-empty-state alone"><p>No momento voce nao possui contas a receber pendentes</p></div>' +
            '<button class="manage-btn" onclick="openQuickAdd(\'INCOME\',true)">Cadastrar conta a receber</button>';
        return;
    }

    body.innerHTML = pending.map(transaction => buildPendingItem(transaction, false, 'Receber')).join('') +
        '<button class="manage-btn" onclick="openQuickAdd(\'INCOME\',true)">Cadastrar conta a receber</button>';
}

function buildPendingItem(transaction, overdue, actionLabel) {
    const isExpense = transaction.type === 'EXPENSE';
    const dateLabel = isExpense ? 'Venc.' : 'Prev.';
    const amountColor = isExpense ? overdue ? 'var(--red)' : '#f59e0b' : 'var(--green)';
    return `<div class="pagar-item ${isExpense ? '' : 'income-item'}">
        <div class="pagar-item-icon">${escapeHtml(transaction.categoryIcon || '-')}</div>
        <div class="pagar-item-info">
            <div class="pagar-item-name">${escapeHtml(transaction.description)}</div>
            <div class="pagar-item-sub">${escapeHtml(transaction.categoryName || '')} - ${escapeHtml(transaction.originDescription || 'Origem nao informada')} - ${dateLabel} ${formatDate(transaction.date)}</div>
        </div>
        <div class="pagar-item-actions">
            <div class="pagar-item-amount" style="color:${amountColor}">${formatCurrency(transaction.amount)}</div>
            <button class="settle-btn ${isExpense ? 'expense' : ''}" type="button" onclick="settleTransaction(${transaction.id})">${actionLabel}</button>
        </div>
    </div>`;
}

async function settleTransaction(id) {
    const transaction = allTransactions.find(item => Number(item.id) === Number(id));
    if (!transaction) return;
    if (!transaction.accountId && !transaction.creditCardId) {
        alert('Selecione a origem da movimentacao antes de efetivar o lancamento.');
        openEditTransaction(id);
        return;
    }

    const action = transaction.type === 'INCOME' ? 'receber' : 'pagar';
    if (!confirm(`Deseja ${action} ${transaction.description}?`)) return;
    try {
        const response = await fetch(
            `${API_BASE_URL}/transactions/${id}/settle?userId=${currentUser.id}`,
            { method: 'PATCH' }
        );
        if (!response.ok) throw await response.json();
        await Promise.all([fetchAllTransactions(), fetchPendingTransactions(), fetchAccounts(), fetchCreditCards()]);
        await loadDashboard();
        renderLancamentos();
        loadRelatorios();
    } catch (error) {
        alert('Erro: ' + (error.error || error.message || 'nao foi possivel efetivar o lancamento'));
    }
}

function renderGastosCard() {
    const ctx = document.getElementById('gastosChart');
    const listEl = document.getElementById('gastos-list');
    if (!listEl) return;
    if (gastosChartInstance) { gastosChartInstance.destroy(); gastosChartInstance = null; }

    const now = new Date();
    const expenses = allTransactions.filter(t => {
        if (t.type !== 'EXPENSE' || !isTransactionCompleted(t)) return false;
        const d = parseLocalDate(t.date);
        return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear();
    });

    if (!expenses.length) {
        listEl.innerHTML = '<div class="card-empty-state alone"><p>Sem gastos no período</p></div>';
        if (ctx) {
            gastosChartInstance = new Chart(ctx, {
                type:'doughnut', data:{ datasets:[{ data:[1], backgroundColor:['#e8e8e8'], borderWidth:0 }] },
                options:{ cutout:'70%', plugins:{ legend:{ display:false } }, animation:false }
            });
        }
        return;
    }

    const groups = {};
    expenses.forEach(t => {
        const k = t.categoryName || 'Outros';
        if (!groups[k]) groups[k] = { icon: t.categoryIcon || '📦', total: 0 };
        groups[k].total += t.amount;
    });
    const sorted = Object.entries(groups).sort((a,b) => b[1].total - a[1].total);
    const total = sorted.reduce((a,[,g]) => a + g.total, 0);
    const colors = ['#EFB4B4','#494FDF','#C1D8C0','#f59e0b','#6262D3','#CF0404','#BEBEBE','#14A10C'];

    listEl.innerHTML = sorted.slice(0,5).map(([name, g], i) => {
        const pct = ((g.total/total)*100).toFixed(1);
        return `<div class="gastos-item">
            <div class="gastos-item-icon" style="background:${colors[i]||'#888'}">${g.icon}</div>
            <div class="gastos-item-name">${name}</div>
            <div class="gastos-item-pct">${pct}%</div>
        </div>`;
    }).join('');

    if (ctx) {
        gastosChartInstance = new Chart(ctx, {
            type:'doughnut',
            data:{
                labels: sorted.map(([n]) => n),
                datasets:[{ data: sorted.map(([,g]) => g.total), backgroundColor: colors.slice(0, sorted.length), borderWidth:0 }]
            },
            options:{ cutout:'65%', plugins:{ legend:{ display:false } } }
        });
    }
}

// ===== LIMITES CARD (dashboard) =====
function renderLimitesCard() {
    const body = document.getElementById('limites-body');
    if (!body) return;
    if (!limits.length) {
        body.innerHTML = '<div class="card-empty-state alone"><p>Você não possui limites configurados</p></div><button class="manage-btn" onclick="showSection(\'limites\',{target:document.querySelectorAll(\'.nav-btn\')[3]})">Definir limites</button>';
        return;
    }
    body.innerHTML = limits.map(l => {
        const used = Number(l.usedAmount ?? l.spent ?? 0);
        const amount = Number(l.amount || 0);
        const pct = Math.min(Number(l.percentage || 0), 100);
        const fillClass = pct >= 100 ? 'over' : pct >= 75 ? 'warn' : '';
        const category = findCategoryByName(l.categoryName);
        return `<div class="lim-item">
            <div class="lim-item-header">
                <div class="lim-item-name">${escapeHtml(category?.icon || '')} ${escapeHtml(l.categoryName)}</div>
                <div class="lim-item-vals">${formatCurrency(used)} / ${formatCurrency(amount)}</div>
            </div>
            <div class="lim-progress-bar"><div class="lim-progress-fill ${fillClass}" style="width:${pct.toFixed(1)}%"></div></div>
        </div>`;
    }).join('');
}

// ===== QUICK ADD MODAL =====
function openQuickAdd(type, pending = false) {
    editingTransactionId = null;
    const form = document.getElementById('quick-modal').querySelector('form');
    if (form) form.reset();
    const typeEl = document.getElementById('q-type');
    if (typeEl) typeEl.value = type;
    document.getElementById('q-periodicity').value = 'SINGLE';
    document.getElementById('q-status').value = pending ? 'PENDING' : 'COMPLETED';
    document.getElementById('q-card-payment-mode').value = 'ONE_TIME';
    document.getElementById('q-installment-count').value = '2';
    const titleEl = document.getElementById('modal-title');
    if (titleEl) titleEl.textContent = type === 'EXPENSE' ? 'Nova Despesa' : 'Nova Receita';
    const dateEl = document.getElementById('q-date');
    if (dateEl) dateEl.value = new Date().toISOString().split('T')[0];
    populateModalCategories();
    populateMovementOrigins();
    syncInstallmentControls();
    if (!categories.length) {
        alert('Nenhuma categoria disponível. Atualize a página e tente novamente.');
        return;
    }
    if (form) form.dataset.mode = 'create';
    document.getElementById('quick-modal').style.display = 'flex';
}
function closeQuickModal() {
    document.getElementById('quick-modal').style.display = 'none';
    editingTransactionId = null;
}

function openTransactionModalFromLancamentos(type) {
    openQuickAdd(type);
    showSection('lancamentos', { target: document.querySelectorAll('.nav-btn')[1] });
}

function openEditTransaction(id) {
    const transaction = allTransactions.find(t => Number(t.id) === Number(id));
    if (!transaction) return;
    if (!categories.some(category => Number(category.id) === Number(transaction.categoryId))) {
        alert('A categoria deste lançamento não está mais disponível.');
        return;
    }
    editingTransactionId = id;
    document.getElementById('q-type').value = transaction.type;
    document.getElementById('q-amount').value = transaction.amount;
    document.getElementById('q-description').value = transaction.description;
    document.getElementById('q-date').value = transaction.date;
    document.getElementById('q-periodicity').value = transaction.periodicity || 'SINGLE';
    document.getElementById('q-status').value = transaction.status || 'COMPLETED';
    populateModalCategories();
    document.getElementById('q-category').value = transaction.categoryId;
    const originValue = transaction.accountId
        ? `ACCOUNT:${transaction.accountId}`
        : transaction.creditCardId ? `CREDIT_CARD:${transaction.creditCardId}` : '';
    populateMovementOrigins(originValue);
    document.getElementById('q-card-payment-mode').value = Number(transaction.installmentCount || 1) > 1
        ? 'INSTALLMENT'
        : 'ONE_TIME';
    document.getElementById('q-installment-count').value = Number(transaction.installmentCount || 2);
    syncInstallmentControls(transaction.installmentTotalAmount);
    document.getElementById('modal-title').textContent = 'Editar Lançamento';
    const form = document.getElementById('quick-modal').querySelector('form');
    if (form) form.dataset.mode = 'edit';
    document.getElementById('quick-modal').style.display = 'flex';
}

function populateModalCategories() {
    const sel = document.getElementById('q-category');
    if (!sel) return;
    sel.innerHTML = '';
    const hasCategories = categories.length > 0;
    sel.disabled = !hasCategories;

    if (!hasCategories) {
        const option = document.createElement('option');
        option.value = '';
        option.textContent = 'Nenhuma categoria disponível';
        sel.appendChild(option);
        syncTransactionSubmitAvailability();
        return;
    }

    categories.forEach(c => {
        const o = document.createElement('option');
        o.value = c.id;
        o.textContent = `${c.icon || ''} ${c.name}`.trim();
        sel.appendChild(o);
    });
    syncTransactionSubmitAvailability();
}

function populateMovementOrigins(selectedValue = '') {
    const select = document.getElementById('q-origin');
    if (!select) return;
    const type = document.getElementById('q-type')?.value || 'EXPENSE';
    const accountOptions = accounts
        .filter(account => account.type === 'CHECKING' || account.type === 'SAVINGS')
        .map(account => ({
            value: `ACCOUNT:${account.id}`,
            label: `${formatAccountType(account.type)} - ${account.name}`
        }));
    const cardOptions = type === 'EXPENSE'
        ? creditCards.map(card => ({
            value: `CREDIT_CARD:${card.id}`,
            label: `Cartao de credito - ${card.name} final ${card.lastFour}`
        }))
        : [];
    const options = [...accountOptions, ...cardOptions];

    select.innerHTML = options.length
        ? '<option value="">Selecione a origem</option>' + options.map(option =>
            `<option value="${option.value}">${escapeHtml(option.label)}</option>`
        ).join('')
        : '<option value="">Cadastre uma conta compativel primeiro</option>';
    select.disabled = !options.length;
    if (selectedValue && options.some(option => option.value === selectedValue)) {
        select.value = selectedValue;
    }
    const help = document.getElementById('q-origin-help');
    if (help) {
        help.textContent = options.length
            ? type === 'INCOME'
                ? 'Receitas podem ser creditadas em conta corrente ou poupanca.'
                : 'Despesas podem sair de uma conta ou utilizar um cartao.'
            : 'Crie uma conta corrente, poupanca ou cartao no Dashboard.';
    }
    syncTransactionSubmitAvailability();
    syncInstallmentControls();
}

function syncInstallmentControls(totalOverride = null) {
    const type = document.getElementById('q-type')?.value;
    const origin = document.getElementById('q-origin')?.value || '';
    const paymentMode = document.getElementById('q-card-payment-mode');
    const paymentGroup = document.getElementById('q-card-payment-group');
    const installmentGroup = document.getElementById('q-installment-group');
    const installmentInput = document.getElementById('q-installment-count');
    const periodicity = document.getElementById('q-periodicity');
    const isCardExpense = type === 'EXPENSE' && origin.startsWith('CREDIT_CARD:');
    const isInstallment = isCardExpense && paymentMode?.value === 'INSTALLMENT';

    if (paymentGroup) paymentGroup.hidden = !isCardExpense;
    if (installmentGroup) installmentGroup.hidden = !isInstallment;
    if (installmentInput) installmentInput.required = isInstallment;
    if (periodicity) {
        if (isInstallment) periodicity.value = 'SINGLE';
        periodicity.disabled = isInstallment;
    }

    const count = Number(installmentInput?.value || 0);
    const amount = Number((totalOverride ?? document.getElementById('q-amount')?.value) || 0);
    const preview = document.getElementById('q-installment-preview');
    if (preview) {
        preview.textContent = isInstallment && count > 1 && amount > 0
            ? `${count} parcelas de aproximadamente ${formatCurrency(amount / count)}`
            : 'Informe o valor total e uma quantidade maior que 1.';
    }
}

function syncTransactionSubmitAvailability() {
    const button = document.getElementById('transaction-submit-button');
    const category = document.getElementById('q-category');
    const origin = document.getElementById('q-origin');
    if (button) button.disabled = !category || category.disabled || !origin || origin.disabled;
}

function populateLancamentoCategoryFilters() {
    const menu = document.getElementById('cat-dd');
    if (!menu) return;
    const selected = new Set(
        Array.from(menu.querySelectorAll('input:checked')).map(input => input.value)
    );
    const options = categories.map(category => {
        const value = `category:${category.id}`;
        return `<label><input type="checkbox" value="${value}" ${selected.has(value) ? 'checked' : ''}> ${escapeHtml(`${category.icon || ''} ${category.name}`.trim())}</label>`;
    }).join('');
    menu.innerHTML = '<p class="dd-group-label">Categorias cadastradas</p>' +
        (options || '<p class="filter-loading">Nenhuma categoria disponível</p>');
}

document.addEventListener('change', e => {
    if (e.target && e.target.id === 'q-type') {
        populateModalCategories();
        populateMovementOrigins();
    }
    if (e.target && ['q-origin', 'q-card-payment-mode', 'q-installment-count'].includes(e.target.id)) {
        syncInstallmentControls();
    }
    if (e.target && e.target.closest('.lanc-filters')) renderLancamentos();
});

document.addEventListener('input', e => {
    if (e.target && ['q-amount', 'q-installment-count'].includes(e.target.id)) {
        syncInstallmentControls();
    }
});

document.addEventListener('click', e => {
    if (e.target && e.target.classList.contains('filter-search-btn')) {
        toggleLancSearch();
    }
});

async function handleQuickAdd(e) {
    e.preventDefault();
    if (!currentUser) return;
    const type = document.getElementById('q-type').value;
    const amount = parseFloat(document.getElementById('q-amount').value);
    const description = document.getElementById('q-description').value;
    const date = document.getElementById('q-date').value;
    const periodicity = document.getElementById('q-periodicity').value;
    const status = document.getElementById('q-status').value;
    const originValue = document.getElementById('q-origin').value;
    const paymentMode = document.getElementById('q-card-payment-mode').value;
    const isCardExpense = type === 'EXPENSE' && originValue.startsWith('CREDIT_CARD:');
    const installmentCount = isCardExpense && paymentMode === 'INSTALLMENT'
        ? Number(document.getElementById('q-installment-count').value)
        : 1;
    const categoryId = parseInt(document.getElementById('q-category').value, 10);
    if (!categories.some(category => Number(category.id) === categoryId)) {
        alert('Selecione uma categoria válida.');
        return;
    }
    if (!originValue || !Number.isFinite(amount) || amount <= 0) {
        alert('Informe um valor valido e selecione a origem da movimentacao.');
        return;
    }
    if (paymentMode === 'INSTALLMENT' && (!isCardExpense || !Number.isInteger(installmentCount) || installmentCount <= 1)) {
        alert('Compras parceladas exigem cartao de credito e quantidade de parcelas maior que 1.');
        return;
    }
    const [originType, originIdValue] = originValue.split(':');
    const originId = Number(originIdValue);
    const originPayload = originType === 'ACCOUNT'
        ? { originType, accountId: originId, creditCardId: null }
        : { originType, accountId: null, creditCardId: originId };
    try {
        const url = editingTransactionId
            ? `${API_BASE_URL}/transactions/${editingTransactionId}`
            : `${API_BASE_URL}/transactions?userId=${currentUser.id}`;
        const res = await fetch(url, {
            method: editingTransactionId ? 'PUT' : 'POST',
            headers:{'Content-Type':'application/json'},
            body: JSON.stringify({
                type,
                categoryId,
                amount,
                description,
                date,
                periodicity,
                status,
                installmentCount,
                ...originPayload
            })
        });
        if (res.ok) {
            closeQuickModal();
            document.getElementById('quick-modal').querySelector('form').reset();
            editingTransactionId = null;
            await fetchAllTransactions();
            loadDashboard();
            loadLancamentos();
        } else { const err = await res.json(); alert('Erro: ' + err.error); }
    } catch { alert('Erro ao salvar'); }
}

function closeModalOutside(e) {
    if (e.target === e.currentTarget) {
        closeQuickModal();
        closeLimitModal();
        closeAccountsModal();
        closeCardsModal();
        closeProfileModal();
        closeReportFiltersModal();
    }
}

// ===== LANÇAMENTOS =====
function getFilteredTransactions() {
    const m = monthState.lanc;
    const validCategoryIds = new Set(categories.map(category => Number(category.id)));
    return allTransactions.filter(t => {
        if (!validCategoryIds.has(Number(t.categoryId))) return false;
        const d = parseLocalDate(t.date);
        if (d.getMonth() !== m.getMonth() || d.getFullYear() !== m.getFullYear()) return false;
        if (lancSearchTerm) {
            const haystack = normalizeText(`${t.description || ''} ${t.categoryName || ''} ${t.type || ''} ${t.originName || ''} ${t.originDescription || ''} ${getPeriodicityLabel(t.periodicity)}`);
            if (!haystack.includes(normalizeText(lancSearchTerm))) return false;
        }

        const checked = Array.from(document.querySelectorAll('.lanc-filters input[type=checkbox]:checked')).map(cb => cb.value);
        if (!checked.length) return true;

        const selectedTypes = checked.filter(value => value.startsWith('type:')).map(value => value.slice(5));
        const selectedCategories = checked.filter(value => value.startsWith('category:')).map(value => Number(value.slice(9)));
        const typeMatch = !selectedTypes.length || selectedTypes.includes(t.type);
        const categoryMatch = !selectedCategories.length || selectedCategories.includes(Number(t.categoryId));

        return typeMatch && categoryMatch;
    });
}

async function loadLancamentos() {
    await fetchAllTransactions();
    renderLancamentos();
}

function renderLancamentos() {
    const list = document.getElementById('lanc-list');
    if (!list) return;
    const transactions = getFilteredTransactions();
    const today = new Date(); today.setHours(0,0,0,0);

    if (!transactions.length) {
        list.innerHTML = '<div class="page-empty-state"><div class="empty-icon-circle">!</div><p>Nenhuma movimentação no período.</p></div>';
        updateLancFooter([]);
        return;
    }

    const sorted = [...transactions].sort((a,b) => parseLocalDate(b.date) - parseLocalDate(a.date));
    list.innerHTML = sorted.map(t => {
        const isExp = t.type === 'EXPENSE';
        const isPending = !isTransactionCompleted(t);
        const d = parseLocalDate(t.date); d.setHours(0,0,0,0);
        const isOverdue = isExp && isPending && d < today;
        const isFuture = isExp && isPending && d >= today;
        let badge = '';
        if (isOverdue) badge = '<span class="lanc-item-badge" style="background:#ffe0e0;color:var(--red);">Atrasada</span>';
        else if (isFuture) badge = '<span class="lanc-item-badge" style="background:#e8f4fd;color:#0369a1;">A vencer</span>';
        else if (isPending) badge = '<span class="lanc-item-badge">A receber</span>';
        else badge = `<span class="lanc-item-badge" style="background:#e8f8e7;color:var(--green);">${isExp ? 'Pago' : 'Recebido'}</span>`;
        const periodicityLabel = getPeriodicityLabel(t.periodicity);
        return `<div class="lanc-item ${isExp ? 'expense' : 'income'} ${isOverdue ? 'overdue' : ''}">
            <div class="lanc-item-icon ${isExp ? 'expense-icon' : 'income-icon'}">${t.categoryIcon || (isExp ? '💸' : '💰')}</div>
            <div class="lanc-item-info">
                <div class="lanc-item-name">${escapeHtml(t.description)} ${badge}</div>
                <div class="lanc-item-sub">${escapeHtml(t.categoryName || '')} - ${formatDate(t.date)} - ${escapeHtml(periodicityLabel)} - ${escapeHtml(t.originDescription || 'Origem nao informada')}</div>
            </div>
            <div class="lanc-item-amount ${isExp ? 'expense' : 'income'}">
                ${isExp ? '-' : '+'}R$ ${Math.abs(t.amount).toFixed(2).replace('.',',')}
            </div>
            <div class="lanc-actions">
                ${isPending ? `<button onclick="settleTransaction(${t.id})" title="Efetivar">${isExp ? 'Pagar' : 'Receber'}</button>` : ''}
                <button onclick="openEditTransaction(${t.id})" title="Editar">Editar</button>
                <button onclick="deleteTransaction(${t.id})" title="Excluir">Excluir</button>
            </div>
        </div>`;
    }).join('');

    updateLancFooter(transactions);
}

function updateLancFooter(transactions) {
    const completed = transactions.filter(isTransactionCompleted);
    const pending = transactions.filter(transaction => !isTransactionCompleted(transaction));
    const income = completed.filter(t => t.type === 'INCOME').reduce((a,t) => a + Number(t.amount), 0);
    const expense = completed.filter(t => t.type === 'EXPENSE').reduce((a,t) => a + Number(t.amount), 0);
    const pendingIncome = pending.filter(t => t.type === 'INCOME').reduce((a,t) => a + Number(t.amount), 0);
    const pendingExpense = pending.filter(t => t.type === 'EXPENSE').reduce((a,t) => a + Number(t.amount), 0);
    const saldo = income - expense;
    const forecast = saldo + pendingIncome - pendingExpense;
    setElText('f-saldo', saldo.toFixed(2).replace('.',','));
    setElText('f-previsto', forecast.toFixed(2).replace('.',','));
    const expandedRows = document.querySelectorAll('#lanc-summary-expanded .lanc-footer-row span:last-child');
    if (expandedRows.length >= 7) {
        expandedRows[1].textContent = income.toFixed(2).replace('.', ',');
        expandedRows[2].textContent = pendingIncome.toFixed(2).replace('.', ',');
        expandedRows[3].textContent = expense.toFixed(2).replace('.', ',');
        expandedRows[4].textContent = pendingExpense.toFixed(2).replace('.', ',');
        expandedRows[5].textContent = saldo.toFixed(2).replace('.', ',');
        expandedRows[6].textContent = forecast.toFixed(2).replace('.', ',');
    }
}

function toggleLancSummary() {
    const collapsed = document.getElementById('lanc-summary-collapsed');
    const expanded = document.getElementById('lanc-summary-expanded');
    const btn = document.getElementById('summary-toggle-btn');
    const isHidden = expanded.style.display === 'none' || !expanded.style.display;
    collapsed.style.display = isHidden ? 'none' : 'block';
    expanded.style.display = isHidden ? 'block' : 'none';
    btn.textContent = isHidden ? '−' : '+';
}

function clearLancFilters() {
    document.querySelectorAll('.lanc-filters input[type=checkbox]').forEach(cb => cb.checked = false);
    const search = document.getElementById('lanc-search-input');
    if (search) search.value = '';
    lancSearchTerm = '';
    renderLancamentos();
}

function toggleLancSearch() {
    const box = document.getElementById('lanc-search-box');
    const input = document.getElementById('lanc-search-input');
    if (!box || !input) return;
    const willOpen = box.style.display === 'none' || !box.style.display;
    box.style.display = willOpen ? 'block' : 'none';
    if (willOpen) {
        input.focus();
    } else {
        lancSearchTerm = '';
        input.value = '';
        renderLancamentos();
    }
}

function applyLancSearch() {
    const input = document.getElementById('lanc-search-input');
    lancSearchTerm = input ? input.value.trim() : '';
    renderLancamentos();
}

function toggleDropdown(id) {
    const dd = document.getElementById(id);
    const isOpen = dd.classList.contains('open');
    document.querySelectorAll('.dropdown-menu').forEach(d => d.classList.remove('open'));
    if (!isOpen) dd.classList.add('open');
}

function setupDropdownClose() {
    document.addEventListener('click', e => {
        if (!e.target.closest('.filter-dropdown')) {
            document.querySelectorAll('.dropdown-menu').forEach(d => d.classList.remove('open'));
        }
    });
}

async function deleteTransaction(id) {
    if (!confirm('Tem certeza que deseja deletar esta transação?')) return;
    try {
        const res = await fetch(`${API_BASE_URL}/transactions/${id}`, { method:'DELETE' });
        if (res.ok) { await fetchAllTransactions(); renderLancamentos(); loadDashboard(); }
        else alert('Erro ao deletar');
    } catch { alert('Erro ao deletar'); }
}

// ===== RELATÓRIOS =====
async function loadRelatorios() {
    await fetchAllTransactions();
    renderReportFilterBadge();
    renderCategoryReport(getReportMonthTransactions());
    renderFlowReport();
}

function getReportMonthTransactions() {
    const month = monthState.rel;
    return filterReportTransactions(allTransactions.filter(transaction => {
        if (!isTransactionCompleted(transaction)) return false;
        const date = parseLocalDate(transaction.date);
        return date.getMonth() === month.getMonth() && date.getFullYear() === month.getFullYear();
    }));
}

function filterReportTransactions(transactions) {
    const validCategoryIds = new Set(categories.map(category => Number(category.id)));
    return transactions.filter(transaction => {
        const availableCategory = validCategoryIds.has(Number(transaction.categoryId));
        const typeMatch = reportFilters.types.has(transaction.type);
        const categoryMatch = !reportFilters.categoryIds.size ||
            reportFilters.categoryIds.has(Number(transaction.categoryId));
        const originMatch = !reportFilters.originKeys.size ||
            reportFilters.originKeys.has(getOriginKey(transaction));
        return availableCategory && typeMatch && categoryMatch && originMatch;
    });
}

function calculateReportTotals(transactions) {
    const income = transactions
        .filter(transaction => transaction.type === 'INCOME')
        .reduce((total, transaction) => total + Number(transaction.amount || 0), 0);
    const expense = transactions
        .filter(transaction => transaction.type === 'EXPENSE')
        .reduce((total, transaction) => total + Number(transaction.amount || 0), 0);
    return { income, expense, result: income - expense, count: transactions.length };
}

function renderReportSummary(transactions) {
    const totals = calculateReportTotals(transactions);
    return `<div class="report-summary-grid">
        <div class="report-summary-item"><span>Entradas</span><strong class="val-green">${formatCurrency(totals.income)}</strong></div>
        <div class="report-summary-item"><span>Saídas</span><strong class="val-red">${formatCurrency(totals.expense)}</strong></div>
        <div class="report-summary-item"><span>Resultado</span><strong class="val-purple">${formatCurrency(totals.result)}</strong></div>
        <div class="report-summary-item"><span>Lançamentos</span><strong>${totals.count}</strong></div>
    </div>`;
}

function groupTransactionsByCategory(transactions) {
    const grouped = new Map();
    transactions.forEach(transaction => {
        const key = String(transaction.categoryId || transaction.categoryName || 'outros');
        if (!grouped.has(key)) {
            grouped.set(key, {
                name: transaction.categoryName || 'Outros',
                icon: transaction.categoryIcon || '',
                color: transaction.categoryColor || '#6262D3',
                total: 0
            });
        }
        grouped.get(key).total += Number(transaction.amount || 0);
    });
    return Array.from(grouped.values()).sort((a, b) => b.total - a.total);
}

function renderCategoryReport(transactions) {
    const container = document.getElementById('rel-cat-content');
    if (!container) return;
    destroyReportCategoryCharts();

    if (relViewMode === 'evolution') {
        renderCategoryEvolution(container, transactions);
        return;
    }

    const expenses = groupTransactionsByCategory(transactions.filter(transaction => transaction.type === 'EXPENSE'));
    const incomes = groupTransactionsByCategory(transactions.filter(transaction => transaction.type === 'INCOME'));

    if (!transactions.length) {
        container.innerHTML = renderReportSummary([]) +
            '<div class="page-empty-state"><div class="empty-icon-circle">!</div><p>Nenhum lançamento para os filtros selecionados</p></div>';
        return;
    }

    const blocks = [
        renderCategoryBlock('Despesas', expenses, 'rel-chart-exp'),
        renderCategoryBlock('Receitas', incomes, 'rel-chart-inc')
    ].filter(Boolean).join('');

    container.innerHTML = renderReportSummary(transactions) +
        `<div class="report-category-grid">${blocks}</div>`;

    requestAnimationFrame(() => {
        relChartExpense = createDoughnutChart('rel-chart-exp', expenses);
        relChartIncome = createDoughnutChart('rel-chart-inc', incomes);
    });
}

function renderCategoryBlock(label, groups, chartId) {
    if (!groups.length) return '';
    const total = groups.reduce((sum, group) => sum + group.total, 0);
    const rows = groups.map(group => {
        const percentage = total > 0 ? (group.total / total) * 100 : 0;
        return `<div class="rel-item">
            <div class="rel-item-icon" style="background:${escapeHtml(group.color)}">${escapeHtml(group.icon)}</div>
            <div class="rel-item-name">${escapeHtml(group.name)}</div>
            <div class="rel-item-vals">
                <span class="rel-item-amount">${formatCurrency(group.total)}</span>
                <span class="rel-item-pct">${percentage.toFixed(1)}%</span>
            </div>
        </div>`;
    }).join('');

    return `<section class="rel-block">
        <p class="rel-section-label">${label}</p>
        <div class="report-category-content">
            <div class="rel-list">${rows}<div class="rel-total"><span>Total</span><span>${formatCurrency(total)}</span></div></div>
            <div class="rel-chart"><canvas id="${chartId}"></canvas></div>
        </div>
    </section>`;
}

function createDoughnutChart(canvasId, groups) {
    const canvas = document.getElementById(canvasId);
    if (!canvas || !groups.length) return null;
    return new Chart(canvas, {
        type: 'doughnut',
        data: {
            labels: groups.map(group => group.name),
            datasets: [{
                data: groups.map(group => group.total),
                backgroundColor: groups.map(group => group.color),
                borderWidth: 0
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '64%',
            plugins: { legend: { position: 'bottom', labels: { boxWidth: 10, usePointStyle: true } } }
        }
    });
}

function renderCategoryEvolution(container, transactions) {
    const groups = buildFlowGroups(transactions, 'diario');
    container.innerHTML = renderReportSummary(transactions) + `
        <section class="report-chart-panel">
            <div class="report-panel-heading">
                <div><span>Evolução financeira</span><small>Saldo acumulado no mês selecionado</small></div>
            </div>
            <div class="report-chart-canvas"><canvas id="rel-category-evolution"></canvas></div>
        </section>`;

    requestAnimationFrame(() => {
        const canvas = document.getElementById('rel-category-evolution');
        if (!canvas) return;
        relCategoryEvolutionChart = new Chart(canvas, {
            type: 'line',
            data: {
                labels: groups.map(group => group.label),
                datasets: [{
                    label: 'Saldo',
                    data: groups.map(group => group.balance),
                    borderColor: '#494FDF',
                    backgroundColor: 'rgba(73,79,223,0.12)',
                    fill: true,
                    tension: 0.28,
                    pointRadius: groups.length > 20 ? 1 : 3
                }]
            },
            options: reportChartOptions()
        });
    });
}

function destroyReportCategoryCharts() {
    [relChartExpense, relChartIncome, relCategoryEvolutionChart].forEach(chart => {
        if (chart) chart.destroy();
    });
    relChartExpense = null;
    relChartIncome = null;
    relCategoryEvolutionChart = null;
}

function populateReportFilterOptions() {
    const container = document.getElementById('rel-filter-categories');
    if (!container) return;
    const allSelected = reportFilters.categoryIds.size === 0;
    const options = categories.map(category => `
        <label class="modal-check">
            <input type="checkbox" value="${category.id}" ${allSelected || reportFilters.categoryIds.has(Number(category.id)) ? 'checked' : ''}>
            <span>${escapeHtml(`${category.icon || ''} ${category.name}`.trim())}</span>
        </label>`).join('');
    container.innerHTML = options || '<p class="modal-empty">Nenhuma categoria disponível</p>';

    const originsContainer = document.getElementById('rel-filter-origins');
    if (!originsContainer) return;
    const origins = getAvailableOrigins();
    const allOriginsSelected = reportFilters.originKeys.size === 0;
    originsContainer.innerHTML = origins.length ? origins.map(origin => `
        <label class="modal-check">
            <input type="checkbox" value="${origin.value}" ${allOriginsSelected || reportFilters.originKeys.has(origin.value) ? 'checked' : ''}>
            <span>${escapeHtml(origin.label)}</span>
        </label>`).join('') : '<p class="modal-empty">Nenhuma origem cadastrada</p>';
}

function openReportFiltersModal() {
    populateReportFilterOptions();
    document.getElementById('rel-filter-income').checked = reportFilters.types.has('INCOME');
    document.getElementById('rel-filter-expense').checked = reportFilters.types.has('EXPENSE');
    document.getElementById('report-filters-modal').style.display = 'flex';
}

function closeReportFiltersModal() {
    const modal = document.getElementById('report-filters-modal');
    if (modal) modal.style.display = 'none';
}

function applyReportFilters() {
    if (!categories.length) {
        alert('Não foi possível carregar as categorias.');
        return;
    }
    const types = new Set();
    if (document.getElementById('rel-filter-income').checked) types.add('INCOME');
    if (document.getElementById('rel-filter-expense').checked) types.add('EXPENSE');
    if (!types.size) {
        alert('Selecione receitas, despesas ou ambos.');
        return;
    }

    const selectedCategories = Array.from(
        document.querySelectorAll('#rel-filter-categories input:checked')
    ).map(input => Number(input.value));
    if (!selectedCategories.length) {
        alert('Selecione ao menos uma categoria.');
        return;
    }
    reportFilters.types = types;
    reportFilters.categoryIds = selectedCategories.length === categories.length
        ? new Set()
        : new Set(selectedCategories);
    const availableOrigins = getAvailableOrigins();
    const selectedOrigins = Array.from(
        document.querySelectorAll('#rel-filter-origins input:checked')
    ).map(input => input.value);
    reportFilters.originKeys = !availableOrigins.length || selectedOrigins.length === availableOrigins.length
        ? new Set()
        : new Set(selectedOrigins);
    closeReportFiltersModal();
    loadRelatorios();
}

function clearReportFilters() {
    reportFilters.types = new Set(['INCOME', 'EXPENSE']);
    reportFilters.categoryIds = new Set();
    reportFilters.originKeys = new Set();
    populateReportFilterOptions();
    document.getElementById('rel-filter-income').checked = true;
    document.getElementById('rel-filter-expense').checked = true;
    renderReportFilterBadge();
}

function renderReportFilterBadge() {
    const badge = document.getElementById('rel-filter-count');
    if (!badge) return;
    let count = reportFilters.categoryIds.size;
    count += reportFilters.originKeys.size;
    if (reportFilters.types.size < 2) count += reportFilters.types.size;
    badge.textContent = count ? String(count) : '';
    badge.style.display = count ? 'inline-flex' : 'none';
}

// ===== RELATÓRIOS ENTRADAS x SAÍDAS =====
function switchPeriod(period, button) {
    document.querySelectorAll('.period-tab').forEach(tab => tab.classList.remove('active'));
    button.classList.add('active');
    currentPeriod = period;
    renderFlowReport();
}

function getFlowTransactions() {
    const selectedMonth = monthState.rel;
    let scoped = allTransactions.filter(isTransactionCompleted);
    if (currentPeriod === 'diario' || currentPeriod === 'semanal') {
        scoped = allTransactions.filter(isTransactionCompleted).filter(transaction => {
            const date = parseLocalDate(transaction.date);
            return date.getMonth() === selectedMonth.getMonth() &&
                date.getFullYear() === selectedMonth.getFullYear();
        });
    } else if (currentPeriod === 'mensal') {
        scoped = allTransactions.filter(isTransactionCompleted).filter(transaction =>
            parseLocalDate(transaction.date).getFullYear() === selectedMonth.getFullYear()
        );
    }
    return filterReportTransactions(scoped);
}

function createFlowGroup(label, key) {
    return { label, key, income: 0, expense: 0, result: 0, balance: 0 };
}

function buildFlowGroups(transactions, period) {
    const selectedMonth = monthState.rel;
    let groups = [];

    if (period === 'diario') {
        const days = new Date(selectedMonth.getFullYear(), selectedMonth.getMonth() + 1, 0).getDate();
        groups = Array.from({ length: days }, (_, index) =>
            createFlowGroup(String(index + 1).padStart(2, '0') + '/' + String(selectedMonth.getMonth() + 1).padStart(2, '0'), index)
        );
        transactions.forEach(transaction => {
            const date = parseLocalDate(transaction.date);
            addTransactionToFlowGroup(groups[date.getDate() - 1], transaction);
        });
    } else if (period === 'semanal') {
        const days = new Date(selectedMonth.getFullYear(), selectedMonth.getMonth() + 1, 0).getDate();
        const month = String(selectedMonth.getMonth() + 1).padStart(2, '0');
        for (let startDay = 1; startDay <= days; startDay += 7) {
            const endDay = Math.min(startDay + 6, days);
            groups.push(createFlowGroup(
                String(startDay).padStart(2, '0') + '/' + month + ' a ' + String(endDay).padStart(2, '0') + '/' + month,
                groups.length
            ));
        }
        transactions.forEach(transaction => {
            const date = parseLocalDate(transaction.date);
            addTransactionToFlowGroup(groups[Math.floor((date.getDate() - 1) / 7)], transaction);
        });
    } else if (period === 'mensal') {
        groups = MONTHS.map((monthName, index) =>
            createFlowGroup(String(index + 1).padStart(2, '0') + '/' + selectedMonth.getFullYear(), index)
        );
        transactions.forEach(transaction => {
            const date = parseLocalDate(transaction.date);
            addTransactionToFlowGroup(groups[date.getMonth()], transaction);
        });
    } else {
        groups = buildAccumulatedGroups(transactions);
    }

    let runningBalance = 0;
    groups.forEach(group => {
        group.result = group.income - group.expense;
        runningBalance += group.result;
        group.balance = runningBalance;
    });
    return groups;
}

function buildAccumulatedGroups(transactions) {
    if (!allTransactions.length) return [];
    const dates = allTransactions.map(transaction => parseLocalDate(transaction.date)).sort((a, b) => a - b);
    const start = new Date(dates[0].getFullYear(), dates[0].getMonth(), 1);
    const latest = dates[dates.length - 1];
    const now = new Date();
    const endCandidate = latest > now ? latest : now;
    const end = new Date(endCandidate.getFullYear(), endCandidate.getMonth(), 1);
    const groups = [];
    const byKey = new Map();

    for (let cursor = new Date(start); cursor <= end; cursor.setMonth(cursor.getMonth() + 1)) {
        const key = cursor.getFullYear() + '-' + String(cursor.getMonth() + 1).padStart(2, '0');
        const group = createFlowGroup(String(cursor.getMonth() + 1).padStart(2, '0') + '/' + cursor.getFullYear(), key);
        groups.push(group);
        byKey.set(key, group);
    }
    transactions.forEach(transaction => {
        const date = parseLocalDate(transaction.date);
        const key = date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0');
        addTransactionToFlowGroup(byKey.get(key), transaction);
    });
    return groups;
}

function addTransactionToFlowGroup(group, transaction) {
    if (!group) return;
    const amount = Number(transaction.amount || 0);
    if (transaction.type === 'INCOME') group.income += amount;
    if (transaction.type === 'EXPENSE') group.expense += amount;
}

function renderFlowReport() {
    const container = document.getElementById('rel-ent-sai-content');
    if (!container) return;
    if (relFlowBarChart) relFlowBarChart.destroy();
    if (relFlowEvolutionChart) relFlowEvolutionChart.destroy();

    const transactions = getFlowTransactions();
    const groups = buildFlowGroups(transactions, currentPeriod);
    if (!groups.length) {
        container.innerHTML = '<div class="page-empty-state"><div class="empty-icon-circle">!</div><p>Nenhum lançamento disponível</p></div>';
        return;
    }

    const tableRows = groups.map(group => `<tr>
        <td>${escapeHtml(group.label)}</td>
        <td class="val-green">${formatCurrency(group.income)}</td>
        <td class="val-red">${formatCurrency(group.expense)}</td>
        <td class="val-purple">${formatCurrency(group.result)}</td>
        <td>${formatCurrency(group.balance)}</td>
    </tr>`).join('');

    container.innerHTML = renderReportSummary(transactions) + `
        <div class="report-charts-grid">
            <section class="report-chart-panel">
                <div class="report-panel-heading"><div><span>Entradas, saídas e resultado</span><small>Valores do período exibido</small></div></div>
                <div class="report-chart-canvas"><canvas id="rel-flow-bars"></canvas></div>
            </section>
            <section class="report-chart-panel">
                <div class="report-panel-heading"><div><span>Evolução do saldo</span><small>Saldo acumulado no período</small></div></div>
                <div class="report-chart-canvas"><canvas id="rel-flow-evolution"></canvas></div>
            </section>
        </div>
        <div class="report-table-wrap">
            <table class="report-table">
                <thead><tr><th>Período</th><th>Entradas</th><th>Saídas</th><th>Resultado</th><th>Saldo</th></tr></thead>
                <tbody>${tableRows}</tbody>
            </table>
        </div>`;

    requestAnimationFrame(() => createFlowCharts(groups));
}

function createFlowCharts(groups) {
    const labels = groups.map(group => group.label);
    const barCanvas = document.getElementById('rel-flow-bars');
    const evolutionCanvas = document.getElementById('rel-flow-evolution');

    if (barCanvas) {
        relFlowBarChart = new Chart(barCanvas, {
            type: 'bar',
            data: {
                labels,
                datasets: [
                    { label: 'Entradas', data: groups.map(group => group.income), backgroundColor: '#14A10C' },
                    { label: 'Saídas', data: groups.map(group => group.expense), backgroundColor: '#CF0404' },
                    { label: 'Resultado', data: groups.map(group => group.result), backgroundColor: '#494FDF' }
                ]
            },
            options: reportChartOptions()
        });
    }
    if (evolutionCanvas) {
        relFlowEvolutionChart = new Chart(evolutionCanvas, {
            type: 'line',
            data: {
                labels,
                datasets: [{
                    label: 'Saldo',
                    data: groups.map(group => group.balance),
                    borderColor: '#494FDF',
                    backgroundColor: 'rgba(73,79,223,0.12)',
                    fill: true,
                    tension: 0.28,
                    pointRadius: groups.length > 20 ? 1 : 3
                }]
            },
            options: reportChartOptions()
        });
    }
}

function reportChartOptions() {
    return {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: {
            legend: { position: 'bottom', labels: { boxWidth: 10, usePointStyle: true } }
        },
        scales: {
            x: { grid: { display: false }, ticks: { maxRotation: 0, autoSkip: true, maxTicksLimit: 12 } },
            y: { beginAtZero: true, ticks: { callback: value => 'R$ ' + Number(value).toLocaleString('pt-BR') } }
        }
    };
}

// ===== PAGE TABS =====
function switchPageTab(section, tab, btn) {
    const sectionId = section === 'rel' ? 'relatorios' : 'limites';
    document.querySelectorAll(`#${sectionId}-section .page-tab`).forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    document.querySelectorAll(`#${sectionId}-section .page-tab-content`).forEach(c => c.classList.remove('active'));
    document.getElementById(`${section}-${tab}`).classList.add('active');
    if (section === 'rel' && tab === 'categorias') {
        renderCategoryReport(getReportMonthTransactions());
    }
    if (section === 'rel' && tab === 'entradas') {
        renderFlowReport();
    }
}

function switchRelView(view, btn) {
    document.querySelectorAll('.report-view-tab').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    relViewMode = view;
    renderCategoryReport(getReportMonthTransactions());
}

// ===== LIMITES =====
async function renderLimites() {
    const container = document.getElementById('limites-list');
    if (!container) return;

    const selectedMonth = monthState.lim;
    await fetchLimitsForMonth(getMonthKey(selectedMonth));

    if (!limits.length) {
        container.innerHTML = '<div class="lim-empty">' +
            '<p>Nenhum limite de gasto definido em ' +
            escapeHtml(MONTHS[selectedMonth.getMonth()] + ' ' + selectedMonth.getFullYear()) +
            '</p><button class="manage-btn" type="button" onclick="openLimitModal()">Definir limite de gastos</button></div>';
        return;
    }

    container.innerHTML = limits.map(function(limit) {
        const used = getLimitUsed(limit);
        const amount = Number(limit.amount || 0);
        const remaining = getLimitRemaining(limit);
        const percentage = Math.min(Number(limit.percentage || 0), 100);
        const percentageLabel = percentage.toFixed(1);
        const fillClass = percentage >= 100 ? 'over' : percentage >= 75 ? 'warn' : '';
        const statusColor = percentage >= 100 ? 'var(--red)' : percentage >= 75 ? '#f59e0b' : 'var(--green)';
        const category = findCategoryByName(limit.categoryName);
        const categoryColor = category && category.color ? category.color : '#555';
        const categoryIcon = category && category.icon ? category.icon : 'C';

        return '<div class="lim-item">' +
            '<div class="lim-item-header">' +
                '<div class="lim-item-name"><span class="lim-category-icon" style="background:' +
                    escapeHtml(categoryColor) + '">' + escapeHtml(categoryIcon) + '</span>' +
                    escapeHtml(limit.categoryName) + '</div>' +
                '<div class="lim-item-vals">' + formatCurrency(used) + ' / ' + formatCurrency(amount) + '</div>' +
            '</div>' +
            '<div class="lim-progress-bar"><div class="lim-progress-fill ' + fillClass +
                '" style="width:' + percentageLabel + '%"></div></div>' +
            '<div class="limit-metrics">' +
                '<span class="limit-metric" style="color:' + statusColor + '">' +
                    percentageLabel + '% utilizado</span>' +
                '<span class="limit-metric">Restante: <strong>' + formatCurrency(remaining) + '</strong></span>' +
            '</div>' +
            '<div class="limit-actions">' +
                '<button class="limit-edit-button" type="button" onclick="openLimitModal(' + limit.id + ')">Editar</button>' +
                '<button class="limit-remove-button" type="button" onclick="removeLimit(' + limit.id + ')">Excluir</button>' +
            '</div>' +
        '</div>';
    }).join('');

    container.innerHTML += '<button class="add-limit-btn" type="button" onclick="openLimitModal()">+ Adicionar limite</button>';
}

function getCurrentLimMonth() {
    return MONTHS[monthState.lim.getMonth()] + ' ' + monthState.lim.getFullYear();
}

function findCategoryByName(name) {
    return categories.find(function(category) {
        return normalizeText(category.name) === normalizeText(name);
    });
}

function getLimitUsed(limit) {
    return Number(limit.usedAmount ?? limit.spent ?? 0);
}

function getLimitRemaining(limit) {
    return Number(limit.remaining ?? Math.max(Number(limit.amount || 0) - getLimitUsed(limit), 0));
}

function openLimitModal(limitId = null) {
    editingLimitId = limitId;
    const limit = limitId == null ? null : limits.find(function(item) { return item.id === limitId; });
    const form = document.querySelector('#limit-modal form');
    if (form) form.reset();

    const selectedMonth = monthState.lim;
    const monthInput = document.getElementById('lim-month');
    monthInput.value = getMonthKey(selectedMonth);
    populateLimitCategories(limit ? limit.categoryName : '');

    document.getElementById('limit-modal-title').textContent =
        limit ? 'Editar limite de gastos' : 'Adicionar limite de gastos';
    document.getElementById('limit-submit-button').textContent =
        limit ? 'Salvar alterações' : 'Salvar limite';

    if (limit) {
        document.getElementById('lim-value').value = Number(limit.amount || 0);
        document.getElementById('lim-used-value').value = getLimitUsed(limit);
        monthInput.value = limit.month;
    }

    document.getElementById('limit-modal').style.display = 'flex';
}

function closeLimitModal() {
    const modal = document.getElementById('limit-modal');
    if (modal) modal.style.display = 'none';
    editingLimitId = null;
}

function populateLimitCategories(selectedName = '') {
    const select = document.getElementById('lim-category');
    if (!select) return;
    if (!categories.length) {
        select.innerHTML = '<option value="">Nenhuma categoria cadastrada</option>';
        return;
    }

    select.innerHTML = categories.map(function(category) {
        const selected = normalizeText(category.name) === normalizeText(selectedName) ? ' selected' : '';
        return '<option value="' + category.id + '"' + selected + '>' +
            escapeHtml(((category.icon || '') + ' ' + category.name).trim()) + '</option>';
    }).join('');
}

async function handleAddLimit(event) {
    event.preventDefault();
    const categoryId = Number(document.getElementById('lim-category').value);
    const category = categories.find(function(item) { return item.id === categoryId; });
    const amount = parseFloat(document.getElementById('lim-value').value);
    const usedAmount = parseFloat(document.getElementById('lim-used-value').value || '0');
    const month = document.getElementById('lim-month').value;

    if (!category || !Number.isFinite(amount) || !Number.isFinite(usedAmount) || !month) {
        alert('Preencha todos os campos do limite.');
        return;
    }
    if (amount <= 0 || usedAmount < 0 || usedAmount > amount) {
        alert('O valor utilizado deve estar entre zero e o valor total do limite.');
        return;
    }

    try {
        const endpoint = editingLimitId == null
            ? API_BASE_URL + '/users/' + currentUser.id + '/limits'
            : API_BASE_URL + '/users/' + currentUser.id + '/limits/' + editingLimitId;
        const response = await fetch(endpoint, {
            method: editingLimitId == null ? 'POST' : 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                categoryKey: normalizeText(category.name).replace(/\s+/g, '-'),
                categoryName: category.name,
                amount: amount,
                usedAmount: usedAmount,
                month: month,
                limitType: 'EXPENSE'
            })
        });
        if (!response.ok) throw await response.json();

        closeLimitModal();
        await renderLimites();
        await fetchLimitsForMonth(getMonthKey(new Date()));
        renderLimitesCard();
        loadRelatorios();
        loadDashboard();
    } catch (error) {
        alert('Erro: ' + (error.error || error.message || 'não foi possível salvar o limite'));
    }
}

async function removeLimit(id) {
    if (!confirm('Remover este limite?')) return;
    try {
        const response = await fetch(
            API_BASE_URL + '/users/' + currentUser.id + '/limits/' + id,
            { method: 'DELETE' }
        );
        if (!response.ok) throw await response.json();

        await renderLimites();
        await fetchLimitsForMonth(getMonthKey(new Date()));
        renderLimitesCard();
        loadRelatorios();
        loadDashboard();
    } catch (error) {
        alert('Erro: ' + (error.error || error.message || 'não foi possível remover o limite'));
    }
}

// ===== PROFILE MENU =====
function toggleProfileMenu() {
    const menu = document.getElementById('profile-dropdown');
    if (!menu) return;
    menu.classList.toggle('open');
}

function closeProfileMenu() {
    const menu = document.getElementById('profile-dropdown');
    if (menu) menu.classList.remove('open');
}

function openProfileModal() {
    closeProfileMenu();
    syncProfileForm();
    document.getElementById('profile-modal').style.display = 'flex';
}

function closeProfileModal() {
    const modal = document.getElementById('profile-modal');
    if (modal) modal.style.display = 'none';
}

function syncProfileForm() {
    if (!currentUser) return;
    setElText('profile-user-name', currentUser.name || 'Usuario');
    setElText('profile-user-email', currentUser.email || '');
    setElText('profile-user-name-modal', `${currentUser.name || 'Usuario'} - ${currentUser.email || ''}`);
    const name = document.getElementById('profile-name');
    const email = document.getElementById('profile-email');
    if (name) name.value = currentUser.name || '';
    if (email) email.value = currentUser.email || '';
}

async function handleProfileUpdate(e) {
    e.preventDefault();
    const name = document.getElementById('profile-name').value.trim();
    const email = document.getElementById('profile-email').value.trim();
    if (!name || !email) return;

    try {
        const res = await fetch(`${API_BASE_URL}/users/${currentUser.id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, email })
        });
        const data = await res.json();
        if (!res.ok) throw data;
        currentUser = { ...currentUser, id: data.id, name: data.name, email: data.email };
        localStorage.setItem('user', JSON.stringify(currentUser));
        showApp();
        closeProfileModal();
    } catch(err) {
        alert('Erro: ' + (err.error || 'nao foi possivel atualizar o perfil'));
    }
}

function handleGlobalKeydown(e) {
    if (e.key !== 'Escape') return;
    closeProfileMenu();
    closeProfileModal();
    closeAccountsModal();
    closeCardsModal();
    closeQuickModal();
    closeLimitModal();
    closeReportFiltersModal();
}

document.addEventListener('click', e => {
    if (!e.target.closest('.profile-menu')) closeProfileMenu();
});

// ===== UTILS =====
function isTransactionCompleted(transaction) {
    if (transaction.status) return transaction.status === 'COMPLETED';
    const date = parseLocalDate(transaction.date);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    date.setHours(0, 0, 0, 0);
    return date < today;
}

function getPeriodicityLabel(periodicity) {
    const labels = { SINGLE: 'Unico', MONTHLY: 'Mensal', ANNUAL: 'Anual' };
    return labels[periodicity] || labels.SINGLE;
}

function getOriginKey(transaction) {
    if (transaction.accountId) return `ACCOUNT:${transaction.accountId}`;
    if (transaction.creditCardId) return `CREDIT_CARD:${transaction.creditCardId}`;
    return 'UNSPECIFIED';
}

function getAvailableOrigins() {
    const accountOrigins = accounts
        .filter(account => account.type === 'CHECKING' || account.type === 'SAVINGS')
        .map(account => ({
            value: `ACCOUNT:${account.id}`,
            label: `${formatAccountType(account.type)} - ${account.name}`
        }));
    const cardOrigins = creditCards.map(card => ({
        value: `CREDIT_CARD:${card.id}`,
        label: `Cartao de credito - ${card.name} final ${card.lastFour}`
    }));
    return [...accountOrigins, ...cardOrigins];
}

function formatCurrency(v) {
    if (!v) v = 0;
    return `R$ ${parseFloat(v).toFixed(2).replace('.',',')}`;
}
function formatDate(d) {
    const date = parseLocalDate(d);
    return date.toLocaleDateString('pt-BR');
}

function parseLocalDate(value) {
    if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
        const [year, month, day] = value.split('-').map(Number);
        return new Date(year, month - 1, day);
    }
    return new Date(value);
}

function escapeHtml(value) {
    return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function normalizeText(value) {
    return String(value || '')
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .toLowerCase()
        .trim();
}
