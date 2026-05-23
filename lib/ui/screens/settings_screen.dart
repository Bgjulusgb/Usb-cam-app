import 'package:flutter/material.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import '../../camera/camera_provider.dart';
import '../../camera/codec_config.dart';
import '../../main.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(cameraProvider);
    final settings = state.settings;
    final notifier = ref.read(cameraProvider.notifier);

    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _Section(
            title: 'Video',
            children: [
              _DropdownTile<Resolution>(
                label: 'Resolution',
                value: CodecConfig.standardResolutions.firstWhere(
                  (r) => r.width == settings.width && r.height == settings.height,
                  orElse: () => CodecConfig.standardResolutions[1],
                ),
                items: CodecConfig.standardResolutions,
                itemLabel: (r) => r.label,
                onChanged: (r) => notifier.updateSettings(
                  settings.copyWith(width: r.width, height: r.height),
                ),
              ),
              _DropdownTile<int>(
                label: 'Frame Rate',
                value: settings.fps,
                items: CodecConfig.standardFps,
                itemLabel: (f) => '$f fps',
                onChanged: (f) => notifier.updateSettings(settings.copyWith(fps: f)),
              ),
              _DropdownTile<String>(
                label: 'Format',
                value: settings.format,
                items: CodecConfig.uvcFormats,
                itemLabel: (f) => f,
                onChanged: (f) => notifier.updateSettings(settings.copyWith(format: f)),
              ),
            ],
          ),
          const SizedBox(height: 16),
          _Section(
            title: 'Performance',
            children: [
              SwitchListTile(
                title: const Text('Low Latency Mode'),
                subtitle: const Text('Reduce preview delay at cost of quality'),
                value: settings.lowLatencyMode,
                onChanged: (v) => notifier.updateSettings(settings.copyWith(lowLatencyMode: v)),
              ),
            ],
          ),
          const SizedBox(height: 16),
          _Section(
            title: 'RTSP Stream (LAN only)',
            children: [
              SwitchListTile(
                title: const Text('Enable RTSP Server'),
                subtitle: const Text('Stream via LAN (no internet)'),
                value: settings.enableRtsp,
                onChanged: (v) => notifier.updateSettings(settings.copyWith(enableRtsp: v)),
              ),
              if (settings.enableRtsp)
                ListTile(
                  title: const Text('RTSP Port'),
                  subtitle: Text('rtsp://[phone-ip]:${settings.rtspPort}/stream'),
                  trailing: SizedBox(
                    width: 80,
                    child: TextFormField(
                      initialValue: settings.rtspPort.toString(),
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(border: OutlineInputBorder()),
                      onChanged: (v) {
                        final port = int.tryParse(v);
                        if (port != null) notifier.updateSettings(settings.copyWith(rtspPort: port));
                      },
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 16),
          _Section(
            title: 'Appearance',
            children: [
              ListTile(
                title: const Text('Theme'),
                trailing: ToggleButtons(
                  isSelected: [
                    ref.watch(themeModeProvider) == ThemeMode.dark,
                    ref.watch(themeModeProvider) == ThemeMode.light,
                  ],
                  onPressed: (i) {
                    ref.read(themeModeProvider.notifier).state =
                        i == 0 ? ThemeMode.dark : ThemeMode.light;
                  },
                  children: const [
                    Padding(padding: EdgeInsets.symmetric(horizontal: 12), child: Text('Dark')),
                    Padding(padding: EdgeInsets.symmetric(horizontal: 12), child: Text('Light')),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          _Section(
            title: 'About',
            children: [
              const ListTile(
                title: Text('USB Cam'),
                subtitle: Text('Version 1.0.0\n100% local, no cloud, no tracking'),
              ),
              const ListTile(
                title: Text('Supported Devices'),
                subtitle: Text('UVC cameras, EasyCap (UTVF007, STK1160, EM2860, SMI2021),\nCapture cards (Elgato, ezcap), Endoscopes, IR cameras'),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _Section extends StatelessWidget {
  final String title;
  final List<Widget> children;

  const _Section({required this.title, required this.children});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: theme.textTheme.titleSmall?.copyWith(
          color: theme.colorScheme.primary,
          fontWeight: FontWeight.bold,
          letterSpacing: 0.5,
        )),
        const SizedBox(height: 8),
        Card(child: Column(children: children)),
      ],
    );
  }
}

class _DropdownTile<T> extends StatelessWidget {
  final String label;
  final T value;
  final List<T> items;
  final String Function(T) itemLabel;
  final ValueChanged<T> onChanged;

  const _DropdownTile({
    required this.label,
    required this.value,
    required this.items,
    required this.itemLabel,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      title: Text(label),
      trailing: DropdownButton<T>(
        value: value,
        onChanged: (v) { if (v != null) onChanged(v); },
        items: items.map((item) => DropdownMenuItem(
          value: item,
          child: Text(itemLabel(item), style: const TextStyle(fontSize: 14)),
        )).toList(),
        underline: const SizedBox(),
      ),
    );
  }
}
