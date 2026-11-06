'use strict';

/* 管理端只组织服务端返回的数据；收银、权限、退款和库存仍复用 app.js 的业务接口。 */
const staffOriginalShell = renderShell;
renderShell = function () {
  staffOriginalShell();
  if (!isMember()) $('.shell')?.classList.add('staff-workspace');
};

function staffThumb(product, className = '') {
  const source = String(product.imageUrl || product.productImageUrl || '');
  const validImage = /^\/images\/products\/[A-Za-z0-9._-]+$/.test(source);
  return `<span class="staff-device-thumb ${className}">${validImage ? `<img src="${esc(source)}" alt="${esc(product.name || product.productName || '商品')}" loading="lazy">` : esc(product.emoji || '📦')}</span>`;
}

// 筛选分类后仍保留已选商品的图片；清单金额与库存由结账接口再次确认。
const staffProductCache = new Map();
const staffOriginalLoadCatalog = loadCatalog;
loadCatalog = async function () {
  await staffOriginalLoadCatalog();
  if (!isMember()) array(state.products).forEach(product => staffProductCache.set(num(product.id), product));
};
const staffOriginalBasketItems = basketItems;
basketItems = function () {
  const items = staffOriginalBasketItems();
  return isMember() ? items : items.map(item => ({ ...staffProductCache.get(num(item.productId)), ...item, imageUrl: item.imageUrl || staffProductCache.get(num(item.productId))?.imageUrl }));
};

function staffShortcut(page, label, detail, glyph) {
  return `<button class="staff-shortcut" data-action="navigate" data-page="${page}"><span class="staff-shortcut-icon">${icon(glyph)}</span><span><strong>${label}</strong><small>${detail}</small></span>${icon('arrow')}</button>`;
}

function staffHero(title, detail, queue) {
  return `<section class="staff-hero"><div><div class="eyebrow">JIXU DIGITAL / ${isAdmin() ? 'STORE OPERATIONS' : 'SALES WORKSPACE'}</div><h1>${esc(title)}</h1><p>${esc(detail)}</p><div class="staff-hero-actions"><button class="btn white" data-action="navigate" data-page="pos">${icon('queue')}进入收银工作台</button><button class="staff-hero-link" data-action="navigate" data-page="queue">查看排队看板 ${icon('arrow')}</button></div></div><div class="staff-live"><span class="staff-live-label"><i></i>${isAdmin() ? '门店实时状态' : '我的柜台状态'}</span><div><strong>${integer(queue.waitingCount)}</strong><span>等待确认</span><strong>${integer(queue.processingCount)}</strong><span>正在处理</span></div><small>${array(queue.counters).filter(counter => ['IDLE', 'WAITING', 'BUSY'].includes(counter.status)).length} 个收银台接收提交</small></div></section><div class="staff-overview-counters">${staffCounterRoster(queue)}</div>`;
}

