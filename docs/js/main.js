/* ============================================================
   SILENTFIT — Main Website JavaScript
   ============================================================ */

document.addEventListener('DOMContentLoaded', () => {
  initNavigation();
  initThemeToggle();
  initPresetGallery();
  initWatchShowcase();
  initScrollAnimations();
  initSmoothScrolling();
});

// --- Navigation ---
function initNavigation() {
  const navToggle = document.getElementById('nav-toggle');
  const navLinks = document.getElementById('nav-links');

  if (navToggle && navLinks) {
    navToggle.addEventListener('click', () => {
      navLinks.classList.toggle('open');
    });

    // Close on link click
    navLinks.querySelectorAll('.nav-link').forEach(link => {
      link.addEventListener('click', () => {
        navLinks.classList.remove('open');
      });
    });
  }

  // Active link on scroll
  const sections = document.querySelectorAll('section[id]');
  window.addEventListener('scroll', () => {
    const scrollY = window.scrollY + 100;
    sections.forEach(section => {
      const top = section.offsetTop;
      const height = section.offsetHeight;
      const id = section.getAttribute('id');
      const link = document.querySelector(`.nav-link[href="#${id}"]`);
      if (link) {
        if (scrollY >= top && scrollY < top + height) {
          document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
          link.classList.add('active');
        }
      }
    });
  });
}

// --- Theme Toggle ---
function initThemeToggle() {
  const toggle = document.getElementById('theme-toggle');
  if (!toggle) return;

  const savedTheme = localStorage.getItem('silentfit-theme') || 'dark';
  document.documentElement.setAttribute('data-theme', savedTheme);
  updateThemeIcon(toggle, savedTheme);

  toggle.addEventListener('click', () => {
    const current = document.documentElement.getAttribute('data-theme') || 'dark';
    const next = current === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('silentfit-theme', next);
    updateThemeIcon(toggle, next);
  });
}

function updateThemeIcon(toggle, theme) {
  toggle.textContent = theme === 'dark' ? '◐' : '◑';
  toggle.setAttribute('aria-label', `Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`);
}

// --- Watch Showcase (renders preset faces) ---
function initWatchShowcase() {
  const showcaseCanvases = document.querySelectorAll('.watch-showcase canvas');
  const presetKeys = ['mono-led', 'instrument', 'terminal'];

  showcaseCanvases.forEach((canvas, i) => {
    const key = presetKeys[i % presetKeys.length];
    const preset = window.PRESETS[key];
    if (preset && canvas) {
      const ctx = canvas.getContext('2d');
      // Wait for fonts to load
      document.fonts.ready.then(() => {
        fitCanvas(canvas);
        window.WatchFaceRenderer.renderFace(ctx, preset);
      });
    }
  });

  // Also render the hero preview
  const heroCanvas = document.querySelector('.hero-watch-frame canvas');
  if (heroCanvas) {
    const ctx = heroCanvas.getContext('2d');
    document.fonts.ready.then(() => {
      fitCanvas(heroCanvas);
      const preset = window.PRESETS['mono-led'];
      if (preset) {
        window.WatchFaceRenderer.renderFace(ctx, preset);
      }
    });
  }
}

// --- Preset Gallery ---
function initPresetGallery() {
  const grid = document.getElementById('presets-grid');
  const filterBtns = document.querySelectorAll('.preset-filter');

  if (!grid) return;

  // Render all presets
  renderPresetCards(grid, 'all');

  // Filter buttons
  filterBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      filterBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      const category = btn.dataset.category;
      renderPresetCards(grid, category);
    });
  });
}

function renderPresetCards(grid, category) {
  grid.innerHTML = '';

  Object.entries(window.PRESETS).forEach(([key, preset]) => {
    if (category !== 'all' && preset.category !== category) return;

    const card = document.createElement('div');
    card.className = 'preset-card';
    card.dataset.preset = key;

    const canvas = document.createElement('canvas');
    canvas.width = 240;
    canvas.height = 280;
    card.appendChild(canvas);
    canvas.setAttribute('aria-label', `${preset.name} watch face`);

    const info = document.createElement('div');
    info.className = 'preset-card-info';
    const nameEl = document.createElement('span');
    nameEl.className = 'preset-card-name';
    nameEl.textContent = preset.name;
    info.appendChild(nameEl);
    card.appendChild(info);

    card.addEventListener('click', () => {
      // Navigate to designer with this preset
      window.location.href = `designer/index.html?preset=${key}`;
    });

    grid.appendChild(card);

    // Render after DOM insert
    document.fonts.ready.then(() => {
      fitCanvas(canvas);
      window.WatchFaceRenderer.renderFace(canvas.getContext('2d'), preset);
    });
  });
}

// Backing store matches the displayed size so faces stay sharp on retina screens.
function fitCanvas(canvas) {
  const rect = canvas.getBoundingClientRect();
  const w = Math.max(240, Math.round(rect.width * (window.devicePixelRatio || 1)));
  canvas.width = w;
  canvas.height = Math.round((w * 280) / 240);
}

// --- Scroll Animations ---
function initScrollAnimations() {
  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('animate-in');
        observer.unobserve(entry.target);
      }
    });
  }, {
    threshold: 0.1,
    rootMargin: '0px 0px -50px 0px'
  });

  document.querySelectorAll('.animate-on-scroll').forEach(el => {
    observer.observe(el);
  });
}

// --- Smooth Scrolling ---
function initSmoothScrolling() {
  document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', (e) => {
      e.preventDefault();
      const target = document.querySelector(anchor.getAttribute('href'));
      if (target) {
        target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    });
  });
}
