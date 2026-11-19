package com.medichannel.repository;

import com.medichannel.entity.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    Optional<Room> findByName(String name);

    Optional<Room> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    List<Room> findByActiveTrueOrderByNameAsc();

    Page<Room> findByNameContainingIgnoreCase(String name, Pageable pageable);
}