const staffOriginalOverview = viewOverview;
viewOverview = async function () {
  if (isMember()) return staffOriginalOverview();
  const [ordersResult, queue, stats] = await Promise.all([api('/orders'), api('/queue'), isAdmin() ? api('/stats') : Promise.resolve(null)]);
  const orders = array(ordersResult), paid = orders.filter(order => order.status === 'PAID');
  const sales = paid.reduce((total, order) => total + num(order.payableAmount), 0);
  const hero = staffHero(isAdmin() ? '乔先生，今天也从容经营。' : `${state.user.displayName || state.user.username}，欢迎进入收银工作台。`, isAdmin() ? '销售、库存、会员与售后，门店业务尽在掌握。' : '选对设备，核对数量，让每一笔交易清楚、有序。', queue);
  if (!isAdmin()) return hero + `<div class="stat-grid">${statCard('本人净销售额', money(sales), 'coin', '本人经手且未退款的订单', true, true)}${statCard('有效销售订单', integer(paid.length), 'orders', '已支付订单')}${statCard('已退款订单', integer(orders.filter(order => order.status === 'REFUNDED').length), 'refresh', '本人经手的售后记录')}${statCard('待我确认的提交', integer(queue.waitingCount), 'clock', '分配到本人收银台的会员')}</div><div class="staff-shortcuts">${staffShortcut('pos', '会员提交与收银', '核对本柜台会员提交，确认商品后结账', 'queue')}${staffShortcut('catalog', '查询数码设备', '核对型号、售价与实时库存', 'products')}${staffShortcut('orders', '订单与售后', '查看本人经手订单，办理整单退款', 'orders')}</div><section class="panel section-gap"><div class="panel-head"><div><h2>最近经手订单</h2><p>实付金额和售后状态同步更新</p></div><button class="btn link" data-action="navigate" data-page="orders">查看全部 ${icon('arrow')}</button></div>${miniOrders(orders)}</section>`;
  const hot = array(stats.hotProducts), low = array(stats.lowStock);
  return hero + `<div class="stat-grid">${statCard('今日净营业额', money(stats.todaySales), 'coin', '今日有效订单，扣除退款', true, true)}${statCard('累计净销售额', money(stats.totalSales), 'chart', '全部有效订单的实付金额', false, true)}${statCard('有效销售订单', integer(stats.paidOrderCount), 'orders', '已支付且未整单退款')}${statCard('门店数码会员', integer(stats.memberCount), 'users', '已注册且启用的会员')}</div><div class="staff-shortcuts">${staffShortcut('catalog', '商品与分类', `${integer(stats.productCount)} 款设备，型号与售价统一管理`, 'products')}${staffShortcut('procurement', '采购与补货', low.length ? `${low.length} 款商品达到库存预警线` : '库存状态良好，按需采购补货', 'procurement')}${staffShortcut('settings', '优惠与会员', '配置全场折扣、满减和收银台', 'settings')}</div><div class="grid-two section-gap"><section class="panel"><div class="panel-head"><div><h2>近七日销售趋势</h2><p>净销售额 · 最近 7 天</p></div><span class="legend"><i></i>营业额</span></div><div class="panel-body">${salesChart(stats.dailySales)}</div></section><section class="panel"><div class="panel-head"><div><h2>历史商品销量</h2><p>按成交时名称统计，含改版前记录</p></div><span class="tag">TOP ${Math.min(hot.length, 4)}</span></div><div class="panel-body staff-ranking">${hot.length ? hot.slice(0, 4).map((item, index) => `<div class="list-line"><span class="rank ${index === 0 ? 'first' : ''}">${String(index + 1).padStart(2, '0')}</span><div class="grow"><strong>${esc(item.name)}</strong><small>售出 ${integer(item.quantity)} 件</small></div><span class="amount">¥${money(item.amount)}</span></div>`).join('') : empty('等待第一笔销售', '交易完成后，这里会显示历史商品销量。', '📱')}</div></section></div><div class="grid-two section-gap"><section class="panel"><div class="panel-head"><div><h2>最近销售订单</h2><p>及时了解每一笔交易</p></div><button class="btn link" data-action="navigate" data-page="orders">全部订单 ${icon('arrow')}</button></div>${miniOrders(orders)}</section><section class="panel"><div class="panel-head"><div><h2>库存待办</h2><p>达到预警线的设备</p></div><span class="tag ${low.length ? 'orange' : ''}">${low.length} 款待关注</span></div><div class="panel-body staff-stock-list">${low.length ? low.slice(0, 4).map(product => `<div class="list-line">${staffThumb(product)}<div class="grow"><strong>${esc(product.name)}</strong><small>预警线 ${integer(product.lowStockThreshold)} 件</small></div><span class="tag orange">余 ${integer(product.stock)} 件</span></div>`).join('') : empty('库存状态良好', '暂无达到预警线的商品。', '✓')}<button class="btn full outline" data-action="navigate" data-page="procurement">${icon('procurement')}安排采购入库</button></div></section></div><section class="panel section-gap"><div class="panel-head"><div><h2>顾问销售业绩</h2><p>按有效订单统计，整单退款自动扣除</p></div></div><div class="table-wrap"><table><thead><tr><th>数码顾问</th><th>有效订单</th><th>净销售额</th></tr></thead><tbody>${array(stats.cashierPerformance).map(row => `<tr><td><div class="table-main"><div class="avatar">${esc((row.name || '销').slice(0, 1))}</div><strong>${esc(row.name)}</strong></div></td><td>${integer(row.orderCount)} 笔</td><td class="amount">¥${money(row.amount)}</td></tr>`).join('') || '<tr><td colspan="3">暂无销售记录</td></tr>'}</tbody></table></div></section>`;
};

