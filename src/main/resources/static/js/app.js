/**
 * CanteenX — Digital Canteen Token Management Platform
 * Client Application Logic
 */

(function () {
  'use strict';

  // State Management
  const state = {
    user: null,
    token: null,
    canteens: [],
    selectedCanteenId: null,
    menuItems: [],
    activeFilter: 'ALL',
    searchQuery: '',
    cart: {
      canteenId: null,
      canteenName: '',
      items: [] // { itemId, name, price, quantity }
    },
    adminData: null,
    recentOrders: [],
    verifiedToken: null
  };

  // API helper with JSON and Auth header
  async function api(endpoint, options = {}) {
    const headers = options.headers || {};
    if (state.token) {
      headers['Authorization'] = `Bearer ${state.token}`;
    }
    if (!(options.body instanceof FormData) && options.body && typeof options.body === 'object') {
      headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(options.body);
    }
    options.headers = headers;

    try {
      const res = await fetch(endpoint, options);
      if (res.status === 401 || res.status === 403) {
        if (state.token && !endpoint.includes('/api/auth/login')) {
          showToast('Session expired or unauthorized. Please sign in again.', 'error');
          logout(false);
        }
      }

      // Check for CSV blob download
      const contentType = res.headers.get('content-type') || '';
      if (contentType.includes('text/csv')) {
        return await res.blob();
      }

      const json = await res.json();
      if (!res.ok || json.success === false) {
        throw new Error(json.message || `Request failed with status ${res.status}`);
      }
      return json;
    } catch (err) {
      console.error(`API Error on ${endpoint}:`, err);
      throw err;
    }
  }

  // Toast notifications
  function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(8px)';
      setTimeout(() => toast.remove(), 300);
    }, 3500);
  }

  // Live Backend & MySQL Health Check
  async function checkHealth() {
    const pill = document.getElementById('health-pill');
    const label = document.getElementById('health-label');
    if (!pill || !label) return;

    try {
      const res = await fetch('/api/health');
      const data = await res.json();
      if (res.ok && data.data && data.data.status === 'UP') {
        pill.className = 'health-pill up';
        label.textContent = 'Backend UP — MySQL Connected';
      } else {
        pill.className = 'health-pill down';
        label.textContent = 'Backend Degraded — MySQL Offline';
      }
    } catch (e) {
      pill.className = 'health-pill down';
      label.textContent = 'Backend Offline';
    }
  }

  // View Navigation
  function navigate(viewName) {
    document.querySelectorAll('.app-view').forEach(v => v.classList.remove('active'));
    document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));

    const targetView = document.getElementById(`view-${viewName}`);
    if (targetView) targetView.classList.add('active');

    const navBtn = document.querySelector(`[data-view="${viewName}"]`);
    if (navBtn) navBtn.classList.add('active');

    // Load view specific data
    if (viewName === 'landing') {
      loadCanteens();
    } else if (viewName === 'student') {
      if (!state.user || state.user.role !== 'STUDENT') {
        openModal('modal-login');
        showToast('Please sign in with a Student account to access Ordering.', 'error');
        navigate('landing');
        return;
      }
      loadStudentMenu();
      loadMyOrders();
    } else if (viewName === 'shopkeeper') {
      if (!state.user || state.user.role !== 'SHOPKEEPER') {
        openModal('modal-login');
        showToast('Please sign in with a Counter Shopkeeper account.', 'error');
        navigate('landing');
        return;
      }
      initShopkeeperDesk();
    } else if (viewName === 'admin') {
      if (!state.user || state.user.role !== 'ADMIN') {
        openModal('modal-login');
        showToast('Please sign in with an Administrator account.', 'error');
        navigate('landing');
        return;
      }
      loadAdminDashboard();
    }
  }

  // Load Canteens
  async function loadCanteens() {
    try {
      const resp = await api('/api/canteens');
      state.canteens = resp.data || [];
      renderLandingCanteens();
      populateCanteenDropdowns();
    } catch (e) {
      showToast('Could not load canteens from database: ' + e.message, 'error');
    }
  }

  function renderLandingCanteens() {
    const grid = document.getElementById('landing-outlets-grid');
    if (!grid) return;
    grid.innerHTML = '';

    if (state.canteens.length === 0) {
      grid.innerHTML = `<div class="outlet-card"><p>No active canteens found in database.</p></div>`;
      return;
    }

    state.canteens.forEach(c => {
      const card = document.createElement('div');
      card.className = 'outlet-card';
      card.innerHTML = `
        <div>
          <div class="outlet-header">
            <span class="outlet-badge">PREFIX: ${escapeHtml(c.tokenPrefix)}</span>
            <span style="font-size:0.75rem; font-weight:700; color:var(--success);">● ACTIVE</span>
          </div>
          <h3 class="outlet-name">${escapeHtml(c.name)}</h3>
          <div class="outlet-meta">
            <span>Dishes: <strong>${c.availableItems || 0}</strong> available</span>
            <span>Token: <strong>${escapeHtml(c.tokenPrefix)}-XXXXXXX</strong></span>
          </div>
        </div>
        <button class="btn btn-primary btn-block" onclick="CanteenXApp.selectCanteenAndOrder(${c.canteenId})">
          Browse Menu & Order →
        </button>
      `;
      grid.appendChild(card);
    });
  }

  function populateCanteenDropdowns() {
    const studentSelect = document.getElementById('student-canteen-select');
    if (studentSelect) {
      const currentVal = studentSelect.value;
      studentSelect.innerHTML = '';
      state.canteens.forEach(c => {
        const opt = document.createElement('option');
        opt.value = c.canteenId;
        opt.textContent = `${c.name} (${c.tokenPrefix})`;
        studentSelect.appendChild(opt);
      });
      if (currentVal && state.canteens.some(c => c.canteenId == currentVal)) {
        studentSelect.value = currentVal;
      } else if (state.canteens.length > 0) {
        studentSelect.value = state.canteens[0].canteenId;
      }
      state.selectedCanteenId = studentSelect.value;
    }

    const adminSelect = document.getElementById('admin-dish-canteen');
    if (adminSelect) {
      adminSelect.innerHTML = '';
      state.canteens.forEach(c => {
        const opt = document.createElement('option');
        opt.value = c.canteenId;
        opt.textContent = `${c.name} (${c.tokenPrefix})`;
        adminSelect.appendChild(opt);
      });
    }
  }

  // Student Menu & Ordering
  async function loadStudentMenu() {
    const select = document.getElementById('student-canteen-select');
    if (!select || !select.value) return;
    state.selectedCanteenId = select.value;

    try {
      const resp = await api(`/api/canteens/${state.selectedCanteenId}/menu?includeUnavailable=false`);
      state.menuItems = resp.data || [];
      renderStudentMenu();
    } catch (e) {
      showToast('Error loading menu: ' + e.message, 'error');
    }
  }

  function renderStudentMenu() {
    const grid = document.getElementById('student-menu-grid');
    if (!grid) return;
    grid.innerHTML = '';

    let filtered = state.menuItems;
    if (state.activeFilter !== 'ALL') {
      filtered = filtered.filter(i => i.category.toLowerCase() === state.activeFilter.toLowerCase());
    }
    if (state.searchQuery.trim() !== '') {
      const q = state.searchQuery.toLowerCase();
      filtered = filtered.filter(i => i.name.toLowerCase().includes(q) || i.category.toLowerCase().includes(q));
    }

    if (filtered.length === 0) {
      grid.innerHTML = `
        <div style="grid-column: 1/-1; text-align:center; padding: 3rem; background:#fff; border-radius:var(--radius-lg);">
          <p style="color:var(--text-muted); font-size:1.05rem;">No dishes match your filter in this outlet.</p>
        </div>`;
      return;
    }

    filtered.forEach(item => {
      const card = document.createElement('div');
      card.className = 'menu-card';
      card.innerHTML = `
        <div class="menu-card-top">
          <span class="menu-category-tag">${escapeHtml(item.category)}</span>
          <span style="font-size:0.75rem; color:var(--success); font-weight:700;">● In Stock</span>
        </div>
        <h4 class="menu-dish-name">${escapeHtml(item.name)}</h4>
        <div class="menu-card-bottom">
          <span class="menu-price">₹${Number(item.price).toFixed(2)}</span>
          <div class="qty-control">
            <button class="btn btn-sm btn-outline" onclick="CanteenXApp.addToCart(${item.id}, '${escapeQuote(item.name)}', ${item.price}, ${item.canteenId})">
              + Add to Cart
            </button>
          </div>
        </div>
      `;
      grid.appendChild(card);
    });
  }

  // Single-Canteen Cart Management
  function addToCart(itemId, name, price, canteenId) {
    // Single-canteen check
    if (state.cart.canteenId && state.cart.canteenId !== canteenId && state.cart.items.length > 0) {
      const currentCanteenObj = state.canteens.find(c => c.canteenId == state.cart.canteenId);
      const newCanteenObj = state.canteens.find(c => c.canteenId == canteenId);
      const currentName = currentCanteenObj ? currentCanteenObj.name : 'another outlet';
      const newName = newCanteenObj ? newCanteenObj.name : 'this outlet';

      if (confirm(`Single-Canteen Policy:\n\nYour cart currently has items from ${currentName}.\n\nCanteenX enforces a single-outlet cart rule to guarantee isolated token queues.\n\nClear current cart and switch to ${newName}?`)) {
        clearCart();
      } else {
        return;
      }
    }

    const canteenObj = state.canteens.find(c => c.canteenId == canteenId);
    state.cart.canteenId = canteenId;
    state.cart.canteenName = canteenObj ? canteenObj.name : '';

    const existing = state.cart.items.find(i => i.itemId === itemId);
    if (existing) {
      if (existing.quantity >= 50) {
        showToast('Maximum 50 items per dish allowed.', 'error');
        return;
      }
      existing.quantity += 1;
    } else {
      state.cart.items.push({
        itemId: itemId,
        name: name,
        price: Number(price),
        quantity: 1
      });
    }

    renderCart();
    showToast(`Added ${name} to cart.`, 'success');
  }

  function modifyCartQty(itemId, delta) {
    const item = state.cart.items.find(i => i.itemId === itemId);
    if (!item) return;

    item.quantity += delta;
    if (item.quantity <= 0) {
      state.cart.items = state.cart.items.filter(i => i.itemId !== itemId);
    } else if (item.quantity > 50) {
      item.quantity = 50;
      showToast('Maximum 50 items allowed per dish.', 'error');
    }

    if (state.cart.items.length === 0) {
      state.cart.canteenId = null;
      state.cart.canteenName = '';
    }

    renderCart();
  }

  function clearCart() {
    state.cart.canteenId = null;
    state.cart.canteenName = '';
    state.cart.items = [];
    renderCart();
  }

  function renderCart() {
    const list = document.getElementById('cart-items-list');
    const badge = document.getElementById('cart-canteen-badge');
    const subtotalEl = document.getElementById('cart-subtotal');
    const totalEl = document.getElementById('cart-total');
    const checkoutBtn = document.getElementById('btn-cart-checkout');
    if (!list) return;

    list.innerHTML = '';
    if (state.cart.items.length === 0) {
      if (badge) badge.textContent = 'Cart is Empty';
      list.innerHTML = `<p style="text-align:center; color:var(--text-muted); padding:2rem 0;">No dishes added yet.</p>`;
      if (subtotalEl) subtotalEl.textContent = '₹0.00';
      if (totalEl) totalEl.textContent = '₹0.00';
      if (checkoutBtn) checkoutBtn.disabled = true;
      return;
    }

    if (badge) badge.textContent = state.cart.canteenName || 'Single Outlet Order';
    if (checkoutBtn) checkoutBtn.disabled = false;

    let subtotal = 0;
    state.cart.items.forEach(item => {
      const lineTotal = item.price * item.quantity;
      subtotal += lineTotal;

      const row = document.createElement('div');
      row.className = 'cart-item-row';
      row.innerHTML = `
        <div style="flex:1;">
          <div style="font-weight:700;">${escapeHtml(item.name)}</div>
          <div style="font-size:0.75rem; color:var(--text-muted);">₹${item.price.toFixed(2)} each</div>
        </div>
        <div class="qty-control" style="margin: 0 0.5rem;">
          <button class="qty-btn" onclick="CanteenXApp.modifyCartQty(${item.itemId}, -1)">−</button>
          <span style="font-weight:700; min-width:20px; text-align:center;">${item.quantity}</span>
          <button class="qty-btn" onclick="CanteenXApp.modifyCartQty(${item.itemId}, 1)">+</button>
        </div>
        <div style="font-weight:700; min-width:65px; text-align:right;">₹${lineTotal.toFixed(2)}</div>
      `;
      list.appendChild(row);
    });

    if (subtotalEl) subtotalEl.textContent = `₹${subtotal.toFixed(2)}`;
    if (totalEl) totalEl.textContent = `₹${subtotal.toFixed(2)}`;
  }

  // Checkout & Digital Receipt
  function openCheckoutModal() {
    if (state.cart.items.length === 0) {
      showToast('Your shopping cart is empty.', 'error');
      return;
    }
    const outletNameEl = document.getElementById('checkout-outlet-name');
    const amountEl = document.getElementById('checkout-amount-display');
    if (outletNameEl) outletNameEl.textContent = state.cart.canteenName;

    const total = state.cart.items.reduce((sum, i) => sum + (i.price * i.quantity), 0);
    if (amountEl) amountEl.textContent = `₹${total.toFixed(2)}`;

    openModal('modal-checkout');
  }

  async function submitOrder() {
    if (state.cart.items.length === 0) return;
    const paymentMethod = document.querySelector('input[name="checkout-payment"]:checked')?.value || 'CAMPUS_UPI';

    const payload = {
      canteenId: state.cart.canteenId,
      paymentMethod: paymentMethod,
      items: state.cart.items.map(i => ({ itemId: i.itemId, quantity: i.quantity }))
    };

    try {
      const resp = await api('/api/orders', {
        method: 'POST',
        body: payload
      });

      closeModal('modal-checkout');
      clearCart();
      showToast('Order placed successfully! Token generated.', 'success');

      // Display digital token receipt
      displayDigitalReceipt(resp.data);
      loadMyOrders();
    } catch (e) {
      showToast('Failed to place order: ' + e.message, 'error');
    }
  }

  function displayDigitalReceipt(order) {
    const modal = document.getElementById('modal-receipt');
    if (!modal) return;

    document.getElementById('receipt-token-code').textContent = order.tokenCode;
    document.getElementById('receipt-outlet-name').textContent = order.canteenName;
    document.getElementById('receipt-order-id').textContent = '#' + order.orderId;
    document.getElementById('receipt-timestamp').textContent = new Date(order.createdAt).toLocaleString();
    document.getElementById('receipt-payment-method').textContent = order.paymentMethod.replace('_', ' ');
    document.getElementById('receipt-payment-status').textContent = order.paymentStatus;
    document.getElementById('receipt-total-amount').textContent = `₹${Number(order.totalAmount).toFixed(2)}`;

    const tbody = document.getElementById('receipt-items-tbody');
    if (tbody) {
      tbody.innerHTML = '';
      (order.items || []).forEach(it => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td>${escapeHtml(it.itemName)}</td>
          <td style="text-align:center;">× ${it.quantity}</td>
          <td style="text-align:right;">₹${Number(it.lineTotal).toFixed(2)}</td>
        `;
        tbody.appendChild(tr);
      });
    }

    openModal('modal-receipt');
  }

  // Student Order History
  async function loadMyOrders() {
    if (!state.user || state.user.role !== 'STUDENT') return;
    const list = document.getElementById('student-orders-list');
    if (!list) return;

    try {
      const resp = await api('/api/orders/my');
      state.recentOrders = resp.data || [];
      renderMyOrders();
    } catch (e) {
      showToast('Could not load order history: ' + e.message, 'error');
    }
  }

  function renderMyOrders() {
    const list = document.getElementById('student-orders-list');
    if (!list) return;
    list.innerHTML = '';

    if (state.recentOrders.length === 0) {
      list.innerHTML = `<p style="text-align:center; color:var(--text-muted); padding:2rem 0;">No past orders found.</p>`;
      return;
    }

    state.recentOrders.forEach(o => {
      const card = document.createElement('div');
      card.className = 'outlet-card';
      card.style.marginBottom = '1rem';
      const isRedeemed = o.orderStatus === 'REDEEMED';
      card.innerHTML = `
        <div style="display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:0.75rem;">
          <div>
            <span class="outlet-badge" style="font-size:1.1rem; padding:0.35rem 0.75rem;">${escapeHtml(o.tokenCode)}</span>
            <span style="font-size:0.8rem; color:var(--text-muted); margin-left:0.5rem;">${new Date(o.createdAt).toLocaleString()}</span>
          </div>
          <span class="outlet-badge" style="background:${isRedeemed ? 'var(--warning-bg)' : 'var(--success-bg)'}; color:${isRedeemed ? 'var(--warning-text)' : 'var(--success-text)'};">
            ${isRedeemed ? '✓ REDEEMED' : '● READY FOR HANDOVER'}
          </span>
        </div>
        <div style="font-size:0.95rem; margin-bottom:0.75rem;">
          <strong>${escapeHtml(o.canteenName)}</strong> · Total: <strong>₹${Number(o.totalAmount).toFixed(2)}</strong> (${o.paymentMethod})
        </div>
        <div style="font-size:0.85rem; color:var(--text-muted); margin-bottom:1rem;">
          ${(o.items || []).map(i => `${escapeHtml(i.itemName)} × ${i.quantity}`).join(' · ')}
        </div>
        <button class="btn btn-outline btn-sm" onclick="CanteenXApp.viewReceiptById(${o.orderId})">
          View Digital Receipt 📄
        </button>
      `;
      list.appendChild(card);
    });
  }

  async function viewReceiptById(orderId) {
    try {
      const resp = await api(`/api/orders/${orderId}`);
      displayDigitalReceipt(resp.data);
    } catch (e) {
      showToast('Error opening receipt: ' + e.message, 'error');
    }
  }

  // Shopkeeper Terminal
  function initShopkeeperDesk() {
    if (!state.user || state.user.role !== 'SHOPKEEPER') return;
    const title = document.getElementById('shopkeeper-canteen-title');
    if (title) title.textContent = state.user.canteenName || 'Assigned Counter';
    resetShopkeeperFeedback();
  }

  function resetShopkeeperFeedback() {
    state.verifiedToken = null;
    const statusBox = document.getElementById('shopkeeper-status-box');
    const detailsBox = document.getElementById('shopkeeper-details-box');
    const redeemBtn = document.getElementById('btn-confirm-handover');
    if (statusBox) statusBox.style.display = 'none';
    if (detailsBox) detailsBox.style.display = 'none';
    if (redeemBtn) redeemBtn.disabled = true;
  }

  async function verifyShopkeeperToken() {
    const input = document.getElementById('shopkeeper-token-input');
    if (!input || !input.value.trim()) {
      showToast('Enter a valid token code (e.g. CAF-0000001)', 'error');
      return;
    }
    const tokenCode = input.value.trim().toUpperCase();

    try {
      const resp = await api(`/api/shopkeeper/tokens/${encodeURIComponent(tokenCode)}`);
      state.verifiedToken = resp.data;
      renderShopkeeperTokenDetails(resp.data);
    } catch (e) {
      resetShopkeeperFeedback();
      const statusBox = document.getElementById('shopkeeper-status-box');
      if (statusBox) {
        statusBox.style.display = 'block';
        statusBox.className = 'status-callout error';
        statusBox.innerHTML = `⚠️ ${escapeHtml(e.message)}`;
      }
    }
  }

  function renderShopkeeperTokenDetails(d) {
    const statusBox = document.getElementById('shopkeeper-status-box');
    const detailsBox = document.getElementById('shopkeeper-details-box');
    const redeemBtn = document.getElementById('btn-confirm-handover');

    if (statusBox) {
      statusBox.style.display = 'block';
      if (d.eligibleForHandover) {
        statusBox.className = 'status-callout valid';
        statusBox.innerHTML = `✓ ${escapeHtml(d.verificationMessage)}`;
      } else if (d.orderStatus === 'REDEEMED') {
        statusBox.className = 'status-callout redeemed';
        statusBox.innerHTML = `⚠️ ALREADY REDEEMED · Handed over at ${new Date(d.redeemedAt).toLocaleTimeString()}`;
      } else {
        statusBox.className = 'status-callout error';
        statusBox.innerHTML = `❌ ${escapeHtml(d.verificationMessage)}`;
      }
    }

    if (detailsBox) {
      detailsBox.style.display = 'block';
      detailsBox.innerHTML = `
        <div style="display:flex; justify-content:space-between; margin-bottom:1rem; border-bottom:1px solid var(--border-light); padding-bottom:0.75rem;">
          <div>
            <div style="font-size:0.8rem; color:var(--text-muted);">STUDENT CUSTOMER</div>
            <div style="font-weight:700; font-size:1.1rem;">${escapeHtml(d.studentName)} (${escapeHtml(d.studentEmail)})</div>
          </div>
          <div style="text-align:right;">
            <div style="font-size:0.8rem; color:var(--text-muted);">TOTAL BILL</div>
            <div style="font-weight:800; font-size:1.25rem; color:var(--primary);">₹${Number(d.totalAmount).toFixed(2)}</div>
          </div>
        </div>
        <h5 style="margin-bottom:0.5rem; font-size:0.9rem;">Ordered Dishes:</h5>
        <ul style="padding-left:1.25rem; margin-bottom:1rem;">
          ${(d.items || []).map(i => `<li><strong>${escapeHtml(i.itemName)}</strong> × ${i.quantity} (₹${Number(i.unitPrice).toFixed(2)})</li>`).join('')}
        </ul>
      `;
    }

    if (redeemBtn) {
      redeemBtn.disabled = !d.eligibleForHandover;
    }
  }

  async function confirmTokenHandover() {
    if (!state.verifiedToken || !state.verifiedToken.tokenCode) return;
    const tokenCode = state.verifiedToken.tokenCode;

    try {
      const resp = await api(`/api/shopkeeper/tokens/${encodeURIComponent(tokenCode)}/redeem`, {
        method: 'POST'
      });
      state.verifiedToken = resp.data;
      renderShopkeeperTokenDetails(resp.data);
      showToast(`Handover recorded! Token ${tokenCode} is redeemed.`, 'success');
    } catch (e) {
      showToast('Redemption failed: ' + e.message, 'error');
    }
  }

  // Administrator Dashboard & Operations
  async function loadAdminDashboard() {
    if (!state.user || state.user.role !== 'ADMIN') return;

    try {
      const resp = await api('/api/admin/dashboard');
      state.adminData = resp.data;
      renderAdminKpis(resp.data);
      renderAdminOutletMetrics(resp.data.outletMetrics || []);
      renderAdminTopDishes(resp.data.topDishes || []);
      loadAdminMenuItems();
      loadAdminUsers();
      loadAdminAuditLogs();
    } catch (e) {
      showToast('Error loading admin dashboard: ' + e.message, 'error');
    }
  }

  function renderAdminKpis(d) {
    document.getElementById('kpi-revenue').textContent = `₹${Number(d.campusRevenue || 0).toFixed(2)}`;
    document.getElementById('kpi-orders').textContent = d.totalOrders || 0;
    document.getElementById('kpi-pending').textContent = d.activeUnredeemedTokens || 0;
    document.getElementById('kpi-redeemed').textContent = d.redeemedOrders || 0;
  }

  function renderAdminOutletMetrics(outlets) {
    const container = document.getElementById('admin-outlet-metrics-container');
    if (!container) return;
    container.innerHTML = '';

    outlets.forEach(o => {
      const card = document.createElement('div');
      card.className = 'outlet-card';
      card.innerHTML = `
        <div class="outlet-header">
          <span class="outlet-badge">${escapeHtml(o.tokenPrefix)}</span>
          <span style="font-weight:700;">${o.totalOrders} Orders</span>
        </div>
        <h4 style="font-size:1.2rem; margin-bottom:0.5rem;">${escapeHtml(o.canteenName)}</h4>
        <div style="font-size:1.4rem; font-weight:800; color:var(--primary);">
          ₹${Number(o.revenue).toFixed(2)}
        </div>
      `;
      container.appendChild(card);
    });
  }

  function renderAdminTopDishes(dishes) {
    const tbody = document.getElementById('admin-top-dishes-tbody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (dishes.length === 0) {
      tbody.innerHTML = `<tr><td colspan="4" style="text-align:center;">No dishes sold yet.</td></tr>`;
      return;
    }

    dishes.forEach((d, idx) => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td>#${idx + 1}</td>
        <td><strong>${escapeHtml(d.dishName)}</strong></td>
        <td>${d.quantitySold} units</td>
        <td>₹${Number(d.totalRevenue).toFixed(2)}</td>
      `;
      tbody.appendChild(tr);
    });
  }

  async function loadAdminMenuItems() {
    const tbody = document.getElementById('admin-menu-tbody');
    if (!tbody) return;

    try {
      const resp = await api('/api/menu?includeUnavailable=true');
      const items = resp.data || [];
      tbody.innerHTML = '';

      items.forEach(item => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td>${item.id}</td>
          <td><span class="outlet-badge">${escapeHtml(item.canteenName)}</span></td>
          <td><strong>${escapeHtml(item.name)}</strong></td>
          <td>${escapeHtml(item.category)}</td>
          <td>₹${Number(item.price).toFixed(2)}</td>
          <td>
            <span style="color:${item.available ? 'var(--success)' : 'var(--danger)'}; font-weight:700;">
              ${item.available ? '● Available' : '○ Disabled'}
            </span>
          </td>
          <td>
            <button class="btn btn-sm btn-outline" onclick="CanteenXApp.openEditMenuModal(${item.id}, ${item.canteenId}, '${escapeQuote(item.name)}', '${escapeQuote(item.category)}', ${item.price}, ${item.available})">
              Edit
            </button>
            <button class="btn btn-sm btn-outline" style="color:var(--danger); border-color:#f5c2c7;" onclick="CanteenXApp.archiveMenuItem(${item.id}, '${escapeQuote(item.name)}')">
              Archive
            </button>
          </td>
        `;
        tbody.appendChild(tr);
      });
    } catch (e) {
      showToast('Error loading menu admin list: ' + e.message, 'error');
    }
  }

  function openCreateMenuModal() {
    populateCanteenDropdowns();
    document.getElementById('modal-dish-title').textContent = 'Add New Menu Item';
    document.getElementById('admin-dish-id').value = '';
    document.getElementById('admin-dish-name').value = '';
    document.getElementById('admin-dish-category').value = 'Breakfast';
    document.getElementById('admin-dish-price').value = '';
    document.getElementById('admin-dish-available').checked = true;
    openModal('modal-dish');
  }

  function openEditMenuModal(id, canteenId, name, category, price, available) {
    populateCanteenDropdowns();
    document.getElementById('modal-dish-title').textContent = 'Edit Menu Item';
    document.getElementById('admin-dish-id').value = id;
    document.getElementById('admin-dish-canteen').value = canteenId;
    document.getElementById('admin-dish-name').value = name;
    document.getElementById('admin-dish-category').value = category;
    document.getElementById('admin-dish-price').value = price;
    document.getElementById('admin-dish-available').checked = available;
    openModal('modal-dish');
  }

  async function saveMenuItem() {
    const id = document.getElementById('admin-dish-id').value;
    const canteenId = document.getElementById('admin-dish-canteen').value;
    const name = document.getElementById('admin-dish-name').value.trim();
    const category = document.getElementById('admin-dish-category').value.trim();
    const price = parseFloat(document.getElementById('admin-dish-price').value);
    const available = document.getElementById('admin-dish-available').checked;

    if (!name || isNaN(price) || price < 0) {
      showToast('Please enter a valid dish name and non-negative price.', 'error');
      return;
    }

    const payload = {
      canteenId: Number(canteenId),
      name: name,
      category: category,
      price: price,
      available: available
    };

    try {
      if (id) {
        await api(`/api/admin/menu/${id}`, { method: 'PUT', body: payload });
        showToast('Dish updated successfully.', 'success');
      } else {
        await api('/api/admin/menu', { method: 'POST', body: payload });
        showToast('New dish created.', 'success');
      }
      closeModal('modal-dish');
      loadAdminMenuItems();
    } catch (e) {
      showToast('Error saving dish: ' + e.message, 'error');
    }
  }

  async function archiveMenuItem(id, name) {
    if (!confirm(`Are you sure you want to archive "${name}"?\n\nThis will soft-delete the dish so it cannot be ordered, while preserving all historical order receipts.`)) {
      return;
    }
    try {
      await api(`/api/admin/menu/${id}`, { method: 'DELETE' });
      showToast(`"${name}" archived successfully.`, 'success');
      loadAdminMenuItems();
    } catch (e) {
      showToast('Failed to archive: ' + e.message, 'error');
    }
  }

  async function loadAdminUsers() {
    const tbody = document.getElementById('admin-users-tbody');
    if (!tbody) return;

    try {
      const resp = await api('/api/admin/users');
      const users = resp.data || [];
      tbody.innerHTML = '';
      users.forEach(u => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td>#${u.userId}</td>
          <td><strong>${escapeHtml(u.name)}</strong></td>
          <td>${escapeHtml(u.email)}</td>
          <td><span class="user-role-badge">${escapeHtml(u.role)}</span></td>
          <td>${u.canteenName ? escapeHtml(u.canteenName) : '—'}</td>
          <td><span style="color:var(--success); font-weight:700;">● Active</span></td>
        `;
        tbody.appendChild(tr);
      });
    } catch (e) {
      console.warn('Error loading users:', e);
    }
  }

  async function loadAdminAuditLogs() {
    const tbody = document.getElementById('admin-audit-tbody');
    if (!tbody) return;

    try {
      const resp = await api('/api/admin/audit-logs?limit=25');
      const logs = resp.data || [];
      tbody.innerHTML = '';
      logs.forEach(l => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td>${new Date(l.createdAt).toLocaleTimeString()}</td>
          <td><span class="outlet-badge">${escapeHtml(l.eventType)}</span></td>
          <td>${escapeHtml(l.actorName || 'System')}</td>
          <td>${escapeHtml(l.eventDescription)}</td>
          <td><span style="color:${l.result === 'SUCCESS' ? 'var(--success)' : 'var(--danger)'}; font-weight:700;">${escapeHtml(l.result)}</span></td>
        `;
        tbody.appendChild(tr);
      });
    } catch (e) {
      console.warn('Error loading audit logs:', e);
    }
  }

  async function downloadCsvReport() {
    try {
      const blob = await api('/api/admin/reports/sales.csv');
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `canteenx_sales_report_${new Date().toISOString().slice(0, 10)}.csv`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
      showToast('CSV Report downloaded successfully.', 'success');
    } catch (e) {
      showToast('Failed to download CSV: ' + e.message, 'error');
    }
  }

  // Authentication Management
  async function submitLogin() {
    const email = document.getElementById('login-email').value.trim();
    const password = document.getElementById('login-password').value;

    if (!email || !password) {
      showToast('Enter your email and password.', 'error');
      return;
    }

    try {
      const resp = await api('/api/auth/login', {
        method: 'POST',
        body: { email, password }
      });

      state.token = resp.data.token;
      state.user = resp.data;
      localStorage.setItem('canteenx_token', state.token);
      localStorage.setItem('canteenx_user', JSON.stringify(state.user));

      updateUserNavbar();
      closeModal('modal-login');
      showToast(`Welcome back, ${state.user.name}!`, 'success');

      // Auto route by role
      if (state.user.role === 'STUDENT') navigate('student');
      else if (state.user.role === 'SHOPKEEPER') navigate('shopkeeper');
      else if (state.user.role === 'ADMIN') navigate('admin');
    } catch (e) {
      showToast('Login failed: ' + e.message, 'error');
    }
  }

  async function submitRegister() {
    const name = document.getElementById('reg-name').value.trim();
    const email = document.getElementById('reg-email').value.trim();
    const password = document.getElementById('reg-password').value;

    if (!name || !email || password.length < 6) {
      showToast('Please provide your name, valid email, and password (min 6 chars).', 'error');
      return;
    }

    try {
      const resp = await api('/api/auth/register', {
        method: 'POST',
        body: { name, email, password }
      });

      state.token = resp.data.token;
      state.user = resp.data;
      localStorage.setItem('canteenx_token', state.token);
      localStorage.setItem('canteenx_user', JSON.stringify(state.user));

      updateUserNavbar();
      closeModal('modal-register');
      showToast('Account registered! Welcome to CanteenX.', 'success');
      navigate('student');
    } catch (e) {
      showToast('Registration failed: ' + e.message, 'error');
    }
  }

  function quickLogin(email, password) {
    document.getElementById('login-email').value = email;
    document.getElementById('login-password').value = password;
    submitLogin();
  }

  async function seedDemoAccounts() {
    try {
      const resp = await api('/api/auth/seed-demo', { method: 'POST' });
      showToast(resp.message, 'success');
    } catch (e) {
      showToast('Demo setup error: ' + e.message, 'error');
    }
  }

  function logout(informServer = true) {
    if (informServer && state.token) {
      api('/api/auth/logout', { method: 'POST' }).catch(() => {});
    }
    state.user = null;
    state.token = null;
    localStorage.removeItem('canteenx_token');
    localStorage.removeItem('canteenx_user');
    updateUserNavbar();
    navigate('landing');
    showToast('Signed out successfully.', 'info');
  }

  function updateUserNavbar() {
    const userContainer = document.getElementById('nav-user-container');
    const guestContainer = document.getElementById('nav-guest-container');
    const userNameEl = document.getElementById('nav-user-name');
    const userRoleEl = document.getElementById('nav-user-role');
    const studentNav = document.getElementById('nav-link-student');
    const shopkeeperNav = document.getElementById('nav-link-shopkeeper');
    const adminNav = document.getElementById('nav-link-admin');

    if (state.user && state.token) {
      if (userContainer) userContainer.style.display = 'flex';
      if (guestContainer) guestContainer.style.display = 'none';
      if (userNameEl) userNameEl.textContent = state.user.name;
      if (userRoleEl) userRoleEl.textContent = state.user.role;

      if (studentNav) studentNav.style.display = state.user.role === 'STUDENT' ? 'inline-flex' : 'none';
      if (shopkeeperNav) shopkeeperNav.style.display = state.user.role === 'SHOPKEEPER' ? 'inline-flex' : 'none';
      if (adminNav) adminNav.style.display = state.user.role === 'ADMIN' ? 'inline-flex' : 'none';
    } else {
      if (userContainer) userContainer.style.display = 'none';
      if (guestContainer) guestContainer.style.display = 'flex';
      if (studentNav) studentNav.style.display = 'none';
      if (shopkeeperNav) shopkeeperNav.style.display = 'none';
      if (adminNav) adminNav.style.display = 'none';
    }
  }

  // Modal helpers
  function openModal(id) {
    const modal = document.getElementById(id);
    if (modal) modal.classList.add('active');
  }

  function closeModal(id) {
    const modal = document.getElementById(id);
    if (modal) modal.classList.remove('active');
  }

  // Utilities
  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function escapeQuote(str) {
    if (!str) return '';
    return String(str).replace(/'/g, "\\'").replace(/"/g, '&quot;');
  }

  // Public Interface for onclick callbacks
  window.CanteenXApp = {
    navigate,
    openModal,
    closeModal,
    submitLogin,
    submitRegister,
    quickLogin,
    seedDemoAccounts,
    logout,
    selectCanteenAndOrder: (canteenId) => {
      state.selectedCanteenId = canteenId;
      const select = document.getElementById('student-canteen-select');
      if (select) select.value = canteenId;
      navigate('student');
    },
    filterCategory: (cat) => {
      state.activeFilter = cat;
      document.querySelectorAll('.filter-pill').forEach(p => {
        p.classList.toggle('active', p.getAttribute('data-cat') === cat);
      });
      renderStudentMenu();
    },
    onSearchChange: (val) => {
      state.searchQuery = val;
      renderStudentMenu();
    },
    onCanteenChange: () => {
      loadStudentMenu();
    },
    addToCart,
    modifyCartQty,
    clearCart,
    openCheckoutModal,
    submitOrder,
    viewReceiptById,
    verifyShopkeeperToken,
    confirmTokenHandover,
    openCreateMenuModal,
    openEditMenuModal,
    saveMenuItem,
    archiveMenuItem,
    downloadCsvReport
  };

  // Initialization on DOMContentLoaded
  document.addEventListener('DOMContentLoaded', () => {
    // Restore saved session
    const savedToken = localStorage.getItem('canteenx_token');
    const savedUser = localStorage.getItem('canteenx_user');
    if (savedToken && savedUser) {
      try {
        state.token = savedToken;
        state.user = JSON.parse(savedUser);
        updateUserNavbar();
      } catch (e) {
        logout(false);
      }
    } else {
      updateUserNavbar();
    }

    // Health monitoring
    checkHealth();
    setInterval(checkHealth, 25000);

    // Initial data load
    loadCanteens();
    navigate('landing');

    // Setup Shopkeeper token input Enter-key listener
    const tokenInput = document.getElementById('shopkeeper-token-input');
    if (tokenInput) {
      tokenInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
          e.preventDefault();
          verifyShopkeeperToken();
        }
      });
    }

    // Close modals on clicking backdrop
    document.querySelectorAll('.modal-backdrop').forEach(modal => {
      modal.addEventListener('click', (e) => {
        if (e.target === modal) {
          modal.classList.remove('active');
        }
      });
    });
  });

})();
