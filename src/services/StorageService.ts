import { Filesystem, Directory } from '@capacitor/filesystem';
import { Capacitor } from '@capacitor/core';

export interface MediaFile {
  name: string;
  /** Relative path under the external app dir, e.g. "Videos/VID_x.mp4". */
  path: string;
  /** Native file:// URI returned by Capacitor (used for delete). */
  uri: string;
  /** WebView-displayable src (convertFileSrc of the uri). */
  displaySrc: string;
  isVideo: boolean;
  size: number;
  modifiedAt: number;
}

const VIDEO_EXT = ['.mp4', '.mkv', '.avi'];
const IMAGE_EXT = ['.jpg', '.jpeg', '.png'];

export class StorageService {
  async init(): Promise<void> {
    // Ensure the media folders exist so readdir never throws on first launch.
    for (const dir of ['Videos', 'Images']) {
      try {
        await Filesystem.mkdir({ path: dir, directory: Directory.External, recursive: true });
      } catch {
        // already exists – ignore
      }
    }
  }

  getVideos(): Promise<MediaFile[]> {
    return this.listDir('Videos', VIDEO_EXT);
  }

  getImages(): Promise<MediaFile[]> {
    return this.listDir('Images', IMAGE_EXT);
  }

  async getAllMedia(): Promise<MediaFile[]> {
    const [videos, images] = await Promise.all([this.getVideos(), this.getImages()]);
    return [...videos, ...images].sort((a, b) => b.modifiedAt - a.modifiedAt);
  }

  async deleteFile(uri: string): Promise<boolean> {
    try {
      await Filesystem.deleteFile({ path: uri });
      return true;
    } catch {
      return false;
    }
  }

  private async listDir(subDir: string, extensions: string[]): Promise<MediaFile[]> {
    try {
      const { files } = await Filesystem.readdir({ path: subDir, directory: Directory.External });
      return files
        .filter(f => extensions.some(ext => f.name.toLowerCase().endsWith(ext)))
        .map(f => ({
          name: f.name,
          path: `${subDir}/${f.name}`,
          uri: f.uri,
          displaySrc: Capacitor.convertFileSrc(f.uri),
          isVideo: VIDEO_EXT.some(ext => f.name.toLowerCase().endsWith(ext)),
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