const staffOriginalBasket = basketPanel;
basketPanel = function () {
  if (isMember()) return staffOriginalBasket();
  const items = basketItems(), quantity = items.reduce((total, item) => total + num(item.quantity), 0);
  const total = items.reduce((sum, item) => sum + Math.round(num(item.price) * 100) * num(item.quantity), 0) / 100;
  return `<aside class="panel basket-panel staff-pos-basket"><div class="panel-head"><div><div class="eyebrow">CURRENT SALE</div><h2>本次收银</h2><p>${quantity} 件设备 · ${items.length} 种型号</p></div><span class="tag">模拟支付</span></div>${items.length ? `<div class="basket-list">${items.map(item => `<div class="basket-row">${staffThumb(item)}<div class="basket-info"><strong>${esc(item.name)}</strong><div class="basket-price">¥${money(item.price)} / 件</div></div><div class="quantity"><button data-action="basket-minus" data-id="${num(item.productId)}" aria-label="减少 ${esc(item.name)} 数量">−</button><span>${integer(item.quantity)}</span><button data-action="basket-plus" data-id="${num(item.productId)}" aria-label="增加 ${esc(item.name)} 数量" ${num(item.quantity) >= num(item.stock) ? 'disabled' : ''}>+</button></div></div>`).join('')}</div>` : empty('准备开始一笔销售', '从左侧选择设备，清单会显示在这里。', '🛍️')}<div class="basket-member"><label class="field"><span>选择顾客身份</span><select id="pos-member"><option value="">到店散客 · 无会员折扣</option>${state.members.map(member => `<option value="${num(member.id)}" ${String(member.id) === state.selectedMember ? 'selected' : ''}>${esc(member.displayName || member.username)} · ${esc(member.username)} · ${esc(levelFor(member))}</option>`).join('')}</select></label><small class="staff-pos-member-note">选择会员后，系统计算等级优惠并累积积分。</small></div><div class="basket-summary"><div class="summary-line"><span>商品原价合计</span><span class="amount">¥${money(total)}</span></div><div class="summary-line"><span>优惠与积分</span><span>下一步确认</span></div><div class="summary-total"><span>原价小计</span><span class="amount">¥${money(total)}</span></div><button class="btn primary full" data-action="preview-checkout" ${!items.length ? 'disabled' : ''}>确认优惠与收款 ${icon('arrow')}</button><p class="basket-tip">本项目使用模拟支付，确认后生成门店订单。</p>${items.length ? '<button class="btn link full small" data-action="clear-basket">清空本次收银</button>' : ''}</div></aside>`;
};

const staffOriginalOrders = viewOrders;
let staffOrderKeyword = '', staffOrderStatus = '';
viewOrders = async function () {
  if (isMember()) return staffOriginalOrders();
  state.orders = array(await api('/orders'));
  staffOrderKeyword = ''; staffOrderStatus = '';
  const paid = state.orders.filter(order => order.status === 'PAID');
  return heading(isAdmin() ? '销售订单与售后' : '我的经手订单', isAdmin() ? '查找交易、核对优惠与收银归属，售后记录清楚可查。' : '查看本人经手的交易、会员退款申请及老板的审核结果。', `<button class="btn outline" data-action="refresh">${icon('refresh')}刷新订单</button>${isAdmin() ? `<button class="btn primary" data-action="export">${icon('download')}导出报表</button>` : ''}`) + `<div class="staff-order-metrics"><div><span>订单记录</span><strong>${state.orders.length}<small> 笔</small></strong></div><div><span>有效净销售额</span><strong>¥${money(paid.reduce((total, order) => total + num(order.payableAmount), 0))}</strong></div><div><span>已退款订单</span><strong>${state.orders.filter(order => order.status === 'REFUNDED').length}<small> 笔</small></strong></div></div><div class="toolbar staff-order-filters"><label class="search">${icon('search')}<input id="staff-order-search" placeholder="搜索订单号、顾客或设备名称…" aria-label="搜索订单" maxlength="100"></label><select id="staff-order-status" aria-label="筛选订单状态"><option value="">全部状态</option><option value="PAID">已支付</option><option value="REFUNDED">已退款</option></select></div><section class="panel"><div class="panel-head"><h2>订单记录</h2><span class="small muted" id="staff-order-count">共 ${state.orders.length} 笔</span></div><div id="staff-order-results">${staffOrderRows(state.orders)}</div></section>`;
};

