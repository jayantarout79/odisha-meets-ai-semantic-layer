// Odisha Meets AI dashboard: fetches predefined panels from the Spring Boot API
// (which queries Cube) and renders them with Chart.js. Filter state lives in the URL,
// so any filtered view can be bookmarked or shared.

const PALETTE = ['#4b3fd1', '#0fa3a3', '#f2994a', '#eb5757', '#27ae60', '#9b51e0', '#2d9cdb', '#f2c94c', '#828282', '#56ccf2'];
const FILTER_IDS = ['preset', 'from', 'to', 'granularity', 'persona', 'category', 'bookingSource', 'session'];
const WIDE = new Set(['activity', 'bookingStatus']);
const charts = {};

const $ = (id) => document.getElementById(id);
const nf = new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 });

function formatValue(kpi) {
  if (kpi.format === 'percent') return `${nf.format(kpi.value)}%`;
  if (kpi.format === 'decimal') return nf.format(kpi.value);
  return Math.round(kpi.value).toLocaleString('en-US');
}

function readFilters() {
  const params = new URLSearchParams();
  FILTER_IDS.forEach((id) => {
    const v = $(id).value;
    if (v && !(id === 'preset' && v === 'all') && !(id === 'granularity' && v === 'week')) params.set(id, v);
  });
  if ($('preset').value !== 'custom') { params.delete('from'); params.delete('to'); }
  return params;
}

function writeFilters(params) {
  FILTER_IDS.forEach((id) => {
    if (params.has(id)) $(id).value = params.get(id);
  });
  toggleCustom();
}

function toggleCustom() {
  document.querySelector('.filters').classList.toggle('custom', $('preset').value === 'custom');
}

function fillSelect(id, values) {
  const select = $(id);
  const first = select.options[0];
  select.innerHTML = '';
  select.appendChild(first);
  values.forEach((v) => {
    const opt = document.createElement('option');
    opt.value = v;
    opt.textContent = v;
    select.appendChild(opt);
  });
}

function showError(message) {
  const box = $('error');
  box.textContent = message;
  box.hidden = !message;
}

function renderKpis(kpis) {
  $('kpis').innerHTML = kpis.map((k) => `
    <article class="kpi">
      <div class="label">${k.label}</div>
      <div class="value">${formatValue(k)}</div>
      <div class="hint">${k.hint || ''}</div>
    </article>`).join('');
}

function ensureCard(chart) {
  let card = document.getElementById(`card-${chart.id}`);
  if (!card) {
    card = document.createElement('article');
    card.className = `card${WIDE.has(chart.id) ? ' wide' : ''}`;
    card.id = `card-${chart.id}`;
    card.innerHTML = `<h3></h3><p class="sub"></p><div class="canvas-wrap"></div>`;
    $('charts').appendChild(card);
  }
  card.querySelector('h3').textContent = chart.title;
  card.querySelector('.sub').textContent = chart.subtitle || '';
  return card;
}

function renderChart(chart) {
  const card = ensureCard(chart);
  const wrap = card.querySelector('.canvas-wrap');
  if (charts[chart.id]) { charts[chart.id].destroy(); delete charts[chart.id]; }

  const hasData = chart.labels.length && chart.datasets.some((d) => d.data.some((v) => v > 0));
  if (!hasData) {
    wrap.innerHTML = '<div class="empty">No data for these filters</div>';
    return;
  }
  wrap.innerHTML = '<canvas></canvas>';

  const isDoughnut = chart.type === 'doughnut';
  const isHbar = chart.type === 'hbar';
  const multi = chart.datasets.length > 1;

  const datasets = chart.datasets.map((d, i) => ({
    label: d.label,
    data: d.data,
    borderColor: isDoughnut ? '#fff' : PALETTE[i % PALETTE.length],
    backgroundColor: isDoughnut || (!multi && !isHbar && chart.type === 'bar')
      ? chart.labels.map((_, j) => PALETTE[(isDoughnut ? j : i) % PALETTE.length])
      : (chart.type === 'line' ? PALETTE[i % PALETTE.length] + '22' : PALETTE[i % PALETTE.length]),
    borderWidth: chart.type === 'line' ? 2.5 : (isDoughnut ? 2 : 0),
    borderRadius: chart.type === 'line' || isDoughnut ? 0 : 6,
    tension: 0.3,
    fill: false,
    pointRadius: 3,
  }));

  charts[chart.id] = new Chart(wrap.querySelector('canvas'), {
    type: isHbar ? 'bar' : chart.type,
    data: { labels: chart.labels, datasets },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      indexAxis: isHbar ? 'y' : 'x',
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: multi || isDoughnut, position: isDoughnut ? 'right' : 'top', labels: { boxWidth: 12 } },
        tooltip: {
          callbacks: {
            label: (ctx) => {
              const v = isDoughnut ? ctx.parsed : (isHbar ? ctx.parsed.x : ctx.parsed.y);
              return ` ${ctx.dataset.label}: ${nf.format(v)}`;
            },
          },
        },
      },
      scales: isDoughnut ? {} : {
        x: { stacked: chart.stacked, grid: { display: isHbar }, ticks: { precision: 0 } },
        y: {
          stacked: chart.stacked,
          beginAtZero: true,
          grid: { display: !isHbar },
          ticks: {
            precision: 0,
            callback: function (value) {
              const label = this.getLabelForValue(value);
              return isHbar && typeof label === 'string' && label.length > 28 ? label.slice(0, 27) + '…' : label;
            },
          },
        },
      },
    },
  });
}

async function loadDashboard() {
  const params = readFilters();
  history.replaceState(null, '', params.toString() ? `?${params}` : location.pathname);
  document.body.classList.add('loading');
  showError('');
  try {
    const res = await fetch(`/api/dashboard?${params}`);
    const body = await res.json();
    if (!res.ok) throw new Error(body.error || `HTTP ${res.status}`);
    renderKpis(body.kpis);
    body.charts.forEach(renderChart);
    const a = body.applied;
    const range = a.from ? `${a.from} → ${a.to}` : 'All time';
    $('meta').innerHTML = `${range}<br>Refreshed ${body.generatedAt} · ${body.queryMillis} ms`;
  } catch (e) {
    showError(`Could not load the dashboard: ${e.message}`);
  } finally {
    document.body.classList.remove('loading');
  }
}

async function loadFilterOptions() {
  const res = await fetch('/api/filters');
  if (!res.ok) throw new Error('Could not load filter options');
  const o = await res.json();
  fillSelect('persona', o.personas);
  fillSelect('category', o.categories);
  fillSelect('bookingSource', o.bookingSources);
  fillSelect('session', o.sessions);
}

async function init() {
  $('preset').addEventListener('change', toggleCustom);
  $('apply').addEventListener('click', loadDashboard);
  $('reset').addEventListener('click', () => {
    FILTER_IDS.forEach((id) => { $(id).value = id === 'preset' ? 'all' : id === 'granularity' ? 'week' : ''; });
    toggleCustom();
    loadDashboard();
  });
  ['granularity', 'persona', 'category', 'bookingSource', 'session'].forEach((id) =>
    $(id).addEventListener('change', loadDashboard));
  $('preset').addEventListener('change', () => { if ($('preset').value !== 'custom') loadDashboard(); });

  try {
    await loadFilterOptions();
  } catch (e) {
    showError(e.message);
  }
  writeFilters(new URLSearchParams(location.search));
  loadDashboard();
}

init();
