import type { UsbDeviceInfo } from './models/DeviceProfile';
import { UsbCameraService } from './services/UsbCameraService';
import { StorageService } from './services/StorageService';
import { SettingsService } from './services/SettingsService';
import type { AppSettings } from './services/SettingsService';
import { HomeScreen } from './screens/HomeScreen';
import { PreviewScreen } from './screens/PreviewScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { GalleryScreen } from './screens/GalleryScreen';

// ─── Application State ──────────────────────────────────────────────────────
export interface AppState {
  devices: UsbDeviceInfo[];
  activeDevice?: UsbDeviceInfo;
  isPreviewing: boolean;
  isRecording: boolean;
  connectionStatus: 'disconnected' | 'connecting' | 'connected' | 'error';
  errorMessage?: string;
  currentScreen: 'home' | 'preview' | 'settings' | 'gallery';
}

// ─── Main Application ───────────────────────────────────────────────────────
export class App {
  private cameraService = new UsbCameraService();
  private storageService = new StorageService();
  private settingsService = new SettingsService();

  private state: AppState = {
    devices: [],
    isPreviewing: false,
    isRecording: false,
    connectionStatus: 'disconnected',
    currentScreen: 'home',
  };

  private settings!: AppSettings;
  private root!: HTMLElement;
  private previewScreenInstance?: PreviewScreen;
  private previewStreamUrl?: string;

  // ─── Boot ────────────────────────────────────────────────────────────────

  async init(rootEl: HTMLElement): Promise<void> {
    this.root = rootEl;
    this.settings = await this.settingsService.load();
    await this.storageService.init();
    this.applyTheme(this.settings.theme);

    this.setupCameraListeners();
    await this.cameraService.startListening();

    this.setupGlobalEvents();
    this.renderApp();
    this.navigateTo('home');
    this.refreshDevices();

    // Ask for runtime permissions (mic / notifications / media on Android 13+)
    // without blocking first paint; the system dialog waits for the user.
    // USB device permission is requested separately when a device is connected.
    void this.cameraService.requestAppPermissions().catch(() => false);
  }

  // ─── Camera event listeners ──────────────────────────────────────────────

  private setupCameraListeners(): void {
    this.cameraService.onDeviceConnected = (device) => {
      const idx = this.state.devices.findIndex(d => d.deviceKey === device.deviceKey);
      const updated = idx >= 0
        ? this.state.devices.map((d, i) => i === idx ? device : d)
        : [...this.state.devices, device];
      this.setState({ devices: updated, activeDevice: device, connectionStatus: 'connected' });
    };

    this.cameraService.onDeviceDisconnected = (key) => {
      const updated = this.state.devices.filter(d => d.deviceKey !== key);
      const wasActive = this.state.activeDevice?.deviceKey === key;
      this.setState({
        devices: updated,
        activeDevice: wasActive ? undefined : this.state.activeDevice,
        isPreviewing: wasActive ? false : this.state.isPreviewing,
        isRecording: wasActive ? false : this.state.isRecording,
        connectionStatus: wasActive ? 'disconnected' : this.state.connectionStatus,
      });
      if (wasActive) this.showToast('Gerät getrennt');
    };

    this.cameraService.onRecordingState = (isRecording, path) => {
      this.setState({ isRecording });
      if (!isRecording && path) this.showToast(`Aufnahme gespeichert: ${path.split('/').pop()}`);
      const previewEl = document.querySelector('#screen-preview');
      if (previewEl && this.previewScreenInstance) {
        this.previewScreenInstance.updateRecordingState(isRecording, previewEl as HTMLElement);
      }
    };

    this.cameraService.onError = (msg) => {
      this.setState({ errorMessage: msg, connectionStatus: 'error' });
      this.showToast(`Fehler: ${msg}`);
    };
  }

  // ─── Global DOM events ───────────────────────────────────────────────────

  private setupGlobalEvents(): void {
    document.addEventListener('navigate', (e) => {
      this.navigateTo((e as CustomEvent).detail);
    });
    document.addEventListener('disconnectDevice', async () => {
      await this.cameraService.disconnect();
      this.setState({ activeDevice: undefined, isPreviewing: false, isRecording: false, connectionStatus: 'disconnected' });
    });
  }

  // ─── State management ────────────────────────────────────────────────────

  private setState(patch: Partial<AppState>): void {
    this.state = { ...this.state, ...patch };
    this.rerenderCurrentScreen();
  }

  // ─── Navigation ──────────────────────────────────────────────────────────

  private navigateTo(screen: AppState['currentScreen']): void {
    this.state = { ...this.state, currentScreen: screen };
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    const el = document.querySelector(`#screen-${screen}`);
    if (el) el.classList.add('active');

    document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
    const navBtn = document.querySelector(`[data-nav="${screen}"]`);
    navBtn?.classList.add('active');

    this.renderScreen(screen);
  }

  // ─── Render ──────────────────────────────────────────────────────────────