function staffOrderRows(orders) {
  if (!orders.length) return empty('没有符合条件的订单', '尝试其他订单号、顾客名称或状态。', '🧾');
  return `<div class="table-wrap"><table><thead><tr><th>订单编号 / 时间</th><th>顾客 / 设备</th><th>优惠金额</th><th>实付金额</th><th>收银顾问</th><th>状态</th><th>操作</th></tr></thead><tbody>${orders.map(order => `<tr><td><strong class="mono">${esc(order.orderNo)}</strong><div class="table-sub">${dateTime(order.createdAt)}</div></td><td><strong>${esc(order.memberName || '到店散客')}</strong><div class="table-sub staff-order-products">${esc(array(order.items).map(item => item.productName || item.name).join('、') || '查看订单明细')}</div></td><td class="primary-text">− ¥${money(num(order.originalAmount) - num(order.payableAmount))}</td><td class="amount">¥${money(order.payableAmount)}</td><td>${esc(order.cashierName || '—')}</td><td>${statusTag(order.status)}<div class="table-sub">${refundTag(order)}</div>${order.legacyDemoOrder ? '<div class="table-sub"><span class="tag orange">早期演示订单</span></div>' : ''}</td><td><div class="table-actions"><button class="btn tiny" data-action="order-detail" data-id="${num(order.id)}">查看详情</button>${refundActions(order)}</div></td></tr>`).join('')}</tbody></table></div>`;
}

function staffFilterOrders() {
  if (isMember() || state.page !== 'orders') return;
  const keyword = staffOrderKeyword.toLocaleLowerCase('zh-CN');
  const orders = array(state.orders).filter(order => (!staffOrderStatus || order.status === staffOrderStatus) && (!keyword || [order.orderNo, order.memberName, order.cashierName, ...array(order.items).map(item => item.productName || item.name)].join(' ').toLocaleLowerCase('zh-CN').includes(keyword)));
  const results = $('#staff-order-results'), count = $('#staff-order-count');
  if (results) results.innerHTML = staffOrderRows(orders);
  if (count) count.textContent = `显示 ${orders.length} / ${array(state.orders).length} 笔`;
}
document.addEventListener('input', event => { if (event.target.id === 'staff-order-search') { staffOrderKeyword = event.target.value.trim(); staffFilterOrders(); } });
document.addEventListener('change', event => { if (event.target.id === 'staff-order-status') { staffOrderStatus = event.target.value; staffFilterOrders(); } });

/* 会员提交与到店开单是两条入口：会员票据只由分配到的收银员确认，最终权限由 Java 再校验。 */
const staffQueueUI = { timer: null, polling: false, confirming: false, fingerprint: '', ticket: null };

