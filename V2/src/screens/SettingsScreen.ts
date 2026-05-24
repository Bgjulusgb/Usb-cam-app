import type { AppSettings } from '../services/SettingsService';
import { SUPPORTED_RESOLUTIONS, SUPPORTED_FPS, SUPPORTED_FORMATS } from '../models/VidPidDatabase';

export class SettingsScreen {
  private settings: AppSettings;
  private onSave: (s: Partial<AppSettings>) => void;

  constructor(settings: AppSettings, onSave: (s: Partial<AppSettings>) => void) {
    this.settings = settings;
    this.onSave = onSave;
  }

  render(): string {
    const s = this.settings;
    const resOpts = SUPPORTED_RESOLUTIONS.map(r =>
      `<option value="${r.width}x${r.height}" ${r.width === s.width && r.height === s.height ? 'selected' : ''}>${r.label}</option>`
    ).join('');
    const fpsOpts = SUPPORTED_FPS.map(f =>
      `<option value="${f}" ${f === s.fps ? 'selected' : ''}>${f} fps</option>`
    ).join('');
    const fmtOpts = SUPPORTED_FORMATS.map(f =>
      `<option value="${f}" ${f === s.format ? 'selected' : ''}>${f}</option>`
    ).join('');

    return `
      <div class="app-bar">
        <button class="btn-icon" id="settings-back">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="15 18 9 12 15 6"></polyline>
          </svg>
        </button>
        <h1>Einstellungen</h1>
      </div>
      <div class="settings-scroll">

        <div>
          <div class="settings-section-title">Video</div>
          <div class="settings-card">
            <div class="settings-row">
              <div class="settings-label"><span>Auflösung</span></div>
              <select id="set-resolution">${resOpts}</select>
            </div>
            <div class="settings-row">
              <div class="settings-label"><span>Framerate</span></div>
              <select id="set-fps">${fpsOpts}</select>
            </div>
            <div class="settings-row">
              <div class="settings-label">
                <span>Format</span>
                <small id="set-format-desc">${this.formatDesc(s.format)}</small>
              </div>
              <select id="set-format">${fmtOpts}</select>
            </div>
          </div>
        </div>

        <div>
          <div class="settings-section-title">Performance</div>
          <div class="settings-card">
            <div class="settings-row">
              <div class="settings-label">
                <span>Low-Latency Modus</span>
                <small>Geringere Verzögerung, etwas weniger Qualität</small>
              </div>
              <label class="toggle-switch">
                <input type="checkbox" id="set-lowlatency" ${s.lowLatencyMode ? 'checked' : ''} />
                <span class="toggle-slider"></span>
              </label>
            </div>
          </div>
        </div>

        <div>
          <div class="settings-section-title">RTSP-Stream (nur LAN)</div>
          <div class="settings-card">
            <div class="settings-row">
              <div class="settings-label">
                <span>RTSP-Server aktivieren</span>
                <small>Kein Internet – nur lokales Netzwerk</small>
              </div>
              <label class="toggle-switch">
                <input type="checkbox" id="set-rtsp" ${s.enableRtsp ? 'checked' : ''} />
                <span class="toggle-slider"></span>
              </label>
            </div>
            <div class="settings-row" id="rtsp-port-row" style="${s.enableRtsp ? '' : 'display:none'}">
              <div class="settings-label">
                <span>RTSP Port</span>
                <small id="rtsp-url-hint">rtsp://[handy-ip]:${s.rtspPort}/stream</small>
              </div>
              <input type="number" id="set-rtsp-port" value="${s.rtspPort}"
                style="width:80px;background:var(--bg);color:var(--text);border:1px solid var(--border);border-radius:6px;padding:6px;font-size:13px"
              />
            </div>
          </div>
        </div>

        <div>
          <div class="settings-section-title">Design</div>
          <div class="settings-card">
            <div class="settings-row">
              <div class="settings-label"><span>Theme</span></div>
              <div style="display:flex;gap:8px">
                <button class="btn-secondary" id="theme-dark" style="${s.theme === 'dark' ? 'border-color:var(--primary)' : ''}">Dark</button>
                <button class="btn-secondary" id="theme-light" style="${s.theme === 'light' ? 'border-color:var(--primary)' : ''}">Light</button>
              </div>
            </div>
          </div>
        </div>

        <div>
          <div class="settings-section-title">Über die App</div>
          <div class="settings-card">
            <div class="settings-row">
              <div class="settings-label">
                <span>USB Cam V2</span>
                <small>Version 2.0.0 · 100% lokal · Kein Tracking</small>
              </div>
            </div>
            <div class="settings-row">
              <div class="settings-label">
                <span>Unterstützte Geräte</span>
                <small>UVC, EasyCap (UTVF007/STK1160/EM2860/SMI2021),<br/>Capture Cards, Endoskope, IR-Kameras</small>
              </div>
            </div>
          </div>
        </div>

      </div>
    `;
  }

