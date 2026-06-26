package com.pps.profilesystem.Repository;

import com.pps.profilesystem.Entity.User;
import com.pps.profilesystem.Entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByUserAndIsOnlineTrue(User user);

    @Query("SELECT us FROM UserSession us LEFT JOIN FETCH us.user WHERE us.isOnline = true")
    List<UserSession> findByIsOnlineTrue();

    List<UserSession> findByUser(User user);

    void deleteByUser(User user);
}
