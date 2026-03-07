/**
 * Ocean View Resort HRMS — vanilla HTML + JS app.
 * No React; uses same Java backend REST API.
 */
(function () {
  const loginScreen = document.getElementById('login-screen');
  const appScreen = document.getElementById('app-screen');
  const pageContent = document.getElementById('page-content');
  const toastContainer = document.getElementById('toast-container');

  let currentUser = null;
  let currentPage = 'dashboard';

  const API_BASE = '/api';
  const USERNAME_KEY = 'ovr_username';

  function getUsername() {
    return sessionStorage.getItem(USERNAME_KEY);
  }

  function setUsername(username) {
    if (username) sessionStorage.setItem(USERNAME_KEY, username);
    else sessionStorage.removeItem(USERNAME_KEY);
  }

  async function api(path, options = {}) {
    const username = getUsername();
    const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
    if (username) headers['X-Username'] = username;
    const res = await fetch(API_BASE + path, { ...options, headers });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || res.statusText);
    return data;
  }



  async function logout() {
    await api('/auth/logout', { method: 'POST' }).catch(() => {});
    setUsername(null);
  }

  async function me() {
    return api('/auth/me');
  }

  async function login(username, password) {
    const res = await fetch(API_BASE + '/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || res.statusText);
    return data;
  }

  async function listReservations(params = {}) {
    const q = new URLSearchParams();
    if (params.status) q.set('status', params.status);
    if (params.search) q.set('search', params.search);
    const query = q.toString();
    return api('/reservations' + (query ? '?' + query : ''));
  }

  async function getReservation(id) {
    return api('/reservations/' + id);
  }

  async function createReservation(body) {
    return api('/reservations', { method: 'POST', body: JSON.stringify(body) });
  }

  async function cancelReservation(id) {
    return api('/reservations/' + id + '/cancel', { method: 'POST' });
  }

  async function checkoutReservation(id) {
    return api('/reservations/' + id + '/checkout', { method: 'POST' });
  }

  async function listRooms() {
    return api('/rooms');
  }

  async function getAvailableRooms(roomType, checkIn, checkOut) {
    return api('/rooms/available?roomType=' + encodeURIComponent(roomType) + '&checkIn=' + checkIn + '&checkOut=' + checkOut);
  }

  async function getBill(reservationId) {
    return api('/billing/bill/' + reservationId);
  }

  async function getOccupancyReport() {
    return api('/reports/occupancy');
  }

  async function getRevenueReport() {
    return api('/reports/revenue');
  }

  async function getAuditLog() {
    return api('/reports/audit');
  }

  async function listUsers() {
    return api('/users');
  }

  /** Create a new user (admin only). Body: username, password, fullName, email, role (STAFF|ADMINISTRATOR). */
  async function createUser(body) {
    return api('/users', { method: 'POST', body: JSON.stringify(body) });
  }

  /** Update user by id (admin only). Body: fullName, email, role, password (optional). */
  async function updateUser(id, body) {
    return api('/users/' + encodeURIComponent(id), { method: 'PUT', body: JSON.stringify(body) });
  }

  /** Deactivate user by id (admin only). Cannot delete self or last admin. */
  async function deleteUser(id) {
    return api('/users/' + encodeURIComponent(id), { method: 'DELETE' });
  }

  /** Get one user by id (admin only, for edit form). */
  async function getUser(id) {
    return api('/users/' + encodeURIComponent(id));
  }

  // ---------- Room status / Housekeeping ----------
  async function setRoomStatus(roomId, status, notes) {
    return api('/rooms/' + encodeURIComponent(roomId) + '/status', { method: 'PUT', body: JSON.stringify({ status, notes: notes || '' }) });
  }

  // ---------- Guests ----------
  async function listGuests(q) {
    return api('/guests' + (q ? '?q=' + encodeURIComponent(q) : ''));
  }
  async function getGuest(id) { return api('/guests/' + id); }
  async function createGuest(body) { return api('/guests', { method: 'POST', body: JSON.stringify(body) }); }
  async function updateGuest(id, body) { return api('/guests/' + id, { method: 'PUT', body: JSON.stringify(body) }); }

  // ---------- Extra charges & Bill print ----------
  async function getBillPrint(reservationId) { return api('/billing/bill/' + encodeURIComponent(reservationId) + '/print'); }
  async function getExtraCharges(reservationId) { return api('/billing/bill/' + encodeURIComponent(reservationId) + '/extra-charges'); }
  async function addExtraCharge(reservationId, description, amount) { return api('/billing/bill/' + encodeURIComponent(reservationId) + '/extra-charges', { method: 'POST', body: JSON.stringify({ description, amount }) }); }
  async function deleteExtraCharge(id) { return api('/extra-charges/' + id, { method: 'DELETE' }); }

  // ---------- Reports: date range & export ----------
  async function getRevenueByRange(from, to) {
    let u = '/reports/revenue-by-range';
    const p = [];
    if (from) p.push('from=' + encodeURIComponent(from));
    if (to) p.push('to=' + encodeURIComponent(to));
    if (p.length) u += '?' + p.join('&');
    return api(u);
  }
  async function exportCsv(type, from, to) {
    let u = '/export/csv?type=' + encodeURIComponent(type || 'reservations');
    if (from) u += '&from=' + encodeURIComponent(from);
    if (to) u += '&to=' + encodeURIComponent(to);
    const data = await api(u);
    const bytes = Uint8Array.from(atob(data.content), c => c.charCodeAt(0));
    const blob = new Blob([bytes], { type: 'text/csv' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = data.filename || 'export.csv';
    a.click();
    URL.revokeObjectURL(a.href);
  }

  // ---------- Maintenance ----------
  async function listMaintenance(roomId) { return api('/maintenance' + (roomId ? '?roomId=' + encodeURIComponent(roomId) : '')); }
  async function createMaintenance(body) { return api('/maintenance', { method: 'POST', body: JSON.stringify(body) }); }
  async function updateMaintenance(id, patch) { return api('/maintenance/' + id, { method: 'PATCH', body: JSON.stringify(patch) }); }

  const MENU = [
    { id: 'dashboard', icon: 'fa-gauge-high', label: 'Dashboard' },
    { id: 'reservations', icon: 'fa-calendar-days', label: 'Reservations' },
    { id: 'new-reservation', icon: 'fa-calendar-plus', label: 'New Booking' },
    { id: 'rooms', icon: 'fa-door-open', label: 'Rooms' },
    { id: 'billing', icon: 'fa-file-invoice-dollar', label: 'Billing' },
    { id: 'reports', icon: 'fa-chart-column', label: 'Reports' },
    { id: 'guests', icon: 'fa-address-book', label: 'Guests' },
    { id: 'maintenance', icon: 'fa-wrench', label: 'Maintenance' },
    { id: 'users', icon: 'fa-users', label: 'Users', adminOnly: true },
    { id: 'help', icon: 'fa-circle-question', label: 'Help' },
  ];

  function showToast(msg, type = 'success') {
    const el = document.createElement('div');
    el.className = 'toast ' + type;
    el.textContent = msg;
    el.onclick = () => el.remove();
    toastContainer.appendChild(el);
    setTimeout(() => el.remove(), 4000);
  }

  function renderNav() {
    const nav = document.getElementById('nav-menu');
    const isAdmin = currentUser && currentUser.role === 'ADMINISTRATOR';
    nav.innerHTML = MENU.filter((m) => !m.adminOnly || isAdmin)
      .map(
        (m) =>
          '<button class="nav-item' + (currentPage === m.id ? ' active' : '') + '" data-page="' + m.id + '">' +
          '<i class="fa-solid ' + m.icon + '"></i> ' + m.label + '</button>'
      )
      .join('');
    nav.querySelectorAll('.nav-item').forEach((btn) => {
      btn.addEventListener('click', () => switchPage(btn.dataset.page));
    });
  }

  function switchPage(page) {
    currentPage = page;
    renderNav();
    if (page === 'dashboard') renderDashboard();
    else if (page === 'reservations') renderReservations();
    else if (page === 'new-reservation') renderNewReservation();
    else if (page === 'rooms') renderRooms();
    else if (page === 'billing') renderBilling();
    else if (page === 'reports') renderReports();
    else if (page === 'guests') renderGuests();
    else if (page === 'maintenance') renderMaintenance();
    else if (page === 'users') renderUsers();
    else if (page === 'help') renderHelp();
  }

  function renderDashboard() {
    Promise.all([listReservations(), listRooms(), getOccupancyReport(), getRevenueReport()])
      .then(([reservations, rooms, occupancy, revenue]) => {
        const confirmed = reservations.filter((r) => r.status === 'CONFIRMED').length;
        const totalRev = reservations.filter((r) => r.status !== 'CANCELLED').reduce((s, r) => s + (r.total || 0), 0);
        const recent = [...reservations].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)).slice(0, 5);
        const statusBadge = (s) => ({ CONFIRMED: 'confirmed', CANCELLED: 'cancelled', CHECKED_OUT: 'checkedout' }[s] || '');
        pageContent.innerHTML =
          '<h2 class="page-title">Dashboard</h2>' +
          '<p class="page-subtitle">Welcome back, ' + currentUser.fullName + ' - ' + new Date().toLocaleDateString('en-LK', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' }) + '</p>' +
          '<div class="stat-grid">' +
          '<div class="stat-card"><div class="label">Active Bookings</div><div class="value" style="color:#00796b">' + confirmed + '</div><div class="sub">' + reservations.length + ' total</div></div>' +
          '<div class="stat-card"><div class="label">Total Revenue</div><div class="value" style="color:#bf360c">LKR ' + (totalRev / 1000).toFixed(0) + 'K</div><div class="sub">All time</div></div>' +
          '<div class="stat-card"><div class="label">Checked Out</div><div class="value" style="color:#00897b">' + reservations.filter((r) => r.status === 'CHECKED_OUT').length + '</div></div>' +
          '<div class="stat-card"><div class="label">Cancellations</div><div class="value" style="color:#c62828">' + reservations.filter((r) => r.status === 'CANCELLED').length + '</div></div>' +
          '</div>' +
          '<div class="card"><h3 style="color:#004d40;margin-bottom:16px">Recent Reservations</h3><table><thead><tr><th>Res. No.</th><th>Guest</th><th>Room</th><th>Check-In</th><th>Nights</th><th>Status</th></tr></thead><tbody>' +
          recent
            .map((r) => {
              const room = rooms.find((rm) => rm.id === r.roomId);
              return '<tr><td style="color:#00796b;font-weight:600">' + r.id + '</td><td>' + r.guestName + '</td><td>' + (room ? room.roomNumber : '') + '</td><td>' + r.checkInDate + '</td><td>' + r.nights + 'N</td><td><span class="badge ' + statusBadge(r.status) + '">' + r.status + '</span></td></tr>';
            })
            .join('') +
          '</tbody></table></div>';
      })
      .catch((e) => {
        pageContent.innerHTML = '<p style="color:#c62828">Error: ' + e.message + '</p>';
      });
  }

  function renderReservations() {
    const searchEl = document.createElement('input');
    searchEl.placeholder = 'Search by guest name…';
    searchEl.className = 'input';
    searchEl.style.cssText = 'max-width:300px';
    let filter = 'ALL';
    let search = '';
    function refresh() {
      listReservations({ status: filter !== 'ALL' ? filter : undefined, search: search || undefined })
        .then((reservations) => listRooms().then((rooms) => ({ reservations, rooms })))
        .then(({ reservations, rooms }) => {
          const statusBadge = (s) => ({ CONFIRMED: 'confirmed', CANCELLED: 'cancelled', CHECKED_OUT: 'checkedout' }[s] || '');
          pageContent.innerHTML =
            '<h2 class="page-title">Reservations</h2><p class="page-subtitle">Manage all guest bookings</p>' +
            '<div style="display:flex;align-items:center;gap:12px;margin-bottom:20px;flex-wrap:wrap"></div>' +
            '<div class="card" style="overflow:auto"><table><thead><tr><th>Res. No.</th><th>Guest</th><th>Room</th><th>Check-In</th><th>Check-Out</th><th>Nights</th><th>Total (LKR)</th><th>Status</th><th>Actions</th></tr></thead><tbody>' +
            reservations
              .map((r) => {
                const room = rooms.find((rm) => rm.id === r.roomId);
                let actions = '';
                if (r.status === 'CONFIRMED') {
                  actions = '<button class="btn btn-secondary checkout-btn" data-id="' + r.id + '">Check Out</button> <button class="btn cancel-btn" data-id="' + r.id + '" style="background:rgba(198,40,40,0.1);color:#c62828;border:1px solid rgba(198,40,40,0.25)">Cancel</button>';
                }
                return '<tr class="row-click" data-id="' + r.id + '"><td style="color:#00796b;font-weight:600">' + r.id + '</td><td>' + r.guestName + '</td><td>' + (room ? room.roomNumber : '') + '</td><td>' + r.checkInDate + '</td><td>' + r.checkOutDate + '</td><td>' + r.nights + '</td><td style="color:#bf360c">' + (r.total || 0).toLocaleString() + '</td><td><span class="badge ' + statusBadge(r.status) + '">' + r.status + '</span></td><td>' + actions + '</td></tr>';
              })
              .join('') +
            '</tbody></table></div>';
          const wrap = pageContent.querySelector('div[style*="display:flex"]');
          wrap.appendChild(searchEl);
          ['ALL', 'CONFIRMED', 'CHECKED_OUT', 'CANCELLED'].forEach((s) => {
            const b = document.createElement('button');
            b.className = 'btn ' + (filter === s ? 'btn-success' : 'btn-secondary');
            b.textContent = s;
            b.onclick = () => {
              filter = s;
              refresh();
            };
            wrap.appendChild(b);
          });
          searchEl.oninput = () => {
            search = searchEl.value.trim();
            refresh();
          };
          pageContent.querySelectorAll('.checkout-btn').forEach((btn) => {
            btn.onclick = (e) => {
              e.stopPropagation();
              checkoutReservation(btn.dataset.id).then(() => { showToast('Checkout done'); refresh(); }).catch((err) => showToast(err.message, 'error'));
            };
          });
          pageContent.querySelectorAll('.cancel-btn').forEach((btn) => {
            btn.onclick = (e) => {
              e.stopPropagation();
              if (confirm('Cancel reservation ' + btn.dataset.id + '?')) {
                cancelReservation(btn.dataset.id).then(() => { showToast('Cancelled'); refresh(); }).catch((err) => showToast(err.message, 'error'));
              }
            };
          });
          pageContent.querySelectorAll('.row-click').forEach((row) => {
            row.onclick = () => {
              getReservation(row.dataset.id).then((r) => {
                const room = rooms.find((rm) => rm.id === r.roomId);
                const d = ['Guest', r.guestName, 'Address', r.guestAddress, 'Contact', r.contactNumber, 'Email', r.email, 'Room', room ? room.roomNumber + ' - ' + room.roomType : '', 'Check-In', r.checkInDate, 'Check-Out', r.checkOutDate, 'Nights', r.nights, 'Total', 'LKR ' + (r.total || 0).toLocaleString()];
                let html = '<div class="modal-overlay" id="detail-modal"><div class="modal"><h3 style="margin-bottom:24px">' + r.id + '</h3>';
                for (let i = 0; i < d.length; i += 2) html += '<div style="display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px solid #e0f2f1"><span style="color:#546e7a">' + d[i] + '</span><span>' + d[i + 1] + '</span></div>';
                html += '<button class="btn btn-secondary" style="margin-top:20px;width:100%" id="close-modal">Close</button></div></div>';
                document.body.insertAdjacentHTML('beforeend', html);
                document.getElementById('close-modal').onclick = () => document.getElementById('detail-modal').remove();
                document.getElementById('detail-modal').onclick = (ev) => { if (ev.target.id === 'detail-modal') ev.target.remove(); };
              });
            };
          });
        })
        .catch((e) => (pageContent.innerHTML = '<p style="color:#c62828">Error: ' + e.message + '</p>'));
    }
    refresh();
  }

  function renderNewReservation() {
    const step1 = '<div class="card"><h3 style="margin-bottom:16px">Step 1 - Guest details</h3><div id="step1-fields"></div><button class="btn btn-success" id="to-step2">Continue to Booking</button></div>';
    const step2 = '<div class="card"><h3 style="margin-bottom:16px">Step 2 - Room & dates</h3><div id="step2-fields"></div><button class="btn btn-secondary" id="back-step1">← Back</button> <button class="btn btn-success" id="confirm-booking">Confirm Booking</button></div>';
    pageContent.innerHTML = '<h2 class="page-title">New Reservation</h2><p class="page-subtitle">Complete the form to create a new guest booking</p><div id="wizard"></div>';
    const wizard = document.getElementById('wizard');
    let step = 1;
    const form = { guestName: '', guestAddress: '', contactNumber: '', email: '', nationality: '', roomType: '', checkInDate: '', checkOutDate: '', specialRequests: '' };
    const today = new Date().toISOString().split('T')[0];

    function showStep() {
      if (step === 1) {
        wizard.innerHTML = step1;
        const f = document.getElementById('step1-fields');
        ['guestName', 'guestAddress', 'contactNumber', 'email', 'nationality'].forEach((name) => {
          const label = name.replace(/([A-Z])/g, ' $1').replace(/^./, (s) => s.toUpperCase());
          f.innerHTML += '<div class="form-group"><label>' + label + ' *</label><input type="' + (name === 'email' ? 'email' : 'text') + '" id="f-' + name + '" class="input" placeholder="' + label + '" value="' + (form[name] || '') + '"></div>';
        });
        f.querySelectorAll('input').forEach((inp) => {
          inp.oninput = () => (form[inp.id.replace('f-', '')] = inp.value);
        });
        document.getElementById('to-step2').onclick = () => {
          form.guestName = document.getElementById('f-guestName').value;
          form.guestAddress = document.getElementById('f-guestAddress').value;
          form.contactNumber = document.getElementById('f-contactNumber').value;
          form.email = document.getElementById('f-email').value;
          form.nationality = document.getElementById('f-nationality').value;
          if (!form.guestName || !form.contactNumber || !form.email) {
            showToast('Please fill required guest fields', 'error');
            return;
          }
          step = 2;
          showStep();
        };
      } else {
        wizard.innerHTML = step2;
        const f = document.getElementById('step2-fields');
        f.innerHTML =
          '<div class="form-group"><label>Room Type *</label><select id="f-roomType" class="input"><option value="">-- Select --</option><option value="STANDARD">Standard - LKR 8,000/night</option><option value="DELUXE">Deluxe - LKR 15,000/night</option><option value="SUITE">Suite - LKR 25,000/night</option></select></div>' +
          '<div class="form-group"><label>Check-In *</label><input type="date" id="f-checkInDate" class="input" min="' + today + '"></div>' +
          '<div class="form-group"><label>Check-Out *</label><input type="date" id="f-checkOutDate" class="input"></div>' +
          '<div class="form-group"><label>Special Requests</label><input type="text" id="f-specialRequests" class="input" placeholder="Optional"></div>';
        document.getElementById('f-roomType').value = form.roomType;
        document.getElementById('f-checkInDate').value = form.checkInDate;
        document.getElementById('f-checkOutDate').value = form.checkOutDate;
        document.getElementById('f-specialRequests').value = form.specialRequests || '';
        f.querySelectorAll('input, select').forEach((inp) => {
          inp.oninput = inp.onchange = () => (form[inp.id.replace('f-', '')] = inp.value);
        });
        document.getElementById('back-step1').onclick = () => {
          step = 1;
          showStep();
        };
        document.getElementById('confirm-booking').onclick = () => {
          form.roomType = document.getElementById('f-roomType').value;
          form.checkInDate = document.getElementById('f-checkInDate').value;
          form.checkOutDate = document.getElementById('f-checkOutDate').value;
          form.specialRequests = document.getElementById('f-specialRequests').value;
          if (!form.roomType || !form.checkInDate || !form.checkOutDate) {
            showToast('Please select room type and dates', 'error');
            return;
          }
          if (form.checkOutDate <= form.checkInDate) {
            showToast('Check-out must be after check-in', 'error');
            return;
          }
          createReservation(form)
            .then((res) => {
              showToast('Reservation ' + res.id + ' confirmed!');
              pageContent.innerHTML = '<div class="card" style="text-align:center"><div style="font-size:48px;margin-bottom:16px;color:#00796b"><i class="fa-solid fa-circle-check"></i></div><h2 style="color:#00796b">Reservation Confirmed!</h2><p style="margin:16px 0">' + res.id + ' - ' + res.guestName + '</p><button class="btn btn-success" onclick="location.reload()">Back to Dashboard</button></div>';
            })
            .catch((err) => showToast(err.message, 'error'));
        };
      }
    }
    showStep();
  }

  function renderRooms() {
    Promise.all([listRooms(), listReservations()]).then(([rooms, reservations]) => {
      const today = new Date().toISOString().split('T')[0];
      const occupied = new Set(reservations.filter((r) => r.status === 'CONFIRMED' && r.checkInDate <= today && r.checkOutDate > today).map((r) => r.roomId));
      const statuses = ['AVAILABLE', 'CLEANING', 'OUT_OF_ORDER'];
      pageContent.innerHTML =
        '<h2 class="page-title">Room Inventory</h2><p class="page-subtitle">Overview and housekeeping status</p>' +
        '<div style="display:grid;grid-template-columns:repeat(3,1fr);gap:16px">' +
        rooms
          .map((r) => {
            const occ = occupied.has(r.id);
            const rs = r.roomStatus || 'AVAILABLE';
            const opts = statuses.map((s) => '<option value="' + s + '"' + (s === rs ? ' selected' : '') + '>' + s + '</option>').join('');
            return (
              '<div class="card" data-room-id="' + r.id + '"><div style="display:flex;justify-content:space-between;margin-bottom:12px"><div><strong style="font-size:20px">Room ' + r.roomNumber + '</strong><div style="color:#546e7a;font-size:12px">Floor ' + r.floor + ' · ' + (r.view || '') + '</div></div><span class="badge ' + (occ ? 'cancelled' : 'confirmed') + '">' + (occ ? 'OCCUPIED' : (rs === 'CLEANING' ? 'CLEANING' : rs === 'OUT_OF_ORDER' ? 'OUT OF ORDER' : 'AVAILABLE')) + '</span></div><div style="color:#bf360c;font-weight:700">LKR ' + r.nightlyRate.toLocaleString() + '/night</div><div style="margin-top:8px"><label style="font-size:11px;color:#546e7a">Status:</label><select class="input room-status-select" data-room-id="' + r.id + '" style="margin-top:4px;height:32px;padding:0 8px">' + opts + '</select><button class="btn btn-secondary btn-room-status" data-room-id="' + r.id + '" style="margin-top:6px;height:28px;font-size:11px">Update</button></div><div style="margin-top:8px">' + (r.amenities || []).map((a) => '<span style="background:#e0f2f1;padding:2px 8px;border-radius:12px;font-size:10px;margin-right:4px;color:#546e7a">' + a + '</span>').join('') + '</div></div>'
            );
          })
          .join('') +
        '</div>';
      pageContent.querySelectorAll('.btn-room-status').forEach((btn) => {
        btn.onclick = () => {
          const roomId = btn.dataset.roomId;
          const card = pageContent.querySelector('.card[data-room-id="' + roomId + '"]');
          const sel = card.querySelector('.room-status-select');
          setRoomStatus(roomId, sel.value, '').then(() => { showToast('Room status updated'); renderRooms(); }).catch((e) => showToast(e.message, 'error'));
        };
      });
    }).catch((e) => (pageContent.innerHTML = '<p style="color:#c62828">Error: ' + e.message + '</p>'));
  }

  function renderBilling() {
    pageContent.innerHTML =
      '<h2 class="page-title">Billing & Invoices</h2><p class="page-subtitle">Generate detailed bill for guest checkout</p>' +
      '<div class="card"><div style="display:flex;gap:12px"><input type="text" id="bill-res-id" class="input" placeholder="Reservation No. (e.g. RES-0005)" style="flex:1"><button class="btn btn-success" id="btn-generate-bill">Generate Bill</button></div><p id="bill-err" style="color:#c62828;margin-top:8px"></p></div>' +
      '<div id="bill-result"></div>';
    document.getElementById('btn-generate-bill').onclick = () => {
      const id = document.getElementById('bill-res-id').value.trim().toUpperCase();
      document.getElementById('bill-err').textContent = '';
      if (!id) {
        document.getElementById('bill-err').textContent = 'Please enter a reservation number';
        return;
      }
      getBill(id)
        .then((data) => {
          const r = data.reservation;
          const b = data.bill;
          document.getElementById('bill-result').innerHTML =
            '<div class="card"><h3 style="margin-bottom:16px">Invoice - ' + r.id + '</h3>' +
            '<p><strong>Bill To:</strong> ' + r.guestName + '<br>' + r.guestAddress + '<br>' + r.contactNumber + '</p>' +
            '<p style="margin-top:12px">Room ' + (data.room && data.room.roomNumber ? data.room.roomNumber : b.roomNumber) + ' - ' + (data.room && data.room.roomType ? data.room.roomType : b.roomType) + '<br>Check-In: ' + r.checkInDate + ' · Check-Out: ' + r.checkOutDate + '</p>' +
            '<table style="margin-top:16px"><tr><td>Sub-Total</td><td style="text-align:right">LKR ' + Number(b.subTotal).toLocaleString() + '</td></tr><tr><td>VAT (10%)</td><td style="text-align:right">LKR ' + Number(b.tax).toLocaleString() + '</td></tr><tr><td>Discount</td><td style="text-align:right">LKR ' + Number(b.discount).toLocaleString() + '</td></tr><tr style="font-weight:700;color:#00796b"><td>TOTAL</td><td style="text-align:right">LKR ' + Number(b.total).toLocaleString() + '</td></tr></table>' +
            '<div style="margin-top:16px"><button class="btn btn-success" id="btn-print-bill">Print Bill</button> <button class="btn btn-secondary" id="btn-download-bill">Download</button></div>' +
            '</div><div class="card" id="bill-extra-charges"><h4 style="margin-bottom:12px">Extra Charges</h4><div id="extra-charges-list"></div><div style="margin-top:12px"><input type="text" id="extra-desc" class="input" placeholder="Description" style="max-width:200px;display:inline-block;margin-right:8px"><input type="number" id="extra-amount" class="input" placeholder="Amount" style="max-width:120px;display:inline-block;margin-right:8px"><button class="btn btn-success" id="btn-add-extra">Add</button></div></div>';
          document.getElementById('bill-result').dataset.resId = id;
          document.getElementById('btn-print-bill').onclick = () => getBillPrint(id).then((d) => { const w = window.open('', '_blank'); w.document.write(d.html); w.document.close(); w.print(); });
          document.getElementById('btn-download-bill').onclick = () => getBillPrint(id).then((d) => { const w = window.open('', '_blank'); w.document.write(d.html); w.document.close(); const a = w.document.createElement('a'); a.href = 'data:text/html;charset=utf-8,' + encodeURIComponent(d.html); a.download = 'invoice-' + id + '.html'; a.click(); });
          function refreshExtras() {
            getExtraCharges(id).then((list) => {
              const el = document.getElementById('extra-charges-list');
              if (!el) return;
              el.innerHTML = list.length ? '<table><thead><tr><th>Description</th><th>Amount</th><th></th></tr></thead><tbody>' + list.map((e) => '<tr><td>' + e.description + '</td><td>LKR ' + Number(e.amount).toLocaleString() + '</td><td><button class="btn btn-secondary" style="height:28px;font-size:11px" data-id="' + e.id + '">Remove</button></td></tr>').join('') + '</tbody></table>' : '<p style="color:#546e7a">No extra charges.</p>';
              el.querySelectorAll('button[data-id]').forEach((b) => b.onclick = () => deleteExtraCharge(b.dataset.id).then(() => { showToast('Removed'); refreshExtras(); }).catch((e) => showToast(e.message, 'error')));
            }).catch(() => {});
          }
          refreshExtras();
          document.getElementById('btn-add-extra').onclick = () => {
            const desc = document.getElementById('extra-desc').value.trim();
            const amt = document.getElementById('extra-amount').value;
            if (!desc || !amt) { showToast('Description and amount required', 'error'); return; }
            addExtraCharge(id, desc, parseFloat(amt)).then(() => { document.getElementById('extra-desc').value = ''; document.getElementById('extra-amount').value = ''; showToast('Added'); refreshExtras(); }).catch((e) => showToast(e.message, 'error'));
          };
        })
        .catch((err) => {
          document.getElementById('bill-err').textContent = err.message;
          document.getElementById('bill-result').innerHTML = '';
        });
    };
  }

  function renderReports() {
    const tabs = ['occupancy', 'revenue', 'revenue-by-range', 'export', 'guests', 'status'].concat(currentUser && currentUser.role === 'ADMINISTRATOR' ? ['audit'] : []);
    pageContent.innerHTML = '<h2 class="page-title">Reports</h2><p class="page-subtitle">Decision-support reports</p><div id="report-tabs" style="margin-bottom:24px"></div><div id="report-body" class="card"></div>';
    const tabEl = document.getElementById('report-tabs');
    const bodyEl = document.getElementById('report-body');
    tabs.forEach((t) => {
      const b = document.createElement('button');
      b.className = 'btn btn-secondary';
      b.textContent = t.replace(/-/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
      b.onclick = () => loadReport(t, bodyEl);
      tabEl.appendChild(b);
    });
    loadReport('occupancy', bodyEl);
  }

  function loadReport(type, el) {
    if (type === 'occupancy') {
      getOccupancyReport().then((occ) => {
        el.innerHTML = '<h3 style="margin-bottom:16px">Occupancy by room type</h3><table><thead><tr><th>Type</th><th>Total</th><th>Occupied</th><th>Rate/night</th></tr></thead><tbody>' + Object.entries(occ).map(([k, v]) => '<tr><td>' + k + '</td><td>' + v.totalRooms + '</td><td>' + v.occupied + '</td><td>LKR ' + Number(v.nightlyRate).toLocaleString() + '</td></tr>').join('') + '</tbody></table>';
      });
    } else if (type === 'revenue') {
      getRevenueReport().then((rows) => {
        el.innerHTML = '<h3 style="margin-bottom:16px">Monthly Revenue</h3><table><thead><tr><th>Month</th><th>Bookings</th><th>Revenue (LKR)</th></tr></thead><tbody>' + rows.map((r) => '<tr><td>' + r.month + '</td><td>' + r.bookings + '</td><td>' + Number(r.revenue).toLocaleString() + '</td></tr>').join('') + '</tbody></table>';
      });
    } else if (type === 'revenue-by-range') {
      el.innerHTML = '<h3 style="margin-bottom:16px">Revenue by date range</h3><div style="display:flex;gap:12px;margin-bottom:16px"><input type="date" id="report-from" class="input" style="width:160px"><input type="date" id="report-to" class="input" style="width:160px"><button class="btn btn-success" id="report-range-btn">Apply</button></div><div id="report-range-table"></div>';
      document.getElementById('report-range-btn').onclick = () => {
        const from = document.getElementById('report-from').value;
        const to = document.getElementById('report-to').value;
        getRevenueByRange(from || undefined, to || undefined).then((rows) => {
          document.getElementById('report-range-table').innerHTML = rows.length ? '<table><thead><tr><th>Date</th><th>Bookings</th><th>Revenue (LKR)</th></tr></thead><tbody>' + rows.map((r) => '<tr><td>' + r.date + '</td><td>' + r.bookings + '</td><td>' + Number(r.revenue).toLocaleString() + '</td></tr>').join('') + '</tbody></table>' : '<p>No data for this range.</p>';
        }).catch((e) => (document.getElementById('report-range-table').innerHTML = '<p style="color:#c62828">' + e.message + '</p>'));
      };
    } else if (type === 'export') {
      el.innerHTML = '<h3 style="margin-bottom:16px">Export CSV</h3><div style="display:flex;gap:12px;flex-wrap:wrap;align-items:center"><span>Type:</span><select id="export-type" class="input" style="width:160px"><option value="reservations">Reservations</option><option value="revenue">Revenue by date</option></select><span>From:</span><input type="date" id="export-from" class="input" style="width:160px"><span>To:</span><input type="date" id="export-to" class="input" style="width:160px"><button class="btn btn-success" id="export-btn">Download CSV</button></div>';
      document.getElementById('export-btn').onclick = () => {
        const t = document.getElementById('export-type').value;
        const from = document.getElementById('export-from').value || undefined;
        const to = document.getElementById('export-to').value || undefined;
        exportCsv(t, from, to).then(() => showToast('Download started')).catch((e) => showToast(e.message, 'error'));
      };
    } else if (type === 'guests') {
      listReservations().then((list) => {
        listRooms().then((rooms) => {
          el.innerHTML = '<h3 style="margin-bottom:16px">Guest History</h3><table><thead><tr><th>Guest</th><th>Res. No.</th><th>Room</th><th>Check-In</th><th>Total</th><th>Status</th></tr></thead><tbody>' + list.map((r) => {
            const room = rooms.find((rm) => rm.id === r.roomId);
            const badge = { CONFIRMED: 'confirmed', CANCELLED: 'cancelled', CHECKED_OUT: 'checkedout' }[r.status] || '';
            return '<tr><td>' + r.guestName + '</td><td>' + r.id + '</td><td>' + (room ? room.roomNumber : '') + '</td><td>' + r.checkInDate + '</td><td>' + (r.total || 0).toLocaleString() + '</td><td><span class="badge ' + badge + '">' + r.status + '</span></td></tr>';
          }).join('') + '</tbody></table>';
        });
      });
    } else if (type === 'status') {
      listReservations().then((list) => {
        const counts = { CONFIRMED: 0, CHECKED_OUT: 0, CANCELLED: 0 };
        list.forEach((r) => { counts[r.status] = (counts[r.status] || 0) + 1; });
        el.innerHTML = '<h3 style="margin-bottom:16px">Status Summary</h3><div style="display:grid;grid-template-columns:repeat(3,1fr);gap:16px">' + ['CONFIRMED', 'CHECKED_OUT', 'CANCELLED'].map((s) => '<div class="stat-card"><div class="label">' + s + '</div><div class="value">' + (counts[s] || 0) + '</div></div>').join('') + '</div>';
      });
    } else if (type === 'audit') {
      getAuditLog().then((log) => {
        el.innerHTML = '<h3 style="margin-bottom:16px">Audit Log</h3><table><thead><tr><th>Time</th><th>Action</th><th>Table</th><th>Record ID</th><th>User</th></tr></thead><tbody>' + log.map((e) => '<tr><td>' + e.createdAt + '</td><td>' + e.action + '</td><td>' + e.tableName + '</td><td>' + e.recordId + '</td><td>' + e.userId + '</td></tr>').join('') + '</tbody></table>';
      }).catch((err) => (el.innerHTML = '<p style="color:#c62828">' + err.message + '</p>'));
    }
  }

  /**
   * Renders the User Management page (admin only): list users with Add / Edit / Delete.
   * Add: opens modal with username, password, full name, email, role.
   * Edit: opens modal with full name, email, role, optional new password.
   * Delete: confirms then deactivates user (soft delete).
   */
  function renderGuests() {
    const q = document.getElementById('guest-search') ? document.getElementById('guest-search').value : '';
    listGuests(q || undefined)
      .then((guests) => {
        pageContent.innerHTML =
          '<h2 class="page-title">Guests</h2><p class="page-subtitle">Guest profiles and history</p>' +
          '<div class="card"><div style="margin-bottom:16px"><input type="text" id="guest-search" class="input" placeholder="Search by name or email" style="max-width:300px;margin-right:8px"><button class="btn btn-success" id="btn-search-guests">Search</button> <button class="btn btn-secondary" id="btn-add-guest">Add Guest</button></div>' +
          '<table><thead><tr><th>ID</th><th>Full Name</th><th>Email</th><th>Phone</th><th>Nationality</th></tr></thead><tbody>' +
          (guests.length ? guests.map((g) => '<tr><td>' + g.id + '</td><td>' + (g.fullName || '') + '</td><td>' + (g.email || '') + '</td><td>' + (g.phone || '') + '</td><td>' + (g.nationality || '') + '</td></tr>').join('') : '<tr><td colspan="5">No guests found.</td></tr>') +
          '</tbody></table></div>';
        document.getElementById('btn-search-guests').onclick = () => renderGuests();
        document.getElementById('btn-add-guest').onclick = () => {
          const name = prompt('Full name');
          if (!name) return;
          const email = prompt('Email (optional)');
          const phone = prompt('Phone (optional)');
          createGuest({ fullName: name, email: email || '', phone: phone || '' }).then(() => { showToast('Guest added'); renderGuests(); }).catch((e) => showToast(e.message, 'error'));
        };
      })
      .catch((e) => (pageContent.innerHTML = '<p style="color:#c62828">Error: ' + e.message + '</p>'));
  }

  function renderMaintenance() {
    listMaintenance()
      .then((list) => {
        listRooms().then((rooms) => {
          pageContent.innerHTML =
            '<h2 class="page-title">Maintenance</h2><p class="page-subtitle">Room issues and status</p>' +
            '<div class="card" style="margin-bottom:20px"><h4 style="margin-bottom:12px">Report issue</h4><div style="display:grid;grid-template-columns:1fr 1fr 1fr auto;gap:12px;align-items:end"><div class="form-group"><label>Room</label><select id="m-room" class="input">' + rooms.map((r) => '<option value="' + r.id + '">' + r.roomNumber + '</option>').join('') + '</select></div><div class="form-group"><label>Title</label><input type="text" id="m-title" class="input" placeholder="e.g. AC not cooling"></div><div class="form-group"><label>Category</label><select id="m-category" class="input"><option value="AC">AC</option><option value="PLUMBING">Plumbing</option><option value="ELECTRICAL">Electrical</option><option value="FURNITURE">Furniture</option><option value="OTHER">Other</option></select></div><button class="btn btn-success" id="m-submit">Add</button></div><div class="form-group" style="margin-top:8px"><label>Description</label><input type="text" id="m-desc" class="input" placeholder="Optional"></div></div>' +
            '<div class="card"><table><thead><tr><th>Room</th><th>Title</th><th>Category</th><th>Status</th><th>Assigned</th><th>Reported</th><th>Actions</th></tr></thead><tbody>' +
            list.map((i) => {
              const room = rooms.find((r) => r.id === i.roomId);
              const statusOpts = ['OPEN', 'IN_PROGRESS', 'DONE'].map((s) => '<option value="' + s + '"' + (i.status === s ? ' selected' : '') + '>' + s + '</option>').join('');
              return '<tr data-id="' + i.id + '"><td>' + (room ? room.roomNumber : i.roomId) + '</td><td>' + (i.title || '') + '</td><td>' + (i.category || '') + '</td><td><select class="m-status" data-id="' + i.id + '">' + statusOpts + '</select></td><td>' + (i.assignedTo || '-') + '</td><td>' + (i.reportedAt || '') + '</td><td><button class="btn btn-secondary btn-m-status" data-id="' + i.id + '" style="height:28px;font-size:11px">Update</button></td></tr>';
            }).join('') +
            '</tbody></table></div>';
          pageContent.querySelectorAll('.btn-m-status').forEach((btn) => {
            btn.onclick = () => {
              const id = btn.dataset.id;
              const row = pageContent.querySelector('tr[data-id="' + id + '"]');
              const sel = row.querySelector('.m-status');
              updateMaintenance(id, { status: sel.value }).then(() => { showToast('Updated'); renderMaintenance(); }).catch((e) => showToast(e.message, 'error'));
            };
          });
          document.getElementById('m-submit').onclick = () => {
            const roomId = document.getElementById('m-room').value;
            const title = document.getElementById('m-title').value.trim();
            const category = document.getElementById('m-category').value;
            const desc = document.getElementById('m-desc').value.trim();
            if (!title) { showToast('Title required', 'error'); return; }
            createMaintenance({ roomId, title, description: desc, category, status: 'OPEN' }).then(() => { document.getElementById('m-title').value = ''; document.getElementById('m-desc').value = ''; showToast('Issue added'); renderMaintenance(); }).catch((e) => showToast(e.message, 'error'));
          };
        });
      })
      .catch((e) => (pageContent.innerHTML = '<p style="color:#c62828">Error: ' + e.message + '</p>'));
  }

  function renderUsers() {
    listUsers()
      .then((users) => {
        pageContent.innerHTML =
          '<h2 class="page-title">User Management</h2><p class="page-subtitle">Administrator only — add, edit, or deactivate staff.</p>' +
          '<div class="card"><div style="margin-bottom:16px"><button class="btn btn-success" id="btn-add-user"><i class="fa-solid fa-user-plus"></i> Add Staff</button></div>' +
          '<table><thead><tr><th>ID</th><th>Full Name</th><th>Username</th><th>Role</th><th>Email</th><th>Status</th><th>Actions</th></tr></thead><tbody>' +
          users.map((u) => {
            const roleBadge = u.role === 'ADMINISTRATOR' ? 'checkedout' : 'confirmed';
            return '<tr data-user-id="' + u.id + '">' +
              '<td>' + u.id + '</td><td>' + (u.full_name || '') + '</td><td>' + u.username + '</td>' +
              '<td><span class="badge ' + roleBadge + '">' + u.role + '</span></td><td>' + (u.email || '') + '</td>' +
              '<td><span class="badge confirmed">ACTIVE</span></td>' +
              '<td><button class="btn btn-secondary btn-edit-user" data-id="' + u.id + '">Edit</button> ' +
              '<button class="btn btn-delete-user" data-id="' + u.id + '" data-username="' + (u.username || '') + '" style="background:rgba(198,40,40,0.1);color:#c62828;border:1px solid rgba(198,40,40,0.25)">Delete</button></td></tr>';
          }).join('') +
          '</tbody></table></div>';

        document.getElementById('btn-add-user').onclick = openAddUserModal;
        pageContent.querySelectorAll('.btn-edit-user').forEach((btn) => {
          btn.onclick = () => { const id = btn.dataset.id; getUser(id).then((u) => openEditUserModal(id, u)).catch((e) => showToast(e.message, 'error')); };
        });
        pageContent.querySelectorAll('.btn-delete-user').forEach((btn) => {
          btn.onclick = () => confirmDeleteUser(btn.dataset.id, btn.dataset.username);
        });
      })
      .catch((e) => (pageContent.innerHTML = '<p style="color:#c62828">Error: ' + e.message + '</p>'));
  }

  /** Opens modal to add a new user. Submits to createUser() then refreshes list. */
  function openAddUserModal() {
    const html = '<div class="modal-overlay" id="user-modal"><div class="modal">' +
      '<h3 style="margin-bottom:16px;color:#004d40"><i class="fa-solid fa-user-plus"></i> Add Staff</h3>' +
      '<div class="form-group"><label>Username *</label><input type="text" id="um-username" class="input" placeholder="Login name"></div>' +
      '<div class="form-group"><label>Password *</label><input type="password" id="um-password" class="input" placeholder="Password"></div>' +
      '<div class="form-group"><label>Full Name *</label><input type="text" id="um-fullname" class="input" placeholder="Full name"></div>' +
      '<div class="form-group"><label>Email</label><input type="email" id="um-email" class="input" placeholder="Email"></div>' +
      '<div class="form-group"><label>Role</label><select id="um-role" class="input"><option value="STAFF">STAFF</option><option value="ADMINISTRATOR">ADMINISTRATOR</option></select></div>' +
      '<p id="um-err" style="color:#c62828;font-size:12px;margin-bottom:12px"></p>' +
      '<div style="display:flex;gap:10px"><button class="btn btn-success" id="um-submit">Create User</button><button class="btn btn-secondary" id="um-cancel">Cancel</button></div></div></div>';
    document.body.insertAdjacentHTML('beforeend', html);
    document.getElementById('um-cancel').onclick = () => document.getElementById('user-modal').remove();
    document.getElementById('user-modal').onclick = (e) => { if (e.target.id === 'user-modal') e.target.remove(); };
    document.getElementById('um-submit').onclick = () => {
      const username = document.getElementById('um-username').value.trim();
      const password = document.getElementById('um-password').value;
      const fullName = document.getElementById('um-fullname').value.trim();
      const email = document.getElementById('um-email').value.trim();
      const role = document.getElementById('um-role').value;
      document.getElementById('um-err').textContent = '';
      if (!username) { document.getElementById('um-err').textContent = 'Username is required'; return; }
      if (!password) { document.getElementById('um-err').textContent = 'Password is required'; return; }
      if (!fullName) { document.getElementById('um-err').textContent = 'Full name is required'; return; }
      createUser({ username, password, fullName, email, role })
        .then(() => { document.getElementById('user-modal').remove(); showToast('User created'); renderUsers(); })
        .catch((err) => { document.getElementById('um-err').textContent = err.message || 'Failed to create user'; });
    };
  }

  /** Opens modal to edit user (u = { id, username, fullName, full_name, email, role }). Submits to updateUser() then refreshes list. */
  function openEditUserModal(id, u) {
    const username = u.username || '';
    const fullName = u.fullName || u.full_name || '';
    const email = u.email || '';
    const role = u.role || 'STAFF';
    const safe = (s) => String(s).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    const html = '<div class="modal-overlay" id="user-modal"><div class="modal">' +
      '<h3 style="margin-bottom:16px;color:#004d40"><i class="fa-solid fa-user-pen"></i> Edit User</h3>' +
      '<div class="form-group"><label>Username</label><input type="text" id="um-username" class="input" value="' + safe(username) + '" disabled></div>' +
      '<div class="form-group"><label>New password (leave blank to keep)</label><input type="password" id="um-password" class="input" placeholder="Leave blank to keep"></div>' +
      '<div class="form-group"><label>Full Name *</label><input type="text" id="um-fullname" class="input" value="' + safe(fullName) + '"></div>' +
      '<div class="form-group"><label>Email</label><input type="email" id="um-email" class="input" value="' + safe(email) + '"></div>' +
      '<div class="form-group"><label>Role</label><select id="um-role" class="input"><option value="STAFF"' + (role === 'STAFF' ? ' selected' : '') + '>STAFF</option><option value="ADMINISTRATOR"' + (role === 'ADMINISTRATOR' ? ' selected' : '') + '>ADMINISTRATOR</option></select></div>' +
      '<p id="um-err" style="color:#c62828;font-size:12px;margin-bottom:12px"></p>' +
      '<div style="display:flex;gap:10px"><button class="btn btn-success" id="um-submit">Save</button><button class="btn btn-secondary" id="um-cancel">Cancel</button></div></div></div>';
    document.body.insertAdjacentHTML('beforeend', html);
    document.getElementById('um-cancel').onclick = () => document.getElementById('user-modal').remove();
    document.getElementById('user-modal').onclick = (e) => { if (e.target.id === 'user-modal') e.target.remove(); };
    document.getElementById('um-submit').onclick = () => {
      const fullName = document.getElementById('um-fullname').value.trim();
      const email = document.getElementById('um-email').value.trim();
      const role = document.getElementById('um-role').value;
      const password = document.getElementById('um-password').value;
      document.getElementById('um-err').textContent = '';
      if (!fullName) { document.getElementById('um-err').textContent = 'Full name is required'; return; }
      const body = { fullName, email, role };
      if (password) body.password = password;
      updateUser(id, body)
        .then(() => { document.getElementById('user-modal').remove(); showToast('User updated'); renderUsers(); })
        .catch((err) => { document.getElementById('um-err').textContent = err.message || 'Failed to update user'; });
    };
  }

  /** Asks for confirmation then deactivates user. Cannot delete self or last admin. */
  function confirmDeleteUser(id, username) {
    if (!confirm('Deactivate user "' + username + '" (ID: ' + id + ')? They will not be able to log in.')) return;
    deleteUser(id)
      .then(() => { showToast('User deactivated'); renderUsers(); })
      .catch((err) => showToast(err.message || 'Failed to deactivate', 'error'));
  }

  function renderHelp() {
    pageContent.innerHTML =
      '<h2 class="page-title">Help & Documentation</h2><p class="page-subtitle">Ocean View Resort HRMS - Staff Guide</p>' +
      '<div class="card"><h3 style="color:#00796b;margin-bottom:12px"><i class="fa-solid fa-key"></i> Authentication</h3><p style="color:#546e7a;line-height:1.7">Log in with your username and password. Passwords are SHA-256 hashed. Always log out when leaving the terminal.</p></div>' +
      '<div class="card"><h3 style="color:#00796b;margin-bottom:12px"><i class="fa-solid fa-calendar-days"></i> Reservations</h3><p style="color:#546e7a;line-height:1.7">View and filter bookings. Click a row to see full details. Check Out and Cancel are available for CONFIRMED reservations.</p></div>' +
      '<div class="card"><h3 style="color:#00796b;margin-bottom:12px"><i class="fa-solid fa-calendar-plus"></i> New Booking</h3><p style="color:#546e7a;line-height:1.7">Two steps: (1) Guest details - name, address, contact, email, nationality. (2) Room type and dates. Confirm to create.</p></div>' +
      '<div class="card"><h3 style="color:#00796b;margin-bottom:12px"><i class="fa-solid fa-file-invoice-dollar"></i> Billing</h3><p style="color:#546e7a;line-height:1.7">Enter reservation number to generate invoice. 10% VAT and loyalty discount (returning guests) applied automatically.</p></div>' +
      '<div class="card"><h3 style="color:#00796b;margin-bottom:12px"><i class="fa-solid fa-users-gear"></i> User Management</h3><p style="color:#546e7a;line-height:1.7">Admin only. Add new staff or administrators (username, password, full name, email, role). Edit existing users (name, email, role, optional new password). Delete deactivates a user so they cannot log in. You cannot delete yourself or the last administrator.</p></div>' +
      '<div class="card"><h3 style="color:#00897b"><i class="fa-solid fa-sitemap"></i> Architecture</h3><p style="color:#546e7a;line-height:1.7;margin-top:8px">3-tier: Presentation (this HTML+JS app), Business (Java backend), Data (MySQL). Design patterns: Singleton, Factory, Repository, Observer, Facade.</p></div>';
  }

  function init() {
    document.getElementById('btn-login').addEventListener('click', () => {
      const username = document.getElementById('login-username').value.trim();
      const password = document.getElementById('login-password').value;
      document.getElementById('err-username').textContent = '';
      document.getElementById('err-password').textContent = '';
      if (!username) {
        document.getElementById('err-username').textContent = 'Username required';
        return;
      }
      if (!password) {
        document.getElementById('err-password').textContent = 'Password required';
        return;
      }
      document.getElementById('btn-login').disabled = true;
      login(username, password)
        .then((data) => {
          setUsername(data.user.username);
          currentUser = data.user;
          loginScreen.classList.add('hidden');
          appScreen.classList.remove('hidden');
          document.getElementById('user-info').innerHTML = '<strong>' + currentUser.fullName + '</strong><br><span class="role' + (currentUser.role === 'ADMINISTRATOR' ? ' admin' : '') + '">' + currentUser.role + '</span>';
          switchPage('dashboard');
        })
        .catch((err) => {
          document.getElementById('err-password').textContent = err.message || 'Invalid credentials';
        })
        .finally(() => (document.getElementById('btn-login').disabled = false));
    });

    document.getElementById('btn-logout').addEventListener('click', () => {
      logout();
      currentUser = null;
      appScreen.classList.add('hidden');
      loginScreen.classList.remove('hidden');
      showToast('You have been logged out.', 'info');
    });

    const username = getUsername();
    if (username) {
      me()
        .then((user) => {
          currentUser = user;
          loginScreen.classList.add('hidden');
          appScreen.classList.remove('hidden');
          document.getElementById('user-info').innerHTML = '<strong>' + currentUser.fullName + '</strong><br><span class="role' + (currentUser.role === 'ADMINISTRATOR' ? ' admin' : '') + '">' + currentUser.role + '</span>';
          switchPage('dashboard');
        })
        .catch(() => {
          setUsername(null);
        });
    }
  }

  init();
})();
