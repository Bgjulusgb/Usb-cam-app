import 'dart:io';
import 'package:flutter/material.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import '../../storage/media_repository.dart';
import '../../core/di/injection.dart';

final mediaProvider = FutureProvider<List<MediaItem>>((ref) async {
  final repo = getIt<MediaRepository>();
  return repo.getAllMedia();
});

class GalleryScreen extends ConsumerWidget {
  const GalleryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final mediaAsync = ref.watch(mediaProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Gallery'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.invalidate(mediaProvider),
          ),
        ],
      ),
      body: mediaAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('Error: $e')),
        data: (items) {
          if (items.isEmpty) {
            return const Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.photo_library_outlined, size: 64, color: Colors.white24),
                  SizedBox(height: 16),
                  Text('No recordings yet', style: TextStyle(color: Colors.white38)),
                ],
              ),
            );
          }
          return GridView.builder(
            padding: const EdgeInsets.all(8),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 3,
              crossAxisSpacing: 4,
              mainAxisSpacing: 4,
            ),
            itemCount: items.length,
            itemBuilder: (context, i) {
              final item = items[i];
              return _MediaTile(
                item: item,
                onDelete: () async {
                  final repo = getIt<MediaRepository>();
                  await repo.delete(item);
                  ref.invalidate(mediaProvider);
                },
              );
            },
          );
        },
      ),
    );
  }
}

class _MediaTile extends StatelessWidget {
  final MediaItem item;
  final VoidCallback onDelete;

  const _MediaTile({required this.item, required this.onDelete});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onLongPress: () => _confirmDelete(context),
      child: Stack(
        fit: StackFit.expand,
        children: [
          Container(
            color: Colors.grey.shade900,
            child: item.isVideo
                ? const Icon(Icons.videocam, color: Colors.white38, size: 32)
                : Image.file(File(item.path), fit: BoxFit.cover,
                    errorBuilder: (_, __, ___) => const Icon(Icons.broken_image, color: Colors.white38)),
          ),
          if (item.isVideo)
            const Positioned(
              bottom: 4, left: 4,
              child: Icon(Icons.play_circle_outline, color: Colors.white70, size: 20),
            ),
          Positioned(
            bottom: 0, left: 0, right: 0,
            child: Container(
              color: Colors.black54,
              padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
              child: Text(
                item.name,
                style: const TextStyle(color: Colors.white70, fontSize: 9),
                overflow: TextOverflow.ellipsis,
                maxLines: 1,
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _confirmDelete(BuildContext context) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete'),
        content: Text('Delete ${item.name}?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          FilledButton(
            onPressed: () { Navigator.pop(ctx); onDelete(); },
            child: const Text('Delete'),
          ),
        ],
      ),
    );
  }
}