  private renderApp(): void {
    this.root.innerHTML = `
      <div id="screen-home" class="screen"></div>
      <div id="screen-preview" class="screen"></div>
      <div id="screen-settings" class="screen"></div>
      <div id="screen-gallery" class="screen"></div>

      <nav class="bottom-nav" id="bottom-nav">
        <button class="nav-btn active" data-nav="home">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z"/>
            <polyline points="9 22 9 12 15 12 15 22"/>
          </svg>
          Start
        </button>
        <button class="nav-btn" data-nav="preview">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M15 10l4.553-2.069A1 1 0 0121 8.87v6.26a1 1 0 01-1.447.894L15 14M3 8a2 2 0 012-2h10a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V8z"/>
          </svg>
          Kamera
        </button>
        <button class="nav-btn" data-nav="gallery">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="2"/>
            <circle cx="8.5" cy="8.5" r="1.5"/>
            <polyline points="21 15 16 10 5 21"/>
          </svg>
          Galerie
        </button>
        <button class="nav-btn" data-nav="settings">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="3"/>
            <path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/>
          </svg>
          Settings
        </button>
      </nav>

      <div class="toast" id="toast"></div>
    `;

    document.querySelectorAll('.nav-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const nav = (btn as HTMLElement).dataset['nav'] as AppState['currentScreen'];
        this.navigateTo(nav);
      });
    });
  }

  private renderScreen(screen: AppState['currentScreen']): void {
    const el = document.querySelector(`#screen-${screen}`) as HTMLElement;
    if (!el) return;

    // Hide/show bottom nav (preview is fullscreen)
    const nav = document.querySelector('#bottom-nav') as HTMLElement;
    nav.style.display = screen === 'preview' ? 'none' : 'flex';

    switch (screen) {
      case 'home': {
        const home = new HomeScreen(
          this.state,
          (device) => this.connectDevice(device),
          () => this.refreshDevices(),
        );
        el.innerHTML = home.render();
        home.attachEvents(el);
        break;
      }
      case 'preview': {
        this.previewScreenInstance?.destroy();
        const preview = new PreviewScreen(
          this.state,
          () => this.toggleRecording(),
          () => this.capturePhoto(),
          () => this.navigateTo('home'),
        );
        this.previewScreenInstance = preview;
        el.innerHTML = preview.render();
        preview.attachEvents(el);
        if (this.state.activeDevice && !this.state.isPreviewing) {
          this.startPreview();
        } else if (this.state.isPreviewing && this.previewStreamUrl) {
          preview.setStream(this.previewStreamUrl);
        }
        break;
      }
      case 'settings': {
        const settings = new SettingsScreen(
          this.settings,
          async (patch) => {
            this.settings = await this.settingsService.save(patch);
            if (patch.theme) this.applyTheme(patch.theme);
          },
        );
        el.innerHTML = settings.render();
        settings.attachEvents(el);
        break;
      }
      case 'gallery': {
        const gallery = new GalleryScreen(this.storageService);
        el.innerHTML = gallery.render();
        gallery.load().then(() => {
          el.innerHTML = gallery.render();
          gallery.attachEvents(el);
        });
        gallery.attachEvents(el);
        break;
      }
    }
  }

  private rerenderCurrentScreen(): void {
    // The preview screen manages its own DOM imperatively (stream <img>,
    // recording timer) so re-rendering it on every state change would
    // reconnect the MJPEG stream and reset timers. Skip it here.
    if (this.state.currentScreen === 'preview') return;
    this.renderScreen(this.state.currentScreen);
  }

  // ─── Actions ─────────────────────────────────────────────────────────────

  private async refreshDevices(): Promise<void> {
    const devices = await this.cameraService.getConnectedDevices();
    this.setState({ devices });
  }

  private async connectDevice(device: UsbDeviceInfo): Promise<void> {
    this.setState({ connectionStatus: 'connecting' });
    const granted = await this.cameraService.requestPermission(device.deviceKey);
    if (!granted) {
      this.setState({ connectionStatus: 'error', errorMessage: 'USB-Berechtigung verweigert' });
      return;
    }
    const success = await this.cameraService.openDevice(device.deviceKey);
    if (success) {
      this.setState({ activeDevice: device, connectionStatus: 'connected' });
      this.navigateTo('preview');
    } else {
      this.setState({ connectionStatus: 'error', errorMessage: `Gerät konnte nicht geöffnet werden: ${device.name}` });
    }
  }

  private async startPreview(): Promise<void> {
    const s = this.settings;
    const result = await this.cameraService.startPreview({
      width: s.width,
      height: s.height,
      fps: s.fps,
      format: s.format,
      lanAccessible: s.enableRtsp,
    });
    if (result.success) {
      this.previewStreamUrl = result.streamUrl;
      this.setState({ isPreviewing: true });
      this.previewScreenInstance?.setStream(result.streamUrl);
      if (result.lanUrl) this.showToast(`LAN-Stream: ${result.lanUrl}`, 4000);
    }
  }

  private async toggleRecording(): Promise<void> {
    if (this.state.isRecording) {
      await this.cameraService.stopRecording();
      this.setState({ isRecording: false });
      const el = document.querySelector('#screen-preview') as HTMLElement | null;
      if (el && this.previewScreenInstance) {
        this.previewScreenInstance.updateRecordingState(false, el);
      }
    } else {
      const path = await this.cameraService.startRecording();
      if (path) {
        this.setState({ isRecording: true });
        const el = document.querySelector('#screen-preview') as HTMLElement | null;
        if (el && this.previewScreenInstance) {
          this.previewScreenInstance.updateRecordingState(true, el);
        }
        this.showToast(`Aufnahme: ${path.split('/').pop()}`);
      }
    }
  }

  private async capturePhoto(): Promise<void> {
    const path = await this.cameraService.capturePhoto();
    if (path) this.showToast(`Foto gespeichert: ${path.split('/').pop()}`);
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  private applyTheme(theme: 'dark' | 'light'): void {
    if (theme === 'light') {
      document.documentElement.setAttribute('data-theme', 'light');
    } else {
      document.documentElement.removeAttribute('data-theme');
    }
  }

  private showToast(message: string, duration = 2500): void {
    const toast = document.querySelector('#toast') as HTMLElement | null;
    if (!toast) return;
    toast.textContent = message;
    toast.classList.add('visible');
    setTimeout(() => toast.classList.remove('visible'), duration);
  }
}
