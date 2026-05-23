import type { AppState } from '../App';
import { hex4 } from '../models/VidPidDatabase';
import type { UsbDeviceInfo } from '../models/DeviceProfile';

const ICONS = {
  uvc: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 10l4.553-2.069A1 1 0 0121 8.87v6.26a1 1 0 01-1.447.894L15 14M3 8a2 2 0 012-2h10a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V8z"/></svg>`,
  usb: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2v8M8 6l4-4 4 4M7 12h10a2 2 0 012 2v4a2 2 0 01-2 2H7a2 2 0 01-2-2v-4a2 2 0 012-2zM12 12v8"/></svg>`,
  refresh: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 4v6h6M23 20v-6h-6"/><path d="M20.49 9A9 9 0 005.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 013.51 15"/></svg>`,
  usbOff: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="1" y1="1" x2="23" y2="23"/><path d="M7 12h8.5M12 2v6M8 6l4-4 4 4M7 12H5a2 2 0 00-2 2v4a2 2 0 002 2h12"/></svg>`,
};

export class HomeScreen {
  private state: AppState;
  private onConnectDevice: (device: UsbDeviceInfo) => void;
  private onRefresh: () => void;

  constructor(
    state: AppState,
    onConnectDevice: (device: UsbDeviceInfo) => void,
    onRefresh: () => void,
  ) {
    this.state = state;
    this.onConnectDevice = onConnectDevice;
    this.onRefresh = onRefresh;
  }

  render(): string {
    const { devices, activeDevice, connectionStatus } = this.state;
    return `
      <div class="app-bar">
        <h1>USB Cam</h1>
        <div class="actions">
          <button class="btn-icon" id="home-refresh-btn" title="Scan for devices">
            ${ICONS.refresh}
          </button>
        </div>
      </div>

      ${this.renderStatusBanner()}

      <div class="device-list">
        ${activeDevice ? this.renderActiveCard(activeDevice) : ''}
        ${this.renderDeviceList(devices, activeDevice?.deviceKey)}
      </div>
    `;
  }

  private renderStatusBanner(): string {
    const { connectionStatus, activeDevice } = this.state;
    const map: Record<string, { cls: string; text: string }> = {
      connected: { cls: 'connected', text: `Verbunden: ${activeDevice?.name ?? ''}` },
      connecting: { cls: 'connecting', text: 'Verbinde...' },
      error: { cls: 'error', text: this.state.errorMessage ?? 'Fehler' },
      disconnected: { cls: 'disconnected', text: 'USB-Kamera via OTG-Kabel anschließen' },
    };
    const { cls, text } = map[connectionStatus] ?? map['disconnected'];
    return `
      <div class="status-banner ${cls}">
        <div class="status-dot"></div>
        <span>${text}</span>
      </div>
    `;
  }

  private renderActiveCard(device: UsbDeviceInfo): string {
    return `
      <div class="card" style="margin-bottom:12px">
        <div style="display:flex;align-items:center;gap:12px">
          <div class="device-icon">${device.isUvc ? ICONS.uvc : ICONS.usb}</div>
          <div style="flex:1">
            <div class="device-name">${device.name}</div>
            <div class="device-meta">${device.vendorId}:${device.productId}</div>
          </div>
          <span style="font-size:10px;padding:3px 8px;border-radius:10px;background:rgba(0,255,136,0.15);color:var(--success);font-weight:700">VERBUNDEN</span>
        </div>
        <div class="device-chips" style="margin-top:10px">
          ${device.formats.slice(0, 3).map(f => `<span class="chip">${f}</span>`).join('')}
          <span class="chip">${device.maxWidth}×${device.maxHeight}</span>
          <span class="chip">${device.maxFps}fps</span>
          ${device.hasAudio ? '<span class="chip audio">Audio</span>' : ''}
          <span class="chip">${device.isUvc ? 'UVC' : 'Non-UVC'}</span>
        </div>
        <div style="display:flex;gap:8px;margin-top:12px">
          <button class="btn-primary" style="flex:1" id="home-open-preview">Kamera öffnen</button>
          <button class="btn-secondary" id="home-disconnect">Trennen</button>
        </div>
      </div>
    `;
  }

  private renderDeviceList(devices: UsbDeviceInfo[], activeKey?: string): string {
    if (devices.length === 0) {
      return `
        <div class="empty-state">
          ${ICONS.usbOff}
          <p>Keine USB-Geräte gefunden</p>
          <small>USB-Kamera über OTG-Kabel anschließen<br/>Dann "Scan" tippen</small>
        </div>
      `;
    }

    return devices.map(d => `
      <div class="device-card ${d.deviceKey === activeKey ? 'active' : ''}" data-device-key="${d.deviceKey}">
        <div class="device-icon">${d.isUvc ? ICONS.uvc : ICONS.usb}</div>
        <div class="device-info">
          <div class="device-name">${d.name}</div>
          <div class="device-meta">${d.vendorId}:${d.productId} · ${d.maxWidth}×${d.maxHeight} · ${d.isUvc ? 'UVC' : 'Non-UVC'}</div>
          <div class="device-chips">
            ${d.formats.slice(0, 2).map(f => `<span class="chip">${f}</span>`).join('')}
            ${d.hasAudio ? '<span class="chip audio">Audio</span>' : ''}
          </div>
        </div>
        <div class="device-action">
          ${d.deviceKey === activeKey
            ? '<span style="font-size:10px;padding:3px 8px;border-radius:10px;background:rgba(0,212,255,0.15);color:var(--primary)">Aktiv</span>'
            : `<button class="btn-primary" style="font-size:12px;padding:7px 14px" data-connect="${d.deviceKey}">Verbinden</button>`
          }
        </div>
      </div>
    `).join('');
  }

  attachEvents(container: HTMLElement): void {
    container.querySelector('#home-refresh-btn')?.addEventListener('click', () => this.onRefresh());
    container.querySelector('#home-open-preview')?.addEventListener('click', () => {
      document.dispatchEvent(new CustomEvent('navigate', { detail: 'preview' }));
    });
    container.querySelector('#home-disconnect')?.addEventListener('click', () => {
      document.dispatchEvent(new CustomEvent('disconnectDevice'));
    });
    container.querySelectorAll('[data-connect]').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const key = (e.currentTarget as HTMLElement).dataset['connect'];
        const device = this.state.devices.find(d => d.deviceKey === key);
        if (device) this.onConnectDevice(device);
      });
    });
  }
}
