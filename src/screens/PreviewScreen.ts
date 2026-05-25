import type { AppState } from '../App';

export class PreviewScreen {
  private state: AppState;
  private hideControlsTimer?: ReturnType<typeof setTimeout>;
  private filterVisible = false;
  private recTimer?: ReturnType<typeof setInterval>;
  private recSeconds = 0;

  private onRecord: () => void;
  private onCapture: () => void;
  private onBack: () => void;

  constructor(
    state: AppState,
    onRecord: () => void,
    onCapture: () => void,
    onBack: () => void,
  ) {
    this.state = state;
    this.onRecord = onRecord;
    this.onCapture = onCapture;
    this.onBack = onBack;
  }

  render(): string {
    const { activeDevice, isRecording } = this.state;
    return `
      <div class="preview-container" id="preview-wrap">
        <img id="preview-stream" class="preview-stream" alt="Live preview" />

        <div class="preview-placeholder" id="preview-placeholder">
          <div class="spinner"></div>
          <p>${activeDevice ? `${activeDevice.name}\nVorschau wird gestartet...` : 'Kein Gerät verbunden'}</p>
        </div>

        <div class="rec-indicator" id="rec-indicator">
          <div class="rec-dot"></div>
          REC <span id="rec-time">00:00</span>
        </div>

        <!-- Filter panel -->
        <div class="filter-panel" id="filter-panel">
          <h3>Filter</h3>
          <div class="filter-row">
            <div class="filter-label"><span>Helligkeit</span><span id="brightness-val">0</span></div>
            <input type="range" id="filter-brightness" min="-100" max="100" value="0" />
          </div>
          <div class="filter-row">
            <div class="filter-label"><span>Kontrast</span><span id="contrast-val">100</span></div>
            <input type="range" id="filter-contrast" min="0" max="300" value="100" />
          </div>
          <div class="filter-row">
            <div class="filter-label"><span>Sättigung</span><span id="saturation-val">100</span></div>
            <input type="range" id="filter-saturation" min="0" max="200" value="100" />
          </div>
          <div class="filter-toggle">
            <span>Graustufen</span>
            <label class="toggle-switch">
              <input type="checkbox" id="filter-grayscale" />
              <span class="toggle-slider"></span>
            </label>
          </div>
        </div>

        <div class="preview-overlay" id="preview-controls">
          <!-- Top -->
          <div class="preview-top">
            <button class="btn-icon" id="preview-back">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="15 18 9 12 15 6"></polyline>
              </svg>
            </button>
            <span class="preview-title">${activeDevice?.name ?? 'Vorschau'}</span>
            <button class="btn-icon" id="preview-filter-btn">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="4" y1="6" x2="20" y2="6"/><line x1="8" y1="12" x2="16" y2="12"/>
                <line x1="10" y1="18" x2="14" y2="18"/>
              </svg>
            </button>
          </div>

          <!-- Bottom -->
          <div class="preview-bottom">
            <button class="btn-icon" id="preview-gallery-btn">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/>
                <polyline points="21 15 16 10 5 21"/>
              </svg>
            </button>

            <div class="record-btn-wrap">
              <button class="record-btn ${isRecording ? 'recording' : ''}" id="record-btn">
                <div class="record-inner"></div>
              </button>
            </div>

            <button class="btn-icon" id="capture-btn">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M23 19a2 2 0 01-2 2H3a2 2 0 01-2-2V8a2 2 0 012-2h4l2-3h6l2 3h4a2 2 0 012 2z"/>
                <circle cx="12" cy="13" r="4"/>
              </svg>
            </button>
          </div>
        </div>
      </div>
    `;
  }

