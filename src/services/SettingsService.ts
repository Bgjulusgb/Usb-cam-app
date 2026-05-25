import { Preferences } from '@capacitor/preferences';

export interface AppSettings {
  width: number;
  height: number;
  fps: number;
  format: string;
  lowLatencyMode: boolean;
  enableRtsp: boolean;
  rtspPort: number;
  theme: 'dark' | 'light';
}

const DEFAULTS: AppSettings = {
  width: 640,
  height: 480,
  fps: 30,
  format: 'MJPG',
  lowLatencyMode: true,
  enableRtsp: false,
  rtspPort: 8554,
  theme: 'dark',
};

const KEY = 'app_settings';

export class SettingsService {
  private cache: AppSettings = { ...DEFAULTS };

  async load(): Promise<AppSettings> {
    try {
      const { value } = await Preferences.get({ key: KEY });
      if (value) {
        this.cache = { ...DEFAULTS, ...JSON.parse(value) };
      }
    } catch {
      this.cache = { ...DEFAULTS };
    }
    return this.cache;
  }

  async save(settings: Partial<AppSettings>): Promise<AppSettings> {
    this.cache = { ...this.cache, ...settings };
    await Preferences.set({ key: KEY, value: JSON.stringify(this.cache) });
    return this.cache;
  }

  get(): AppSettings {
    return this.cache;
  }
}