function stopStaffQueuePoll() { clearTimeout(staffQueueUI.timer); staffQueueUI.timer = null; }
function staffCounterLabel(counter) { return `${integer(counter.counterNo)} 号收银台 · ${counter.cashierName || '待安排'}`; }
function staffCounterRoster(queue) {
  return `<div class="staff-counter-roster">${array(queue.counters).map(counter => `<div class="staff-counter-chip ${num(counter.cashierId) === num(state.user.id) ? 'mine' : ''}"><span>${integer(counter.counterNo)} 号收银台${num(counter.cashierId) === num(state.user.id) ? ' · 我的柜台' : ''}</span><strong>${esc(counter.cashierName || '待安排收银员')}</strong><small>${counter.cashierUsername ? `${esc(counter.cashierUsername)} · ` : ''}${esc(statusNames[counter.status] || '尚未绑定')}</small></div>`).join('')}</div>`;
}
function staffQueueItems(ticket) {
  return `<div class="staff-submission-items">${array(ticket.items).map(item => `<div class="staff-submission-item">${staffThumb(item)}<div class="staff-submission-product"><strong>${esc(item.name || item.productName || '商品')}</strong><small>${esc(item.sku || '')} · ¥${money(item.unitPrice)} / 件</small></div><span>× ${integer(item.quantity)}</span><strong>¥${money(item.lineTotal ?? item.subtotal ?? num(item.unitPrice) * num(item.quantity))}</strong></div>`).join('') || '<p class="small muted">商品清单暂时不可用，请刷新后核对。</p>'}</div>`;
}
function staffQueueSection(queue) {
  const mine = array(queue.counters).find(counter => num(counter.cashierId) === num(state.user.id));
  const tickets = array(queue.tickets).filter(ticket => ['WAITING', 'PROCESSING'].includes(ticket.status) && (isAdmin() || num(ticket.cashierId) === num(state.user.id))).sort((a, b) => num(a.counterNo) - num(b.counterNo) || num(a.id) - num(b.id));
  return `<section class="panel staff-member-queue"><div class="panel-head"><div><div class="eyebrow">MEMBER SUBMISSIONS / CASHIER CONFIRMATION</div><h2>${isAdmin() ? '会员提交 · 待收银员确认' : mine ? esc(staffCounterLabel(mine)) : '会员提交 · 我的待办'}</h2><p>${isAdmin() ? '查看各柜台的会员提交，由对应收银员登录后确认结账。' : '会员提交的商品会显示在这里，核对后由你确认结账。'}</p></div><button class="btn outline small" data-action="staff-queue-refresh">${icon('refresh')}刷新待办</button></div>${staffCounterRoster(queue)}<div class="staff-submission-heading"><strong>${isAdmin() ? '全店待确认' : '我的待确认'} <span>${tickets.length}</span></strong><small id="staff-queue-connection">每 2 秒更新 · 按各柜台提交顺序办理</small></div><div class="staff-submission-list">${tickets.length ? tickets.map(ticket => `<article class="staff-submission" data-ticket-id="${num(ticket.id)}"><header><div><span class="staff-submission-number">Q${String(num(ticket.id)).padStart(5, '0')}</span><strong>${esc(ticket.memberName || '会员')}</strong><small>${dateTime(ticket.createdAt, true)} 提交</small></div>${statusTag(ticket.status)}</header><div class="staff-submission-assignment">${icon('queue')}<span>${esc(staffCounterLabel(ticket))}${ticket.cashierUsername ? `（${esc(ticket.cashierUsername)}）` : ''}</span>${num(ticket.position) > 1 ? `<small>前方 ${integer(num(ticket.position) - 1)} 笔</small>` : '<small>本柜台当前首位</small>'}</div>${staffQueueItems(ticket)}${ticket.previewError ? `<div class="callout orange">${icon('info')}<p>${esc(ticket.previewError)}，请联系会员取消并修改商品。</p></div>` : ''}<footer><div><span>预计实付</span><strong>${ticket.totalAmount == null ? '待核对' : `¥${money(ticket.totalAmount)}`}</strong><small>优惠按确认时核算 · 模拟支付</small></div>${!isAdmin() ? `<button class="btn primary" data-action="staff-queue-review" data-id="${num(ticket.id)}" ${ticket.status !== 'WAITING' || num(ticket.position) > 1 || ticket.previewError || !array(ticket.items).length ? 'disabled' : ''}>${icon('check')}${num(ticket.position) > 1 ? '等待前序办理' : '核对并确认结账'}</button>` : `<span class="small muted">等待 ${esc(ticket.cashierName || '指定收银员')} 确认</span>`}</footer></article>`).join('') : empty(isAdmin() ? '暂时没有待确认提交' : '我的收银台暂无待确认商品', mine?.status === 'CLOSED' ? '当前柜台暂不接收新提交，已有提交仍可继续办理。' : mine ? '会员提交到本柜台后，商品清单会自动显示。' : isAdmin() ? '会员提交后，将分配到可用收银台。' : '当前账号尚未绑定收银台，请联系管理员。', '🧾')}</div><div class="staff-queue-footnote">${icon('shield')}等待期间不扣库存、不生成订单。只有对应收银员确认后才完成结账。</div></section>`;
}

const staffOriginalCatalog = viewCatalog;
viewCatalog = async function () {
  const catalog = await staffOriginalCatalog();
  if (isMember() || state.page !== 'pos') return catalog;
  const queue = await api('/queue');
  staffQueueUI.fingerprint = JSON.stringify(queue);
  return `<div id="staff-queue-live">${staffQueueSection(queue)}</div><div class="staff-walkin-sale">${catalog}</div>`;
};

