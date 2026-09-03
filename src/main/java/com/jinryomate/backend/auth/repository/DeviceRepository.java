package com.jinryomate.backend.auth.repository;

import com.jinryomate.backend.auth.entity.Device;
import com.jinryomate.backend.auth.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByPushToken(String pushToken);

    List<Device> findAllByUser(User user);

    void deleteAllByUser(User user);
}