  attachEvents(container: HTMLElement): void {
    container.querySelector('#settings-back')?.addEventListener('click', () => {
      document.dispatchEvent(new CustomEvent('navigate', { detail: 'home' }));
    });

    const save = () => {
      const res = (container.querySelector('#set-resolution') as HTMLSelectElement).value.split('x');
      const fps = +(container.querySelector('#set-fps') as HTMLSelectElement).value;
      const format = (container.querySelector('#set-format') as HTMLSelectElement).value;
      const lowLatency = (container.querySelector('#set-lowlatency') as HTMLInputElement).checked;
      const rtsp = (container.querySelector('#set-rtsp') as HTMLInputElement).checked;
      const rtspPort = +(container.querySelector('#set-rtsp-port') as HTMLInputElement).value;
      this.onSave({ width: +res[0]!, height: +res[1]!, fps, format, lowLatencyMode: lowLatency, enableRtsp: rtsp, rtspPort });
    };

    ['set-resolution', 'set-fps', 'set-lowlatency', 'set-rtsp', 'set-rtsp-port'].forEach(id => {
      container.querySelector(`#${id}`)?.addEventListener('change', save);
    });

    container.querySelector('#set-format')?.addEventListener('change', (e) => {
      const val = (e.target as HTMLSelectElement).value;
      const desc = container.querySelector('#set-format-desc');
      if (desc) desc.textContent = this.formatDesc(val);
      save();
    });

    const rtspToggle = container.querySelector('#set-rtsp') as HTMLInputElement | null;
    const rtspRow = container.querySelector('#rtsp-port-row') as HTMLElement | null;
    rtspToggle?.addEventListener('change', () => {
      if (rtspRow) rtspRow.style.display = rtspToggle.checked ? '' : 'none';
    });

    container.querySelector('#set-rtsp-port')?.addEventListener('input', (e) => {
      const port = +(e.target as HTMLInputElement).value;
      const hint = container.querySelector('#rtsp-url-hint');
      if (hint) hint.textContent = `rtsp://[handy-ip]:${port}/stream`;
    });

    container.querySelector('#theme-dark')?.addEventListener('click', () => {
      this.onSave({ theme: 'dark' });
      document.documentElement.removeAttribute('data-theme');
    });
    container.querySelector('#theme-light')?.addEventListener('click', () => {
      this.onSave({ theme: 'light' });
      document.documentElement.setAttribute('data-theme', 'light');
    });
  }

  private formatDesc(format: string): string {
    const map: Record<string, string> = {
      MJPG: 'Motion JPEG – am kompatibelsten',
      YUY2: 'Raw YCbCr 4:2:2 – niedriger CPU-Verbrauch',
      H264: 'H.264 – Hardware-komprimiert',
      H265: 'H.265/HEVC – hohe Effizienz (Android 5+)',
      NV12: 'NV12 – GPU-optimiert',
      P010: 'P010 – 10-Bit HDR',
    };
    return map[format] ?? format;
  }
}