// 轮询只刷新会员待办区域，不重绘现场收银的购物清单、搜索框或会员选择。
async function refreshStaffQueue() {
  const version = state.routeVersion;
  const queue = await api('/queue');
  if (!state.user || isMember() || state.page !== 'pos' || version !== state.routeVersion) return;
  const target = $('#staff-queue-live'), fingerprint = JSON.stringify(queue);
  if (target && fingerprint !== staffQueueUI.fingerprint) { target.innerHTML = staffQueueSection(queue); staffQueueUI.fingerprint = fingerprint; }
  const connection = $('#staff-queue-connection');
  if (connection) connection.textContent = '每 2 秒更新 · 按各柜台提交顺序办理';
}
function startStaffQueuePoll() {
  stopStaffQueuePoll();
  const version = state.routeVersion;
  staffQueueUI.timer = setTimeout(async function poll() {
    if (!state.user || isMember() || state.page !== 'pos' || version !== state.routeVersion) return;
    if (!staffQueueUI.polling && !staffQueueUI.confirming && !document.hidden && !$('#modal-body')) {
      staffQueueUI.polling = true;
      try { await refreshStaffQueue(); }
      catch (error) { if (version === state.routeVersion && $('#staff-queue-connection')) $('#staff-queue-connection').textContent = '连接暂时中断，正在重试…'; }
      finally { staffQueueUI.polling = false; }
    }
    if (state.user && !isMember() && state.page === 'pos' && version === state.routeVersion) staffQueueUI.timer = setTimeout(poll, 2000);
  }, 2000);
}
const staffPreviousNavigate = navigate;
navigate = async function (page = 'overview') {
  stopStaffQueuePoll();
  const pending = staffPreviousNavigate(page), version = state.routeVersion;
  await pending;
  if (state.user && !isMember() && state.page === 'pos' && version === state.routeVersion && $('#staff-queue-live')) startStaffQueuePoll();
};
const staffPreviousResetSession = resetSession;
resetSession = function () { stopStaffQueuePoll(); staffQueueUI.ticket = null; staffQueueUI.fingerprint = ''; staffProductCache.clear(); staffPreviousResetSession(); };
const staffPreviousRenderAuth = renderAuth;
renderAuth = function (message = '') { stopStaffQueuePoll(); staffPreviousRenderAuth(message); };

Object.assign(actions, {
  'staff-queue-refresh': refreshStaffQueue,
  'staff-queue-review': async element => {
    const queue = await api('/queue');
    const ticket = array(queue.tickets).find(row => num(row.id) === num(element.dataset.id));
    if (!ticket || ticket.status !== 'WAITING') { await refreshStaffQueue(); throw new Error('这笔提交已取消或已办理，请核对最新待办。'); }
    if (isAdmin() || num(ticket.cashierId) !== num(state.user.id)) throw new Error('请由分配到的收银员确认本次结账。');
    if (num(ticket.position) > 1) throw new Error('请先办理本收银台前面的提交。');
    if (ticket.previewError) throw new Error(ticket.previewError);
    if (!array(ticket.items).length || ticket.totalAmount == null) throw new Error('商品金额暂时不可用，请刷新后重试。');
    staffQueueUI.ticket = ticket;
    showModal('确认会员提交的商品', `<div class="staff-confirm-assignment"><strong>${esc(staffCounterLabel(ticket))}</strong><span>会员 ${esc(ticket.memberName)} · 票号 Q${String(num(ticket.id)).padStart(5, '0')}</span></div>${staffQueueItems(ticket)}<div class="staff-confirm-total"><span>预计实付</span><strong>¥${money(ticket.totalAmount)}</strong></div><p class="small muted">${esc(ticket.discountDescription || '会员优惠按确认结账时的规则计算。')}</p><div class="callout section-gap">${icon('info')}<p>请与会员核对设备、数量及金额。点击下方确认后，完成模拟支付、生成订单，并扣减库存、累积积分。</p></div>`, `<button class="btn outline" data-action="modal-close">暂不确认</button><button class="btn primary" data-action="staff-queue-confirm" data-id="${num(ticket.id)}">确认结账 ¥${money(ticket.totalAmount)}</button>`, true);
  },
  'staff-queue-confirm': async element => {
    if (staffQueueUI.confirming) return;
    const ticket = staffQueueUI.ticket;
    if (!ticket || num(ticket.id) !== num(element.dataset.id)) throw new Error('确认信息已过期，请重新核对。');
    staffQueueUI.confirming = true;
    try {
      const completed = await api(`/queue/${num(ticket.id)}/confirm`, 'POST');
      staffQueueUI.ticket = null;
      closeModal();
      toast(`${ticket.memberName || '会员'} 的商品已确认结账，订单已生成`);
      if (state.user && !isMember() && state.page === 'pos') await navigate('pos');
      if (completed.orderId && state.user && !isMember() && state.page === 'pos') await orderDetail(completed.orderId);
    } finally { staffQueueUI.confirming = false; }
  }
});
