import 'package:flutter/material.dart';
import '../../camera/camera_service.dart';

class UsbDeviceList extends StatelessWidget {
  final List<UsbDeviceInfo> devices;
  final String? activeDeviceKey;
  final void Function(UsbDeviceInfo) onConnect;

  const UsbDeviceList({
    super.key,
    required this.devices,
    this.activeDeviceKey,
    required this.onConnect,
  });

  @override
  Widget build(BuildContext context) {
    if (devices.isEmpty) {
      return SliverToBoxAdapter(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 40),
          child: Column(
            children: [
              Icon(Icons.usb_off, size: 64, color: Colors.grey.shade600),
              const SizedBox(height: 16),
              Text(
                'No USB devices detected',
                style: TextStyle(color: Colors.grey.shade500, fontSize: 16),
              ),
              const SizedBox(height: 8),
              Text(
                'Connect a USB camera via OTG cable',
                style: TextStyle(color: Colors.grey.shade600, fontSize: 13),
              ),
            ],
          ),
        ),
      );
    }

    return SliverList(
      delegate: SliverChildBuilderDelegate(
        (context, i) => _DeviceTile(
          device: devices[i],
          isActive: devices[i].deviceKey == activeDeviceKey,
          onConnect: () => onConnect(devices[i]),
        ),
        childCount: devices.length,
      ),
    );
  }
}

class _DeviceTile extends StatelessWidget {
  final UsbDeviceInfo device;
  final bool isActive;
  final VoidCallback onConnect;

  const _DeviceTile({
    required this.device,
    required this.isActive,
    required this.onConnect,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: isActive
            ? BorderSide(color: theme.colorScheme.primary, width: 2)
            : BorderSide.none,
      ),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: theme.colorScheme.primary.withOpacity(0.15),
          child: Icon(
            device.isUvc ? Icons.videocam : Icons.usb,
            color: theme.colorScheme.primary,
          ),
        ),
        title: Text(device.name, maxLines: 1, overflow: TextOverflow.ellipsis),
        subtitle: Text(
          '${device.vendorId}:${device.productId} · '
          '${device.maxWidth}×${device.maxHeight} · '
          '${device.isUvc ? "UVC" : "Non-UVC"}',
          style: const TextStyle(fontSize: 11),
        ),
        trailing: isActive
            ? Chip(
                label: const Text('Active'),
                backgroundColor: theme.colorScheme.primary.withOpacity(0.2),
                labelStyle: TextStyle(color: theme.colorScheme.primary, fontSize: 11),
                padding: EdgeInsets.zero,
              )
            : FilledButton.tonal(
                onPressed: onConnect,
                child: const Text('Connect'),
              ),
        onTap: isActive ? null : onConnect,
      ),
    );
  }
}
