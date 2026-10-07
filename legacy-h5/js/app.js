(() => {
  'use strict';

  function fail(msg) {
    let el = document.getElementById('boot-error');
    if (!el) {
      el = document.createElement('div');
      el.id = 'boot-error';
      el.style.cssText =
        'position:fixed;left:12px;right:12px;top:12px;z-index:99;background:#fff3e8;color:#7a2e12;padding:12px 14px;border-radius:12px;font:14px/1.5 system-ui;box-shadow:0 8px 24px rgba(0,0,0,.2)';
      document.body.appendChild(el);
    }
    el.hidden = false;
    el.textContent = msg;
    console.error(msg);
  }

  try {
    if (typeof d3 === 'undefined') return fail('D3 未加载：请确认 js/d3.min.js');
    if (!window.MAP_DATA) return fail('地图数据未加载：请确认 js/map-data.js');
    if (!window.ITINERARY_DATA) return fail('行程数据未加载：请确认 js/itinerary-data.js');

    const data = window.MAP_DATA;
    const itinerary = window.ITINERARY_DATA;
    const $ = (s) => document.querySelector(s);
    const toastEl = $('#toast');
    let toastTimer;
    function toast(msg) {
      if (!toastEl) return;
      toastEl.hidden = false;
      toastEl.textContent = msg;
      clearTimeout(toastTimer);
      toastTimer = setTimeout(() => {
        toastEl.hidden = true;
      }, 2000);
    }

    // enrich
    const anchorSpecs = [
      ['way/184960645', '坡子街·餐饮片区', 'sat'],
      ['way/445814171', '太平街·可选闲逛', 'sat'],
      ['way/751872167', '湖大·午餐片区', 'sun']
    ];
    for (const [id, name, dayId] of anchorSpecs) {
      const f = data.base.features.find((x) => x.properties.id === id);
      if (!f || f.geometry.type !== 'LineString') continue;
      const c = f.geometry.coordinates;
      data.points.push({
        id: 'area/' + id,
        name,
        kind: 'area',
        day: dayId,
        coordinates: dayId === 'sun' ? c[c.length - 1] : d3.geoInterpolate(c[0], c[c.length - 1])(0.5),
        description: '片区锚点，非门店门口'
      });
    }
    if (!data.points.some((p) => p.name === '湘江中路·住宿区域')) {
      data.points.push({
        id: 'derived/example-base',
        name: '湘江中路·住宿区域',
        kind: 'base',
        day: 'sat',
        coordinates: [112.9639139, 28.1986407],
        description: '湘江中路住宿区域片区，示例待确认（携程）'
      });
    }

    let day = itinerary.days[0];
    let selected = day.steps.find((s) => s.id === day.defaultStep) || day.steps[0];
    let width = 360;
    let height = 640;
    let projection = null;
    let view = 'core';
    const extents = {
      core: [[112.928, 28.166], [112.973, 28.205]],
      city: [[112.927, 28.145], [113.065, 28.207]],
      academy: [[112.922, 28.174], [112.952, 28.195]]
    };

    const shell = $('#map-shell');
    const svgNode = document.getElementById('map');
    if (!shell || !svgNode) return fail('找不到地图容器 #map-shell / #map');

    const svg = d3.select(svgNode);
    svg.selectAll('*').remove();

    const defs = svg.append('defs');
    const filter = defs
      .append('filter')
      .attr('id', 'glow')
      .attr('x', '-40%')
      .attr('y', '-40%')
      .attr('width', '180%')
      .attr('height', '180%');
    filter.append('feGaussianBlur').attr('stdDeviation', '2.2').attr('result', 'blur');
    const merge = filter.append('feMerge');
    merge.append('feMergeNode').attr('in', 'blur');
    merge.append('feMergeNode').attr('in', 'SourceGraphic');
    defs.append('clipPath').attr('id', 'cs-clip').append('rect');

    const scene = svg.append('g').attr('clip-path', 'url(#cs-clip)');
    const gWater = scene.append('g');
    const gLand = scene.append('g');
    const gRoads = scene.append('g');
    const gRoutes = scene.append('g');
    const gActive = scene.append('g');
    const gMarks = scene.append('g');
    const gLabels = scene.append('g');
    const gAdorn = svg.append('g');

    const colors = {
      metro2: '#0f5a52',
      metro4: '#2f6f80',
      walk2: '#c45c3e',
      walk1: '#c45c3e',
      shuttle: '#ab5227',
      optional: '#6a8a92'
    };
    const mainPoints = [
      '湘江中路站',
      '橘子洲站',
      '溁湾镇站',
      '湖南大学站',
      '岳麓书院入口',
      '青年毛泽东雕塑',
      '洲头·问天台',
      '湘江中路·住宿区域',
      '长沙南站'
    ];
    const offsets = {
      湘江中路站: [14, -10],
      橘子洲站: [-12, 24],
      溁湾镇站: [-12, -14],
      湖南大学站: [10, 20],
      岳麓书院入口: [-14, -16],
      青年毛泽东雕塑: [14, -8],
      '洲头·问天台': [14, 18],
      湘江中路·住宿区域: [14, 20],
      长沙南站: [-12, -14]
    };
    const aliases = {
      湘江中路站: () => (day.id === 'sat' ? '湘江中路' : '取行李'),
      青年毛泽东雕塑: () => '橘子洲南端',
      岳麓书院入口: () => '岳麓书院',
      湘江中路·住宿区域: () => '住宿区域'
    };

    let current = d3.zoomIdentity;
    const zoom = d3
      .zoom()
      .scaleExtent([0.7, 7])
      .on('zoom', (event) => {
        current = event.transform;
        renderMap();
      });
    svg.call(zoom).on('dblclick.zoom', null);

    function project(c) {
      return current.apply(projection(c));
    }

    function fit() {
      const b = extents[view] || extents.core;
      const padX = 16;
      const padTop = Math.min(84, Math.round(height * 0.16));
      const padBottom = Math.min(36, Math.round(height * 0.08));
      const y0 = padTop;
      const y1 = Math.max(y0 + 80, height - padBottom);
      projection = d3.geoMercator().fitExtent(
        [
          [padX, y0],
          [Math.max(padX + 120, width - padX), y1]
        ],
        {
          type: 'FeatureCollection',
          features: b.map((c) => ({ type: 'Feature', geometry: { type: 'Point', coordinates: c } }))
        }
      );
      current = d3.zoomIdentity;
      svg.call(zoom.transform, current);
    }

    function featurePath(f) {
      const p = d3
        .geoMercator()
        .scale(projection.scale() * current.k)
        .translate(current.apply(projection.translate()));
      return d3.geoPath(p)(f);
    }

    function metroSlice(f) {
      if (f.properties.id !== 'metro2') return f;
      const map = {
        'sat-arrival': ['长沙南站', '湘江中路站'],
        'sat-transfer': ['湘江中路站', '橘子洲站'],
        'sat-dinner': ['橘子洲站', '湘江中路站'],
        'sun-transfer': ['湘江中路站', '溁湾镇站'],
        'sun-luggage': ['溁湾镇站', '湘江中路站'],
        'sun-return': ['湘江中路站', '长沙南站']
      };
      const endpoints = map[selected.id];
      if (!endpoints) return f;
      const a = data.points.find((p) => p.name === endpoints[0]);
      const b = data.points.find((p) => p.name === endpoints[1]);
      if (!a || !b) return f;
      const cs = f.geometry.coordinates;
      const ix = [a.coordinates, b.coordinates].map((c) => d3.minIndex(cs, (p) => d3.geoDistance(p, c)));
      let coordinates = cs.slice(Math.min(...ix), Math.max(...ix) + 1);
      if (ix[0] > ix[1]) coordinates = coordinates.slice().reverse();
      return { ...f, geometry: { type: 'LineString', coordinates } };
    }

    function dashFor(type) {
      if (type === 'shuttle') return '6 5';
      if (String(type).startsWith('walk')) return '1.5 4';
      if (type === 'optional') return '3 5';
      return null;
    }

    function renderMap() {
      if (!projection) return;
      svg.attr('viewBox', '0 0 ' + width + ' ' + height).attr('width', width).attr('height', height);
      defs.select('rect').attr('width', width).attr('height', height);

      const water = data.base.features.filter((f) => f.properties.kind === 'water');
      const land = data.base.features.filter((f) => f.properties.kind === 'park' || f.properties.kind === 'academy');
      const roads = data.base.features.filter((f) => f.properties.kind === 'road');

      gWater
        .selectAll('path')
        .data(water, (f) => f.properties.id)
        .join('path')
        .attr('d', featurePath)
        .attr('fill', 'rgba(126,182,196,0.55)')
        .attr('stroke', 'rgba(90,151,168,0.45)')
        .attr('stroke-width', 0.8);

      gLand
        .selectAll('path')
        .data(land, (f) => f.properties.id)
        .join('path')
        .attr('d', featurePath)
        .attr('fill', (f) => (f.properties.kind === 'academy' ? 'rgba(47,143,106,0.18)' : 'rgba(125,171,154,0.22)'))
        .attr('stroke', 'rgba(47,143,106,0.22)')
        .attr('stroke-width', 0.5);

      gRoads
        .selectAll('path')
        .data(roads, (f) => f.properties.id)
        .join('path')
        .attr('d', featurePath)
        .attr('fill', 'none')
        .attr('stroke', 'rgba(28,42,40,0.12)')
        .attr('stroke-width', view === 'city' ? 0.45 : 0.7);

      // 两天路线重点不同：当天用的线更亮，另一天的线压暗
      const dayRouteIds =
        day.id === 'sat'
          ? ['metro2', 'park-car', 'island-walk']
          : ['metro2', 'metro4', 'academy-walk', 'aiwan-walk'];
      gRoutes
        .selectAll('path')
        .data(data.routes)
        .join('path')
        .attr('d', featurePath)
        .attr('fill', 'none')
        .attr('opacity', (f) => (dayRouteIds.includes(f.properties.id) ? 0.38 : 0.08))
        .attr('stroke', (f) => colors[f.properties.type] || '#7dab9a')
        .attr('stroke-width', (f) => (String(f.properties.type).startsWith('metro') ? 2.8 : 1.8))
        .attr('stroke-dasharray', (f) => dashFor(f.properties.type))
        .attr('stroke-linecap', 'round');

      const active = data.routes.filter((f) => (selected.routes || []).includes(f.properties.id)).map(metroSlice);
      gActive
        .selectAll('path')
        .data(active)
        .join('path')
        .attr('d', featurePath)
        .attr('fill', 'none')
        .attr('filter', 'url(#glow)')
        .attr('stroke', (f) => colors[f.properties.type] || '#c45c3e')
        .attr('stroke-width', (f) => (String(f.properties.type).startsWith('metro') ? 3.4 : 2.4))
        .attr('stroke-dasharray', (f) => dashFor(f.properties.type))
        .attr('stroke-linecap', 'round');

      const points = data.points.filter((p) => {
        if (p.day && p.day !== day.id) return false;
        const xy = project(p.coordinates);
        return xy[0] > 8 && xy[0] < width - 8 && xy[1] > 50 && xy[1] < height - 140;
      });

      const g = gMarks
        .selectAll('g')
        .data(points, (p) => p.id)
        .join((enter) => {
          const gg = enter.append('g');
          gg.append('circle').attr('class', 'hit');
          gg.append('circle').attr('class', 'pulse');
          gg.append('circle').attr('class', 'dot');
          gg.append('path').attr('class', 'diamond');
          return gg;
        })
        .attr('transform', (p) => 'translate(' + project(p.coordinates) + ')')
        .style('cursor', 'pointer')
        .on('click', function (event, p) {
          showPoint(p);
        });

      g.select('.hit').attr('r', 22).attr('fill', 'transparent');
      g.select('.pulse')
        .attr('r', (p) => ((selected.points || []).includes(p.name) ? 14 : 0))
        .attr('fill', 'none')
        .attr('stroke', 'rgba(196,92,62,0.55)')
        .attr('stroke-width', 1.5);
      g.select('.dot')
        .attr('r', (p) => (String(p.kind).startsWith('core') || p.kind === 'base' ? 5.5 : 4))
        .attr('display', (p) => (p.kind === 'area' ? null : null))
        .attr('fill', (p) => {
          if (p.kind === 'area') return 'rgba(244,239,228,0.15)';
          if (p.kind === 'optional') return 'rgba(244,239,228,0.25)';
          if (p.kind === 'core2') return '#2f6f80';
          if (p.kind === 'core1') return '#c45c3e';
          return '#fffdf8';
        })
        .attr('stroke', (p) => {
          if (p.kind === 'core2') return '#4a8fa0';
          if (p.kind === 'core1') return '#e07a5a';
          return '#0f5a52';
        })
        .attr('stroke-width', 1.8);
      g.select('.diamond')
        .attr('d', d3.symbol().type(d3.symbolDiamond).size(70)())
        .attr('fill', 'rgba(255,253,248,0.9)')
        .attr('stroke', 'rgba(28,42,40,0.35)')
        .attr('stroke-width', 1.2)
        .attr('display', (p) => (p.kind === 'area' ? null : 'none'));

      // labels without getBBox (safe)
      gLabels.selectAll('*').remove();
      const labelPts = points.filter(
        (p) => mainPoints.includes(p.name) || (selected.points || []).includes(p.name) || view === 'academy'
      );
      // draw selected first so they win visually
      labelPts.sort((a, b) => Number((selected.points || []).includes(b.name)) - Number((selected.points || []).includes(a.name)));
      for (const p of labelPts) {
        const xy = project(p.coordinates);
        const off = offsets[p.name] || [12, -10];
        const name = aliases[p.name] ? aliases[p.name]() : p.name;
        gLabels
          .append('line')
          .attr('x1', xy[0])
          .attr('y1', xy[1])
          .attr('x2', xy[0] + off[0])
          .attr('y2', xy[1] + off[1] - 3)
          .attr('stroke', 'rgba(28,42,40,0.25)')
          .attr('stroke-width', 0.7);
        gLabels
          .append('text')
          .attr('class', 'point-label')
          .attr('x', xy[0] + off[0])
          .attr('y', xy[1] + off[1])
          .attr('text-anchor', off[0] < 0 ? 'end' : 'start')
          .text(name)
          .style('cursor', 'pointer')
          .on('click', () => showPoint(p));
      }

      if (view !== 'academy') {
        const river = project([112.9509, 28.1767]);
        if (river[0] > 20 && river[0] < width - 20 && river[1] > 60 && river[1] < height - 160) {
          gLabels
            .append('text')
            .attr('class', 'context-label')
            .attr('x', river[0])
            .attr('y', river[1])
            .attr('text-anchor', 'middle')
            .text('湘 江');
        }
      }

      gAdorn.selectAll('*').remove();
      const m = view === 'city' ? 2000 : view === 'academy' ? 200 : 500;
      try {
        const anchor = projection.invert(current.invert([18, Math.max(80, height - 150)]));
        const dest = [anchor[0] + m / (111320 * Math.cos((anchor[1] * Math.PI) / 180)), anchor[1]];
        const length = project(dest)[0] - project(anchor)[0];
        if (length > 24 && length < width - 50) {
          const y = Math.max(80, height - 150);
          gAdorn
            .append('line')
            .attr('x1', 18)
            .attr('y1', y)
            .attr('x2', 18 + length)
            .attr('y2', y)
            .attr('stroke', 'rgba(28,42,40,0.35)')
            .attr('stroke-width', 1.5);
          gAdorn
            .append('text')
            .attr('x', 18)
            .attr('y', y - 6)
            .attr('fill', 'rgba(106,122,118,0.9)')
            .attr('font-size', 10)
            .text(m >= 1000 ? m / 1000 + ' km' : m + ' m');
        }
      } catch (_) {
        /* scale bar optional */
      }

      shell.dataset.rendered = 'true';
      shell.dataset.points = String(points.length);
    }

    function showPoint(p) {
      const choices = day.steps.filter((s) => (s.points || []).includes(p.name));
      if (choices.length && !(selected.points || []).includes(p.name)) {
        selectStep(choices[0], false);
      }
      toast(p.name);
    }

    function oneLiner(step) {
      // Row-KEY tokens, not display text: must match keys in maps/itinerary-data.json rows.
      // Keep this list identical to the one in mobile-guide/dist/app.js (quick). It had drifted:
      // '二选一' was a dead token left after that row was re-keyed to '轻松'. Membership is what
      // matters — find() scans rows in order, so this array's own order is irrelevant.
      const prefer = ['已采纳去程', '已采纳返程', '慢游', '交通', '落脚', '区域', '先讲解', '顺路', '早餐'];
      const row = (step.rows || []).find((r) => prefer.includes(r[0])) || (step.rows || [])[0];
      if (!row) return step.title || '';
      let t = row[1] || '';
      // 卡片只留一句动作，长说明进「更多」
      if (t.includes(' → ')) {
        const parts = t.split(' → ').map((s) => s.trim());
        t = parts[0];
        if (parts.length > 1) t += ' → ' + parts[parts.length - 1].replace(/[；。].*$/, '');
      }
      t = t.split('；')[0].split('。')[0];
      if (t.length > 36) t = t.slice(0, 36) + '…';
      return t;
    }

    function shortTimeLabel(step) {
      const t = step.shortTime || step.time || '';
      // keep first time-like token
      const m = t.match(/\d{1,2}:\d{2}/);
      if (m) return m[0];
      // 没有钟点、但这一条有硬节点时（返程写的是「傍晚返程」），显示它第一个节点的时刻。
      // 不变量：被放大的那一条，显示的必须是一个钟点——否则「放大」放的不是时间。
      const n = nodesOf(step)[0];
      if (n) return n.text;
      return t.split('·')[0].split(' ')[0] || t;
    }

    // —— 时间节点临近放大 ——
    // 「现在」离某一条的时刻越近，那一条就放得越大，避免错过发车、取行李这类硬节点。
    // 只在旅行当天生效；加 `?now=HH:MM` 可强制进入该模式（供预览与自检脚本使用）。
    const TRIP_DATE = { sat: '2025-03-15', sun: '2025-03-16' };
    const LEAD_MIN = 60; // 提前多少分钟开始放大
    const FADE_MIN = 20; // 过点之后还提示多久
    const nowOverride = (() => {
      const raw = new URLSearchParams(location.search).get('now') || '';
      const m = raw.match(/^(\d{1,2}):(\d{2})$/);
      return m ? Number(m[1]) * 60 + Number(m[2]) : null;
    })();

    function clockOf(value) {
      const m = String(value == null ? '' : value).match(/(\d{1,2}):(\d{2})/);
      return m ? Number(m[1]) * 60 + Number(m[2]) : null;
    }
    function fmtClock(min) {
      return String(Math.floor(min / 60)).padStart(2, '0') + ':' + String(min % 60).padStart(2, '0');
    }

    // 这一条的硬节点 = 行上显示的钟点。刻意不从 step.time 里再挖一个钟点：那样「中午」
    // 「整个下午」这类行会在一个它们并没显示的时刻被放大，看着莫名其妙。
    //
    // 两个例外，时刻都读自权威 adopted，不在这里写死；每个节点自带它该显示的文字，
    // 于是「被放大的那一条，显示的必须是一个钟点」这条不变量在任何节点下都成立：
    //   · 去程那条显示「07:20 出门」，真正要防的是出门晚；但 09:05 发车同样不能错过——
    //     人到了站也可能逛散、看错检票口。所以它有第二个节点，临近时行上的字跟着换成发车时刻。
    //   · 返程那条写的是「傍晚返程」，只有 18:28 一个节点，直接显示钟点。
    function nodesOf(step) {
      const t = itinerary.adopted || {};
      const out = [];
      const shown = clockOf(step.shortTime);
      if (shown !== null) {
        out.push({ at: shown, text: (step.shortTime || '').match(/\d{1,2}:\d{2}/)[0] });
      }
      if (step.id === 'sat-arrival') {
        const dep = clockOf((t.trainOut || {}).dep);
        if (dep !== null) out.push({ at: dep, text: fmtClock(dep) + ' 发车' });
      }
      if (step.id === 'sun-return') {
        const dep = clockOf((t.trainBack || {}).dep);
        if (dep !== null) out.push({ at: dep, text: fmtClock(dep) });
      }
      return out;
    }

    // 当前分钟数；不是旅行当天就返回 null（整块提示都不出现）。
    function currentMinutes() {
      if (nowOverride !== null) return nowOverride;
      const now = new Date();
      const date = new Intl.DateTimeFormat('en-CA', {
        timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit'
      }).format(now);
      if (date !== TRIP_DATE[day.id]) return null;
      return clockOf(
        new Intl.DateTimeFormat('en-GB', {
          timeZone: 'Asia/Shanghai', hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
        }).format(now)
      );
    }

    // 只改样式和行上那个时刻的字，不重建列表——重建会打断滚动位置。
    // 返回正在逼近的那条步骤 id（没有就 null），并在它刚进入临近状态时把它滚进视野。
    let lastNudged = null;
    function paintProximity() {
      const list = $('#timeline');
      if (!list) return null;
      const now = currentMinutes();
      let best = null;
      for (const li of list.querySelectorAll('li')) {
        li.classList.remove('is-near', 'is-pass');
        li.style.removeProperty('--near');
        const timeEl = li.querySelector('.t-time');
        if (timeEl && timeEl.dataset.stable && timeEl.textContent !== timeEl.dataset.stable) {
          timeEl.textContent = timeEl.dataset.stable;
        }
        if (now === null) continue;
        const step = (day.steps || []).find((s) => s.id === li.dataset.stepId);
        if (!step) continue;
        // 一条行可能有多个节点（去程：07:20 出门 ＋ 09:05 发车）。取窗口内最该提示的那个：
        // 未过的优先；同为未过或同为已过时取更近的。
        let pick = null;
        for (const n of nodesOf(step)) {
          const g = n.at - now;
          if (g < -FADE_MIN || g > LEAD_MIN) continue;
          const upcoming = g > 0;
          if (!pick
            || (upcoming && !(pick.gap > 0))
            || (upcoming === pick.gap > 0 && Math.abs(g) < Math.abs(pick.gap))) {
            pick = { at: n.at, text: n.text, gap: g };
          }
        }
        if (!pick) continue;
        const gap = pick.gap;
        li.classList.add('is-near');
        li.style.setProperty('--near', (gap > 0 ? 1 - gap / LEAD_MIN : 1).toFixed(3));
        if (gap <= 0) li.classList.add('is-pass');
        // 行上的字跟着当前节点走——否则「放大」放的不是它显示的那个时刻。
        if (timeEl && timeEl.textContent !== pick.text) timeEl.textContent = pick.text;
        const upcoming = gap > 0;
        if (!best
          || (upcoming && !(best.gap > 0))
          || (upcoming === best.gap > 0 && Math.abs(gap) < Math.abs(best.gap))) {
          best = { gap, at: pick.at, id: step.id };
        }
      }
      const bestId = best ? best.id : null;
      // 放大了却在屏幕外，等于没提示——刚进入临近状态的那条自己跳进视野。
      // 只在「逼近的那条换了」时滚一次，否则会跟用户点选的那条抢滚动。
      if (bestId !== lastNudged) {
        lastNudged = bestId;
        const target = bestId && list.querySelector('li[data-step-id="' + bestId + '"]');
        if (target && target.scrollIntoView) target.scrollIntoView({ block: 'nearest' });
      }
      const nowEl = $('#timeline-now');
      if (!nowEl) return bestId;
      if (now === null) { nowEl.hidden = true; nowEl.textContent = ''; return bestId; }
      nowEl.hidden = false;
      nowEl.textContent = '现在 ' + fmtClock(now) + (best
        ? best.gap > 0
          ? ' · ' + best.gap + ' 分后 ' + fmtClock(best.at)
          : ' · ' + fmtClock(best.at) + ' 刚过 ' + (-best.gap) + ' 分'
        : '');
      return bestId;
    }

    function updateTimeline() {
      const steps = day.steps || [];
      const kicker = $('#timeline-kicker');
      if (kicker) kicker.textContent = day.id === 'sat' ? '周六 · 橘子洲时刻表' : '周日 · 岳麓书院时刻表';
      const list = $('#timeline');
      if (!list) return;
      list.replaceChildren();
      steps.forEach((s, idx) => {
        const li = document.createElement('li');
        if (s.id === selected.id) li.className = 'is-on';
        li.dataset.stepId = s.id; // 临近提示要按行回查步骤
        li.tabIndex = 0;
        const dot = document.createElement('span');
        dot.className = 't-dot';
        const time = document.createElement('span');
        time.className = 't-time';
        time.textContent = shortTimeLabel(s);
        // 临近提示会临时把这里的字换成当前节点的时刻（去程：出门 ↔ 发车），
        // 记下固定文案，好在不临近时换回来。
        time.dataset.stable = time.textContent;
        const title = document.createElement('span');
        title.className = 't-title';
        title.textContent = s.title || '';
        const line = document.createElement('span');
        line.className = 't-line';
        line.textContent = oneLiner(s);
        li.append(dot, time, title, line);

        if (s.id === selected.id) {
          const more = document.createElement('span');
          more.className = 't-more';
          const bits = (s.rows || []).slice(0, 3).map(([k, v]) => {
            let t = String(v || '');
            if (t.includes(' → ')) {
              const parts = t.split(' → ').map((x) => x.trim());
              t = parts[0] + ' → ' + parts[parts.length - 1].replace(/[；。].*$/, '');
            }
            t = t.split('；')[0].split('。')[0];
            if (t.length > 34) t = t.slice(0, 34) + '…';
            return k + '：' + t;
          });
          more.textContent = bits.join(' · ');
          li.append(more);
        }

        const pick = () => selectStep(s, true);
        li.addEventListener('click', pick);
        li.addEventListener('keydown', (e) => {
          if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); pick(); }
        });
        list.append(li);
      });
      // 先滚到选中那条，再让临近节点有机会抢走滚动——它比「当前选中」更要紧
      const on = list.querySelector('li.is-on');
      if (on && on.scrollIntoView) on.scrollIntoView({ block: 'nearest' });
      paintProximity();
    }

    function selectStep(step, changeView) {
      selected = step;
      updateTimeline();
      if (changeView) {
        view = step.view || 'core';
        document.querySelectorAll('[data-view]').forEach((b) => {
          b.classList.toggle('is-on', b.dataset.view === view);
        });
        fit();
        renderMap();
      } else {
        renderMap();
      }
    }

    function setDay(dayId) {
      day = itinerary.days.find((d) => d.id === dayId) || itinerary.days[0];
      document.querySelectorAll('[data-day]').forEach((b) => {
        const on = b.dataset.day === day.id;
        b.classList.toggle('is-on', on);
        b.setAttribute('aria-selected', on ? 'true' : 'false');
      });
      const next = day.steps.find((s) => s.id === day.defaultStep) || day.steps[0];
      updateDayTicket();
      selectStep(next, true);
    }

    function resize() {
      const rect = shell.getBoundingClientRect();
      const w = Math.max(280, Math.floor(rect.width) || 360);
      const h = Math.max(200, Math.floor(rect.height) || 300);
      if (w === width && h === height && projection) {
        renderMap();
        return;
      }
      width = w;
      height = h;
      fit();
      renderMap();
    }

    function updateDayTicket() {
      const head = $('#ticket-head');
      const main = $('#ticket-main');
      const sub = $('#ticket-sub');
      const card = $('#ticket-card');
      if (!head || !main || !sub || !card) return;
      const t = itinerary.adopted || {};
      if (day.id === 'sat') {
        const o = t.trainOut || { primary: '示例去程', dep: '武汉站 09:00', arr: '长沙南 10:15' };
        const depStr = clockOf(o.dep) !== null ? fmtClock(clockOf(o.dep)) : '09:00';
        const arrStr = clockOf(o.arr) !== null ? fmtClock(clockOf(o.arr)) : '10:15';
        head.textContent = '去 · 示例第一天';
        main.innerHTML =
          '<b>' + (o.primary || '示例去程') + '</b><span>' + depStr + '</span><i>→</i><span>' + arrStr + '</span>';
        sub.textContent = '武汉站 → 长沙南 · 示例待确认';
        card.dataset.tip = '合成示例，按实际票面和地点配置；无真实订单。';
        card.classList.remove('back');
        card.classList.add('out');
      } else {
        const r = t.trainBack || { primary: '示例返程', dep: '长沙南 18:30', arr: '武汉站 19:45' };
        const depStr = clockOf(r.dep) !== null ? fmtClock(clockOf(r.dep)) : '18:30';
        const arrStr = clockOf(r.arr) !== null ? fmtClock(clockOf(r.arr)) : '19:45';
        head.textContent = '回 · 示例第二天';
        main.innerHTML =
          '<b>' + (r.primary || '示例返程') + '</b><span>' + depStr + '</span><i>→</i><span>' + arrStr + '</span>';
        sub.textContent = '长沙南 → 武汉站 · 示例待确认';
        card.dataset.tip = '合成示例，按实际票面和地点配置；无真实订单。';
        card.classList.remove('out');
        card.classList.add('back');
      }
    }

    function setDockExtra(text, btn) {
      const extra = $('#dock-extra');
      const card = $('#ticket-card');
      const same = extra && !extra.hidden && extra.dataset.key === btn;
      if (card) card.setAttribute('aria-expanded', 'false');
      if (!extra) return;
      if (same || !text) {
        extra.hidden = true;
        extra.textContent = '';
        extra.dataset.key = '';
        return;
      }
      extra.hidden = false;
      extra.dataset.key = btn;
      extra.textContent = text;
      if (card && btn === 'ticket') card.setAttribute('aria-expanded', 'true');
    }

    const ticketCard = $('#ticket-card');
    if (ticketCard) {
      ticketCard.addEventListener('click', () => {
        setDockExtra(ticketCard.dataset.tip || '', 'ticket');
      });
    }

    document.querySelectorAll('[data-day]').forEach((b) => {
      b.addEventListener('click', () => setDay(b.dataset.day));
    });
    document.querySelectorAll('[data-view]').forEach((b) => {
      b.addEventListener('click', () => {
        view = b.dataset.view;
        document.querySelectorAll('[data-view]').forEach((x) => x.classList.toggle('is-on', x.dataset.view === view));
        fit();
        renderMap();
      });
    });
    document.querySelectorAll('[data-zoom]').forEach((b) => {
      b.addEventListener('click', () => svg.call(zoom.scaleBy, Number(b.dataset.zoom)));
    });
    const reset = $('#zoom-reset');
    if (reset) reset.addEventListener('click', () => {
      fit();
      renderMap();
    });

    const checksPanel = $('#checks');
    const openChecks = $('#open-checks');
    const closeChecks = $('#close-checks');
    const copyHotelBtn = $('#copy-hotel-btn');
    if (copyHotelBtn) {
      copyHotelBtn.addEventListener('click', async () => {
        const addr = '住宿区域示例（湘江中路）长沙市湘江中路住宿区域';
        try {
          await navigator.clipboard.writeText(addr);
          toast('已复制酒店地址');
        } catch (_) {
          toast('请长按复制：湘江中路住宿区域');
        }
      });
    }
    const CHECK_KEY = 'bw0047-h5-checks-v3';
    let checks = new Set();
    try {
      const raw = JSON.parse(localStorage.getItem(CHECK_KEY) || '[]');
      if (Array.isArray(raw)) raw.forEach((n) => { if (Number.isInteger(n) && n >= 0 && n < 4) checks.add(n); });
    } catch (_) {}
    function renderChecks() {
      document.querySelectorAll('#checks [data-check]').forEach((box) => {
        box.checked = checks.has(Number(box.dataset.check));
      });
    }
    document.querySelectorAll('#checks [data-check]').forEach((box) => {
      box.addEventListener('change', () => {
        const i = Number(box.dataset.check);
        if (box.checked) checks.add(i); else checks.delete(i);
        try { localStorage.setItem(CHECK_KEY, JSON.stringify([...checks])); } catch (_) {}
      });
    });
    if (openChecks && checksPanel) openChecks.addEventListener('click', () => {
      renderChecks();
      checksPanel.hidden = false;
    });
    if (closeChecks && checksPanel) closeChecks.addEventListener('click', () => { checksPanel.hidden = true; });
    if (checksPanel) checksPanel.addEventListener('click', (e) => { if (e.target === checksPanel) checksPanel.hidden = true; });

    updateDayTicket();
    updateTimeline();
    resize();
    if (typeof ResizeObserver !== 'undefined') new ResizeObserver(() => resize()).observe(shell);
    window.addEventListener('resize', resize);
    window.addEventListener('orientationchange', () => setTimeout(resize, 200));
    // 临近提示要跟着时间走。只刷样式、不重建列表，否则每 30 秒会打断一次滚动位置。
    setInterval(paintProximity, 30000);

    const err = document.getElementById('boot-error');
    if (err) err.hidden = true;
  } catch (e) {
    fail('初始化失败：' + (e && e.message ? e.message : e));
  }
})();
