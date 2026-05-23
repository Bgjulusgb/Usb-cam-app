import { Filesystem, Directory, Encoding } from '@capacitor/filesystem';
import UsbCameraPlugin from '../plugins/usb-camera.plugin';

export interface MediaFile {
  name: string;
  path: string;
  isVideo: boolean;
  size: number;
  modifiedAt: number;
}

export class StorageService {
  private basePath = '';

  async init(): Promise<void> {
    try {
      const { path } = await UsbCameraPlugin.getStoragePath();
      this.basePath = path;
    } catch {
      this.basePath = '';
    }
  }

  async getVideos(): Promise<MediaFile[]> {
    return this.listDir('Videos', ['.mp4', '.mkv', '.avi']);
  }

  async getImages(): Promise<MediaFile[]> {
    return this.listDir('Images', ['.jpg', '.jpeg', '.png']);
  }

  async getAllMedia(): Promise<MediaFile[]> {
    const [videos, images] = await Promise.all([this.getVideos(), this.getImages()]);
    return [...videos, ...images].sort((a, b) => b.modifiedAt - a.modifiedAt);
  }

  async deleteFile(path: string): Promise<boolean> {
    try {
      await Filesystem.deleteFile({ path });
      return true;
    } catch {
      return false;
    }
  }

  private async listDir(subDir: string, extensions: string[]): Promise<MediaFile[]> {
    try {
      const dirPath = this.basePath ? `${this.basePath}/${subDir}` : subDir;
      const { files } = await Filesystem.readdir({ path: dirPath, directory: Directory.External });
      return files
        .filter(f => extensions.some(ext => f.name.toLowerCase().endsWith(ext)))
        .map(f => ({
          name: f.name,
          path: `${dirPath}/${f.name}`,
          isVideo: ['.mp4', '.mkv', '.avi'].some(ext => f.name.toLowerCase().endsWith(ext)),
          size: f.size ?? 0,
          modifiedAt: f.mtime ?? Date.now(),
        }))
        .sort((a, b) => b.modifiedAt - a.modifiedAt);
    } catch {
      return [];
    }
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  }
}