  attachEvents(container: HTMLElement): void {
    const wrap = container.querySelector('#preview-wrap') as HTMLElement;
    const controls = container.querySelector('#preview-controls') as HTMLElement;
    const filterPanel = container.querySelector('#filter-panel') as HTMLElement;

    // Show/hide controls on tap
    wrap.addEventListener('click', (e) => {
      if ((e.target as HTMLElement).closest('button, input, .filter-panel')) return;
      const visible = controls.style.opacity !== '0';
      controls.style.opacity = visible ? '0' : '1';
      controls.style.pointerEvents = visible ? 'none' : 'auto';
      if (!visible) this.scheduleHide(controls);
    });
    this.scheduleHide(controls);

    container.querySelector('#preview-back')?.addEventListener('click', () => this.onBack());
    container.querySelector('#record-btn')?.addEventListener('click', () => this.onRecord());
    container.querySelector('#capture-btn')?.addEventListener('click', () => this.onCapture());
    container.querySelector('#preview-gallery-btn')?.addEventListener('click', () => {
      document.dispatchEvent(new CustomEvent('navigate', { detail: 'gallery' }));
    });
    container.querySelector('#preview-filter-btn')?.addEventListener('click', () => {
      this.filterVisible = !this.filterVisible;
      filterPanel.classList.toggle('visible', this.filterVisible);
    });

    // Filter sliders apply CSS/GPU filters to the live MJPEG <img>.
    const stream = container.querySelector('#preview-stream') as HTMLImageElement | null;
    const updateFilter = () => {
      const b = +(container.querySelector('#filter-brightness') as HTMLInputElement).value;
      const c = +(container.querySelector('#filter-contrast') as HTMLInputElement).value;
      const s = +(container.querySelector('#filter-saturation') as HTMLInputElement).value;
      const g = (container.querySelector('#filter-grayscale') as HTMLInputElement).checked;
      (container.querySelector('#brightness-val') as HTMLElement).textContent = String(b);
      (container.querySelector('#contrast-val') as HTMLElement).textContent = String(c);
      (container.querySelector('#saturation-val') as HTMLElement).textContent = String(s);
      if (stream) {
        stream.style.filter = g
          ? 'grayscale(1)'
          : `brightness(${1 + b / 100}) contrast(${c / 100}) saturate(${s / 100})`;
      }
    };
    ['filter-brightness', 'filter-contrast', 'filter-saturation', 'filter-grayscale'].forEach(id => {
      container.querySelector(`#${id}`)?.addEventListener('input', updateFilter);
    });
  }

  /** Point the live <img> at the local MJPEG stream URL and hide the spinner. */
  setStream(url: string): void {
    const img = document.querySelector('#preview-stream') as HTMLImageElement | null;
    const placeholder = document.querySelector('#preview-placeholder') as HTMLElement | null;
    if (!img) return;
    // Cache-bust so a re-open forces a fresh multipart connection.
    img.src = `${url}${url.includes('?') ? '&' : '?'}t=${Date.now()}`;
    img.onload = () => { if (placeholder) placeholder.style.display = 'none'; };
    img.onerror = () => { if (placeholder) placeholder.style.display = 'flex'; };
  }

  updateRecordingState(isRecording: boolean, container: HTMLElement): void {
    const btn = container.querySelector('#record-btn');
    const indicator = container.querySelector('#rec-indicator') as HTMLElement | null;
    btn?.classList.toggle('recording', isRecording);
    if (indicator) indicator.classList.toggle('visible', isRecording);

    if (isRecording) {
      this.recSeconds = 0;
      this.recTimer = setInterval(() => {
        this.recSeconds++;
        const m = Math.floor(this.recSeconds / 60).toString().padStart(2, '0');
        const s = (this.recSeconds % 60).toString().padStart(2, '0');
        const el = container.querySelector('#rec-time');
        if (el) el.textContent = `${m}:${s}`;
      }, 1000);
    } else {
      if (this.recTimer) clearInterval(this.recTimer);
    }
  }

  private scheduleHide(controls: HTMLElement): void {
    clearTimeout(this.hideControlsTimer);
    this.hideControlsTimer = setTimeout(() => {
      controls.style.opacity = '0';
      controls.style.pointerEvents = 'none';
    }, 4000);
  }

  destroy(): void {
    clearTimeout(this.hideControlsTimer);
    if (this.recTimer) clearInterval(this.recTimer);
  }
}
