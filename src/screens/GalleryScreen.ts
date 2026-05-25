import type { MediaFile } from '../services/StorageService';
import type { StorageService } from '../services/StorageService';

export class GalleryScreen {
  private storage: StorageService;
  private items: MediaFile[] = [];

  constructor(storage: StorageService) {
    this.storage = storage;
  }

  async load(): Promise<void> {
    this.items = await this.storage.getAllMedia();
  }

  render(): string {
    return `
      <div class="app-bar">
        <button class="btn-icon" id="gallery-back">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="15 18 9 12 15 6"></polyline>
          </svg>
        </button>
        <h1>Galerie</h1>
        <div class="actions">
          <button class="btn-icon" id="gallery-refresh">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M1 4v6h6M23 20v-6h-6"/><path d="M20.49 9A9 9 0 005.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 013.51 15"/>
            </svg>
          </button>
        </div>
      </div>
      <div id="gallery-content" style="flex:1;overflow-y:auto">
        ${this.renderContent()}
      </div>
    `;
  }

  private renderContent(): string {
    if (this.items.length === 0) {
      return `
        <div class="empty-state" style="height:100%">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <rect x="3" y="3" width="18" height="18" rx="2"/>
            <circle cx="8.5" cy="8.5" r="1.5"/>
            <polyline points="21 15 16 10 5 21"/>
          </svg>
          <p>Noch keine Aufnahmen</p>
          <small>Videos und Fotos erscheinen hier</small>
        </div>
      `;
    }

    return `
      <div class="gallery-grid">
        ${this.items.map((item, i) => this.renderItem(item, i)).join('')}
      </div>
    `;
  }

  private renderItem(item: MediaFile, index: number): string {
    const size = this.storage.formatSize(item.size);
    return `
      <div class="gallery-item" data-index="${index}" data-uri="${item.uri}">
        ${item.isVideo
          ? `<div style="width:100%;height:100%;display:flex;align-items:center;justify-content:center;background:var(--card)">
               <svg viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.4)" stroke-width="1.5" style="width:32px;height:32px">
                 <path d="M15 10l4.553-2.069A1 1 0 0121 8.87v6.26a1 1 0 01-1.447.894L15 14M3 8a2 2 0 012-2h10a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V8z"/>
               </svg>
             </div>`
          : `<img src="${item.displaySrc}" loading="lazy" alt="${item.name}" onerror="this.style.display='none'" />`
        }
        ${item.isVideo ? `<div class="video-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:18px;height:18px"><circle cx="12" cy="12" r="10"/><polygon points="10 8 16 12 10 16 10 8"/></svg></div>` : ''}
        <div class="file-name">${item.name} · ${size}</div>
      </div>
    `;
  }

  attachEvents(container: HTMLElement): void {
    container.querySelector('#gallery-back')?.addEventListener('click', () => {
      document.dispatchEvent(new CustomEvent('navigate', { detail: 'home' }));
    });

    container.querySelector('#gallery-refresh')?.addEventListener('click', async () => {
      await this.load();
      const content = container.querySelector('#gallery-content');
      if (content) content.innerHTML = this.renderContent();
      this.attachItemEvents(container);
    });

    this.attachItemEvents(container);
  }

  private attachItemEvents(container: HTMLElement): void {
    let pressTimer: ReturnType<typeof setTimeout>;
    container.querySelectorAll('.gallery-item').forEach(el => {
      el.addEventListener('touchstart', () => {
        pressTimer = setTimeout(() => {
          const uri = (el as HTMLElement).dataset['uri'] ?? '';
          this.confirmDelete(uri, container);
        }, 600);
      });
      el.addEventListener('touchend', () => clearTimeout(pressTimer));
      el.addEventListener('contextmenu', (e) => {
        e.preventDefault();
        const uri = (el as HTMLElement).dataset['uri'] ?? '';
        this.confirmDelete(uri, container);
      });
    });
  }

  private confirmDelete(uri: string, container: HTMLElement): void {
    const overlay = document.createElement('div');
    overlay.className = 'dialog-overlay';
    const fileName = decodeURIComponent(uri.split('/').pop() ?? 'Datei');
    overlay.innerHTML = `
      <div class="dialog">
        <h2>Löschen</h2>
        <p>${fileName} löschen?</p>
        <div class="dialog-actions">
          <button class="btn-secondary" id="del-cancel">Abbrechen</button>
          <button class="btn-danger" id="del-confirm">Löschen</button>
        </div>
      </div>
    `;
    document.body.appendChild(overlay);
    overlay.querySelector('#del-cancel')?.addEventListener('click', () => overlay.remove());
    overlay.querySelector('#del-confirm')?.addEventListener('click', async () => {
      overlay.remove();
      await this.storage.deleteFile(uri);
      await this.load();
      const content = container.querySelector('#gallery-content');
      if (content) content.innerHTML = this.renderContent();
      this.attachItemEvents(container);
    });
  }
}
