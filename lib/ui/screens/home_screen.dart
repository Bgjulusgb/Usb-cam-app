import 'package:flutter/material.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import '../../camera/camera_provider.dart';
import '../../camera/camera_service.dart';
import '../widgets/device_status_card.dart';
import '../widgets/usb_device_list.dart';
import '../../main.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  @override
  void initState() {
    super.initState();
    Future.microtask(() => ref.read(cameraProvider.notifier).refreshDevices());
  }

  @override
  Widget build(BuildContext context) {
    final cameraState = ref.watch(cameraProvider);
    final theme = Theme.of(context);

    ref.listen(cameraProvider, (prev, next) {
      if (next.errorMessage != null && prev?.errorMessage != next.errorMessage) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(next.errorMessage!),
            backgroundColor: theme.colorScheme.error,
            action: SnackBarAction(
              label: 'OK',
              textColor: Colors.white,
              onPressed: () => ref.read(cameraProvider.notifier).clearError(),
            ),
          ),
        );
      }
    });

    return Scaffold(
      appBar: AppBar(
        title: const Text('USB Cam'),
        actions: [
          IconButton(
            icon: const Icon(Icons.photo_library),
            tooltip: 'Gallery',
            onPressed: () => Navigator.pushNamed(context, '/gallery'),
          ),
          IconButton(
            icon: const Icon(Icons.settings),
            tooltip: 'Settings',
            onPressed: () => Navigator.pushNamed(context, '/settings'),
          ),
          IconButton(
            icon: Icon(
              ref.watch(themeModeProvider) == ThemeMode.dark
                  ? Icons.light_mode
                  : Icons.dark_mode,
            ),
            onPressed: () {
              final current = ref.read(themeModeProvider);
              ref.read(themeModeProvider.notifier).state =
                  current == ThemeMode.dark ? ThemeMode.light : ThemeMode.dark;
            },
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.read(cameraProvider.notifier).refreshDevices(),
        child: CustomScrollView(
          slivers: [
            SliverToBoxAdapter(
              child: _StatusBanner(state: cameraState),
            ),
            SliverPadding(
              padding: const EdgeInsets.all(16),
              sliver: SliverToBoxAdapter(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (cameraState.activeDevice != null) ...[
                      DeviceStatusCard(device: cameraState.activeDevice!),
                      const SizedBox(height: 16),
                      _QuickActions(state: cameraState),
                      const SizedBox(height: 24),
                    ],
                    Text(
                      'USB Devices (${cameraState.devices.length})',
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 8),
                  ],
                ),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              sliver: UsbDeviceList(
                devices: cameraState.devices,
                activeDeviceKey: cameraState.activeDevice?.deviceKey,
                onConnect: (device) => _connectDevice(context, device),
              ),
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => ref.read(cameraProvider.notifier).refreshDevices(),
        icon: const Icon(Icons.refresh),
        label: const Text('Scan'),
      ),
    );
  }

  Future<void> _connectDevice(BuildContext context, UsbDeviceInfo device) async {
    final notifier = ref.read(cameraProvider.notifier);
    final success = await notifier.connectDevice(device);
    if (success && mounted) {
      Navigator.pushNamed(context, '/preview');
    }
  }
}

class _StatusBanner extends StatelessWidget {
  final CameraState state;
  const _StatusBanner({required this.state});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final (color, icon, text) = switch (state.connectionStatus) {
      ConnectionStatus.connected => (Colors.green, Icons.usb, 'Connected: ${state.activeDevice?.name ?? ""}'),
      ConnectionStatus.connecting => (Colors.orange, Icons.sync, 'Connecting...'),
      ConnectionStatus.error => (theme.colorScheme.error, Icons.error_outline, state.errorMessage ?? 'Error'),
      ConnectionStatus.disconnected => (Colors.grey, Icons.usb_off, 'Plug in a USB Camera via OTG'),
    };

    return Container(
      width: double.infinity,
      color: color.withOpacity(0.15),
      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 16),
      child: Row(
        children: [
          Icon(icon, color: color, size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Text(text, style: TextStyle(color: color, fontSize: 13)),
          ),
        ],
      ),
    );
  }
}

class _QuickActions extends ConsumerWidget {
  final CameraState state;
  const _QuickActions({required this.state});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Row(
      children: [
        Expanded(
          child: FilledButton.icon(
            onPressed: () => Navigator.pushNamed(context, '/preview'),
            icon: const Icon(Icons.videocam),
            label: const Text('Open Camera'),
          ),
        ),
        const SizedBox(width: 12),
        OutlinedButton.icon(
          onPressed: () async {
            await ref.read(cameraProvider.notifier).disconnect();
          },
          icon: const Icon(Icons.usb_off),
          label: const Text('Disconnect'),
        ),
      ],
    );
  }
}
