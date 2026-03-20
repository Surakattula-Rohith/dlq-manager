package com.dlqmanager.service;

import com.dlqmanager.model.entity.NotificationChannel;
import com.dlqmanager.model.enums.NotificationChannelType;
import com.dlqmanager.repository.NotificationChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationChannelService {

    private final NotificationChannelRepository notificationChannelRepository;
    private final NotificationService notificationService;

    public List<NotificationChannel> getAll() {
        return notificationChannelRepository.findAll();
    }

    public Optional<NotificationChannel> getById(UUID id) {
        return notificationChannelRepository.findById(id);
    }

    public NotificationChannel create(String name, NotificationChannelType type, String configuration) {
        NotificationChannel channel = new NotificationChannel();
        channel.setName(name);
        channel.setType(type);
        channel.setConfiguration(configuration);
        channel.setEnabled(true);
        return notificationChannelRepository.save(channel);
    }

    public NotificationChannel update(UUID id, String name, NotificationChannelType type,
                                       String configuration, boolean enabled) {
        NotificationChannel channel = notificationChannelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification channel not found: " + id));
        channel.setName(name);
        channel.setType(type);
        channel.setConfiguration(configuration);
        channel.setEnabled(enabled);
        return notificationChannelRepository.save(channel);
    }

    public void delete(UUID id) {
        notificationChannelRepository.deleteById(id);
    }

    public Map<String, Object> testChannel(UUID id) {
        NotificationChannel channel = notificationChannelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification channel not found: " + id));
        return notificationService.testChannel(channel);
    }
}
